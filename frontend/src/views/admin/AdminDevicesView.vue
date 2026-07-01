<script setup lang="ts">
// 设备管理（§13）
// 设计来源：product/11_功能需求.md §13、product/12_业务流程与状态机.md §13.6
// §13.6：每次设备状态变化必须记录来源状态、目标状态、操作人、时间和原因
// 表单字段对齐后端 B2 DeviceCreateRequest/DeviceUpdateRequest：
//   code/name/type/departmentId/location/manufacturer/model/serialNumber/notes/purchaseDate/warrantyUntil
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminDevices,
  createDevice,
  updateDevice,
  setDeviceStatus,
  getDeviceStatusHistory,
  getDepartments,
} from '@/api/admin'
import type {
  DeviceResponse,
  DeviceStatus,
  DeviceStatusHistory,
  DeviceCategory,
} from '@/types/device'
import type { DepartmentResponse } from '@/types/admin'

// ---- 列表状态 ----
const loading = ref(false)
const loadError = ref('')
const devices = ref<DeviceResponse[]>([])

// ---- 科室列表（用于表单下拉与列表展示科室名）----
const departments = ref<DepartmentResponse[]>([])
const departmentNameById = (id: number | null | undefined): string => {
  if (id === null || id === undefined) return ''
  return departments.value.find((d) => d.id === id)?.name ?? ''
}

// ---- 新增/编辑表单 ----
const showForm = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formSaving = ref(false)
const editingId = ref<number | null>(null)
const formModel = reactive({
  name: '',
  code: '',
  category: 'EXAMINATION' as DeviceCategory,
  departmentId: null as number | null,
  location: '',
  manufacturer: '',
  model: '',
  serialNumber: '',
  notes: '',
  purchaseDate: '',
  warrantyUntil: '',
})

// ---- 状态变更 ----
const showStatus = ref(false)
const statusSaving = ref(false)
const statusDevice = ref<DeviceResponse | null>(null)
const statusModel = reactive({
  status: 'AVAILABLE' as DeviceStatus,
  reason: '',
})

// ---- 状态历史 ----
const showHistory = ref(false)
const historyLoading = ref(false)
const historyDevice = ref<DeviceResponse | null>(null)
const historyList = ref<DeviceStatusHistory[]>([])

// ---- 元信息映射 ----
// 用 string 索引以兼容需求中提到的 ABNORMAL 色板（当前 DeviceStatus 不含 ABNORMAL）
const statusBadge: Record<string, { label: string; cls: string }> = {
  AVAILABLE: { label: '可用', cls: 'st-available' },
  IN_USE: { label: '使用中', cls: 'st-in-use' },
  MAINTENANCE: { label: '维护中', cls: 'st-maintenance' },
  DISABLED: { label: '已停用', cls: 'st-disabled' },
  ABNORMAL: { label: '异常', cls: 'st-abnormal' },
}

const categoryOptions: { value: DeviceCategory; label: string }[] = [
  { value: 'EXAMINATION', label: '检查设备' },
  { value: 'LABORATORY', label: '检验设备' },
  { value: 'MONITOR', label: '监护设备' },
  { value: 'OTHER', label: '其他' },
]

const statusOptions: { value: DeviceResponse['status']; label: string }[] = [
  { value: 'AVAILABLE', label: '空闲' },
  { value: 'IN_USE', label: '使用中' },
  { value: 'ABNORMAL', label: '异常' },
  { value: 'DISABLED', label: '停用' },
]

function categoryLabel(c: DeviceCategory): string {
  return categoryOptions.find((o) => o.value === c)?.label ?? c
}

function statusLabel(s: string): string {
  return statusBadge[s]?.label ?? s
}

function statusClass(s: string): string {
  return statusBadge[s]?.cls ?? ''
}

function formatDateTime(iso: string): string {
  if (!iso) return '--'
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return iso
  }
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '--'
  return iso.slice(0, 10)
}

// ---- 加载列表 ----
async function loadDevices() {
  loading.value = true
  loadError.value = ''
  try {
    const [list, depts] = await Promise.all([getAdminDevices(), getDepartments()])
    devices.value = list
    departments.value = depts
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载设备列表失败'
    console.error('[AdminDevices] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

// ---- 表单 ----
function resetForm() {
  formModel.name = ''
  formModel.code = ''
  formModel.category = 'EXAMINATION'
  formModel.departmentId = null
  formModel.location = ''
  formModel.manufacturer = ''
  formModel.model = ''
  formModel.serialNumber = ''
  formModel.notes = ''
  formModel.purchaseDate = ''
  formModel.warrantyUntil = ''
  editingId.value = null
}

function openCreate() {
  formMode.value = 'create'
  resetForm()
  showForm.value = true
}

function openEdit(device: DeviceResponse) {
  formMode.value = 'edit'
  editingId.value = device.id
  formModel.name = device.name
  formModel.code = device.code // 编辑时只读展示
  formModel.category = device.category
  formModel.departmentId = device.departmentId
  formModel.location = device.location
  formModel.manufacturer = device.manufacturer
  formModel.model = device.model
  formModel.serialNumber = device.serialNumber
  formModel.notes = device.notes
  formModel.purchaseDate = device.purchaseDate ?? ''
  formModel.warrantyUntil = device.warrantyUntil ?? ''
  showForm.value = true
}

function closeForm() {
  showForm.value = false
}

async function submitForm() {
  const name = formModel.name.trim()
  const code = formModel.code.trim()
  if (!name) {
    ElMessage.error('请输入设备名称')
    return
  }
  if (formMode.value === 'create' && !code) {
    ElMessage.error('请输入设备编码')
    return
  }
  const payload: Partial<DeviceResponse> = {
    name,
    category: formModel.category,
    departmentId: formModel.departmentId,
    location: formModel.location.trim(),
    manufacturer: formModel.manufacturer.trim(),
    model: formModel.model.trim(),
    serialNumber: formModel.serialNumber.trim(),
    notes: formModel.notes.trim(),
    purchaseDate: formModel.purchaseDate || null,
    warrantyUntil: formModel.warrantyUntil || null,
  }
  if (formMode.value === 'create') {
    payload.code = code
  }
  formSaving.value = true
  try {
    if (formMode.value === 'create') {
      await createDevice(payload)
      ElMessage.success('设备已创建')
    } else if (editingId.value !== null) {
      await updateDevice(editingId.value, payload)
      ElMessage.success('设备已更新')
    }
    showForm.value = false
    await loadDevices()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存设备失败')
    console.error('[AdminDevices] 保存失败：', e)
  } finally {
    formSaving.value = false
  }
}

// ---- 状态变更 ----
function openStatusChange(device: DeviceResponse) {
  statusDevice.value = device
  statusModel.status = device.status
  statusModel.reason = ''
  showStatus.value = true
}

function closeStatus() {
  showStatus.value = false
  statusDevice.value = null
}

async function submitStatus() {
  if (!statusDevice.value) return
  const reason = statusModel.reason.trim()
  if (!reason) {
    ElMessage.error('请填写状态变更原因（§13.6）')
    return
  }
  const device = statusDevice.value
  const target = statusModel.status
  try {
    await ElMessageBox.confirm(
      `确认将设备「${device.name}」状态变更为「${statusLabel(target)}」？`,
      '状态变更确认',
      { confirmButtonText: '确认变更', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // 用户取消
  }
  statusSaving.value = true
  try {
    await setDeviceStatus(device.id, target, reason)
    ElMessage.success('设备状态已变更')
    showStatus.value = false
    await loadDevices()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '状态变更失败')
    console.error('[AdminDevices] 状态变更失败：', e)
  } finally {
    statusSaving.value = false
  }
}

// ---- 状态历史 ----
async function openHistory(device: DeviceResponse) {
  historyDevice.value = device
  showHistory.value = true
  historyLoading.value = true
  historyList.value = []
  try {
    historyList.value = await getDeviceStatusHistory(device.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载状态历史失败')
    console.error('[AdminDevices] 状态历史加载失败：', e)
  } finally {
    historyLoading.value = false
  }
}

function handleHistoryClose(done: () => void) {
  showHistory.value = false
  historyDevice.value = null
  historyList.value = []
  done()
}

onMounted(loadDevices)
</script>

<template>
  <div class="devices-view">
    <!-- 页头 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">设备管理</h1>
        <div class="header-sub">维护医疗设备档案、运行状态与变更轨迹（§13）</div>
      </div>
      <button class="primary-btn" @click="openCreate">新增设备</button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="loading-spinner" />
      <span class="state-text">正在加载设备列表…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error-card">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadDevices">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="devices.length === 0" class="state-card">
      <div class="state-title">暂无设备</div>
      <div class="state-desc">点击右上角「新增设备」开始建档</div>
    </div>

    <!-- 设备卡片列表 -->
    <div v-else class="device-grid">
      <div v-for="device in devices" :key="device.id" class="device-card">
        <div class="card-head">
          <div class="card-title-row">
            <span class="device-name">{{ device.name }}</span>
            <span class="status-badge" :class="statusClass(device.status)">
              {{ statusLabel(device.status) }}
            </span>
          </div>
          <div class="device-code">{{ device.code }}</div>
        </div>

        <div class="card-body">
          <div class="meta-row">
            <span class="meta-label">分类</span>
            <span class="meta-value">{{ categoryLabel(device.category) }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">所属科室</span>
            <span class="meta-value">{{ departmentNameById(device.departmentId) || '--' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">位置</span>
            <span class="meta-value">{{ device.location || '--' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">厂商/型号</span>
            <span class="meta-value">
              {{ [device.manufacturer, device.model].filter(Boolean).join(' / ') || '--' }}
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-label">序列号</span>
            <span class="meta-value">{{ device.serialNumber || '--' }}</span>
          </div>
          <div class="meta-row">
            <span class="meta-label">采购/保修至</span>
            <span class="meta-value">
              {{ formatDate(device.purchaseDate) }} / {{ formatDate(device.warrantyUntil) }}
            </span>
          </div>
          <div v-if="device.notes" class="meta-row">
            <span class="meta-label">备注</span>
            <span class="meta-value">{{ device.notes }}</span>
          </div>
        </div>

        <div class="card-actions">
          <button class="ghost-btn" @click="openEdit(device)">编辑</button>
          <button class="ghost-btn" @click="openHistory(device)">状态历史</button>
          <button class="primary-btn small" @click="openStatusChange(device)">状态变更</button>
        </div>
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="showForm"
      :title="formMode === 'create' ? '新增设备' : '编辑设备'"
      width="560px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form label-width="92px" label-position="right">
        <el-form-item label="设备名称" required>
          <el-input v-model="formModel.name" placeholder="请输入设备名称" maxlength="128" />
        </el-form-item>
        <el-form-item label="设备编码" required>
          <el-input
            v-model="formModel.code"
            placeholder="请输入设备编码"
            maxlength="32"
            :disabled="formMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="设备分类" required>
          <el-select v-model="formModel.category" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="opt in categoryOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属科室">
          <el-select
            v-model="formModel.departmentId"
            placeholder="选择所属科室（可不选）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="dept in departments"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备位置">
          <el-input v-model="formModel.location" placeholder="请输入设备所在位置" maxlength="128" />
        </el-form-item>
        <el-form-item label="厂商">
          <el-input v-model="formModel.manufacturer" placeholder="如 GE、飞利浦" maxlength="128" />
        </el-form-item>
        <el-form-item label="型号">
          <el-input v-model="formModel.model" placeholder="设备型号" maxlength="64" />
        </el-form-item>
        <el-form-item label="序列号">
          <el-input v-model="formModel.serialNumber" placeholder="设备序列号" maxlength="64" />
        </el-form-item>
        <el-form-item label="采购日期">
          <el-date-picker
            v-model="formModel.purchaseDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择采购日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="保修至">
          <el-date-picker
            v-model="formModel.warrantyUntil"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择保修截止日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="formModel.notes"
            type="textarea"
            :rows="2"
            placeholder="其他备注信息"
            maxlength="512"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-actions">
          <button class="ghost-btn" @click="closeForm">取消</button>
          <button class="primary-btn" :disabled="formSaving" @click="submitForm">
            {{ formSaving ? '保存中…' : '保存' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 状态变更弹窗 -->
    <el-dialog
      v-model="showStatus"
      title="设备状态变更"
      width="480px"
      :close-on-click-modal="false"
      append-to-body
    >
      <div v-if="statusDevice" class="status-target">
        <span class="meta-label">当前设备</span>
        <span class="status-device-name">{{ statusDevice.name }}</span>
        <span class="status-badge" :class="statusClass(statusDevice.status)">
          {{ statusLabel(statusDevice.status) }}
        </span>
      </div>
      <el-form label-width="88px" label-position="right" style="margin-top: 16px">
        <el-form-item label="新状态" required>
          <el-select v-model="statusModel.status" style="width: 100%">
            <el-option
              v-for="opt in statusOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因" required>
          <el-input
            v-model="statusModel.reason"
            type="textarea"
            :rows="3"
            placeholder="状态变更必须记录原因（§13.6）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-actions">
          <button class="ghost-btn" @click="closeStatus">取消</button>
          <button class="primary-btn" :disabled="statusSaving" @click="submitStatus">
            {{ statusSaving ? '提交中…' : '确认变更' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 状态历史抽屉 -->
    <el-drawer
      v-model="showHistory"
      :title="`状态历史 - ${historyDevice?.name ?? ''}`"
      direction="rtl"
      size="420px"
      append-to-body
      :before-close="handleHistoryClose"
    >
      <div v-loading="historyLoading" class="history-wrap">
        <div v-if="historyList.length === 0 && !historyLoading" class="history-empty">
          暂无状态变更记录
        </div>
        <el-timeline v-else>
          <el-timeline-item
            v-for="h in historyList"
            :key="h.id"
            :timestamp="formatDateTime(h.changedAt)"
            placement="top"
            :hollow="h.fromStatus === null"
          >
            <div class="history-card">
              <div class="history-flow">
                <span class="status-badge small" :class="statusClass(h.fromStatus ?? '')">
                  {{ h.fromStatus ? statusLabel(h.fromStatus) : '建档' }}
                </span>
                <span class="flow-arrow">→</span>
                <span class="status-badge small" :class="statusClass(h.toStatus)">
                  {{ statusLabel(h.toStatus) }}
                </span>
              </div>
              <div class="history-meta">
                <span class="meta-label">操作人</span>
                <span class="meta-value">{{ h.operatorName || '--' }}</span>
              </div>
              <div v-if="h.reason" class="history-reason">{{ h.reason }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.devices-view {
  max-width: 1200px;
  margin: 0 auto;
}

/* 页头 */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  gap: 12px;
}

.page-title {
  margin: 0;
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
}

.header-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #8e8e93;
}

/* 按钮 */
.primary-btn {
  padding: 8px 18px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
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

.primary-btn.small {
  padding: 6px 12px;
  font-size: 13px;
}

.ghost-btn {
  padding: 8px 16px;
  background: #ffffff;
  color: #4a5568;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
  white-space: nowrap;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #4facfe;
}

.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 状态卡片（加载/错误/空） */
.state-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 40px 20px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.state-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.error-card .state-title {
  color: #f56c6c;
}

.state-desc {
  font-size: 13px;
  color: #8e8e93;
}

.state-text {
  font-size: 14px;
  color: #8e8e93;
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

/* 设备卡片网格 */
.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.device-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
}

.card-head {
  margin-bottom: 12px;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.device-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-code {
  margin-top: 4px;
  font-size: 12px;
  color: #8e8e93;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.meta-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.meta-label {
  font-size: 12px;
  color: #8e8e93;
  flex-shrink: 0;
  min-width: 56px;
}

.meta-value {
  font-size: 13px;
  color: #1a1a1a;
  line-height: 1.5;
}

.items-row {
  align-items: flex-start;
}

.items-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}

.item-tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #f0f7ff;
  color: #1890ff;
}

.card-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.card-actions .ghost-btn,
.card-actions .primary-btn {
  flex: 1;
  padding: 6px 10px;
  font-size: 13px;
}

/* 状态徽章 */
.status-badge {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
  line-height: 1.6;
  background: #f5f5f5;
  color: #8e8e93;
  white-space: nowrap;
}

.status-badge.small {
  padding: 1px 8px;
  font-size: 12px;
}

.st-available {
  background: #f6ffed;
  color: #389e0d;
}

.st-in-use {
  background: #e6f7ff;
  color: #1890ff;
}

.st-maintenance {
  background: #fff7e6;
  color: #d48806;
}

.st-disabled {
  background: #f5f5f5;
  color: #8e8e93;
}

.st-abnormal {
  background: #fff1f0;
  color: #cf1322;
}

/* 弹窗 */
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.status-target {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.status-device-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

/* 状态历史抽屉 */
.history-wrap {
  padding: 0 4px;
}

.history-empty {
  text-align: center;
  padding: 40px 12px;
  font-size: 13px;
  color: #8e8e93;
}

.history-card {
  background: #f8fafc;
  border-radius: 10px;
  padding: 10px 12px;
}

.history-flow {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.flow-arrow {
  color: #8e8e93;
  font-size: 13px;
}

.history-meta {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
}

.history-reason {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
