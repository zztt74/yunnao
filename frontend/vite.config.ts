import { resolve } from 'node:path'
import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

const repoRoot = resolve(fileURLToPath(new URL('.', import.meta.url)), '..')
const testsDir = resolve(repoRoot, 'tests/front_unit_test').replace(/\\/g, '/')
const frontendDir = fileURLToPath(new URL('.', import.meta.url)).replace(/\\/g, '/')

const piniaPath = resolve(frontendDir, 'node_modules/pinia').replace(/\\/g, '/')

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [
      { find: '@', replacement: `${frontendDir}src` },
      { find: /^pinia$/, replacement: piniaPath },
    ],
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
    fs: {
      allow: [frontendDir, testsDir, piniaPath],
    },
  },
  optimizeDeps: {
    entries: ['src/**/*.ts', `${testsDir}/**/*.spec.ts`],
    include: ['pinia', 'element-plus', 'vue'],
  },
  test: {
    environment: 'happy-dom',
    include: [
      'src/**/*.spec.ts',
      `${testsDir}/**/*.spec.ts`,
    ],
    server: {
      deps: {
        inline: ['pinia', 'element-plus', 'vue'],
      },
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      reportsDirectory: './coverage',
      include: ['src/api/**/*.ts', 'src/stores/**/*.ts', 'src/types/api.ts'],
      exclude: [
        'src/api/real-client.spec.ts',
        'src/types/api.spec.ts',
        'src/main.ts',
        'src/**/*.{vue,d.ts}',
      ],
    },
  },
})
