<script setup lang="ts">
// 医生管理（§4.3）
// 设计来源：product/11_功能需求.md §4.3、roles/12_前端开发AI任务书.md
// 功能：
// - 医生列表（卡片）：姓名、职称、科室、电话、状态、排班/接诊数
// - 筛选：科室、状态、关键字
// - 新增/编辑医生（自定义弹层，新增含账号信息，编辑仅可改部分字段）
// - 启用/停用切换（停用时提示"停用医生不能接受新挂号"）
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDoctors,
  createDoctor,
  updateDoctor,
  setDoctorStatus,
  getDepartments,
} from '@/api/admin'
import type {
  DoctorManageResponse,
  DoctorCreateRequest,
  DoctorUpdateRequest,
  DoctorManageStatus,
  DepartmentResponse,
} from '@/types/admin'
import type { Gender } from '@/types/patient'

const loading = ref(true)
const loadError = ref('')
const doctors = ref<DoctorManageResponse[]>([])
const departments = ref<DepartmentResponse[]>([])

// 筛选
const filterDeptId = ref<number | ''>('')
const filterStatus = ref<DoctorManageStatus | ''>('ACTIVE')
const keyword = ref('')

const filteredDoctors = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return doctors.value.filter((d) => {
    if (filterDeptId.value !== '' && d.departmentId !== filterDeptId.value) return false
    if (filterStatus.value !== '' && d.status !== filterStatus.value) return false
    if (kw) {
      const hay = `${d.name} ${d.username} ${d.phone} ${d.title}`.toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })
})

// 弹层
const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<{
  username: string
  password: string
  name: string
  title: string
  departmentId: number | ''
  gender: Gender
  phone: string
  email: string
  specialty: string
  introduction: string
}>({
  username: '',
  password: '',
  name: '',
  title: '',
  departmentId: '',
  gender: 'MALE',
  phone: '',
  email: '',
  specialty: '',
  introduction: '',
})

const titleOptions = ['主任医师', '副主任医师', '主治医师', '住院医师']

const usernameValid = computed(() => /^[A-Za-z0-9_.]{3,32}$/.test(form.username))
const passwordValid = computed(() => form.password.length >= 6)
const nameValid = computed(() => form.name.trim().length >= 2)
const titleValid = computed(() => form.title.trim().length > 0)
const deptValid = computed(() => form.departmentId !== '' && form.departmentId !== null)
const phoneValid = computed(() => /^1\d{10}$/.test(form.phone))
const emailValid = computed(() => {
  if (!form.email) return true
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)
})

const canSave = computed(() => {
  if (saving.value) return false
  if (!nameValid.value || !titleValid.value || !deptValid.value || !phoneValid.value || !emailValid.value) {
    return false
  }
  if (modalMode.value === 'create') {
    return usernameValid.value && passwordValid.value
  }
  return true
})

function resetForm() {
  form.username = ''
  form.password = ''
  form.name = ''
  form.title = ''
  form.departmentId = ''
  form.gender = 'MALE'
  form.phone = ''
  form.email = ''
  form.specialty = ''
  form.introduction = ''
}

function statusText(status: DoctorManageStatus): string {
  return status === 'ACTIVE' ? '在岗' : '已停用'
}

function genderText(gender: Gender): string {
  return gender === 'MALE' ? '男' : '女'
}

function deptName(id: number): string {
  return departments.value.find((d) => d.id === id)?.name ?? '--'
}

async function loadAll() {
  loading.value = true
  loadError.value = ''
  try {
    const [deptList, docList] = await Promise.all([getDepartments(), getDoctors()])
    departments.value = deptList
    doctors.value = docList
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载医生列表失败'
    console.error('[AdminDoctors] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  modalMode.value = 'create'
  editingId.value = null
  resetForm()
  if (departments.value.length > 0) {
    form.departmentId = departments.value[0].id
  }
  modalVisible.value = true
}

function openEdit(doc: DoctorManageResponse) {
  modalMode.value = 'edit'
  editingId.value = doc.id
  form.username = doc.username
  form.password = ''
  form.name = doc.name
  form.title = doc.title
  form.departmentId = doc.departmentId
  form.gender = doc.gender
  form.phone = doc.phone
  form.email = doc.email
  form.specialty = doc.specialty
  form.introduction = doc.introduction
  modalVisible.value = true
}

function closeModal() {
  if (saving.value) return
  modalVisible.value = false
}

async function handleSubmit() {
  if (!canSave.value) return
  if (modalMode.value === 'create' && !usernameValid.value) {
    ElMessage.warning('用户名需为 3-32 位字母、数字、下划线或点')
    return
  }
  if (modalMode.value === 'create' && !passwordValid.value) {
    ElMessage.warning('初始密码至少 6 位')
    return
  }
  if (!phoneValid.value) {
    ElMessage.warning('请输入有效的手机号（11 位）')
    return
  }
  if (!emailValid.value) {
    ElMessage.warning('邮箱格式不正确')
    return
  }
  saving.value = true
  try {
    if (modalMode.value === 'create') {
      const payload: DoctorCreateRequest = {
        username: form.username.trim(),
        password: form.password,
        name: form.name.trim(),
        title: form.title.trim(),
        departmentId: form.departmentId as number,
        gender: form.gender,
        phone: form.phone.trim(),
        email: form.email.trim() || undefined,
        specialty: form.specialty.trim() || undefined,
        introduction: form.introduction.trim() || undefined,
      }
      const created = await createDoctor(payload)
      doctors.value = [...doctors.value, created]
      // 同步科室医生数量
      departments.value = departments.value.map((d) =>
        d.id === created.departmentId ? { ...d, doctorCount: d.doctorCount + 1 } : d,
      )
      ElMessage.success('医生账号已创建')
    } else if (editingId.value !== null) {
      const payload: DoctorUpdateRequest = {
        name: form.name.trim(),
        title: form.title.trim(),
        departmentId: form.departmentId as number,
        phone: form.phone.trim(),
        email: form.email.trim(),
        specialty: form.specialty.trim(),
        introduction: form.introduction.trim(),
      }
      const updated = await updateDoctor(editingId.value, payload)
      doctors.value = doctors.value.map((d) => (d.id === updated.id ? updated : d))
      ElMessage.success('医生信息已更新')
    }
    modalVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
    console.error('[AdminDoctors] 保存失败：', e)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(doc: DoctorManageResponse) {
  const next: DoctorManageStatus = doc.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  if (next === 'DISABLED') {
    try {
      await ElMessageBox.confirm(
        '停用医生不能接受新挂号，确认停用该医生吗？',
        '停用确认',
        {
          confirmButtonText: '确认停用',
          cancelButtonText: '取消',
          type: 'warning',
        },
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm('确认启用该医生吗？', '启用确认', {
        confirmButtonText: '确认启用',
        cancelButtonText: '取消',
        type: 'info',
      })
    } catch {
      return
    }
  }
  try {
    const updated = await setDoctorStatus(doc.id, next)
    doctors.value = doctors.value.map((d) => (d.id === updated.id ? updated : d))
    ElMessage.success(`${doc.name}已${next === 'ACTIVE' ? '启用' : '停用'}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '状态变更失败')
    console.error('[AdminDoctors] 状态变更失败：', e)
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="admin-doctors-view">
    <div class="page-header">
      <div class="header-left">
        <div class="header-title">医生管理</div>
        <div class="header-sub">维护医生账号档案、所属科室与在岗状态</div>
      </div>
      <button class="primary-btn" @click="openCreate">新增医生</button>
    </div>

    <div class="filter-bar">
      <div class="filter-fields">
        <select v-model="filterDeptId" class="filter-select">
          <option value="">全部科室</option>
          <option v-for="d in departments" :key="d.id" :value="d.id">{{ d.name }}</option>
        </select>
        <select v-model="filterStatus" class="filter-select">
          <option value="">全部状态</option>
          <option value="ACTIVE">在岗</option>
          <option value="DISABLED">已停用</option>
        </select>
        <input
          v-model="keyword"
          class="filter-input"
          placeholder="姓名 / 账号 / 电话 / 职称"
        />
      </div>
      <div class="filter-summary">共 {{ filteredDoctors.length }} 位医生</div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载医生列表…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadAll">重新加载</button>
    </div>

    <div v-else-if="filteredDoctors.length === 0" class="empty-card">
      <div class="empty-icon">--</div>
      <div class="empty-text">
        {{ keyword || filterDeptId !== '' || filterStatus !== '' ? '未找到匹配的医生' : '暂无医生数据' }}
      </div>
    </div>

    <div v-else class="doctor-grid">
      <div
        v-for="doc in filteredDoctors"
        :key="doc.id"
        class="doctor-card"
        :class="{ disabled: doc.status !== 'ACTIVE' }"
      >
        <div class="card-head">
          <div class="avatar" :class="doc.gender === 'MALE' ? 'male' : 'female'">
            {{ doc.name.charAt(0) }}
          </div>
          <div class="head-main">
            <div class="head-name">
              {{ doc.name }}
              <span class="status-tag" :class="doc.status === 'ACTIVE' ? 'tag-active' : 'tag-inactive'">
                {{ statusText(doc.status) }}
              </span>
            </div>
            <div class="head-sub">{{ doc.title }} · {{ doc.departmentName }}</div>
          </div>
        </div>
        <div class="card-info">
          <div class="info-row">
            <span class="info-label">账号</span>
            <span class="info-value">{{ doc.username }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">性别</span>
            <span class="info-value">{{ genderText(doc.gender) }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">电话</span>
            <span class="info-value">{{ doc.phone || '--' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">科室</span>
            <span class="info-value">{{ deptName(doc.departmentId) }}</span>
          </div>
        </div>
        <div class="card-stats">
          <div class="stat-item">
            <div class="stat-num">{{ doc.scheduleCount }}</div>
            <div class="stat-label">排班数</div>
          </div>
          <div class="stat-item">
            <div class="stat-num">{{ doc.encounterCount }}</div>
            <div class="stat-label">接诊数</div>
          </div>
        </div>
        <div class="card-actions">
          <button class="ghost-btn" @click="openEdit(doc)">编辑</button>
          <button
            class="status-btn"
            :class="doc.status === 'ACTIVE' ? 'danger-btn' : 'primary-btn'"
            @click="toggleStatus(doc)"
          >
            {{ doc.status === 'ACTIVE' ? '停用' : '启用' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹层 -->
    <div v-if="modalVisible" class="modal-mask" @click.self="closeModal">
      <div class="modal-card">
        <div class="modal-header">
          <span class="modal-title">
            {{ modalMode === 'create' ? '新增医生' : '编辑医生' }}
          </span>
          <button class="modal-close" @click="closeModal">×</button>
        </div>
        <div class="modal-body">
          <template v-if="modalMode === 'create'">
            <div class="section-title">账号信息</div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">登录账号 <span class="required">*</span></label>
                <input
                  v-model="form.username"
                  class="form-input"
                  :class="{ 'input-error': form.username && !usernameValid }"
                  placeholder="字母/数字/下划线"
                  maxlength="32"
                />
              </div>
              <div class="form-group">
                <label class="form-label">初始密码 <span class="required">*</span></label>
                <input
                  v-model="form.password"
                  type="password"
                  class="form-input"
                  :class="{ 'input-error': form.password && !passwordValid }"
                  placeholder="至少 6 位"
                  maxlength="64"
                />
              </div>
            </div>
          </template>

          <div class="section-title">基本信息</div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">姓名 <span class="required">*</span></label>
              <input
                v-model="form.name"
                class="form-input"
                :class="{ 'input-error': form.name && !nameValid }"
                placeholder="医生姓名"
                maxlength="32"
                :disabled="modalMode === 'edit'"
              />
            </div>
            <div class="form-group">
              <label class="form-label">职称 <span class="required">*</span></label>
              <select v-model="form.title" class="form-input">
                <option value="" disabled>请选择职称</option>
                <option v-for="t in titleOptions" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">所属科室 <span class="required">*</span></label>
              <select v-model="form.departmentId" class="form-input">
                <option value="" disabled>请选择科室</option>
                <option v-for="d in departments" :key="d.id" :value="d.id">{{ d.name }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">性别 <span class="required">*</span></label>
              <select v-model="form.gender" class="form-input" :disabled="modalMode === 'edit'">
                <option value="MALE">男</option>
                <option value="FEMALE">女</option>
              </select>
            </div>
          </div>

          <div class="section-title">联系方式</div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">手机号 <span class="required">*</span></label>
              <input
                v-model="form.phone"
                class="form-input"
                :class="{ 'input-error': form.phone && !phoneValid }"
                placeholder="11 位手机号"
                maxlength="11"
              />
            </div>
            <div class="form-group">
              <label class="form-label">电子邮箱</label>
              <input
                v-model="form.email"
                class="form-input"
                :class="{ 'input-error': form.email && !emailValid }"
                placeholder="如 name@example.com"
                maxlength="64"
              />
            </div>
          </div>

          <div class="section-title">专业信息</div>
          <div class="form-group">
            <label class="form-label">擅长方向</label>
            <textarea
              v-model="form.specialty"
              class="form-textarea"
              rows="2"
              placeholder="如 高血压、糖尿病等慢性病诊治"
              maxlength="200"
            />
          </div>
          <div class="form-group">
            <label class="form-label">个人简介</label>
            <textarea
              v-model="form.introduction"
              class="form-textarea"
              rows="3"
              placeholder="简要介绍从医经历、专业方向等"
              maxlength="500"
            />
          </div>
        </div>
        <div class="modal-footer">
          <button class="ghost-btn" :disabled="saving" @click="closeModal">取消</button>
          <button class="primary-btn" :disabled="!canSave" @click="handleSubmit">
            <span v-if="saving" class="btn-spinner" />
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-doctors-view {
  padding: 16px 16px 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
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

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-fields {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  flex: 1;
}

.filter-select,
.filter-input {
  height: 38px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  padding: 0 12px;
  font-size: 13px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.filter-select {
  min-width: 130px;
  cursor: pointer;
}

.filter-input {
  min-width: 220px;
  flex: 1;
  max-width: 300px;
}

.filter-select:focus,
.filter-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
}

.filter-input::placeholder {
  color: #c0c4cc;
}

.filter-summary {
  font-size: 13px;
  color: #8e8e93;
}

.loading-card {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 48px 20px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
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
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
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

.empty-card {
  padding: 48px 20px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 10px;
  color: #c0c4cc;
}

.empty-text {
  font-size: 14px;
  color: #8e8e93;
}

.doctor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.doctor-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  display: flex;
  flex-direction: column;
  transition: opacity 0.2s, box-shadow 0.2s;
}

.doctor-card:hover {
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
}

.doctor-card.disabled {
  opacity: 0.7;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
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

.head-main {
  flex: 1;
  min-width: 0;
}

.head-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
  flex-shrink: 0;
}

.tag-active {
  background: #f0fff4;
  color: #67c23a;
}

.tag-inactive {
  background: #f5f5f5;
  color: #8e8e93;
}

.head-sub {
  font-size: 12px;
  color: #8e8e93;
}

.card-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  margin-bottom: 14px;
  padding: 12px;
  background: #fafbfc;
  border-radius: 10px;
}

.info-row {
  display: flex;
  font-size: 13px;
  line-height: 1.8;
}

.info-label {
  width: 42px;
  color: #8e8e93;
  flex-shrink: 0;
}

.info-value {
  color: #1a1a1a;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-stats {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.stat-item {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  background: #f0f7ff;
  border-radius: 10px;
}

.stat-num {
  font-size: 18px;
  font-weight: 600;
  color: #4facfe;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #8e8e93;
  margin-top: 2px;
}

.card-actions {
  display: flex;
  gap: 10px;
  margin-top: auto;
}

.ghost-btn {
  flex: 1;
  padding: 8px 12px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  color: #4a5568;
  font-size: 13px;
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
  justify-content: center;
  gap: 6px;
}

.primary-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.status-btn {
  flex: 1;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.danger-btn {
  background: #f56c6c;
  color: #ffffff;
}

.danger-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 45%);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal-card {
  background: #ffffff;
  border-radius: 14px;
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 30px rgb(0 0 0 / 20%);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.modal-close {
  background: none;
  border: none;
  font-size: 22px;
  color: #8e8e93;
  cursor: pointer;
  line-height: 1;
  padding: 0 4px;
  transition: color 0.15s;
}

.modal-close:hover {
  color: #1a1a1a;
}

.modal-body {
  padding: 18px 20px;
  overflow-y: auto;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.section-title:not(:first-child) {
  margin-top: 18px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #f0f0f0;
}

.form-row {
  display: flex;
  gap: 14px;
}

.form-group {
  flex: 1;
  margin-bottom: 14px;
}

.form-label {
  display: block;
  font-size: 13px;
  color: #4a5568;
  margin-bottom: 6px;
  font-weight: 500;
}

.required {
  color: #f56c6c;
}

.form-input {
  width: 100%;
  height: 40px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.form-input:disabled {
  background: #f5f5f5;
  color: #8e8e93;
  cursor: not-allowed;
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
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-textarea:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgb(255 255 255 / 40%);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
</style>
