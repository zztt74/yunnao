const backendBaseUrl = process.env.BACKEND_BASE_URL ?? 'http://localhost:18080'
const timeoutMs = Number(process.env.AI_SMOKE_TIMEOUT_MS ?? 30000)
const username = process.env.AI_SMOKE_USERNAME ?? 'doctor_internal_seed'
const password = process.env.AI_SMOKE_PASSWORD ?? 'DoctorSeed9!2026'

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
      const message = body?.message ?? body?.code ?? text
      throw new Error(`${options.method ?? 'GET'} ${url} -> ${response.status}: ${message}`)
    }
    if (body?.code && body.code !== 'SUCCESS') {
      throw new Error(`${options.method ?? 'GET'} ${url} -> ${body.code}: ${body.message}`)
    }
    return body
  } finally {
    clearTimeout(timer)
  }
}

function authHeaders(token) {
  return { authorization: `Bearer ${token}` }
}

const login = await request(`${backendBaseUrl}/api/auth/login`, {
  method: 'POST',
  body: JSON.stringify({ username, password }),
})
const token = login.data.accessToken

const checks = []

async function check(name, fn, isStructured) {
  try {
    const body = await fn()
    checks.push({
      name,
      ok: Boolean(isStructured(body)),
      status: isStructured(body) ? 'SUCCESS' : 'UNSTRUCTURED',
      summary: summarize(body),
    })
  } catch (error) {
    checks.push({
      name,
      ok: false,
      status: 'FAILED',
      summary: error.message,
    })
  }
}

function summarize(body) {
  const data = body?.data ?? body
  if (Array.isArray(data?.candidates)) {
    return `candidates=${data.candidates.length}, aiStatus=${data.aiStatus}`
  }
  if (data?.aiStatus || data?.aiReviewStatus) {
    return `aiStatus=${data.aiStatus ?? data.aiReviewStatus}`
  }
  if (data?.record?.aiStatus) {
    return `aiStatus=${data.record.aiStatus}`
  }
  return typeof data === 'object' ? Object.keys(data ?? {}).slice(0, 6).join(',') : String(data)
}

await check('assist-diagnosis', () => request(`${backendBaseUrl}/api/ai/assist-diagnosis`, {
  method: 'POST',
  headers: authHeaders(token),
  body: JSON.stringify({
    encounterId: 0,
    chiefComplaint: '胸痛 30 分钟，伴出汗',
    presentIllness: '突发胸骨后压榨痛，休息后不缓解。',
    pastHistory: '高血压病史。',
    physicalExam: '血压 150/90mmHg，心率 102 次/分。',
  }),
}), (body) => Array.isArray(body?.candidates) && body.aiStatus === 'SUCCESS')

const ok = checks.every((item) => item.ok)
console.log(JSON.stringify({
  provider: 'HTTP',
  result: ok ? 'SUCCESS' : 'FAILED',
  checks,
}, null, 2))

if (!ok) {
  process.exitCode = 1
}
