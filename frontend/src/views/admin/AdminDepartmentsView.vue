<script setup lang="ts">
// 科室管理（§4.3）
// 设计来源：product/11_功能需求.md §4.3、roles/12_前端开发AI任务书.md
// 功能：
// - 科室列表（卡片）：编码、名称、描述、医生数量、状态
// - 按名称搜索
// - 新增/编辑科室（自定义弹层）
// - 启用/停用切换（停用时提示"停用科室不能创建新排班"）
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getDepartments,
  createDepartment,
  updateDepartment,
  setDepartmentStatus,
} from '@/api/admin'
import type {
  DepartmentResponse,
  DepartmentCreateRequest,
  DepartmentUpdateRequest,
  DepartmentStatus,
} from '@/types/admin'

const loading = ref(true)
const loadError = ref('')
const departments = ref<DepartmentResponse[]>([])

const keyword = ref('')

const filteredDepartments = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return departments.value
  return departments.value.filter((d) => d.name.toLowerCase().includes(kw))
})

// 弹层
const modalVisible = ref(false)
const modalMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive<{
  code: string
  name: string
  description: string
}>({
  code: '',
  name: '',
  description: '',
})

const codeValid = computed(() => /^[A-Za-z0-9_]{2,32}$/.test(form.code))
const nameValid = computed(() => form.name.trim().length >= 2)
const canSave = computed(
  () =>
    !saving.value &&
    nameValid.value &&
    (modalMode.value === 'edit' || codeValid.value),
)

function resetForm() {
  form.code = ''
  form.name = ''
  form.description = ''
}

function statusText(status: DepartmentStatus): string {
  return status === 'ACTIVE' ? '启用' : '停用'
}

function formatDate(iso: string): string {
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

async function loadDepartments() {
  loading.value = true
  loadError.value = ''
  try {
    departments.value = await getDepartments()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载科室列表失败'
    console.error('[AdminDepartments] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  modalMode.value = 'create'
  editingId.value = null
  resetForm()
  modalVisible.value = true
}

function openEdit(dept: DepartmentResponse) {
  modalMode.value = 'edit'
  editingId.value = dept.id
  form.code = dept.code
  form.name = dept.name
  form.description = dept.description
  modalVisible.value = true
}

function closeModal() {
  if (saving.value) return
  modalVisible.value = false
}

async function handleSubmit() {
  if (!canSave.value) return
  if (modalMode.value === 'create' && !codeValid.value) {
    ElMessage.warning('科室编码需为 2-32 位字母、数字或下划线')
    return
  }
  if (!nameValid.value) {
    ElMessage.warning('请输入科室名称（至少 2 个字）')
    return
  }
  saving.value = true
  try {
    if (modalMode.value === 'create') {
      const payload: DepartmentCreateRequest = {
        code: form.code.trim(),
        name: form.name.trim(),
        description: form.description.trim(),
      }
      const created = await createDepartment(payload)
      departments.value = [...departments.value, created]
      ElMessage.success('科室已创建')
    } else if (editingId.value !== null) {
      const payload: DepartmentUpdateRequest = {
        name: form.name.trim(),
        description: form.description.trim(),
      }
      const updated = await updateDepartment(editingId.value, payload)
      departments.value = departments.value.map((d) =>
        d.id === updated.id ? updated : d,
      )
      ElMessage.success('科室信息已更新')
    }
    modalVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
    console.error('[AdminDepartments] 保存失败：', e)
  } finally {
    saving.value = false
  }
}

async function toggleStatus(dept: DepartmentResponse) {
  const next: DepartmentStatus = dept.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  if (next === 'INACTIVE') {
    try {
      await ElMessageBox.confirm(
        '停用科室不能创建新排班，确认停用该科室吗？',
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
      await ElMessageBox.confirm('确认启用该科室吗？', '启用确认', {
        confirmButtonText: '确认启用',
        cancelButtonText: '取消',
        type: 'info',
      })
    } catch {
      return
    }
  }
  try {
    const updated = await setDepartmentStatus(dept.id, next)
    departments.value = departments.value.map((d) =>
      d.id === updated.id ? updated : d,
    )
    ElMessage.success(`${dept.name}已${next === 'ACTIVE' ? '启用' : '停用'}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '状态变更失败')
    console.error('[AdminDepartments] 状态变更失败：', e)
  }
}

onMounted(loadDepartments)
</script>

<template>
  <div class="admin-departments-view">
    <div class="page-header">
      <div class="header-left">
        <div class="header-title">科室管理</div>
        <div class="header-sub">维护医院科室档案与启用状态</div>
      </div>
      <button class="primary-btn" @click="openCreate">新增科室</button>
    </div>

    <div class="filter-bar">
      <div class="search-wrap">
        <input
          v-model="keyword"
          class="search-input"
          placeholder="按科室名称搜索"
        />
      </div>
      <div class="filter-summary">共 {{ filteredDepartments.length }} 个科室</div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载科室列表…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadDepartments">重新加载</button>
    </div>

    <div v-else-if="filteredDepartments.length === 0" class="empty-card">
      <div class="empty-icon">--</div>
      <div class="empty-text">
        {{ keyword ? '未找到匹配的科室' : '暂无科室数据' }}
      </div>
    </div>

    <div v-else class="dept-grid">
      <div
        v-for="dept in filteredDepartments"
        :key="dept.id"
        class="dept-card"
        :class="{ disabled: dept.status !== 'ACTIVE' }"
      >
        <div class="card-top">
          <div class="card-name">{{ dept.name }}</div>
          <span class="status-tag" :class="dept.status === 'ACTIVE' ? 'tag-active' : 'tag-inactive'">
            {{ statusText(dept.status) }}
          </span>
        </div>
        <div class="card-code">编码：{{ dept.code }}</div>
        <div class="card-desc">{{ dept.description || '暂无描述' }}</div>
        <div class="card-meta">
          <span class="meta-item">医生 <strong>{{ dept.doctorCount }}</strong> 名</span>
          <span class="meta-item">建档 {{ formatDate(dept.createdAt) }}</span>
        </div>
        <div class="card-actions">
          <button class="ghost-btn" @click="openEdit(dept)">编辑</button>
          <button
            class="status-btn"
            :class="dept.status === 'ACTIVE' ? 'danger-btn' : 'primary-btn'"
            @click="toggleStatus(dept)"
          >
            {{ dept.status === 'ACTIVE' ? '停用' : '启用' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹层 -->
    <div v-if="modalVisible" class="modal-mask" @click.self="closeModal">
      <div class="modal-card">
        <div class="modal-header">
          <span class="modal-title">
            {{ modalMode === 'create' ? '新增科室' : '编辑科室' }}
          </span>
          <button class="modal-close" @click="closeModal">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">
              科室编码 <span v-if="modalMode === 'create'" class="required">*</span>
              <span v-if="modalMode === 'edit'" class="form-hint">（创建后不可修改）</span>
            </label>
            <input
              v-model="form.code"
              class="form-input"
              :disabled="modalMode === 'edit'"
              :class="{ 'input-error': modalMode === 'create' && form.code && !codeValid }"
              placeholder="如 DEPT_CARDIO"
              maxlength="32"
            />
          </div>
          <div class="form-group">
            <label class="form-label">
              科室名称 <span class="required">*</span>
            </label>
            <input
              v-model="form.name"
              class="form-input"
              :class="{ 'input-error': form.name && !nameValid }"
              placeholder="如 心内科"
              maxlength="32"
            />
          </div>
          <div class="form-group">
            <label class="form-label">科室描述</label>
            <textarea
              v-model="form.description"
              class="form-textarea"
              rows="3"
              placeholder="简要描述科室诊疗范围"
              maxlength="200"
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
.admin-departments-view {
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

.search-wrap {
  flex: 1;
  min-width: 220px;
  max-width: 320px;
}

.search-input {
  width: 100%;
  height: 40px;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  padding: 0 14px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
}

.search-input::placeholder {
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

.dept-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 14px;
}

.dept-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  display: flex;
  flex-direction: column;
  transition: opacity 0.2s, box-shadow 0.2s;
}

.dept-card:hover {
  box-shadow: 0 4px 16px rgb(0 0 0 / 8%);
}

.dept-card.disabled {
  opacity: 0.7;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
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

.card-code {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 8px;
}

.card-desc {
  font-size: 13px;
  color: #4a5568;
  line-height: 1.6;
  margin-bottom: 12px;
  min-height: 42px;
}

.card-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.meta-item strong {
  color: #1a1a1a;
  font-weight: 600;
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
  max-width: 460px;
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
  padding: 20px;
  overflow-y: auto;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 20px;
  border-top: 1px solid #f0f0f0;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-size: 14px;
  color: #4a5568;
  margin-bottom: 6px;
  font-weight: 500;
}

.form-hint {
  font-size: 12px;
  color: #8e8e93;
  font-weight: 400;
  margin-left: 4px;
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
