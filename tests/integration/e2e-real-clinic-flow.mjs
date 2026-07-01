import fs from 'node:fs'
import path from 'node:path'
import { randomUUID } from 'node:crypto'

const repoRoot = path.resolve(import.meta.dirname, '..', '..')
const envPath = path.join(repoRoot, '.env')
const backendBaseUrl = process.env.BACKEND_BASE_URL ?? 'http://localhost:18080'
const seedAdminPassword = process.env.LOCAL_SEED_ADMIN_PASSWORD ?? 'AdminSeed9!2026'
const doctorPassword = process.env.LOCAL_SEED_DOCTOR_PASSWORD ?? 'DoctorSeed9!2026'
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS ?? 20000)

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

function authHeaders(token) {
  return { authorization: `Bearer ${token}` }
}

function unwrapPage(data) {
  return data?.items ?? data?.list ?? []
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  const year = next.getFullYear()
  const month = String(next.getMonth() + 1).padStart(2, '0')
  const day = String(next.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
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
      const error = new Error(`${options.method ?? 'GET'} ${url} -> ${response.status}: ${message}`)
      error.status = response.status
      error.body = body
      throw error
    }
    if (body?.code && body.code !== 'SUCCESS') {
      const error = new Error(`${options.method ?? 'GET'} ${url} -> ${body.code}: ${body.message}`)
      error.status = response.status
      error.body = body
      throw error
    }
    return body
  } finally {
    clearTimeout(timer)
  }
}

async function requestStatus(url, options = {}) {
  try {
    const body = await request(url, options)
    return { ok: true, status: 200, code: body?.code, body }
  } catch (error) {
    return {
      ok: false,
      status: error.status,
      code: error.body?.code,
      message: error.body?.message ?? error.message,
    }
  }
}

function expect(condition, message) {
  if (!condition) {
    throw new Error(message)
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
    headers: authHeaders(token),
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

async function getAdminToken() {
  const env = readDotenv(envPath)
  const username = process.env.INITIAL_ADMIN_USERNAME ?? env.INITIAL_ADMIN_USERNAME ?? 'admin'
  const configuredPassword = process.env.INITIAL_ADMIN_PASSWORD ?? env.INITIAL_ADMIN_PASSWORD
  if (!configuredPassword) {
    throw new Error('INITIAL_ADMIN_PASSWORD is required in environment or .env')
  }

  let loginData
  let currentPassword = configuredPassword
  try {
    loginData = await login(username, configuredPassword)
  } catch {
    currentPassword = seedAdminPassword
    loginData = await login(username, seedAdminPassword)
  }

  if (loginData.mustChangePassword) {
    await changePassword(loginData.accessToken, currentPassword, seedAdminPassword)
    loginData = await login(username, seedAdminPassword)
  }
  return { username, token: loginData.accessToken }
}

async function getSeedDoctor(adminToken) {
  const doctors = await request(`${backendBaseUrl}/api/doctors?page=1&pageSize=100`, {
    headers: authHeaders(adminToken),
  })
  const doctor = unwrapPage(doctors.data).find((item) => item.name === '联调内科医生')
  if (!doctor) {
    throw new Error('联调内科医生不存在，请先运行 seed-real-clinic-data.mjs')
  }
  return doctor
}

async function ensureSchedule(adminToken, doctor, suffix) {
  const date = addDays(new Date(), 3 + (Number(suffix.slice(-1)) % 3))
  const startHour = 18 + (Number(suffix.slice(-2)) % 2)
  const payload = {
    doctorId: doctor.id,
    departmentId: doctor.departmentId,
    scheduleDate: date,
    startTime: `${date}T${String(startHour).padStart(2, '0')}:00:00`,
    endTime: `${date}T${String(startHour + 1).padStart(2, '0')}:00:00`,
    maxAppointments: 6,
  }
  const created = await requestStatus(`${backendBaseUrl}/api/schedules`, {
    method: 'POST',
    headers: authHeaders(adminToken),
    body: JSON.stringify(payload),
  })
  if (created.ok) return created.body.data

  const available = await request(`${backendBaseUrl}/api/schedules/available?departmentId=${doctor.departmentId}`, {
    headers: authHeaders(adminToken),
  })
  const schedule = available.data
    .filter((item) => item.doctorId === doctor.id && item.remainingCount > 0 && item.status === 'AVAILABLE')
    .sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime))[0]
  if (!schedule) {
    throw new Error(`无法创建或找到可预约排班: ${created.message}`)
  }
  return schedule
}

async function registerPatient(prefix, suffix) {
  const username = `${prefix}_${suffix}`
  const password = `FlowTest9!${suffix.slice(-6)}`
  const phone = `138${suffix.slice(-8)}`
  await request(`${backendBaseUrl}/api/patients/register`, {
    method: 'POST',
    body: JSON.stringify({
      username,
      password,
      name: `联调患者${suffix.slice(-4)}`,
      gender: 'MALE',
      birthDate: '1991-05-10',
      phone,
    }),
  })
  const loginData = await login(username, password)
  const me = await request(`${backendBaseUrl}/api/patients/me`, {
    headers: authHeaders(loginData.accessToken),
  })
  return { username, password, token: loginData.accessToken, patient: me.data }
}

async function optionalStep(label, fn) {
  try {
    const data = await fn()
    return { label, ok: true, data }
  } catch (error) {
    return { label, ok: false, message: error.body?.message ?? error.message, code: error.body?.code }
  }
}

const suffix = `${Date.now()}${Math.floor(Math.random() * 1000)}`
const admin = await getAdminToken()
await request(`${backendBaseUrl}/actuator/health`)

const doctor = await getSeedDoctor(admin.token)
const doctorLogin = await login('doctor_internal_seed', doctorPassword)
const primaryPatient = await registerPatient('flow_patient', suffix)
const otherPatient = await registerPatient('flow_other_patient', `${suffix}9`)
const schedule = await ensureSchedule(admin.token, doctor, suffix)

const triage = await request(`${backendBaseUrl}/api/triage/analyze`, {
  method: 'POST',
  headers: authHeaders(primaryPatient.token),
  body: JSON.stringify({
    patientId: primaryPatient.patient.id,
    symptoms: '发热、咳嗽、咽痛 2 天，伴轻微乏力',
    duration: '2天',
    supplement: '无胸痛，无呼吸困难',
  }),
})
const triageConversationId = randomUUID()
const triageFollowUp = await request(`${backendBaseUrl}/api/triage/analyze`, {
  method: 'POST',
  headers: authHeaders(primaryPatient.token),
  body: JSON.stringify({
    patientId: primaryPatient.patient.id,
    symptoms: '发热咳嗽后补充：夜间咳嗽加重',
    duration: '2天',
    supplement: '仍无胸痛，无呼吸困难',
    conversationId: triageConversationId,
    round: 2,
    history: [
      { role: 'USER', content: '发热、咳嗽、咽痛 2 天，伴轻微乏力' },
      { role: 'ASSISTANT', content: triage.data.aiReason ?? '建议内科就诊' },
      { role: 'USER', content: '夜间咳嗽加重' },
    ],
  }),
})
expect(triageFollowUp.data.conversationId === triageConversationId,
  'triage follow-up should echo conversationId')
expect(triageFollowUp.data.round === 2,
  'triage follow-up should echo round=2')

const appointment = await request(`${backendBaseUrl}/api/appointments`, {
  method: 'POST',
  headers: authHeaders(primaryPatient.token),
  body: JSON.stringify({
    patientId: primaryPatient.patient.id,
    scheduleId: schedule.id,
  }),
})

const queue = await request(`${backendBaseUrl}/api/appointments/doctor/${doctor.id}/pending`, {
  headers: authHeaders(doctorLogin.accessToken),
})
expect(queue.data.some((item) => item.id === appointment.data.id), 'doctor pending queue does not include appointment')

const encounter = await request(`${backendBaseUrl}/api/encounters/start`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({ appointmentId: appointment.data.id }),
})

const patientDetail = await request(`${backendBaseUrl}/api/patients/${primaryPatient.patient.id}`, {
  headers: authHeaders(doctorLogin.accessToken),
})

const diagnosisProbe = await optionalStep('assist-diagnosis', () => request(`${backendBaseUrl}/api/ai/assist-diagnosis`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    encounterId: encounter.data.id,
    chiefComplaint: '发热咳嗽 2 天',
    presentIllness: '体温 38.2 摄氏度，咳嗽，咽痛',
    pastHistory: '无特殊',
    physicalExam: '咽部充血，双肺呼吸音清',
  }),
}))

await request(`${backendBaseUrl}/api/encounters/${encounter.data.id}/diagnoses/doctor`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    diagnosisCode: 'J06.9',
    diagnosisName: '急性上呼吸道感染',
    type: 'FINAL',
    source: 'DOCTOR',
    notes: '医生最终诊断，AI 仅作为辅助参考',
  }),
})

const examination = await request(`${backendBaseUrl}/api/examinations`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    encounterId: encounter.data.id,
    orderType: 'LABORATORY',
    itemCode: 'CBC',
    itemName: '血常规',
  }),
})
const patientExamTrackingOrdered = await request(`${backendBaseUrl}/api/examinations/patient/${primaryPatient.patient.id}/tracking`, {
  headers: authHeaders(primaryPatient.token),
})
expect(patientExamTrackingOrdered.data.some((item) =>
  item.orderId === examination.data.id && item.status === 'ORDERED' && item.nextAction),
'patient tracking should show ORDERED examination with nextAction')

const availableDevices = await request(`${backendBaseUrl}/api/devices/department/${doctor.departmentId}/available`, {
  headers: authHeaders(doctorLogin.accessToken),
})
let device = availableDevices.data[0]
if (!device) {
  const allAvailableDevices = await request(`${backendBaseUrl}/api/devices/status/AVAILABLE`, {
    headers: authHeaders(doctorLogin.accessToken),
  })
  device = allAvailableDevices.data[0]
}
expect(device?.id, 'no available device found for doctor department')

await request(`${backendBaseUrl}/api/devices/${device.id}/usage/start`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    deviceId: device.id,
    encounterId: encounter.data.id,
    notes: '联调闭环检查使用',
  }),
})

await request(`${backendBaseUrl}/api/examinations/${examination.data.id}/start`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})

await request(`${backendBaseUrl}/api/devices/${device.id}/usage/end`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({ notes: '联调闭环检查完成' }),
})

const examResult = await request(`${backendBaseUrl}/api/examinations/${examination.data.id}/result`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    resultText: '白细胞 8.1 x10^9/L，中性粒细胞比例 68%，C 反应蛋白轻度升高。',
    normalRange: '白细胞 3.5-9.5 x10^9/L',
    conclusion: '轻度炎症反应，结合临床考虑上呼吸道感染。',
    abnormalFlag: 'LOW',
  }),
})
const patientExamTrackingResultEntered = await request(`${backendBaseUrl}/api/examinations/patient/${primaryPatient.patient.id}/tracking`, {
  headers: authHeaders(primaryPatient.token),
})
expect(patientExamTrackingResultEntered.data.some((item) =>
  item.orderId === examination.data.id && item.status === 'RESULT_ENTERED' && item.nextAction),
'patient tracking should show RESULT_ENTERED examination with nextAction')

await request(`${backendBaseUrl}/api/examinations/${examination.data.id}/review`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})
const patientExamTrackingReviewed = await request(`${backendBaseUrl}/api/examinations/patient/${primaryPatient.patient.id}/tracking`, {
  headers: authHeaders(primaryPatient.token),
})
expect(patientExamTrackingReviewed.data.some((item) =>
  item.orderId === examination.data.id && item.status === 'REVIEWED' && item.nextAction),
'patient tracking should show REVIEWED examination with nextAction')

await request(`${backendBaseUrl}/api/encounters/${encounter.data.id}/wait-exam`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})

await request(`${backendBaseUrl}/api/encounters/${encounter.data.id}/resume`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})

const aiMedicalRecord = await optionalStep('medical-record-ai-generate', () => request(`${backendBaseUrl}/api/medical-records/ai-generate`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    encounterId: encounter.data.id,
    chiefComplaint: '发热咳嗽 2 天',
    presentIllness: '体温 38.2 摄氏度，咳嗽，咽痛，无呼吸困难。',
    pastHistory: '无特殊病史。',
    physicalExamination: '咽部充血，双肺呼吸音清。',
    preliminaryDiagnoses: ['急性上呼吸道感染'],
    treatmentSuggestion: '对症治疗，注意休息，必要时复诊。',
  }),
}))

let medicalRecord = aiMedicalRecord.ok ? aiMedicalRecord.data : null
if (!medicalRecord) {
  medicalRecord = await request(`${backendBaseUrl}/api/medical-records`, {
    method: 'POST',
    headers: authHeaders(doctorLogin.accessToken),
    body: JSON.stringify({
      encounterId: encounter.data.id,
      content: '主诉：发热咳嗽 2 天。现病史：体温 38.2 摄氏度，咳嗽，咽痛。查体：咽部充血，双肺呼吸音清。诊断：急性上呼吸道感染。处理：对症治疗，注意休息。',
    }),
  })
}

const confirmedMedicalRecord = await request(`${backendBaseUrl}/api/medical-records/${medicalRecord.data.id}/confirm`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})

const prescription = await request(`${backendBaseUrl}/api/prescriptions`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
  body: JSON.stringify({
    encounterId: encounter.data.id,
    items: [
      {
        drugCode: 'DRG_004',
        drugName: '云脑止咳糖浆',
        dosage: '10ml',
        dosageValue: 10,
        frequency: 'TID',
        duration: 3,
        quantity: 1,
        instructions: '饭后口服',
      },
    ],
  }),
})

const confirmedPrescription = await request(`${backendBaseUrl}/api/prescriptions/${prescription.data.id}/confirm`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})

const completedEncounter = await request(`${backendBaseUrl}/api/encounters/${encounter.data.id}/complete`, {
  method: 'POST',
  headers: authHeaders(doctorLogin.accessToken),
})

const patientRecords = await request(`${backendBaseUrl}/api/medical-records/patient/${primaryPatient.patient.id}?page=1&size=20`, {
  headers: authHeaders(primaryPatient.token),
})
const patientExams = await request(`${backendBaseUrl}/api/examinations/patient/${primaryPatient.patient.id}/tracking`, {
  headers: authHeaders(primaryPatient.token),
})
const patientPrescriptions = await request(`${backendBaseUrl}/api/prescriptions/patient/${primaryPatient.patient.id}?page=1&size=20`, {
  headers: authHeaders(primaryPatient.token),
})
const patientEncounters = await request(`${backendBaseUrl}/api/encounters/patient/${primaryPatient.patient.id}?page=1&size=20`, {
  headers: authHeaders(primaryPatient.token),
})

const today = addDays(new Date(), 0)
const statsDashboard = await request(`${backendBaseUrl}/api/statistics/dashboard`, {
  headers: authHeaders(admin.token),
})
const auditLogs = await request(`${backendBaseUrl}/api/audit/logs?page=1&size=5`, {
  headers: authHeaders(admin.token),
})
const aiStats = await request(`${backendBaseUrl}/api/statistics/ai/summary?startDate=${today}&endDate=${today}`, {
  headers: authHeaders(admin.token),
})

const disabledUsername = `flow_disabled_${suffix}`
const disabledPassword = `Disabled9!${suffix.slice(-6)}`
const disabledUser = await request(`${backendBaseUrl}/api/admin/users`, {
  method: 'POST',
  headers: authHeaders(admin.token),
  body: JSON.stringify({
    username: disabledUsername,
    password: disabledPassword,
    role: 'ADMIN',
  }),
})
await request(`${backendBaseUrl}/api/admin/users/${disabledUser.data.id}/status`, {
  method: 'POST',
  headers: authHeaders(admin.token),
  body: JSON.stringify({
    action: 'DISABLE',
  }),
})

const permissions = {
  patientOtherPatientDetail: await requestStatus(`${backendBaseUrl}/api/patients/${otherPatient.patient.id}`, {
    headers: authHeaders(primaryPatient.token),
  }),
  patientAdminStats: await requestStatus(`${backendBaseUrl}/api/statistics/dashboard`, {
    headers: authHeaders(primaryPatient.token),
  }),
  doctorAdminAudit: await requestStatus(`${backendBaseUrl}/api/audit/logs?page=1&size=1`, {
    headers: authHeaders(doctorLogin.accessToken),
  }),
  anonymousPatientMe: await requestStatus(`${backendBaseUrl}/api/patients/me`),
  disabledUserLogin: await requestStatus(`${backendBaseUrl}/api/auth/login`, {
    method: 'POST',
    body: JSON.stringify({
      username: disabledUsername,
      password: disabledPassword,
    }),
  }),
}

expect(!permissions.patientOtherPatientDetail.ok && permissions.patientOtherPatientDetail.status === 403,
  'patient should not access another patient detail')
expect(!permissions.patientAdminStats.ok && permissions.patientAdminStats.status === 403,
  'patient should not access admin statistics')
expect(!permissions.doctorAdminAudit.ok && permissions.doctorAdminAudit.status === 403,
  'doctor should not access admin audit logs')
expect(!permissions.anonymousPatientMe.ok && permissions.anonymousPatientMe.status === 401,
  'anonymous user should not access patients/me')
expect(!permissions.disabledUserLogin.ok && [401, 403].includes(permissions.disabledUserLogin.status),
  'disabled user should not be able to login')

const result = {
  flow: 'SUCCESS',
  accounts: {
    admin: admin.username,
    doctor: 'doctor_internal_seed',
    patient: primaryPatient.username,
  },
  ids: {
    patientId: primaryPatient.patient.id,
    doctorId: doctor.id,
    scheduleId: schedule.id,
    appointmentId: appointment.data.id,
    encounterId: encounter.data.id,
    examinationId: examination.data.id,
    deviceId: device.id,
    medicalRecordId: confirmedMedicalRecord.data.id,
    prescriptionId: confirmedPrescription.data.id,
  },
  steps: {
    triageStatus: triage.data.aiStatus ?? 'SUCCESS',
    appointmentStatus: appointment.data.status,
    encounterStatus: completedEncounter.data.status,
    triageFollowUpRound: triageFollowUp.data.round,
    examStatus: patientExams.data.find((item) => item.orderId === examination.data.id)?.status,
    examNextAction: patientExams.data.find((item) => item.orderId === examination.data.id)?.nextAction,
    examAiStatus: examResult.data.aiStatus,
    medicalRecordStatus: confirmedMedicalRecord.data.status,
    medicalRecordSource: aiMedicalRecord.ok ? 'AI_GENERATED' : 'MANUAL_FALLBACK',
    prescriptionStatus: confirmedPrescription.data.status,
    prescriptionAiReviewStatus: confirmedPrescription.data.aiReviewStatus,
    patientRecordVisible: patientRecords.data.items.some((item) => item.id === confirmedMedicalRecord.data.id),
    patientPrescriptionVisible: patientPrescriptions.data.items.some((item) => item.id === confirmedPrescription.data.id),
    patientEncounterVisible: patientEncounters.data.items.some((item) => item.id === encounter.data.id),
    adminDashboardVisible: Boolean(statsDashboard.data),
    adminAuditLogsVisible: Array.isArray(auditLogs.data.items),
    adminAiStatsVisible: Boolean(aiStats.data),
  },
  aiProbes: {
    assistDiagnosis: diagnosisProbe.ok ? 'SUCCESS' : `FAILED:${diagnosisProbe.code ?? diagnosisProbe.message}`,
    medicalRecordGenerate: aiMedicalRecord.ok ? 'SUCCESS' : `FAILED:${aiMedicalRecord.code ?? aiMedicalRecord.message}`,
  },
  permissions: {
    patientOtherPatientDetail: permissions.patientOtherPatientDetail.status,
    patientAdminStats: permissions.patientAdminStats.status,
    doctorAdminAudit: permissions.doctorAdminAudit.status,
    anonymousPatientMe: permissions.anonymousPatientMe.status,
    disabledUserLogin: permissions.disabledUserLogin.status,
  },
}

console.log(JSON.stringify(result, null, 2))
