<script setup lang="ts">
// 医生个人信息（医生端）
// 设计来源：product/11_功能需求.md §4.3、§2.3、roles/12_前端开发AI任务书.md §3.1
// 功能：
// - 展示医生基本档案（姓名、职称、科室、性别等系统管理字段只读）
// - 编辑可修改字段：联系电话、邮箱、擅长方向、个人简介（§4.3）
// - 修改密码入口（§2.3：密码修改适用于所有角色）
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDoctorProfile, updateDoctorProfile } from '@/api/doctor'
import type { DoctorProfile, DoctorProfileUpdateRequest } from '@/types/doctor'

const router = useRouter()

const loading = ref(true)
const loadError = ref('')
const profile = ref<DoctorProfile | null>(null)

// 编辑模式
const editing = ref(false)
const saving = ref(false)
const form = ref<DoctorProfileUpdateRequest>({
  phone: '',
  email: '',
  specialty: '',
  introduction: '',
})

// 校验
const phoneValid = computed(() => /^1\d{10}$/.test(form.value.phone))
const emailValid = computed(() => {
  if (!form.value.email) return true
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.value.email)
})
const canSave = computed(() => phoneValid.value && emailValid.value && !saving.value)

function genderText(gender?: string): string {
  if (gender === 'MALE') return '男'
  if (gender === 'FEMALE') return '女'
  return '--'
}

function statusText(status?: string): string {
  if (status === 'ACTIVE') return '在岗'
  if (status === 'DISABLED') return '已停用'
  return '--'
}

function formatDate(iso?: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
  } catch {
    return '--'
  }
}

async function loadProfile() {
  loading.value = true
  loadError.value = ''
  try {
    profile.value = await getDoctorProfile()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载个人信息失败'
    console.error('[DoctorProfile] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function startEdit() {
  if (!profile.value) return
  form.value = {
    phone: profile.value.phone,
    email: profile.value.email,
    specialty: profile.value.specialty,
    introduction: profile.value.introduction,
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function saveEdit() {
  if (!phoneValid.value) {
    ElMessage.warning('请输入有效的手机号（11 位）')
    return
  }
  if (!emailValid.value) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  try {
    await ElMessageBox.confirm('确认保存修改后的个人信息吗？', '保存确认', {
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
      type: 'info',
    })
  } catch {
    return
  }
  saving.value = true
  try {
    const updated = await updateDoctorProfile(form.value)
    profile.value = updated
    editing.value = false
    ElMessage.success('个人信息已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function goChangePassword() {
  router.push('/doctor/change-password')
}

onMounted(loadProfile)
</script>

<template>
  <div class="profile-view">
    <div class="page-header">
      <div class="header-title">个人信息</div>
      <div class="header-sub">查看与维护医生个人档案</div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载个人信息…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadProfile">重新加载</button>
    </div>

    <template v-else-if="profile">
      <!-- 基本档案（系统管理字段，只读） -->
      <div class="block">
        <div class="block-title">
          基本档案
          <span class="block-sub">姓名、职称、科室由管理员维护</span>
        </div>
        <div class="profile-head">
          <div class="avatar" :class="profile.gender === 'MALE' ? 'male' : 'female'">
            {{ profile.doctorName.charAt(0) }}
          </div>
          <div class="profile-main">
            <div class="profile-name">
              {{ profile.doctorName }}
              <span class="profile-tag">{{ profile.title }}</span>
              <span class="profile-tag dept">{{ profile.departmentName }}</span>
            </div>
            <div class="profile-sub">
              医生编号 {{ profile.doctorId }} · 建档 {{ formatDate(profile.createdAt) }}
            </div>
          </div>
        </div>
        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">性别</span>
            <span class="info-value">{{ genderText(profile.gender) }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">账号状态</span>
            <span class="info-value" :class="{ 'status-active': profile.status === 'ACTIVE' }">
              {{ statusText(profile.status) }}
            </span>
          </div>
          <div class="info-item">
            <span class="info-label">所属科室</span>
            <span class="info-value">{{ profile.departmentName }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">职称</span>
            <span class="info-value">{{ profile.title }}</span>
          </div>
        </div>
      </div>

      <!-- 可编辑信息 -->
      <div class="block">
        <div class="block-title">
          联系方式与专业信息
          <span class="block-sub">可自行维护</span>
          <button v-if="!editing" class="link-btn" @click="startEdit">编辑</button>
        </div>

        <!-- 查看模式 -->
        <template v-if="!editing">
          <div class="info-grid">
            <div class="info-item">
              <span class="info-label">联系电话</span>
              <span class="info-value">{{ profile.phone || '--' }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">电子邮箱</span>
              <span class="info-value">{{ profile.email || '--' }}</span>
            </div>
          </div>
          <div class="field-block">
            <div class="field-label">擅长方向</div>
            <div class="field-text">{{ profile.specialty || '--' }}</div>
          </div>
          <div class="field-block">
            <div class="field-label">个人简介</div>
            <div class="field-text">{{ profile.introduction || '--' }}</div>
          </div>
        </template>

        <!-- 编辑模式 -->
        <template v-else>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">
                联系电话 <span class="required">*</span>
              </label>
              <input
                v-model="form.phone"
                class="form-input"
                :class="{ 'input-error': !phoneValid }"
                placeholder="11 位手机号"
              />
            </div>
            <div class="form-group">
              <label class="form-label">电子邮箱</label>
              <input
                v-model="form.email"
                class="form-input"
                :class="{ 'input-error': !emailValid }"
                placeholder="如 name@example.com"
              />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">擅长方向</label>
            <textarea
              v-model="form.specialty"
              class="form-textarea"
              rows="2"
              placeholder="如 高血压、糖尿病等慢性病诊治"
            />
          </div>
          <div class="form-group">
            <label class="form-label">个人简介</label>
            <textarea
              v-model="form.introduction"
              class="form-textarea"
              rows="3"
              placeholder="简要介绍从医经历、专业方向等"
            />
          </div>
          <div class="form-actions">
            <button class="ghost-btn" :disabled="saving" @click="cancelEdit">取消</button>
            <button class="primary-btn" :disabled="!canSave" @click="saveEdit">
              <span v-if="saving" class="btn-spinner" />
              {{ saving ? '保存中…' : '保存修改' }}
            </button>
          </div>
        </template>
      </div>

      <!-- 账号安全 -->
      <div class="block">
        <div class="block-title">账号安全</div>
        <div class="action-row" @click="goChangePassword">
          <div class="action-info">
            <div class="action-name">修改密码</div>
            <div class="action-desc">建议定期更换密码以保障账号安全</div>
          </div>
          <span class="action-arrow">→</span>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile-view {
  padding: 16px 16px 24px;
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  margin-bottom: 4px;
}

.header-title {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.header-sub {
  font-size: 13px;
  color: #8e8e93;
}

.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  background: #ffffff;
  border-radius: 14px;
}

.loading-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #e0e0e0;
  border-top-color: #4facfe;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 14px;
  color: #8e8e93;
}

.fallback-card {
  padding: 32px 20px;
  background: #ffffff;
  border-radius: 14px;
  text-align: center;
}

.error-card .fallback-title {
  color: #f56c6c;
}

.fallback-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.fallback-desc {
  font-size: 14px;
  color: #8e8e93;
  margin-bottom: 16px;
  line-height: 1.5;
}

.block {
  background: #ffffff;
  border-radius: 14px;
  padding: 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.block-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 16px;
}

.block-sub {
  font-size: 13px;
  color: #8e8e93;
  font-weight: 400;
}

.link-btn {
  margin-left: auto;
  background: none;
  border: none;
  color: #4facfe;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.15s;
}

.link-btn:hover {
  opacity: 0.8;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 600;
  color: #ffffff;
  flex-shrink: 0;
}

.avatar.male {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
}

.avatar.female {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.profile-name {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.profile-tag {
  font-size: 12px;
  font-weight: 400;
  color: #4facfe;
  background: #e6f7ff;
  padding: 2px 10px;
  border-radius: 10px;
}

.profile-tag.dept {
  color: #67c23a;
  background: #f0fff4;
}

.profile-sub {
  font-size: 13px;
  color: #8e8e93;
  margin-top: 4px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
}

.info-item {
  display: flex;
  font-size: 14px;
  line-height: 1.8;
}

.info-label {
  width: 90px;
  color: #8e8e93;
  flex-shrink: 0;
}

.info-value {
  color: #1a1a1a;
  flex: 1;
}

.status-active {
  color: #67c23a;
  font-weight: 500;
}

.field-block {
  margin-top: 14px;
}

.field-label {
  font-size: 14px;
  color: #8e8e93;
  margin-bottom: 6px;
}

.field-text {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.6;
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 8px;
}

.form-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}

.form-group {
  flex: 1;
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 14px;
  color: #4a5568;
  margin-bottom: 6px;
  font-weight: 500;
}

.required {
  color: #f56c6c;
}

.form-input {
  width: 100%;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.form-input.input-error {
  border-color: #f56c6c;
}

.form-textarea {
  width: 100%;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  resize: vertical;
  font-family: inherit;
  line-height: 1.5;
  box-sizing: border-box;
}

.form-textarea:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.primary-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.primary-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ghost-btn {
  padding: 8px 20px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  color: #4a5568;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.ghost-btn:hover:not(:disabled) {
  border-color: #4facfe;
  color: #4facfe;
}

.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgb(255 255 255 / 40%);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #fafbfc;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s;
}

.action-row:hover {
  background: #f0f7ff;
}

.action-name {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 2px;
}

.action-desc {
  font-size: 12px;
  color: #8e8e93;
}

.action-arrow {
  font-size: 16px;
  color: #c0c0c0;
}
</style>
