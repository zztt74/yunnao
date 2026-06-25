<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPatientInfo, getPatientProfile, updatePatientProfile } from '@/api/patient'
import type { PatientResponse, PatientProfileResponse } from '@/types/patient'

const loading = ref(false)
const saving = ref(false)
const patientInfo = ref<PatientResponse | null>(null)
const profileInfo = ref<PatientProfileResponse | null>(null)

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
    profileForm.address = profile.address || ''
    profileForm.emergencyContact = profile.emergencyContact || ''
    profileForm.emergencyPhone = profile.emergencyPhone || ''
    profileForm.allergies = profile.allergies || ''
    profileForm.medicalHistory = profile.medicalHistory || ''
  } catch (e) {
    // 后端接口未就绪时使用演示数据
    patientInfo.value = {
      id: 1,
      userId: 1,
      name: '张三',
      gender: 'MALE',
      birthDate: '1990-01-01',
      phone: '138****8888',
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    }
    profileInfo.value = {
      id: 1,
      patientId: 1,
      address: '',
      emergencyContact: '',
      emergencyPhone: '',
      allergies: '',
      medicalHistory: '',
      createdAt: '2026-01-01T00:00:00',
      updatedAt: '2026-01-01T00:00:00',
    }
    console.warn('患者信息接口未就绪，使用演示数据：', e)
  } finally {
    loading.value = false
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
    await updatePatientProfile({
      address: profileForm.address,
      emergencyContact: profileForm.emergencyContact,
      emergencyPhone: profileForm.emergencyPhone,
      allergies: profileForm.allergies,
      medicalHistory: profileForm.medicalHistory,
    })
    ElMessage.success('保存成功')
    await loadData()
  } catch (e) {
    console.error('保存失败：', e)
    ElMessage.error('保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

const genderMap: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div v-loading="loading" class="profile-page">
    <div class="profile-section">
      <h2 class="section-title">基本信息</h2>
      <div class="info-grid">
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
      <p class="edit-tip">基本信息如需修改，请联系医院工作人员。</p>
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
</style>
