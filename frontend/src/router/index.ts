import { createRouter, createWebHistory } from 'vue-router'

import NotFoundView from '@/views/NotFoundView.vue'
import RoleBoundaryView from '@/views/RoleBoundaryView.vue'
import StageZeroHomeView from '@/views/StageZeroHomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: StageZeroHomeView,
    },
    {
      path: '/patient',
      name: 'patient-boundary',
      component: RoleBoundaryView,
      props: {
        role: '患者端',
        ownership: 'frontend/src/modules/patient',
      },
    },
    {
      path: '/doctor',
      name: 'doctor-boundary',
      component: RoleBoundaryView,
      props: {
        role: '医生端',
        ownership: 'frontend/src/modules/doctor',
      },
    },
    {
      path: '/admin',
      name: 'admin-boundary',
      component: RoleBoundaryView,
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

export default router
