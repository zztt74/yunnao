<script setup lang="ts">
// 用户管理（§2.3）
// 设计来源：product/11_功能需求.md §2.3、roles/12_前端开发AI任务书.md
// 功能：
// - 用户列表（表格）：账号、姓名、角色、状态、电话、最后登录
// - 筛选：角色、状态、关键字
// - 新增/编辑用户（自定义弹层）
// - 重置密码（弹窗输入新密码）
// - 状态变更：启用→停用（需原因）、停用→启用（需原因）、锁定→启用（需原因）
import { ref, computed, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getUsers,
  getDepartments,
  createUser,
  updateUser,
  changeUserStatus,
  resetUserPassword,
} from '@/api/admin'
import type {
  UserManageResponse,
  UserCreateRequest,
  UserUpdateRequest,
  UserStatus,
  UserStatusChangeRequest,
  ResetPasswordRequest,
  DepartmentResponse,
} from '@/types/admin'
import type { UserRole } from '@/types/auth'

const loading = ref(true)
const loadError = ref('')
const users = ref<UserManageResponse[]>([])
const departments = ref<DepartmentResponse[]>([])

// 筛选
const filterRole = ref<UserRole | 'ALL'>('ALL')
const filterStatus = ref<UserStatus | 'ALL'>('ENABLED')
const keyword = ref('')

const filteredUsers = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return users.value.filter((u) => {
    if (filterRole.value !== 'ALL' && !u.roles.includes(filterRole.value)) return false
    if (filterStatus.value !== 'ALL' && u.status !== filterStatus.value) return false
    if (kw) {
      const hay = `${u.username} ${u.realName} ${u.phone}`.toLowerCase()
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
  realName: string
  roles: UserRole[]
  phone: string
  email: string
  departmentId: number | null
  doctorTitle: string
  specialty: string
  education: string
  experienceYears: number | null
  introduction: string
}>({
  username: '',
  password: '',
  realName: '',
  roles: [],
  phone: '',
  email: '',
  departmentId: null,
  doctorTitle: 'ATTENDING',
  specialty: '',
  education: '',
  experienceYears: null,
  introduction: '',
})

const roleOptions: { value: UserRole; label: string }[] = [
  { value: 'PATIENT', label: '患者' },
  { value: 'DOCTOR', label: '医生' },
  { value: 'ADMIN', label: '管理员' },
]
const doctorTitleOptions = [
  { value: 'CHIEF', label: '主任医师' },
  { value: 'DEPUTY_CHIEF', label: '副主任医师' },
  { value: 'ATTENDING', label: '主治医师' },
  { value: 'RESIDENT', label: '住院医师' },
]
const creatableRoleOptions = computed(() =>
  roleOptions.filter((option) => option.value === 'DOCTOR' || option.value === 'ADMIN'),
)

const usernameValid = computed(() => /^[A-Za-z0-9_.]{3,32}$/.test(form.username))
const passwordValid = computed(() => form.password.length >= 8)
const realNameValid = computed(() => form.realName.trim().length >= 2)
const rolesValid = computed(() => form.roles.length > 0)
const phoneValid = computed(() => /^1\d{10}$/.test(form.phone))
const doctorSelected = computed(() => form.roles.includes('DOCTOR'))
const supportedRoleValid = computed(() => form.roles.every((role) => role === 'ADMIN' || role === 'DOCTOR'))
const departmentValid = computed(() => !doctorSelected.value || form.departmentId !== null)
const emailValid = computed(() => {
  if (!form.email) return true
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)
})

const canSave = computed(() => {
  if (saving.value) return false
  if (!realNameValid.value || !rolesValid.value || !supportedRoleValid.value || !phoneValid.value || !emailValid.value || !departmentValid.value) {
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
  form.realName = ''
  form.roles = []
  form.phone = ''
  form.email = ''
  form.departmentId = null
  form.doctorTitle = 'ATTENDING'
  form.specialty = ''
  form.education = ''
  form.experienceYears = null
  form.introduction = ''
}

function toggleRole(role: UserRole) {
  form.roles = form.roles.includes(role) ? [] : [role]
}

function statusText(status: UserStatus): string {
  switch (status) {
    case 'ENABLED':
      return '正常'
    case 'DISABLED':
      return '已停用'
    case 'LOCKED':
      return '已锁定'
    default:
      return status
  }
}

function statusClass(status: UserStatus): string {
  switch (status) {
    case 'ENABLED':
      return 'tag-enabled'
    case 'DISABLED':
      return 'tag-disabled'
    case 'LOCKED':
      return 'tag-locked'
    default:
      return ''
  }
}

function roleLabel(role: UserRole): string {
  switch (role) {
    case 'PATIENT':
      return '患者'
    case 'DOCTOR':
      return '医生'
    case 'ADMIN':
      return '管理员'
    default:
      return role
  }
}

function roleTagClass(role: UserRole): string {
  switch (role) {
    case 'PATIENT':
      return 'role-patient'
    case 'DOCTOR':
      return 'role-doctor'
    case 'ADMIN':
      return 'role-admin'
    default:
      return ''
  }
}

function formatDateTime(iso: string | null): string {
  if (!iso) return '从未登录'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '--'
  }
}

async function loadUsers() {
  loading.value = true
  loadError.value = ''
  try {
    const [userList, departmentList] = await Promise.all([
      getUsers(),
      getDepartments(),
    ])
    users.value = userList
    departments.value = departmentList
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载用户列表失败'
    console.error('[AdminUsers] 加载失败：', e)
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

function openEdit(user: UserManageResponse) {
  modalMode.value = 'edit'
  editingId.value = user.id
  form.username = user.username
  form.password = ''
  form.realName = user.realName
  form.roles = [...user.roles]
  form.phone = user.phone
  form.email = user.email
  form.departmentId = null
  form.doctorTitle = 'ATTENDING'
  form.specialty = ''
  form.education = ''
  form.experienceYears = null
  form.introduction = ''
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
    ElMessage.warning('初始密码至少 8 位')
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
  if (!supportedRoleValid.value) {
    ElMessage.warning('本阶段仅支持创建或修改医生、管理员账号')
    return
  }
  if (!departmentValid.value) {
    ElMessage.warning('创建医生账号时必须选择科室')
    return
  }
  saving.value = true
  try {
    if (modalMode.value === 'create') {
      const payload: UserCreateRequest = {
        username: form.username.trim(),
        password: form.password,
        realName: form.realName.trim(),
        roles: [...form.roles],
        phone: form.phone.trim(),
        email: form.email.trim() || undefined,
        departmentId: doctorSelected.value ? form.departmentId ?? undefined : undefined,
        doctorName: doctorSelected.value ? form.realName.trim() : undefined,
        doctorTitle: doctorSelected.value ? form.doctorTitle.trim() || undefined : undefined,
        specialty: doctorSelected.value ? form.specialty.trim() || undefined : undefined,
        education: doctorSelected.value ? form.education.trim() || undefined : undefined,
        experienceYears: doctorSelected.value ? form.experienceYears ?? undefined : undefined,
        introduction: doctorSelected.value ? form.introduction.trim() || undefined : undefined,
      }
      const created = await createUser(payload)
      users.value = [created, ...users.value]
      ElMessage.success('用户已创建')
    } else if (editingId.value !== null) {
      const payload: UserUpdateRequest = {
        realName: form.realName.trim(),
        roles: [...form.roles],
        phone: form.phone.trim(),
        email: form.email.trim(),
      }
      const updated = await updateUser(editingId.value, payload)
      users.value = users.value.map((u) => (u.id === updated.id ? updated : u))
      ElMessage.success('用户信息已更新')
    }
    modalVisible.value = false
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
    console.error('[AdminUsers] 保存失败：', e)
  } finally {
    saving.value = false
  }
}

async function handleResetPassword(user: UserManageResponse) {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入 ${user.realName}（${user.username}）的新密码，至少 8 位`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputPlaceholder: '请输入新密码',
        inputType: 'password',
        inputValidator: (input: string) => {
          if (!input) return '请输入新密码'
          if (input.length < 8) return '密码至少 8 位'
          return true
        },
      },
    )
    const payload: ResetPasswordRequest = { newPassword: value }
    await resetUserPassword(user.id, payload)
    ElMessage.success('密码已重置')
  } catch (action) {
    if (action === 'cancel' || action === 'close') return
    ElMessage.error(action instanceof Error ? action.message : '重置密码失败')
    console.error('[AdminUsers] 重置密码失败：', action)
  }
}

async function handleChangeStatus(user: UserManageResponse) {
  // 启用 → 停用（需原因）；停用 → 启用（需原因）；锁定 → 启用（需原因）
  if (user.status === 'ENABLED') {
    let reason = ''
    try {
      const res = await ElMessageBox.prompt(
        '停用后该用户将无法登录系统，请输入停用原因',
        '停用用户',
        {
          confirmButtonText: '确认停用',
          cancelButtonText: '取消',
          inputPlaceholder: '请输入停用原因',
          inputType: 'textarea',
          inputValidator: (input: string) => {
            if (!input || !input.trim()) return '停用原因不能为空'
            return true
          },
        },
      )
      reason = res.value.trim()
    } catch {
      return
    }
    try {
      const payload: UserStatusChangeRequest = { status: 'DISABLED', reason }
      const updated = await changeUserStatus(user.id, payload)
      users.value = users.value.map((u) => (u.id === updated.id ? updated : u))
      ElMessage.success('用户已停用')
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '停用失败')
      console.error('[AdminUsers] 停用失败：', e)
    }
    return
  }

  // 停用 / 锁定 → 启用（输入原因，允许为空）
  const actionLabel = user.status === 'LOCKED' ? '解锁' : '启用'
  let inputReason = ''
  try {
    const res = await ElMessageBox.prompt(
      `请输入${actionLabel}用户 ${user.realName} 的原因`,
      `${actionLabel}用户`,
      {
        confirmButtonText: `确认${actionLabel}`,
        cancelButtonText: '取消',
        inputPlaceholder: `请输入${actionLabel}原因（可不填）`,
        inputType: 'textarea',
        inputValue: `管理员${actionLabel}`,
      },
    )
    inputReason = res.value.trim()
  } catch {
    return
  }
  try {
    const payload: UserStatusChangeRequest = {
      status: 'ENABLED',
      reason: inputReason || `管理员${actionLabel}`,
    }
    const updated = await changeUserStatus(user.id, payload)
    users.value = users.value.map((u) => (u.id === updated.id ? updated : u))
    ElMessage.success(`用户已${actionLabel}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : `${actionLabel}失败`)
    console.error('[AdminUsers] 状态变更失败：', e)
  }
}

function statusActionText(status: UserStatus): string {
  if (status === 'ENABLED') return '停用'
  if (status === 'LOCKED') return '解锁'
  return '启用'
}

onMounted(loadUsers)
</script>

<template>
  <div class="admin-users-view">
    <div class="page-header">
      <div class="header-left">
        <div class="header-title">用户管理</div>
        <div class="header-sub">管理系统账号、角色与启用状态</div>
      </div>
      <button class="primary-btn" @click="openCreate">新增用户</button>
    </div>

    <div class="filter-bar">
      <div class="filter-fields">
        <select v-model="filterRole" class="filter-select">
          <option value="ALL">全部角色</option>
          <option value="PATIENT">患者</option>
          <option value="DOCTOR">医生</option>
          <option value="ADMIN">管理员</option>
        </select>
        <select v-model="filterStatus" class="filter-select">
          <option value="ALL">全部状态</option>
          <option value="ENABLED">正常</option>
          <option value="DISABLED">已停用</option>
          <option value="LOCKED">已锁定</option>
        </select>
        <input
          v-model="keyword"
          class="filter-input"
          placeholder="账号 / 姓名 / 电话"
        />
      </div>
      <div class="filter-summary">共 {{ filteredUsers.length }} 个用户</div>
    </div>

    <div v-if="loading" class="loading-card">
      <span class="loading-spinner" />
      <span class="loading-text">正在加载用户列表…</span>
    </div>

    <div v-else-if="loadError" class="fallback-card error-card">
      <div class="fallback-title">加载失败</div>
      <div class="fallback-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadUsers">重新加载</button>
    </div>

    <div v-else-if="filteredUsers.length === 0" class="empty-card">
      <div class="empty-icon">--</div>
      <div class="empty-text">
        {{ keyword || filterRole !== 'ALL' || filterStatus !== 'ALL' ? '未找到匹配的用户' : '暂无用户数据' }}
      </div>
    </div>

    <div v-else class="table-card">
      <div class="table-scroll">
        <table class="user-table">
          <thead>
            <tr>
              <th class="col-username">账号</th>
              <th class="col-name">姓名</th>
              <th class="col-roles">角色</th>
              <th class="col-status">状态</th>
              <th class="col-phone">电话</th>
              <th class="col-login">最后登录</th>
              <th class="col-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in filteredUsers" :key="user.id">
              <td class="col-username">
                <div class="cell-username">{{ user.username }}</div>
              </td>
              <td class="col-name">{{ user.realName }}</td>
              <td class="col-roles">
                <div class="role-tags">
                  <span
                    v-for="role in user.roles"
                    :key="role"
                    class="role-tag"
                    :class="roleTagClass(role)"
                  >
                    {{ roleLabel(role) }}
                  </span>
                </div>
              </td>
              <td class="col-status">
                <span class="status-tag" :class="statusClass(user.status)">
                  {{ statusText(user.status) }}
                </span>
              </td>
              <td class="col-phone">{{ user.phone || '--' }}</td>
              <td class="col-login">{{ formatDateTime(user.lastLoginAt) }}</td>
              <td class="col-actions">
                <div class="row-actions">
                  <button class="link-btn" @click="openEdit(user)">编辑</button>
                  <button class="link-btn" @click="handleResetPassword(user)">重置密码</button>
                  <button
                    class="link-btn"
                    :class="{ 'link-danger': user.status === 'ENABLED' }"
                    @click="handleChangeStatus(user)"
                  >
                    {{ statusActionText(user.status) }}
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 新增/编辑弹层 -->
    <div v-if="modalVisible" class="modal-mask" @click.self="closeModal">
      <div class="modal-card">
        <div class="modal-header">
          <span class="modal-title">
            {{ modalMode === 'create' ? '新增用户' : '编辑用户' }}
          </span>
          <button class="modal-close" @click="closeModal">×</button>
        </div>
        <div class="modal-body">
          <template v-if="modalMode === 'create'">
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
                  placeholder="至少 8 位"
                  maxlength="64"
                />
              </div>
            </div>
          </template>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">姓名 <span class="required">*</span></label>
              <input
                v-model="form.realName"
                class="form-input"
                :class="{ 'input-error': form.realName && !realNameValid }"
                placeholder="真实姓名"
                maxlength="32"
              />
            </div>
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
          </div>
          <div class="form-group">
            <label class="form-label">
              角色 <span class="required">*</span>
              <span class="form-hint">可多选</span>
            </label>
            <div class="role-checkbox-group">
              <label
                v-for="opt in creatableRoleOptions"
                :key="opt.value"
                class="role-checkbox"
                :class="{ checked: form.roles.includes(opt.value) }"
              >
                <input
                  type="checkbox"
                  :checked="form.roles.includes(opt.value)"
                  @change="toggleRole(opt.value)"
                />
                <span>{{ opt.label }}</span>
              </label>
            </div>
          </div>
          <div v-if="doctorSelected && modalMode === 'create'" class="doctor-fields">
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">所属科室 <span class="required">*</span></label>
                <select v-model="form.departmentId" class="form-input">
                  <option :value="null">请选择科室</option>
                  <option
                    v-for="department in departments"
                    :key="department.id"
                    :value="department.id"
                  >
                    {{ department.name }}
                  </option>
                </select>
              </div>
              <div class="form-group">
                <label class="form-label">医生职称</label>
                <select v-model="form.doctorTitle" class="form-input">
                  <option
                    v-for="option in doctorTitleOptions"
                    :key="option.value"
                    :value="option.value"
                  >
                    {{ option.label }}
                  </option>
                </select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">擅长方向</label>
                <input
                  v-model="form.specialty"
                  class="form-input"
                  placeholder="如 心血管内科"
                  maxlength="255"
                />
              </div>
              <div class="form-group">
                <label class="form-label">学历</label>
                <input
                  v-model="form.education"
                  class="form-input"
                  placeholder="如 硕士"
                  maxlength="64"
                />
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label class="form-label">从业年限</label>
                <input
                  v-model.number="form.experienceYears"
                  class="form-input"
                  type="number"
                  min="0"
                  max="80"
                  placeholder="如 8"
                />
              </div>
              <div class="form-group">
                <label class="form-label">简介</label>
                <input
                  v-model="form.introduction"
                  class="form-input"
                  placeholder="医生简介，可选"
                  maxlength="500"
                />
              </div>
            </div>
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
.admin-users-view {
  padding: 16px 16px 24px;
  max-width: 1200px;
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
  min-width: 120px;
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

.table-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  overflow: hidden;
}

.table-scroll {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 880px;
}

.user-table thead th {
  background: #fafbfc;
  font-size: 13px;
  font-weight: 600;
  color: #4a5568;
  text-align: left;
  padding: 12px 14px;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.user-table tbody td {
  font-size: 13px;
  color: #1a1a1a;
  padding: 12px 14px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.user-table tbody tr:last-child td {
  border-bottom: none;
}

.user-table tbody tr:hover {
  background: #fafcff;
}

.cell-username {
  font-weight: 500;
  color: #1a1a1a;
}

.col-roles {
  min-width: 110px;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.role-tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 8px;
  font-weight: 500;
}

.role-patient {
  background: #e6f7ff;
  color: #1890ff;
}

.role-doctor {
  background: #f0fff4;
  color: #67c23a;
}

.role-admin {
  background: #fff7e6;
  color: #d48806;
}

.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
  display: inline-block;
}

.tag-enabled {
  background: #f0fff4;
  color: #67c23a;
}

.tag-disabled {
  background: #fff1f0;
  color: #f56c6c;
}

.tag-locked {
  background: #fff7e6;
  color: #d48806;
}

.col-login {
  color: #8e8e93;
  font-size: 12px;
  white-space: nowrap;
}

.col-actions {
  min-width: 180px;
}

.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.link-btn {
  background: none;
  border: none;
  color: #4facfe;
  font-size: 13px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}

.link-btn:hover {
  background: #f0f7ff;
}

.link-btn.link-danger {
  color: #f56c6c;
}

.link-btn.link-danger:hover {
  background: #fff1f0;
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
  max-width: 520px;
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

.form-input.input-error {
  border-color: #f56c6c;
}

.role-checkbox-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.role-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 13px;
  color: #4a5568;
  background: #ffffff;
  cursor: pointer;
  transition: all 0.15s;
}

.role-checkbox:hover {
  border-color: #4facfe;
}

.role-checkbox.checked {
  border-color: #4facfe;
  color: #4facfe;
  background: #f0f7ff;
}

.role-checkbox input {
  margin: 0;
  accent-color: #4facfe;
}

.doctor-fields {
  padding: 12px;
  margin-bottom: 14px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  background: #fafcff;
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
</style>
