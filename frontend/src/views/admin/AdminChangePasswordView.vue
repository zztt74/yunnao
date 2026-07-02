<script setup lang="ts">
// 管理员修改密码（角色无关，共享 auth.changePassword 接口）
// F-HW-03 修复：仅保留普通修改密码能力，不做任何首次登录强制改密跳转
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

// 与后端契约保持一致：密码最小长度 8 位（F-HW-05 同步要求）
const MIN_PASSWORD_LENGTH = 8

const oldPwdValid = computed(() => form.oldPassword.length > 0)
const newPwdValid = computed(() => form.newPassword.length >= MIN_PASSWORD_LENGTH)
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
  if (p.length >= 10) score++
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
    setTimeout(() => {
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
      <div class="page-title">修改密码</div>
      <div class="page-sub">普通修改密码流程，与角色无关</div>
    </div>

    <div class="form-card">
      <div class="security-tip">
        <span class="tip-icon">·</span>
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
            {{ showOld ? '隐藏' : '显示' }}
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
            :class="{ 'input-error': form.newPassword && !newPwdValid }"
            :placeholder="`请输入新密码（至少 ${MIN_PASSWORD_LENGTH} 位）`"
            maxlength="64"
            autocomplete="new-password"
          />
          <button class="eye-btn" type="button" @click="showNew = !showNew">
            {{ showNew ? '隐藏' : '显示' }}
          </button>
        </div>
        <div v-if="form.newPassword && !newPwdValid" class="hint err">
          密码长度需不少于 {{ MIN_PASSWORD_LENGTH }} 位
        </div>
        <div v-else-if="form.newPassword" class="strength-bar">
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
            :class="{ 'input-error': form.confirmPassword && !confirmPwdValid }"
            placeholder="请再次输入新密码"
            maxlength="64"
            autocomplete="new-password"
          />
          <button class="eye-btn" type="button" @click="showConfirm = !showConfirm">
            {{ showConfirm ? '隐藏' : '显示' }}
          </button>
        </div>
        <div v-if="form.confirmPassword" class="hint" :class="{ ok: confirmPwdValid, err: !confirmPwdValid }">
          {{ confirmPwdValid ? '✓ 两次密码一致' : '✗ 两次输入的密码不一致' }}
        </div>
      </div>

      <button class="submit-btn" :disabled="!canSubmit" @click="handleSubmit">
        {{ submitting ? '提交中…' : '确认修改' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.change-pwd-page {
  padding: 16px 16px 32px;
  max-width: 720px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
  padding: 0 4px;
}

.page-title {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.page-sub {
  font-size: 13px;
  color: #8e8e93;
}

.form-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 24px 22px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.security-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 13px;
  color: #d48806;
  margin-bottom: 22px;
  line-height: 1.5;
}

.tip-icon {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #d48806;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 18px;
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
  padding: 0 60px 0 14px;
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

.form-input.input-error {
  border-color: #f56c6c;
}

.eye-btn {
  position: absolute;
  top: 50%;
  right: 10px;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  font-size: 12px;
  color: #4facfe;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.15s;
}

.eye-btn:hover {
  background: #e3f0ff;
}

.strength-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
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
  margin-top: 6px;
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
  margin-top: 10px;
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
