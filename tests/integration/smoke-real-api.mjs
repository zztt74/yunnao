const backendBaseUrl = process.env.BACKEND_BASE_URL ?? 'http://localhost:18080'
const frontendBaseUrl = process.env.FRONTEND_BASE_URL ?? 'http://localhost:8088'
const timeoutMs = Number(process.env.SMOKE_TIMEOUT_MS ?? 10000)

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
    const contentType = response.headers.get('content-type') ?? ''
    const text = await response.text()
    const body = contentType.includes('json') || /^[\s]*[{[]/.test(text)
      ? JSON.parse(text)
      : text
    if (!response.ok) {
      throw new Error(`${options.method ?? 'GET'} ${url} -> ${response.status}: ${JSON.stringify(body)}`)
    }
    return body
  } finally {
    clearTimeout(timer)
  }
}

function expect(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
}

const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`
const username = `smoke_patient_${suffix}`
const password = `SmokeTest9!${suffix.slice(-6)}`
const phone = `139${suffix.slice(-8)}`

const backendHealth = await request(`${backendBaseUrl}/actuator/health`)
expect(backendHealth.status === 'UP', `backend health is not UP: ${backendHealth.status}`)

const frontendHtml = await request(`${frontendBaseUrl}/`)
expect(frontendHtml.includes('id="app"'), 'frontend app shell was not found')

const register = await request(`${frontendBaseUrl}/api/patients/register`, {
  method: 'POST',
  body: JSON.stringify({
    username,
    password,
    name: 'Smoke Patient',
    gender: 'MALE',
    birthDate: '1990-01-01',
    phone,
  }),
})
expect(register.code === 'SUCCESS', `patient register failed: ${register.code}`)
expect(register.data?.id, 'patient register did not return an id')

const login = await request(`${frontendBaseUrl}/api/auth/login`, {
  method: 'POST',
  body: JSON.stringify({ username, password }),
})
expect(login.code === 'SUCCESS', `patient login failed: ${login.code}`)
expect(login.data?.accessToken, 'patient login did not return an access token')

const me = await request(`${frontendBaseUrl}/api/patients/me`, {
  headers: {
    authorization: `Bearer ${login.data.accessToken}`,
  },
})
expect(me.code === 'SUCCESS', `patients/me failed: ${me.code}`)
expect(me.data?.id === register.data.id, 'patients/me did not return the registered patient id')

console.log(JSON.stringify({
  backendHealth: backendHealth.status,
  frontend: 'OK',
  registerCode: register.code,
  loginCode: login.code,
  meCode: me.code,
  patientId: me.data.id,
}, null, 2))
