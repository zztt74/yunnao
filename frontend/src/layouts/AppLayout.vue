<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="brand" to="/">智慧云脑诊疗平台</RouterLink>
      <nav aria-label="角色边界">
        <RouterLink to="/patient">患者端</RouterLink>
        <RouterLink to="/doctor">医生端</RouterLink>
        <RouterLink to="/admin">管理端</RouterLink>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>
