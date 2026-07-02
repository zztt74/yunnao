import http from 'node:http'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

const repoRoot = path.resolve(import.meta.dirname, '..', '..')
const backendBaseUrl = process.env.BACKEND_BASE_URL ?? 'http://localhost:18080'
const timeoutMs = Number(process.env.AI_FAULT_TIMEOUT_MS ?? 15000)
const username = process.env.AI_FAULT_USERNAME ?? 'doctor_chen_mingyuan'
const password = process.env.AI_FAULT_PASSWORD ?? 'DoctorSeed9!2026'

function authHeaders(token) {
  return { authorization: `Bearer ${token}` }
}

async function request(url, options = {}) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
      headers: {
        ...(options.body ? { 'content-type': 'application/json' } : {}),
        ...options.headers,
      },
    })
    const text = await response.text()
    const body = text ? JSON.parse(text) : null
    if (!response.ok) {
      const error = new Error(`${options.method ?? 'GET'} ${url} -> ${response.status}`)
      error.status = response.status
      error.body = body
      throw error
    }
    return body?.data ?? body
  } finally {
    clearTimeout(timer)
  }
}

async function waitForBackend() {
  const startedAt = Date.now()
  let lastError
  while (Date.now() - startedAt < 120000) {
    try {
      await request(`${backendBaseUrl}/actuator/health`, { method: 'GET' })
      return
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, 2000))
    }
  }
  throw new Error(`backend did not become healthy: ${lastError?.message ?? 'timeout'}`)
}

function composeUp(env) {
  execFileSync('docker', ['compose', 'up', '-d', '--no-deps', '--build', '--force-recreate', 'backend'], {
    cwd: repoRoot,
    env,
    stdio: 'inherit',
  })
}

function fakeProviderResponse(mode) {
  if (mode === 'invalid-schema') {
    return {
      choices: [
        {
          message: {
            content: JSON.stringify({ notTheExpectedDiagnosisSchema: true }),
          },
        },
      ],
    }
  }
  return {
    choices: [
      {
        message: {
          content: JSON.stringify({
            possibleDiagnoses: [
              {
                diagnosisCode: 'J06.9',
                diagnosisName: 'Acute upper respiratory infection',
                confidence: 'LOW',
                explanation: 'fault injection control response',
              },
            ],
            evidence: ['fault injection control response'],
            missingInformation: [],
            riskFactors: [],
            suggestedExaminations: [],
            disclaimer: 'AI output is for clinician reference only.',
          }),
        },
      },
    ],
  }
}

const server = http.createServer((req, res) => {
  let raw = ''
  req.on('data', (chunk) => {
    raw += chunk
  })
  req.on('end', () => {
    let mode = 'ok'
    try {
      const body = JSON.parse(raw || '{}')
      const userMessage = body.messages?.find((item) => item.role === 'user')?.content ?? ''
      if (userMessage.includes('FI_NON_JSON')) mode = 'non-json'
      if (userMessage.includes('FI_INVALID_SCHEMA')) mode = 'invalid-schema'
      if (userMessage.includes('FI_TIMEOUT')) mode = 'timeout'
    } catch {
      mode = 'non-json'
    }

    if (mode === 'timeout') {
      setTimeout(() => {
        res.writeHead(200, { 'content-type': 'application/json' })
        res.end(JSON.stringify(fakeProviderResponse('ok')))
      }, 5000)
      return
    }
    if (mode === 'non-json') {
      res.writeHead(200, { 'content-type': 'text/plain' })
      res.end('not-json')
      return
    }

    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify(fakeProviderResponse(mode)))
  })
})

await new Promise((resolve) => server.listen(0, '0.0.0.0', resolve))
const port = server.address().port

const faultEnv = {
  ...process.env,
  AI_MODE: 'HTTP',
  AI_API_URL: `http://host.docker.internal:${port}/chat/completions`,
  AI_API_KEY: 'fault-injection-local-key',
  AI_MODEL: 'fault-injection',
  AI_TIMEOUT_MS: '1000',
  AI_MAX_RETRIES: '0',
}

try {
  composeUp(faultEnv)
  await waitForBackend()

  const login = await request(`${backendBaseUrl}/api/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  const token = login.accessToken

  const cases = [
    { name: 'provider-non-json-response', marker: 'FI_NON_JSON' },
    { name: 'provider-invalid-schema', marker: 'FI_INVALID_SCHEMA' },
    { name: 'provider-timeout', marker: 'FI_TIMEOUT' },
  ]

  const results = []
  for (const item of cases) {
    const body = await request(`${backendBaseUrl}/api/ai/assist-diagnosis`, {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({
        encounterId: 0,
        chiefComplaint: item.marker,
        presentIllness: item.marker,
        pastHistory: '',
        physicalExam: '',
      }),
    })
    results.push({
      name: item.name,
      aiStatus: body.aiStatus,
      degraded: body.aiStatus === 'FAILED' && Array.isArray(body.candidates) && body.candidates.length === 0,
    })
  }

  const ok = results.every((item) => item.degraded)
  console.log(JSON.stringify({
    provider: 'HTTP_FAULT_INJECTION',
    result: ok ? 'SUCCESS' : 'FAILED',
    checks: results,
  }, null, 2))

  if (!ok) {
    process.exitCode = 1
  }
} finally {
  server.close()
  composeUp(process.env)
  await waitForBackend().catch(() => {})
}
