<script setup lang="ts">
defineProps<{
  role: string
  ownership: string
}>()
</script>

<template>
  <section class="content-card">
    <p class="eyebrow">ROUTE BOUNDARY</p>
    <h1>{{ role }}</h1>
    <p>该路由边界已建立，尚未包含未经批准的业务流程或演示数据。</p>
    <code>{{ ownership }}</code>
  </section>
</template>
