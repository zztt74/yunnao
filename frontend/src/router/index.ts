import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types/auth'

import NotFoundView from '@/views/NotFoundView.vue'
import RoleBoundaryView from '@/views/RoleBoundaryView.vue'
import StageZeroHomeView from '@/views/StageZeroHomeView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ForbiddenView from '@/views/ForbiddenView.vue'
import PatientHomeView from '@/views/patient/PatientHomeView.vue'
import PatientProfileView from '@/views/patient/PatientProfileView.vue'
import PatientTriageView from '@/views/patient/PatientTriageView.vue'
import PatientAppointmentsView from '@/views/patient/PatientAppointmentsView.vue'
import PatientMedicalRecordsView from '@/views/patient/PatientMedicalRecordsView.vue'
import PatientExaminationsView from '@/views/patient/PatientExaminationsView.vue'
import PatientPrescriptionsView from '@/views/patient/PatientPrescriptionsView.vue'
import PatientTriageHistoryView from '@/views/patient/PatientTriageHistoryView.vue'
import PatientTimelineView from '@/views/patient/PatientTimelineView.vue'
import ChangePasswordView from '@/views/patient/ChangePasswordView.vue'
import DoctorHomeView from '@/views/doctor/DoctorHomeView.vue'
import DoctorSchedulesView from '@/views/doctor/DoctorSchedulesView.vue'
import DoctorQueueView from '@/views/doctor/DoctorQueueView.vue'
import DoctorEncounterHistoryView from '@/views/doctor/DoctorEncounterHistoryView.vue'
import DoctorPatientDetailView from '@/views/doctor/DoctorPatientDetailView.vue'
import DoctorProfileView from '@/views/doctor/DoctorProfileView.vue'
import DoctorChangePasswordView from '@/views/doctor/DoctorChangePasswordView.vue'
import DoctorEncounterView from '@/views/doctor/DoctorEncounterView.vue'
import DoctorEncounterOverview from '@/views/doctor/DoctorEncounterOverview.vue'
import DoctorDiagnosisView from '@/views/doctor/DoctorDiagnosisView.vue'
import DoctorExaminationOrderView from '@/views/doctor/DoctorExaminationOrderView.vue'
import DoctorMedicalRecordView from '@/views/doctor/DoctorMedicalRecordView.vue'
import DoctorPrescriptionView from '@/views/doctor/DoctorPrescriptionView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: UserRole[]
    layout?: 'app' | 'fullscreen'
    title?: string
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: StageZeroHomeView,
      meta: { layout: 'fullscreen' },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { layout: 'fullscreen' },
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: ForbiddenView,
      meta: { layout: 'fullscreen' },
    },
    {
      path: '/patient',
      name: 'patient-home',
      component: PatientHomeView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '患者端首页' },
    },
    {
      path: '/patient/profile',
      name: 'patient-profile',
      component: PatientProfileView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '个人信息' },
    },
    {
      path: '/patient/triage',
      name: 'patient-triage',
      component: PatientTriageView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '智能分诊' },
    },
    {
      path: '/patient/appointments',
      name: 'patient-appointments',
      component: PatientAppointmentsView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '在线挂号' },
    },
    {
      path: '/patient/medical-records',
      name: 'patient-medical-records',
      component: PatientMedicalRecordsView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '我的病历' },
    },
    {
      path: '/patient/examinations',
      name: 'patient-examinations',
      component: PatientExaminationsView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '检查检验' },
    },
    {
      path: '/patient/prescriptions',
      name: 'patient-prescriptions',
      component: PatientPrescriptionsView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '我的处方' },
    },
    {
      path: '/patient/triage-history',
      name: 'patient-triage-history',
      component: PatientTriageHistoryView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '分诊历史' },
    },
    {
      path: '/patient/timeline',
      name: 'patient-timeline',
      component: PatientTimelineView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '诊疗时间线' },
    },
    {
      path: '/patient/change-password',
      name: 'patient-change-password',
      component: ChangePasswordView,
      meta: { requiresAuth: true, roles: ['PATIENT'], title: '修改密码' },
    },
    {
      path: '/doctor',
      name: 'doctor-home',
      component: DoctorHomeView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '医生端工作台' },
    },
    {
      path: '/doctor/schedules',
      name: 'doctor-schedules',
      component: DoctorSchedulesView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '我的排班' },
    },
    {
      path: '/doctor/queue',
      name: 'doctor-queue',
      component: DoctorQueueView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '待诊队列' },
    },
    {
      path: '/doctor/encounters',
      name: 'doctor-encounters',
      component: DoctorEncounterHistoryView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '我的接诊历史' },
    },
    {
      path: '/doctor/patient/:patientId',
      name: 'doctor-patient-detail',
      component: DoctorPatientDetailView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '患者详情' },
    },
    {
      path: '/doctor/profile',
      name: 'doctor-profile',
      component: DoctorProfileView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '个人信息' },
    },
    {
      path: '/doctor/change-password',
      name: 'doctor-change-password',
      component: DoctorChangePasswordView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '修改密码' },
    },
    {
      path: '/doctor/encounter/:id',
      component: DoctorEncounterView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '接诊工作台' },
      children: [
        {
          path: '',
          name: 'doctor-encounter-overview',
          component: DoctorEncounterOverview,
          meta: { requiresAuth: true, roles: ['DOCTOR'], title: '接诊概览' },
        },
        {
          path: 'diagnosis',
          name: 'doctor-encounter-diagnosis',
          component: DoctorDiagnosisView,
          meta: { requiresAuth: true, roles: ['DOCTOR'], title: 'AI 辅助诊断' },
        },
        {
          path: 'examinations',
          name: 'doctor-encounter-examinations',
          component: DoctorExaminationOrderView,
          meta: { requiresAuth: true, roles: ['DOCTOR'], title: '检查检验开立' },
        },
        {
          path: 'medical-record',
          name: 'doctor-encounter-medical-record',
          component: DoctorMedicalRecordView,
          meta: { requiresAuth: true, roles: ['DOCTOR'], title: '病历生成与编辑' },
        },
        {
          path: 'prescription',
          name: 'doctor-encounter-prescription',
          component: DoctorPrescriptionView,
          meta: { requiresAuth: true, roles: ['DOCTOR'], title: '处方开立与审核' },
        },
      ],
    },
    {
      path: '/admin',
      name: 'admin-boundary',
      component: RoleBoundaryView,
      meta: { requiresAuth: true, roles: ['ADMIN'], title: '管理端首页' },
      props: {
        role: '管理端',
        ownership: 'frontend/src/modules/admin',
      },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/', query: { redirect: to.fullPath } }
  }

  if (to.meta.roles && to.meta.roles.length > 0) {
    const hasPermission = to.meta.roles.some((r) => auth.hasRole(r))
    if (!hasPermission) {
      return { path: '/forbidden' }
    }
  }

  return true
})

export default router
