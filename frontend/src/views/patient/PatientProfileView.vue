<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getPatientInfo,
  getPatientProfile,
  updatePatientInfo,
  updatePatientProfile,
} from '@/api/patient'
import type {
  PatientResponse,
  PatientProfileResponse,
  Gender,
  PatientUpdateRequest,
} from '@/types/patient'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const patientInfo = ref<PatientResponse | null>(null)
const profileInfo = ref<PatientProfileResponse | null>(null)

/** 基本信息编辑态（§3.6 患者能修改本人档案） */
const editingBasic = ref(false)
const basicForm = reactive<PatientUpdateRequest>({
  name: '',
  gender: 'MALE' as Gender,
  birthDate: '',
  phone: '',
})
const basicValid = ref(true)
const basicErrors = reactive({ name: '', phone: '', birthDate: '' })

const profileForm = reactive({
  address: '',
  emergencyContact: '',
  emergencyPhone: '',
  allergies: '',
  medicalHistory: '',
})

async function loadData() {
  loading.value = true
  try {
    const [info, profile] = await Promise.all([
      getPatientInfo(),
      getPatientProfile(),
    ])
    patientInfo.value = info
    profileInfo.value = profile
    // 同步基本信息的编辑态
    basicForm.name = info.name || ''
    basicForm.gender = info.gender
    basicForm.birthDate = info.birthDate || ''
    basicForm.phone = info.phone || ''
    profileForm.address = profile.address || ''
    profileForm.emergencyContact = profile.emergencyContact || ''
    profileForm.emergencyPhone = profile.emergencyPhone || ''
    profileForm.allergies = profile.allergies || ''
    profileForm.medicalHistory = profile.medicalHistory || ''
  } catch (e) {
    console.error('加载患者信息失败：', e)
    ElMessage.error('加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function startEditBasic() {
  editingBasic.value = true
}

function cancelEditBasic() {
  // 还原
  if (patientInfo.value) {
    basicForm.name = patientInfo.value.name || ''
    basicForm.gender = patientInfo.value.gender
    basicForm.birthDate = patientInfo.value.birthDate || ''
    basicForm.phone = patientInfo.value.phone || ''
  }
  basicErrors.name = ''
  basicErrors.phone = ''
  basicErrors.birthDate = ''
  editingBasic.value = false
}

function validateBasic(): boolean {
  basicErrors.name = ''
  basicErrors.phone = ''
  basicErrors.birthDate = ''
  let ok = true
  if (!basicForm.name.trim()) {
    basicErrors.name = '姓名不能为空'
    ok = false
  }
  if (!basicForm.birthDate) {
    basicErrors.birthDate = '请选择出生日期'
    ok = false
  }
  if (!/^1\d{10}$/.test(basicForm.phone)) {
    basicErrors.phone = '手机号格式不正确（11 位，1 开头）'
    ok = false
  }
  basicValid.value = ok
  return ok
}

async function saveBasic() {
  if (!validateBasic()) return
  try {
    await ElMessageBox.confirm('确认保存基本信息吗？', '提示', {
      type: 'info',
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  saving.value = true
  try {
    const updated = await updatePatientInfo({
      name: basicForm.name.trim(),
      gender: basicForm.gender,
      birthDate: basicForm.birthDate,
      phone: basicForm.phone,
    })
    patientInfo.value = updated
    ElMessage.success('基本信息保存成功')
    editingBasic.value = false
  } catch (e: any) {
    console.error('保存基本信息失败：', e)
    ElMessage.error(e?.message || '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function handleSave() {
  try {
    await ElMessageBox.confirm('确认保存个人信息吗？', '提示', {
      type: 'info',
      confirmButtonText: '确认保存',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }

  saving.value = true
  try {
    const updated = await updatePatientProfile({
      address: profileForm.address,
      emergencyContact: profileForm.emergencyContact,
      emergencyPhone: profileForm.emergencyPhone,
      allergies: profileForm.allergies,
      medicalHistory: profileForm.medicalHistory,
    })
    // 用接口返回的最新 profile 同步本地状态（含 updatedAt）
    profileInfo.value = updated
    ElMessage.success('保存成功')
  } catch (e: any) {
    // 当前 MOCK 实现不会进入这里；保留兜底是给真实接口留的
    console.error('保存失败：', e)
    const msg = e?.response?.data?.message || e?.message || '保存失败，请稍后重试'
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

const genderMap: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
}

const today = new Date().toISOString().slice(0, 10)

onMounted(() => {
  loadData()
})
</script>

<template>
  <div v-loading="loading" class="profile-page">
    <div class="profile-section">
      <div class="section-header">
        <h2 class="section-title">基本信息</h2>
        <button
          v-if="!editingBasic"
          class="edit-link"
          type="button"
          @click="startEditBasic"
        >
          ✎ 编辑
        </button>
      </div>

      <!-- 只读模式 -->
      <div v-if="!editingBasic" class="info-grid">
        <div class="info-item">
          <span class="info-label">姓名</span>
          <span class="info-value">{{ patientInfo?.name || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">性别</span>
          <span class="info-value">{{ genderMap[patientInfo?.gender || ''] || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">出生日期</span>
          <span class="info-value">{{ patientInfo?.birthDate || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="info-label">联系电话</span>
          <span class="info-value">{{ patientInfo?.phone || '-' }}</span>
        </div>
        <div class="info-item full">
          <span class="info-label">状态</span>
          <span class="info-value status-active">正常</span>
        </div>
      </div>

      <!-- 编辑模式 -->
      <div v-else class="form-grid">
        <div class="form-item">
          <label class="form-label">姓名</label>
          <input
            v-model="basicForm.name"
            type="text"
            class="form-input"
            placeholder="请输入真实姓名"
            maxlength="64"
            @input="validateBasic"
          />
          <div v-if="basicErrors.name" class="err-msg">{{ basicErrors.name }}</div>
        </div>
        <div class="form-item">
          <label class="form-label">性别</label>
          <div class="radio-row">
            <label
              class="radio-item"
              :class="{ active: basicForm.gender === 'MALE' }"
            >
              <input
                v-model="basicForm.gender"
                type="radio"
                value="MALE"
                class="radio-input"
              />
              <span>男</span>
            </label>
            <label
              class="radio-item"
              :class="{ active: basicForm.gender === 'FEMALE' }"
            >
              <input
                v-model="basicForm.gender"
                type="radio"
                value="FEMALE"
                class="radio-input"
              />
              <span>女</span>
            </label>
          </div>
        </div>
        <div class="form-item">
          <label class="form-label">出生日期</label>
          <input
            v-model="basicForm.birthDate"
            type="date"
            class="form-input"
            :max="today"
            @change="validateBasic"
          />
          <div v-if="basicErrors.birthDate" class="err-msg">{{ basicErrors.birthDate }}</div>
        </div>
        <div class="form-item full">
          <label class="form-label">联系电话</label>
          <input
            v-model="basicForm.phone"
            type="tel"
            class="form-input"
            placeholder="11 位手机号"
            maxlength="11"
            @input="validateBasic"
          />
          <div v-if="basicErrors.phone" class="err-msg">{{ basicErrors.phone }}</div>
        </div>
        <div class="form-item full">
          <div class="basic-actions">
            <button
              type="button"
              class="btn-ghost"
              :disabled="saving"
              @click="cancelEditBasic"
            >
              取消
            </button>
            <button
              type="button"
              class="btn-primary"
              :disabled="saving || !basicValid"
              @click="saveBasic"
            >
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>

      <p v-if="!editingBasic" class="edit-tip">
        基本信息可以自行修改；修改后历史诊疗数据不受影响（§3.5）。
      </p>
    </div>

    <div class="profile-section">
      <h2 class="section-title">个人档案</h2>
      <div class="form-grid">
        <div class="form-item">
          <label class="form-label">家庭住址</label>
          <input
            v-model="profileForm.address"
            type="text"
            class="form-input"
            placeholder="请输入家庭住址"
            maxlength="255"
          />
        </div>
        <div class="form-item">
          <label class="form-label">紧急联系人</label>
          <input
            v-model="profileForm.emergencyContact"
            type="text"
            class="form-input"
            placeholder="请输入紧急联系人姓名"
            maxlength="64"
          />
        </div>
        <div class="form-item">
          <label class="form-label">紧急联系电话</label>
          <input
            v-model="profileForm.emergencyPhone"
            type="text"
            class="form-input"
            placeholder="请输入紧急联系电话"
            maxlength="20"
          />
        </div>
        <div class="form-item">
          <label class="form-label">过敏史</label>
          <textarea
            v-model="profileForm.allergies"
            class="form-textarea"
            placeholder="请输入过敏史（如青霉素过敏等）"
            rows="3"
            maxlength="500"
          />
        </div>
        <div class="form-item full">
          <label class="form-label">既往病史</label>
          <textarea
            v-model="profileForm.medicalHistory"
            class="form-textarea"
            placeholder="请输入既往病史（如高血压、糖尿病等）"
            rows="4"
            maxlength="1000"
          />
        </div>
      </div>
      <div class="form-actions">
        <button class="save-btn" :disabled="saving" @click="handleSave">
          {{ saving ? '保存中...' : '保存修改' }}
        </button>
      </div>
    </div>

    <!-- 功能菜单：更多操作 -->
    <div class="profile-section">
      <h2 class="section-title">更多操作</h2>
      <div class="menu-list">
        <div class="menu-item" @click="router.push('/patient/timeline')">
          <div class="menu-left">
            <span class="menu-icon" style="background: #f3e8ff; color: #9b59b6">🕐</span>
            <span class="menu-label">诊疗时间线</span>
          </div>
          <span class="menu-arrow">›</span>
        </div>
        <div class="menu-item" @click="router.push('/patient/triage-history')">
          <div class="menu-left">
            <span class="menu-icon" style="background: #d1f2eb; color: #1abc9c">📝</span>
            <span class="menu-label">分诊历史</span>
          </div>
          <span class="menu-arrow">›</span>
        </div>
        <div class="menu-item" @click="router.push('/patient/change-password')">
          <div class="menu-left">
            <span class="menu-icon" style="background: #fff3e0; color: #e6a23c">🔒</span>
            <span class="menu-label">修改密码</span>
          </div>
          <span class="menu-arrow">›</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px 18px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.section-title {
  margin: 0 0 16px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-item.full {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 14px;
  color: #8e8e93;
  flex-shrink: 0;
}

.info-value {
  font-size: 14px;
  color: #1a1a1a;
  font-weight: 500;
  text-align: right;
}

.status-active {
  color: #34c759;
}

.edit-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: #8e8e93;
}

.form-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-item.full {
  grid-column: 1 / -1;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}

.form-input {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1a1a1a;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: #f8f9fa;
}

.form-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background: #ffffff;
}

.form-input::placeholder {
  color: #c0c4cc;
}

.form-textarea {
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1a1a1a;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
  resize: vertical;
  font-family: inherit;
  background: #f8f9fa;
  line-height: 1.5;
}

.form-textarea:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background: #ffffff;
}

.form-textarea::placeholder {
  color: #c0c4cc;
}

.form-actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.save-btn {
  width: 100%;
  padding: 12px 0;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
}

.save-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgb(79 172 254 / 30%);
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ============ 功能菜单 ============ */
.menu-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 4px;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 6px;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  background: #f8f9fa;
}

.menu-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.menu-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  font-size: 16px;
}

.menu-label {
  font-size: 14px;
  color: #1a1a1a;
  font-weight: 500;
}

.menu-arrow {
  font-size: 18px;
  color: #c0c4cc;
  font-weight: 300;
}

/* ============ 基本信息编辑 ============ */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  margin-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.section-header .section-title {
  margin: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.edit-link {
  background: none;
  border: none;
  color: #4facfe;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
}

.edit-link:active {
  opacity: 0.6;
}

.radio-row {
  display: flex;
  gap: 12px;
}

.radio-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8f9fa;
  font-size: 14px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}

.radio-item.active {
  border-color: #4facfe;
  background: #e3f0ff;
  color: #1a73e8;
  font-weight: 500;
}

.radio-input {
  display: none;
}

.err-msg {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 2px;
}

.basic-actions {
  display: flex;
  gap: 10px;
}

.basic-actions .btn-primary,
.basic-actions .btn-ghost {
  flex: 1;
  height: 40px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: opacity 0.2s;
}

.basic-actions .btn-primary {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
}

.basic-actions .btn-ghost {
  background: #f1f5f9;
  color: #475569;
}

.basic-actions button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
