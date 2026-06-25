<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'

const router = useRouter()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const showOld = ref(false)
const showNew = ref(false)
const showConfirm = ref(false)
const submitting = ref(false)

const oldPwdValid = computed(() => form.oldPassword.length > 0)
const newPwdValid = computed(() => form.newPassword.length >= 6)
const confirmPwdValid = computed(
  () => form.confirmPassword.length > 0 && form.confirmPassword === form.newPassword,
)
const diffFromOld = computed(
  () => form.newPassword.length > 0 && form.newPassword !== form.oldPassword,
)

const canSubmit = computed(
  () =>
    !submitting.value &&
    oldPwdValid.value &&
    newPwdValid.value &&
    confirmPwdValid.value &&
    diffFromOld.value,
)

const passwordStrength = computed(() => {
  const p = form.newPassword
  if (p.length === 0) return { level: 0, label: '', color: '' }
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Z]/.test(p)) score++
  if (/[0-9]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  if (score <= 1) return { level: 1, label: '弱', color: '#f56c6c' }
  if (score <= 2) return { level: 2, label: '中', color: '#e6a23c' }
  return { level: 3, label: '强', color: '#67c23a' }
})

async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功！请使用新密码重新登录')
    // 跳到登录页
    setTimeout(() => {
      // 清空会话（这里用 auth store 的 clearSession）
      sessionStorage.removeItem('cloud-brain.access-token')
      sessionStorage.removeItem('cloud-brain.user')
      router.push('/')
    }, 800)
  } catch (e: any) {
    console.error('修改密码失败：', e)
    ElMessage.error(e?.message || '修改失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="change-pwd-page">
    <div class="page-header">
      <button class="back-btn" @click="router.back()">‹</button>
      <h1 class="page-title">修改密码</h1>
    </div>

    <div class="form-card">
      <div class="security-tip">
        <span class="tip-icon">🔒</span>
        为了您的账号安全，修改密码后需要重新登录
      </div>

      <div class="form-item">
        <label class="form-label">原密码</label>
        <div class="input-wrap">
          <input
            v-model="form.oldPassword"
            :type="showOld ? 'text' : 'password'"
            class="form-input"
            placeholder="请输入原密码"
            maxlength="64"
            autocomplete="current-password"
          />
          <button class="eye-btn" type="button" @click="showOld = !showOld">
            {{ showOld ? '🙈' : '👁' }}
          </button>
        </div>
      </div>

      <div class="form-item">
        <label class="form-label">新密码</label>
        <div class="input-wrap">
          <input
            v-model="form.newPassword"
            :type="showNew ? 'text' : 'password'"
            class="form-input"
            placeholder="请输入新密码（至少 6 位）"
            maxlength="64"
            autocomplete="new-password"
          />
          <button class="eye-btn" type="button" @click="showNew = !showNew">
            {{ showNew ? '🙈' : '👁' }}
          </button>
        </div>
        <div v-if="form.newPassword" class="strength-bar">
          <div class="strength-track">
            <div
              class="strength-fill"
              :style="{
                width: (passwordStrength.level / 3) * 100 + '%',
                background: passwordStrength.color,
              }"
            />
          </div>
          <span class="strength-label" :style="{ color: passwordStrength.color }">
            强度：{{ passwordStrength.label }}
          </span>
        </div>
      </div>

      <div class="form-item">
        <label class="form-label">确认新密码</label>
        <div class="input-wrap">
          <input
            v-model="form.confirmPassword"
            :type="showConfirm ? 'text' : 'password'"
            class="form-input"
            placeholder="请再次输入新密码"
            maxlength="64"
            autocomplete="new-password"
          />
          <button class="eye-btn" type="button" @click="showConfirm = !showConfirm">
            {{ showConfirm ? '🙈' : '👁' }}
          </button>
        </div>
        <div v-if="form.confirmPassword" class="hint" :class="{ ok: confirmPwdValid, err: !confirmPwdValid }">
          {{ confirmPwdValid ? '✓ 两次密码一致' : '✗ 两次输入的密码不一致' }}
        </div>
      </div>

      <button class="submit-btn" :disabled="!canSubmit" @click="handleSubmit">
        {{ submitting ? '提交中...' : '确认修改' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.change-pwd-page {
  padding: 0 0 32px;
}

.page-header {
  display: flex;
  align-items: center;
  padding: 12px 8px;
  background: #fff;
  border-bottom: 1px solid #f1f5f9;
  position: sticky;
  top: 0;
  z-index: 1;
}

.back-btn {
  background: none;
  border: none;
  color: #4facfe;
  font-size: 28px;
  padding: 0 12px;
  cursor: pointer;
  line-height: 1;
}

.page-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.form-card {
  margin: 16px;
  background: #fff;
  border-radius: 14px;
  padding: 18px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.security-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 12px;
  color: #d48806;
  margin-bottom: 18px;
  line-height: 1.5;
}

.tip-icon {
  font-size: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}

.input-wrap {
  position: relative;
}

.form-input {
  width: 100%;
  height: 44px;
  padding: 0 40px 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1a1a1a;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #f8f9fa;
  box-sizing: border-box;
}

.form-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background: #ffffff;
}

.form-input::placeholder {
  color: #c0c4cc;
}

.eye-btn {
  position: absolute;
  top: 50%;
  right: 8px;
  transform: translateY(-50%);
  background: none;
  border: none;
  font-size: 16px;
  cursor: pointer;
  padding: 4px 6px;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.eye-btn:hover {
  opacity: 1;
}

.strength-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.strength-track {
  flex: 1;
  height: 4px;
  background: #f1f5f9;
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  transition: width 0.3s, background 0.3s;
}

.strength-label {
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
}

.hint {
  font-size: 12px;
  margin-top: 4px;
}

.hint.ok {
  color: #67c23a;
}

.hint.err {
  color: #f56c6c;
}

.submit-btn {
  width: 100%;
  padding: 12px 0;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  margin-top: 8px;
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.submit-btn:active:not(:disabled) {
  transform: scale(0.98);
}

.submit-btn:hover:not(:disabled) {
  box-shadow: 0 4px 12px rgb(79 172 254 / 30%);
}

.submit-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
