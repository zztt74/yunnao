<script setup lang="ts">
import { reactive, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { registerPatient } from '@/api/patient'

const router = useRouter()

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  gender: 'MALE',
  birthDate: '',
})

const loading = ref(false)
const formRef = ref()

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validatePhone = (_rule: any, value: string, callback: any) => {
  const phoneReg = /^1[3-9]\d{9}$/
  if (!value) {
    callback(new Error('请输入手机号'))
  } else if (!phoneReg.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3-20 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于 6 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
  ],
  phone: [
    { required: true, validator: validatePhone, trigger: 'blur' },
  ],
  gender: [
    { required: true, message: '请选择性别', trigger: 'change' },
  ],
  birthDate: [
    { required: true, message: '请选择出生日期', trigger: 'change' },
  ],
}

/* ========== 滑块验证 ========== */
const sliderVerified = ref(false)
const sliderLeft = ref(0)
const sliderDragging = ref(false)
const sliderContainerRef = ref<HTMLElement | null>(null)
const hasMoved = ref(false)

const MIN_MOVE_DISTANCE = 5

const sliderMax = computed(() => {
  if (!sliderContainerRef.value) return 0
  const track = sliderContainerRef.value.querySelector('.slider-track') as HTMLElement
  const btn = sliderContainerRef.value.querySelector('.slider-btn') as HTMLElement
  if (!track || !btn) return 0
  return track.offsetWidth - btn.offsetWidth
})

const sliderProgress = computed(() => {
  if (!sliderMax.value) return 0
  return Math.min(100, (sliderLeft.value / sliderMax.value) * 100)
})

let startX = 0
let startLeft = 0

function onSliderMouseDown(e: MouseEvent | TouchEvent) {
  if (sliderVerified.value) return
  sliderDragging.value = true
  hasMoved.value = false
  startX = 'touches' in e ? e.touches[0].clientX : e.clientX
  startLeft = sliderLeft.value
  document.addEventListener('mousemove', onSliderMove)
  document.addEventListener('mouseup', onSliderUp)
  document.addEventListener('touchmove', onSliderMove, { passive: false })
  document.addEventListener('touchend', onSliderUp)
}

function onSliderMove(e: MouseEvent | TouchEvent) {
  if (!sliderDragging.value) return
  e.preventDefault?.()
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const diff = clientX - startX

  if (Math.abs(diff) > MIN_MOVE_DISTANCE) {
    hasMoved.value = true
  }

  if (!hasMoved.value) return

  const max = sliderMax.value
  if (max <= 0) return

  const next = Math.max(0, Math.min(max, startLeft + diff))
  sliderLeft.value = next
  if (next >= max) {
    sliderVerified.value = true
    sliderDragging.value = false
    removeSliderListeners()
  }
}

function onSliderUp() {
  if (!sliderDragging.value) return
  sliderDragging.value = false
  if (!sliderVerified.value) {
    sliderLeft.value = 0
  }
  removeSliderListeners()
}

function removeSliderListeners() {
  document.removeEventListener('mousemove', onSliderMove)
  document.removeEventListener('mouseup', onSliderUp)
  document.removeEventListener('touchmove', onSliderMove)
  document.removeEventListener('touchend', onSliderUp)
}

function resetSlider() {
  sliderVerified.value = false
  sliderLeft.value = 0
  hasMoved.value = false
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  if (!sliderVerified.value) {
    ElMessage.warning('请先完成滑块验证')
    return
  }

  loading.value = true
  try {
    // 调用注册接口（后端未就绪时由 api/patient.ts 内部降级为 MOCK）
    await registerPatient({
      username: registerForm.username,
      password: registerForm.password,
      name: registerForm.realName,
      gender: registerForm.gender as 'MALE' | 'FEMALE',
      birthDate: registerForm.birthDate,
      phone: registerForm.phone,
    })
    ElMessage.success('注册成功！请登录')
    setTimeout(() => {
      router.push('/')
    }, 600)
  } catch (e: any) {
    console.error('注册失败：', e)
    const msg = e?.response?.data?.message || e?.message || '注册失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    loading.value = false
    resetSlider()
  }
}

function goLogin() {
  router.push('/')
}
</script>

<template>
  <div class="register-page">
    <!-- 注册卡片 -->
    <div class="register-card">
      <h2 class="register-card-title">账号注册</h2>
      <el-form
        ref="formRef"
        :model="registerForm"
        :rules="rules"
        label-position="top"
        size="default"
        @keyup.enter="handleRegister"
      >
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="registerForm.username"
                  placeholder="请输入用户名"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="真实姓名" prop="realName">
                <el-input
                  v-model="registerForm.realName"
                  placeholder="请输入真实姓名"
                  clearable
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="手机号" prop="phone">
                <el-input
                  v-model="registerForm.phone"
                  placeholder="请输入手机号"
                  clearable
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="性别" prop="gender">
                <el-radio-group v-model="registerForm.gender">
                  <el-radio value="MALE">男</el-radio>
                  <el-radio value="FEMALE">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="出生日期" prop="birthDate">
            <el-date-picker
              v-model="registerForm.birthDate"
              type="date"
              placeholder="请选择出生日期"
              :disabled-date="(d: Date) => dayjs(d).isAfter(dayjs())"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>

          <!-- 滑块验证 -->
          <el-form-item>
            <div
              ref="sliderContainerRef"
              class="slider-container"
            >
              <div class="slider-track">
                <div
                  class="slider-fill"
                  :class="{ verified: sliderVerified }"
                  :style="{ width: sliderProgress + '%' }"
                />
                <span
                  class="slider-tip"
                  :style="{ opacity: sliderVerified ? 0 : 1 - sliderProgress / 100 }"
                >
                  向右滑动验证
                </span>
                <span v-if="sliderVerified" class="slider-success-text">
                  ✓ 验证成功
                </span>
                <div
                  class="slider-btn"
                  :class="{ verified: sliderVerified, dragging: sliderDragging }"
                  :style="{ left: sliderLeft + 'px' }"
                  @mousedown="onSliderMouseDown"
                  @touchstart="onSliderMouseDown"
                >
                  <template v-if="sliderVerified">
                    <span class="slider-icon check">✓</span>
                  </template>
                  <template v-else>
                    <span class="slider-icon arrow">→</span>
                  </template>
                </div>
              </div>
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="register-btn"
              :loading="loading"
              @click="handleRegister"
            >
              注 册
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 登录入口 -->
        <div class="login-row">
          <span class="login-hint-text">已有账号？</span>
          <a class="login-link" @click="goLogin">立即登录</a>
        </div>
      </div>
  </div>
</template>

<style scoped>
.register-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image: url('/images/home-bg.jpg');
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

/* ========== 注册卡片 ========== */
.register-card {
  position: relative;
  z-index: 1;
  width: 520px;
  padding: 36px 40px;
  border-radius: 16px;
  background: rgb(255 255 255 / 35%);
  border: 1px solid rgb(255 255 255 / 45%);
  box-shadow: 0 20px 60px rgb(0 0 0 / 18%);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.register-card-title {
  margin: 0 0 24px;
  font-size: 24px;
  font-weight: 700;
  color: #1a2b4a;
  text-align: center;
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 0.2em;
  border-radius: 8px;
}

/* ========== 滑块验证 ========== */
.slider-container {
  position: relative;
  width: 100%;
  user-select: none;
}

.slider-track {
  position: relative;
  height: 42px;
  background: rgb(255 255 255 / 50%);
  border: 1px solid rgb(255 255 255 / 60%);
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 0.3s;
}

.slider-track:has(.slider-btn.verified) {
  border-color: #67c23a;
}

.slider-fill {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, rgb(74 144 217 / 25%) 0%, rgb(74 144 217 / 45%) 100%);
  pointer-events: none;
  transition: background 0.4s;
}

.slider-fill.verified {
  background: linear-gradient(90deg, #85ce61 0%, #67c23a 50%, #5daf34 100%);
  animation: slider-verified-glow 2s ease-in-out infinite;
}

@keyframes slider-verified-glow {
  0%,
  100% {
    filter: brightness(1);
  }
  50% {
    filter: brightness(1.08);
  }
}

.slider-tip {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 13px;
  color: rgb(0 0 0 / 55%);
  pointer-events: none;
  transition: opacity 0.25s ease;
  white-space: nowrap;
  letter-spacing: 0.05em;
}

.slider-btn {
  position: absolute;
  top: 2px;
  left: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: calc(100% - 4px);
  background: #ffffff;
  border: 1px solid rgb(255 255 255 / 60%);
  border-radius: 6px;
  cursor: grab;
  box-shadow: 0 2px 6px rgb(0 0 0 / 10%);
  transition: transform 0.15s ease, box-shadow 0.2s, background 0.3s,
    border-color 0.3s;
}

.slider-btn:hover {
  border-color: #4a90d9;
  box-shadow: 0 2px 10px rgb(74 144 217 / 25%);
}

.slider-btn.dragging {
  cursor: grabbing;
  transform: scale(1.05);
  box-shadow: 0 4px 14px rgb(0 0 0 / 15%);
}

.slider-btn.verified {
  background: #ffffff;
  border-color: #ffffff;
  cursor: default;
  box-shadow: 0 2px 8px rgb(93 175 52 / 40%);
  animation: slider-btn-pop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes slider-btn-pop {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.15);
  }
  100% {
    transform: scale(1);
  }
}

.slider-icon {
  font-size: 16px;
  font-weight: bold;
  color: #909399;
  transition: color 0.2s;
}

.slider-btn.verified .slider-icon.check {
  color: #67c23a;
  animation: check-mark 0.5s ease-out forwards;
}

@keyframes check-mark {
  0% {
    transform: scale(0) rotate(-45deg);
    opacity: 0;
  }
  60% {
    transform: scale(1.2) rotate(5deg);
    opacity: 1;
  }
  100% {
    transform: scale(1) rotate(0);
    opacity: 1;
  }
}

.slider-success-text {
  position: absolute;
  top: 50%;
  left: 20px;
  transform: translateY(-50%);
  font-size: 13px;
  color: #ffffff;
  font-weight: 500;
  pointer-events: none;
  letter-spacing: 0.08em;
  text-shadow: 0 1px 2px rgb(0 0 0 / 15%);
  animation: success-fade-in 0.4s ease-out;
  z-index: 2;
}

@keyframes success-fade-in {
  0% {
    opacity: 0;
    transform: translateY(-50%) translateX(-10px);
  }
  100% {
    opacity: 1;
    transform: translateY(-50%) translateX(0);
  }
}

/* ========== 登录入口 ========== */
.login-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px solid rgb(0 0 0 / 8%);
}

.login-hint-text {
  font-size: 13px;
  color: rgb(0 0 0 / 55%);
}

.login-link {
  font-size: 13px;
  color: #1a73e8;
  cursor: pointer;
  font-weight: 500;
}

.login-link:hover {
  color: #4a90d9;
  text-decoration: underline;
}

/* ========== 响应式 ========== */
@media (max-width: 640px) {
  .register-card {
    width: calc(100% - 40px);
    max-width: 420px;
    padding: 28px 24px;
  }
}
</style>
