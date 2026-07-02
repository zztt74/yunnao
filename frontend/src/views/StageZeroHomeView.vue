<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const REMEMBER_KEY = 'cloud-brain.remember-username'

const loginForm = reactive({
  username: '',
  password: '',
  rememberMe: false,
})

const loading = ref(false)
const formRef = ref()

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' },
  ],
}

function loadRememberedUsername() {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    loginForm.username = saved
    loginForm.rememberMe = true
  }
}

function saveRememberedUsername() {
  if (loginForm.rememberMe) {
    localStorage.setItem(REMEMBER_KEY, loginForm.username)
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login({
      username: loginForm.username,
      password: loginForm.password,
    })
    saveRememberedUsername()

    const role = authStore.primaryRole
    if (role === 'ADMIN') router.push('/admin')
    else if (role === 'DOCTOR') router.push('/doctor')
    else router.push('/patient')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function goRegister() {
  router.push('/register')
}

onMounted(loadRememberedUsername)
</script>

<template>
  <div class="home-page">
    <div class="home-container">
      <div class="home-branding">
        <h1 class="home-title">智慧云脑诊疗平台</h1>
        <p class="home-subtitle">AI 驱动的智能医疗协作系统</p>
      </div>

      <div class="home-login-card">
        <h2 class="login-title">账号登录</h2>
        <el-form
          ref="formRef"
          :model="loginForm"
          :rules="rules"
          label-position="top"
          size="large"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="请输入用户名"
              :prefix-icon="'User'"
              clearable
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="'Lock'"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <div class="login-options">
              <el-checkbox v-model="loginForm.rememberMe">记住账号</el-checkbox>
            </div>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="register-row">
          <span class="register-hint">还没有账号？</span>
          <a class="register-link" @click="goRegister">立即注册</a>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: url('/images/home-bg.jpg') no-repeat center center / cover;
}

.home-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 60px;
  width: min(1100px, calc(100% - 80px));
  padding: 40px;
}

.home-branding {
  flex: 1;
  color: #ffffff;
  padding-left: 28px;
  border-left: 4px solid #4facfe;
  text-shadow: 0 3px 16px rgb(0 0 0 / 40%);
}

.home-title {
  margin: 0 0 20px;
  font-size: clamp(42px, 4.8vw, 60px);
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.15;
}

.home-subtitle {
  margin: 0;
  font-size: clamp(16px, 1.6vw, 20px);
  letter-spacing: 0.08em;
}

.home-login-card {
  width: 380px;
  padding: 40px 36px;
  border-radius: 16px;
  background: rgb(255 255 255 / 70%);
  border: 1px solid rgb(255 255 255 / 60%);
  box-shadow: 0 20px 60px rgb(0 0 0 / 18%);
  backdrop-filter: blur(16px);
}

.login-title {
  margin: 0 0 28px;
  font-size: 24px;
  font-weight: 700;
  color: #1a2b4a;
  text-align: center;
}

.login-options {
  display: flex;
  justify-content: flex-end;
  width: 100%;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 0.2em;
  border-radius: 8px;
}

.register-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 4px;
}

.register-hint {
  font-size: 13px;
  color: rgb(0 0 0 / 55%);
}

.register-link {
  font-size: 13px;
  color: #1a73e8;
  cursor: pointer;
  font-weight: 500;
}

@media (max-width: 900px) {
  .home-container {
    flex-direction: column;
    gap: 36px;
    padding: 24px;
  }

  .home-branding {
    text-align: center;
    padding-left: 0;
    border-left: none;
  }

  .home-login-card {
    width: 100%;
    max-width: 400px;
  }
}
</style>
