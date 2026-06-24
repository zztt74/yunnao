<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import MobileLayout from '@/layouts/MobileLayout.vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const isFullscreenPage = computed(() => route.meta.layout === 'fullscreen')

// 患者端使用手机布局，医生端和管理端使用桌面布局
const useMobile = computed(() => auth.isPatient && !isFullscreenPage.value)
</script>

<template>
  <RouterView v-if="isFullscreenPage" />
  <MobileLayout v-else-if="useMobile" />
  <AppLayout v-else />
</template>
