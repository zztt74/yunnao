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
      name: 'doctor-boundary',
      component: RoleBoundaryView,
      meta: { requiresAuth: true, roles: ['DOCTOR'], title: '医生端工作台' },
      props: {
        role: '医生端',
        ownership: 'frontend/src/modules/doctor',
      },
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
