<script setup lang="ts">
// 挂号管理（§7）
// 设计来源：product/11_功能需求.md §7
// 功能：管理员查看全部挂号记录，支持状态/日期/关键字筛选，只读
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminAppointments } from '@/api/admin'
import type { AppointmentResponse, AppointmentStatus } from '@/types/appointment'

const loading = ref(true)
const loadError = ref('')
const allAppointments = ref<AppointmentResponse[]>([])

// 筛选条件
const filter = reactive({
  status: 'ALL' as 'ALL' | AppointmentStatus,
  date: '' as string,
  keyword: '' as string,
})

type StatusFilter = 'ALL' | AppointmentStatus

const statusOptions: Array<{ value: StatusFilter; label: string }> = [
  { value: 'ALL', label: '全部状态' },
  { value: 'BOOKED', label: '待就诊' },
  { value: 'CHECKED_IN', label: '已签到' },
  { value: 'IN_PROGRESS', label: '就诊中' },
  { value: 'WAITING_EXAM', label: '等待检查' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'NO_SHOW', label: '爽约' },
]

const statusMeta: Record<
  AppointmentStatus,
  { label: string; bg: string; color: string }
> = {
  BOOKED: { label: '待就诊', bg: '#e3f0ff', color: '#1a73e8' },
  CHECKED_IN: { label: '已签到', bg: '#fff7e6', color: '#d48806' },
  IN_PROGRESS: { label: '就诊中', bg: '#fff7e6', color: '#d48806' },
  WAITING_EXAM: { label: '等待检查', bg: '#f0f5ff', color: '#2f54eb' },
  COMPLETED: { label: '已完成', bg: '#f6ffed', color: '#389e0d' },
  CANCELLED: { label: '已取消', bg: '#f5f5f5', color: '#8e8e93' },
  NO_SHOW: { label: '爽约', bg: '#fff1f0', color: '#cf1322' },
}

// 汇总统计（基于全量数据，不受筛选影响）
const stats = computed(() => {
  const total = allAppointments.value.length
  let today = 0
  let completed = 0
  let cancelled = 0
  for (const a of allAppointments.value) {
    if (isSameDay(a.bookedAt)) today++
    if (a.status === 'COMPLETED') completed++
    if (a.status === 'CANCELLED') cancelled++
  }
  return { total, today, completed, cancelled }
})

// 筛选后的列表
const filteredAppointments = computed(() => {
  let list = allAppointments.value
  if (filter.status !== 'ALL') {
    list = list.filter((a) => a.status === filter.status)
  }
  if (filter.date) {
    list = list.filter((a) => (a.bookedAt || '').slice(0, 10) === filter.date)
  }
  const kw = filter.keyword.trim().toLowerCase()
  if (kw) {
    list = list.filter((a) => {
      return (
        (a.patientName || '').toLowerCase().includes(kw) ||
        (a.doctorName || '').toLowerCase().includes(kw) ||
        (a.appointmentNumber || '').toLowerCase().includes(kw) ||
        (a.departmentName || '').toLowerCase().includes(kw)
      )
    })
  }
  return [...list].sort(
    (a, b) => new Date(b.bookedAt).getTime() - new Date(a.bookedAt).getTime(),
  )
})

function isSameDay(iso: string): boolean {
  if (!iso) return false
  try {
    const d = new Date(iso)
    const t = new Date()
    return (
      d.getFullYear() === t.getFullYear() &&
      d.getMonth() === t.getMonth() &&
      d.getDate() === t.getDate()
    )
  } catch {
    return false
  }
}

function formatDateTime(iso: string): string {
  if (!iso) return '--'
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return '--'
  }
}

async function loadAppointments() {
  loading.value = true
  loadError.value = ''
  try {
    allAppointments.value = await getAdminAppointments()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载挂号列表失败'
    console.error('[AdminAppointments] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.status = 'ALL'
  filter.date = ''
  filter.keyword = ''
}

onMounted(loadAppointments)
</script>

<template>
  <div class="admin-appointments">
    <div class="page-header">
      <div class="page-title">挂号管理</div>
      <div class="page-sub">查看全部挂号记录，支持按状态、日期与关键字筛选</div>
    </div>

    <!-- 汇总统计 -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">挂号总数</div>
        <div class="stat-value">{{ stats.total }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日挂号</div>
        <div class="stat-value">{{ stats.today }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已完成</div>
        <div class="stat-value success">{{ stats.completed }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已取消</div>
        <div class="stat-value muted">{{ stats.cancelled }}</div>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-card">
      <div class="filter-item">
        <label class="filter-label">状态</label>
        <select v-model="filter.status" class="filter-select">
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">日期</label>
        <input v-model="filter.date" type="date" class="filter-input" />
      </div>
      <div class="filter-item grow">
        <label class="filter-label">关键字</label>
        <input
          v-model="filter.keyword"
          type="text"
          class="filter-input"
          placeholder="患者姓名 / 医生 / 挂号编号 / 科室"
        />
      </div>
      <div class="filter-actions">
        <button class="ghost-btn" @click="resetFilter">重置</button>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="spinner" />
      <span class="state-text">正在加载挂号列表…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadAppointments">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="filteredAppointments.length === 0" class="state-card">
      <div class="state-title">暂无挂号记录</div>
      <div class="state-desc">
        {{
          allAppointments.length === 0
            ? '当前没有任何挂号数据'
            : '没有符合筛选条件的挂号，请调整筛选'
        }}
      </div>
    </div>

    <!-- 挂号表格 -->
    <div v-else class="table-card">
      <div class="table-meta">
        共 <strong>{{ filteredAppointments.length }}</strong> 条记录
      </div>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>挂号编号</th>
              <th>患者</th>
              <th>医生</th>
              <th>科室</th>
              <th>挂号时间</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in filteredAppointments" :key="row.id">
              <td class="mono">{{ row.appointmentNumber }}</td>
              <td>{{ row.patientName }}</td>
              <td>{{ row.doctorName }}</td>
              <td>{{ row.departmentName }}</td>
              <td>{{ formatDateTime(row.bookedAt) }}</td>
              <td>
                <span
                  class="status-tag"
                  :style="{
                    background: statusMeta[row.status].bg,
                    color: statusMeta[row.status].color,
                  }"
                >
                  {{ statusMeta[row.status].label }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-appointments {
  padding: 20px 24px 32px;
  max-width: 1100px;
  margin: 0 auto;
}

/* ============ 页头 ============ */
.page-header {
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

/* ============ 汇总统计 ============ */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 16px 18px;
}

.stat-label {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1a1a1a;
  line-height: 1.1;
}

.stat-value.success {
  color: #389e0d;
}

.stat-value.muted {
  color: #8e8e93;
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

.filter-item.grow {
  flex: 1;
  min-width: 200px;
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

.filter-item.grow .filter-input {
  min-width: 200px;
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

.table-meta {
  font-size: 12px;
  color: #8e8e93;
  padding: 12px 18px 0;
}

.table-meta strong {
  color: #1a1a1a;
  font-weight: 600;
}

.table-scroll {
  overflow-x: auto;
  padding-top: 6px;
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

.mono {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12px;
  color: #475569;
}

/* ============ 状态标签 ============ */
.status-tag {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 10px;
  line-height: 1.6;
  white-space: nowrap;
}

/* ============ 响应式 ============ */
@media (max-width: 720px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
