import fs from 'node:fs'
import path from 'node:path'

const repoRoot = path.resolve(import.meta.dirname, '..', '..')
const envPath = path.join(repoRoot, '.env')
const backendBaseUrl = process.env.BACKEND_BASE_URL ?? 'http://localhost:18080'
const seedAdminPassword = process.env.LOCAL_SEED_ADMIN_PASSWORD ?? 'AdminSeed9!2026'
const doctorPassword = process.env.LOCAL_SEED_DOCTOR_PASSWORD ?? 'DoctorSeed9!2026'
const timeoutMs = Number(process.env.SEED_TIMEOUT_MS ?? 15000)

function readDotenv(file) {
  if (!fs.existsSync(file)) return {}
  const env = {}
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const index = trimmed.indexOf('=')
    if (index === -1) continue
    const key = trimmed.slice(0, index).trim()
    let value = trimmed.slice(index + 1).trim()
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1)
    }
    env[key] = value
  }
  return env
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

async function login(username, password) {
  const res = await request(`${backendBaseUrl}/api/auth/login`, {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  return res.data
}

async function changePassword(token, oldPassword, newPassword) {
  await request(`${backendBaseUrl}/api/auth/change-password`, {
    method: 'POST',
    headers: { authorization: `Bearer ${token}` },
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

function authHeaders(token) {
  return { authorization: `Bearer ${token}` }
}

function unwrapPage(data) {
  return data?.items ?? data?.list ?? []
}

async function getAdminToken() {
  const env = readDotenv(envPath)
  const username = process.env.INITIAL_ADMIN_USERNAME ?? env.INITIAL_ADMIN_USERNAME ?? 'admin'
  const password = process.env.INITIAL_ADMIN_PASSWORD ?? env.INITIAL_ADMIN_PASSWORD
  if (!password) {
    throw new Error('INITIAL_ADMIN_PASSWORD is required in environment or .env')
  }

  let passwordChanged = false
  let loginData
  try {
    loginData = await login(username, password)
  } catch {
    loginData = await login(username, seedAdminPassword)
  }
  if (loginData.mustChangePassword) {
    await changePassword(loginData.accessToken, password, seedAdminPassword)
    passwordChanged = true
    loginData = await login(username, seedAdminPassword)
  }
  return { username, token: loginData.accessToken, passwordChanged }
}

async function ensureDoctor(token, payload) {
  const doctors = await request(`${backendBaseUrl}/api/doctors?page=1&pageSize=100`, {
    headers: authHeaders(token),
  })
  const existing = unwrapPage(doctors.data).find((doctor) => doctor.name === payload.name)
  if (existing) return { doctor: existing, created: false }

  const created = await request(`${backendBaseUrl}/api/doctors`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(payload),
  })
  return { doctor: created.data, created: true }
}

async function ensureSchedule(token, doctor, date, startHour, endHour, maxAppointments) {
  const schedules = await request(`${backendBaseUrl}/api/schedules/doctor/${doctor.id}?page=1&size=100`, {
    headers: authHeaders(token),
  })
  const startTime = `${date}T${String(startHour).padStart(2, '0')}:00:00`
  const endTime = `${date}T${String(endHour).padStart(2, '0')}:00:00`
  const existing = unwrapPage(schedules.data).find((schedule) =>
    schedule.scheduleDate === date && schedule.startTime?.startsWith(startTime.slice(0, 16)))
  if (existing) return { schedule: existing, created: false }

  const created = await request(`${backendBaseUrl}/api/schedules`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({
      doctorId: doctor.id,
      departmentId: doctor.departmentId,
      scheduleDate: date,
      startTime,
      endTime,
      maxAppointments,
    }),
  })
  return { schedule: created.data, created: true }
}

async function ensurePatient(username, password) {
  try {
    const loginData = await login(username, password)
    return { token: loginData.accessToken, created: false }
  } catch {
    await request(`${backendBaseUrl}/api/patients/register`, {
      method: 'POST',
      body: JSON.stringify({
        username,
        password,
        name: '联调患者',
        gender: 'MALE',
        birthDate: '1992-06-01',
        phone: '13900090629',
      }),
    })
    const loginData = await login(username, password)
    return { token: loginData.accessToken, created: true }
  }
}

async function getCurrentPatient(token) {
  const me = await request(`${backendBaseUrl}/api/patients/me`, {
    headers: authHeaders(token),
  })
  return me.data
}

async function ensureAppointment(patientToken, patientId, scheduleId) {
  const appointments = await request(`${backendBaseUrl}/api/appointments/patient/${patientId}?page=1&size=100`, {
    headers: authHeaders(patientToken),
  })
  const existing = unwrapPage(appointments.data).find((appointment) =>
    appointment.scheduleId === scheduleId && appointment.status !== 'CANCELLED')
  if (existing) return { appointment: existing, created: false }

  const created = await request(`${backendBaseUrl}/api/appointments`, {
    method: 'POST',
    headers: authHeaders(patientToken),
    body: JSON.stringify({ patientId, scheduleId }),
  })
  return { appointment: created.data, created: true }
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next.toISOString().slice(0, 10)
}

const admin = await getAdminToken()
const doctorsToSeed = [
  {
    username: 'doctor_internal_seed',
    password: doctorPassword,
    departmentId: 1,
    name: '联调内科医生',
    title: 'ATTENDING',
    specialty: '常见内科疾病、慢病复诊',
    education: '本科',
    experienceYears: 8,
    introduction: '本地联调用医生账号。',
  },
  {
    username: 'doctor_emergency_seed',
    password: doctorPassword,
    departmentId: 7,
    name: '联调急诊医生',
    title: 'RESIDENT',
    specialty: '急诊初筛、常见急症处理',
    education: '本科',
    experienceYears: 5,
    introduction: '本地联调用医生账号。',
  },
]

const doctorResults = []
for (const payload of doctorsToSeed) {
  doctorResults.push(await ensureDoctor(admin.token, payload))
}

const today = new Date()
const dates = [addDays(today, 0), addDays(today, 1), addDays(today, 2)]
const scheduleResults = []
for (const { doctor } of doctorResults) {
  for (const date of dates) {
    scheduleResults.push(await ensureSchedule(admin.token, doctor, date, 9, 12, 12))
    scheduleResults.push(await ensureSchedule(admin.token, doctor, date, 14, 17, 12))
  }
}

const patient = await ensurePatient('patient_seed', 'PatientSeed9!2026')
const patientMe = await getCurrentPatient(patient.token)
const now = Date.now()
const firstInternalSchedule = scheduleResults
  .map((item) => item.schedule)
  .filter((schedule) => schedule.departmentId === 1 && Date.parse(schedule.endTime) > now)
  .sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime))[0]
let appointmentResult = null
if (firstInternalSchedule) {
  appointmentResult = await ensureAppointment(patient.token, patientMe.id, firstInternalSchedule.id)
}

console.log(JSON.stringify({
  adminUsername: admin.username,
  adminPasswordChangedForLocalSeed: admin.passwordChanged,
  seededDoctorUsernames: doctorsToSeed.map((doctor) => doctor.username),
  seededDoctorPassword: doctorPassword,
  doctorsCreated: doctorResults.filter((item) => item.created).length,
  schedulesCreated: scheduleResults.filter((item) => item.created).length,
  patientUsername: 'patient_seed',
  patientPassword: 'PatientSeed9!2026',
  patientCreated: patient.created,
  appointmentCreated: appointmentResult?.created ?? false,
  appointmentNumber: appointmentResult?.appointment?.appointmentNumber ?? null,
}, null, 2))
