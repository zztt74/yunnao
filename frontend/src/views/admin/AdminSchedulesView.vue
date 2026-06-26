<script setup lang="ts">
// 排班管理（§5）
// 设计来源：product/11_功能需求.md §5
// 功能：排班列表筛选、新增排班、取消排班（同步取消未开始挂号）
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminSchedules,
  createSchedule,
  updateSchedule,
  cancelSchedule,
  getDoctors,
  getDepartments,
} from '@/api/admin'
import type {
  ScheduleCreateRequest,
  ScheduleCancelRequest,
  ScheduleUpdateRequest,
  DoctorManageResponse,
  DepartmentResponse,
} from '@/types/admin'
import type { ScheduleResponse, ScheduleStatus } from '@/types/appointment'

const loading = ref(true)
const loadError = ref('')
const schedules = ref<ScheduleResponse[]>([])

const departments = ref<DepartmentResponse[]>([])
const doctors = ref<DoctorManageResponse[]>([])

// 筛选条件
const filter = reactive({
  departmentId: null as number | null,
  doctorId: null as number | null,
  date: '' as string,
})

// 排班弹窗（新增 / 修改共用）
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const canceling = ref(false)
const createForm = reactive({
  doctorId: null as number | null,
  departmentId: null as number | null,
  departmentName: '',
  scheduleDate: '',
  startTime: '',
  endTime: '',
  maxAppointments: 10,
})

// 筛选栏：按已选科室过滤医生
const filteredDoctors = computed(() => {
  if (filter.departmentId == null) return doctors.value
  return doctors.value.filter((d) => d.departmentId === filter.departmentId)
})

// 新增弹窗：按已选科室过滤在职医生
const filteredDoctorOptions = computed(() => {
  const active = doctors.value.filter((d) => d.status === 'ACTIVE')
  if (createForm.departmentId == null) return active
  return active.filter((d) => d.departmentId === createForm.departmentId)
})

function onDepartmentChange() {
  // 科室变更后，原选中的医生可能不属于新科室，清空重选
  createForm.doctorId = null
  createForm.departmentName = ''
}

function onDoctorChange() {
  const doc = doctors.value.find((d) => d.id === createForm.doctorId)
  if (doc) {
    createForm.departmentId = doc.departmentId
    createForm.departmentName = doc.departmentName
  } else {
    createForm.departmentId = null
    createForm.departmentName = ''
  }
}

function statusText(status: ScheduleStatus): string {
  switch (status) {
    case 'AVAILABLE':
      return '可预约'
    case 'FULL':
      return '已满'
    case 'CANCELLED':
      return '已取消'
    case 'COMPLETED':
      return '已结束'
    default:
      return status
  }
}

function statusClass(status: ScheduleStatus): string {
  switch (status) {
    case 'AVAILABLE':
      return 'tag-available'
    case 'FULL':
      return 'tag-full'
    case 'CANCELLED':
      return 'tag-cancelled'
    case 'COMPLETED':
      return 'tag-completed'
    default:
      return ''
  }
}

function canCancel(status: ScheduleStatus): boolean {
  return status === 'AVAILABLE' || status === 'FULL'
}

// §5.3 仅未开始（AVAILABLE）的排班可修改；已满且无预约也可改，但通常 FULL 已有预约
function canEdit(row: ScheduleResponse): boolean {
  return row.status === 'AVAILABLE'
}

function formatTime(iso: string): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    })
  } catch {
    return '--'
  }
}

function formatDate(date: string): string {
  if (!date) return '--'
  return date
}

async function loadOptions() {
  try {
    const [depts, docs] = await Promise.all([getDepartments(), getDoctors()])
    departments.value = depts
    doctors.value = docs
  } catch (e) {
    console.error('[AdminSchedules] 加载下拉数据失败：', e)
  }
}

async function loadSchedules() {
  loading.value = true
  loadError.value = ''
  try {
    const query: { doctorId?: number; departmentId?: number; date?: string } = {}
    if (filter.doctorId != null) query.doctorId = filter.doctorId
    if (filter.departmentId != null) query.departmentId = filter.departmentId
    if (filter.date) query.date = filter.date
    schedules.value = await getAdminSchedules(query)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载排班列表失败'
    console.error('[AdminSchedules] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadSchedules()
}

function resetFilter() {
  filter.departmentId = null
  filter.doctorId = null
  filter.date = ''
  loadSchedules()
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  createForm.doctorId = null
  createForm.departmentId = null
  createForm.departmentName = ''
  createForm.scheduleDate = ''
  createForm.startTime = ''
  createForm.endTime = ''
  createForm.maxAppointments = 10
  dialogVisible.value = true
}

// §5.3 修改未开始排班：预填当前排班数据，医生/科室只读
function openEdit(row: ScheduleResponse) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  createForm.doctorId = row.doctorId
  createForm.departmentId = row.departmentId
  createForm.departmentName = row.departmentName
  createForm.scheduleDate = row.scheduleDate
  createForm.startTime = formatTime(row.startTime)
  createForm.endTime = formatTime(row.endTime)
  createForm.maxAppointments = row.maxAppointments
  dialogVisible.value = true
}

function validateForm(): string {
  if (!createForm.departmentId) return '请选择科室'
  if (!createForm.doctorId) return '请选择医生'
  if (!createForm.scheduleDate) return '请选择排班日期'
  if (!createForm.startTime) return '请选择开始时间'
  if (!createForm.endTime) return '请选择结束时间'
  if (createForm.endTime <= createForm.startTime) return '结束时间必须晚于开始时间'
  if (!createForm.maxAppointments || createForm.maxAppointments <= 0) {
    return '最大挂号数需大于 0'
  }
  return ''
}

async function submitForm() {
  const err = validateForm()
  if (err) {
    ElMessage.warning(err)
    return
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      const payload: ScheduleCreateRequest = {
        doctorId: createForm.doctorId as number,
        departmentId: createForm.departmentId as number,
        scheduleDate: createForm.scheduleDate,
        startTime: createForm.startTime,
        endTime: createForm.endTime,
        maxAppointments: createForm.maxAppointments,
      }
      await createSchedule(payload)
      ElMessage.success('排班创建成功')
    } else {
      const payload: ScheduleUpdateRequest = {
        scheduleDate: createForm.scheduleDate,
        startTime: createForm.startTime,
        endTime: createForm.endTime,
        maxAppointments: createForm.maxAppointments,
      }
      await updateSchedule(editingId.value as number, payload)
      ElMessage.success('排班已修改')
    }
    dialogVisible.value = false
    await loadSchedules()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : dialogMode.value === 'create' ? '创建排班失败' : '修改排班失败'
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row: ScheduleResponse) {
  if (canceling.value) return
  canceling.value = true
  try {
    // 第一步：确认（提示连锁影响）
    try {
      await ElMessageBox.confirm(
        '取消排班将同步取消所有未开始挂号，确认继续？',
        '取消排班',
        {
          type: 'warning',
          confirmButtonText: '继续',
          cancelButtonText: '再想想',
        },
      )
    } catch {
      return
    }
    // 第二步：输入取消原因
    let reason = ''
    try {
      const { value } = await ElMessageBox.prompt('请输入取消原因', '取消原因', {
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
        inputPlaceholder: '例如：医生临时出差',
        inputValidator: (val) =>
          (val != null && val.trim().length > 0) || '请填写取消原因',
      })
      reason = value.trim()
    } catch {
      return
    }
    // 第三步：提交
    try {
      const payload: ScheduleCancelRequest = { reason }
      await cancelSchedule(row.id, payload)
      ElMessage.success('排班已取消')
      await loadSchedules()
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '取消排班失败'
      ElMessage.error(msg)
    }
  } finally {
    canceling.value = false
  }
}

onMounted(async () => {
  await loadOptions()
  await loadSchedules()
})
</script>

<template>
  <div class="admin-schedules">
    <div class="page-header">
      <div class="header-left">
        <div class="page-title">排班管理</div>
        <div class="page-sub">管理医生排班与号源，取消排班将同步取消未开始挂号</div>
      </div>
      <button class="primary-btn" @click="openCreate">新增排班</button>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-card">
      <div class="filter-item">
        <label class="filter-label">科室</label>
        <select v-model="filter.departmentId" class="filter-select">
          <option :value="null">全部科室</option>
          <option v-for="d in departments" :key="d.id" :value="d.id">
            {{ d.name }}
          </option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">医生</label>
        <select v-model="filter.doctorId" class="filter-select">
          <option :value="null">全部医生</option>
          <option v-for="d in filteredDoctors" :key="d.id" :value="d.id">
            {{ d.name }}（{{ d.departmentName }}）
          </option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">日期</label>
        <input v-model="filter.date" type="date" class="filter-input" />
      </div>
      <div class="filter-actions">
        <button class="primary-btn" @click="onSearch">查询</button>
        <button class="ghost-btn" @click="resetFilter">重置</button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="spinner" />
      <span class="state-text">正在加载排班…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadSchedules">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="schedules.length === 0" class="state-card">
      <div class="state-title">暂无排班数据</div>
      <div class="state-desc">尝试调整筛选条件，或点击「新增排班」创建排班</div>
    </div>

    <!-- 排班表格 -->
    <div v-else class="table-card">
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>日期</th>
              <th>医生</th>
              <th>科室</th>
              <th>时段</th>
              <th>已约 / 上限</th>
              <th>剩余</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in schedules" :key="row.id">
              <td>{{ formatDate(row.scheduleDate) }}</td>
              <td>{{ row.doctorName }}</td>
              <td>{{ row.departmentName }}</td>
              <td>{{ formatTime(row.startTime) }} - {{ formatTime(row.endTime) }}</td>
              <td>
                <span class="num-strong">{{ row.bookedCount }}</span>
                <span class="num-sep">/</span>
                <span class="num-soft">{{ row.maxAppointments }}</span>
              </td>
              <td>
                <span class="remaining" :class="{ zero: row.remainingCount === 0 }">
                  {{ row.remainingCount }}
                </span>
              </td>
              <td>
                <span class="status-tag" :class="statusClass(row.status)">
                  {{ statusText(row.status) }}
                </span>
              </td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="canEdit(row)"
                    class="ghost-btn small"
                    :disabled="submitting"
                    @click="openEdit(row)"
                  >
                    修改
                  </button>
                  <button
                    v-if="canCancel(row.status)"
                    class="danger-btn"
                    :disabled="canceling"
                    @click="handleCancel(row)"
                  >
                    取消排班
                  </button>
                  <span v-if="!canEdit(row) && !canCancel(row.status)" class="text-muted">--</span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 新增 / 修改排班弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增排班' : '修改排班'"
      width="460px"
      :close-on-click-modal="false"
      append-to-body
    >
      <div class="form">
        <div class="form-item">
          <label class="form-label">科室<span class="required">*</span></label>
          <select
            v-if="dialogMode === 'create'"
            v-model="createForm.departmentId"
            class="form-select"
            @change="onDepartmentChange"
          >
            <option :value="null" disabled>请选择科室</option>
            <option v-for="d in departments" :key="d.id" :value="d.id">
              {{ d.name }}
            </option>
          </select>
          <div v-else class="form-readonly">{{ createForm.departmentName }}</div>
        </div>
        <div class="form-item">
          <label class="form-label">医生<span class="required">*</span></label>
          <select
            v-if="dialogMode === 'create'"
            v-model="createForm.doctorId"
            class="form-select"
            @change="onDoctorChange"
          >
            <option :value="null" disabled>请选择医生</option>
            <option v-for="d in filteredDoctorOptions" :key="d.id" :value="d.id">
              {{ d.name }}（{{ d.departmentName }}）
            </option>
          </select>
          <div v-else class="form-readonly">
            {{ doctors.find((d) => d.id === createForm.doctorId)?.name ?? '--' }}
          </div>
        </div>
        <div class="form-item">
          <label class="form-label">排班日期<span class="required">*</span></label>
          <input v-model="createForm.scheduleDate" type="date" class="form-input" />
        </div>
        <div class="form-row">
          <div class="form-item half">
            <label class="form-label">开始时间<span class="required">*</span></label>
            <input v-model="createForm.startTime" type="time" class="form-input" />
          </div>
          <div class="form-item half">
            <label class="form-label">结束时间<span class="required">*</span></label>
            <input v-model="createForm.endTime" type="time" class="form-input" />
          </div>
        </div>
        <div class="form-item">
          <label class="form-label">最大挂号数<span class="required">*</span></label>
          <input
            v-model.number="createForm.maxAppointments"
            type="number"
            min="1"
            class="form-input"
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <button class="ghost-btn" @click="dialogVisible = false">取消</button>
          <button
            class="primary-btn"
            :disabled="submitting"
            @click="submitForm"
          >
            {{ submitting ? '提交中…' : dialogMode === 'create' ? '确认创建' : '保存修改' }}
          </button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-schedules {
  padding: 20px 24px 32px;
  max-width: 1100px;
  margin: 0 auto;
}

/* ============ 页头 ============ */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
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
  line-height: 1.5;
}

/* ============ 按钮 ============ */
.primary-btn {
  padding: 8px 18px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.primary-btn:hover {
  opacity: 0.92;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ghost-btn {
  padding: 8px 18px;
  background: #ffffff;
  color: #475569;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
  white-space: nowrap;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #4facfe;
}

.ghost-btn.small {
  padding: 5px 12px;
  font-size: 12px;
  border-radius: 6px;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.danger-btn {
  padding: 5px 12px;
  background: #f56c6c;
  color: #ffffff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.danger-btn:hover {
  opacity: 0.9;
}

.danger-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* ============ 筛选栏 ============ */
.filter-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 16px 18px;
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 16px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-label {
  font-size: 12px;
  color: #8e8e93;
  font-weight: 500;
}

.filter-select,
.filter-input {
  height: 36px;
  min-width: 160px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
  box-sizing: border-box;
}

.filter-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 30px;
}

.filter-select:focus,
.filter-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

.filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

/* ============ 状态卡片（加载/错误/空） ============ */
.state-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 48px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid #e0e0e0;
  border-top-color: #4facfe;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 4px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state-text {
  font-size: 13px;
  color: #8e8e93;
}

.state-card.error .state-title {
  color: #f56c6c;
}

.state-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.state-desc {
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.5;
  margin-bottom: 6px;
}

/* ============ 表格 ============ */
.table-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  overflow: hidden;
}

.table-scroll {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table thead th {
  text-align: left;
  padding: 13px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  background: #fafbfc;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.data-table tbody td {
  padding: 13px 16px;
  color: #1a1a1a;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.data-table tbody tr:last-child td {
  border-bottom: none;
}

.data-table tbody tr:hover {
  background: #fafcff;
}

.num-strong {
  font-weight: 600;
  color: #1a1a1a;
}

.num-sep {
  color: #c0c4cc;
  margin: 0 2px;
}

.num-soft {
  color: #8e8e93;
}

.remaining {
  font-weight: 600;
  color: #67c23a;
}

.remaining.zero {
  color: #f56c6c;
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

/* ============ 状态标签 ============ */
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 10px;
  line-height: 1.6;
}

.tag-available {
  background: #f6ffed;
  color: #389e0d;
}

.tag-full {
  background: #fff7e6;
  color: #d48806;
}

.tag-cancelled {
  background: #f5f5f5;
  color: #8e8e93;
}

.tag-completed {
  background: #e3f0ff;
  color: #1890ff;
}

/* ============ 新增弹窗表单 ============ */
.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-row {
  display: flex;
  gap: 14px;
}

.form-item.half {
  flex: 1;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}

.required {
  color: #f56c6c;
  margin-left: 2px;
}

.form-select,
.form-input {
  height: 38px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
  box-sizing: border-box;
  width: 100%;
}

.form-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 30px;
}

.form-select:focus,
.form-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

.form-readonly {
  height: 38px;
  line-height: 38px;
  padding: 0 12px;
  border: 1px dashed #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #8e8e93;
  background: #f8f9fa;
  box-sizing: border-box;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
