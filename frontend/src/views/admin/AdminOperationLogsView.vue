<script setup lang="ts">
// 操作日志（§16.3）
// 设计来源：product/11_功能需求.md §16
// 只读列表页：展示管理员/医生的关键操作记录，支持按动作类型与关键字筛选
import { ref, computed, onMounted } from 'vue'
import { getOperationLogs } from '@/api/admin'
import type { OperationLog } from '@/types/admin'

const loading = ref(true)
const loadError = ref('')
const logs = ref<OperationLog[]>([])

// 预定义动作类型筛选项；不在列表中的动作归入「其他」
const KNOWN_ACTIONS = ['排班取消', '处方作废', '病历确认', '账号停用', '设备维修'] as const
type ActionFilter = 'ALL' | (typeof KNOWN_ACTIONS)[number] | 'OTHER'
const actionFilter = ref<ActionFilter>('ALL')
const keyword = ref('')

// F-HW-10：把后端审计的英文/技术枚举翻成业务可读的中文。
// 不在表中的 targetType 会被回退到原始值，避免误翻译。
const targetTypeLabels: Record<string, string> = {
  USER: '账号',
  DOCTOR: '医生',
  PATIENT: '患者',
  DEPARTMENT: '科室',
  SCHEDULE: '排班',
  APPOINTMENT: '挂号',
  PRESCRIPTION: '处方',
  MEDICAL_RECORD: '病历',
  ENCOUNTER: '就诊',
  TRIAGE: '分诊',
  EXAMINATION: '检查',
  EXAM: '检查',
  DEVICE: '设备',
  DRUG: '药品',
  ROLE: '角色',
  PERMISSION: '权限',
}

function targetTypeText(type: string): string {
  if (!type) return '--'
  return targetTypeLabels[type] ?? type
}

// 同样对原始 action 字符串做兜底翻译（后端可能下放 CREATE_PRESCRIPTION 等英文枚举）
const actionLabels: Record<string, string> = {
  CREATE_PRESCRIPTION: '开立处方',
  CANCEL_PRESCRIPTION: '处方作废',
  CONFIRM_MEDICAL_RECORD: '病历确认',
  CONFIRM_PRESCRIPTION: '处方确认',
  CANCEL_SCHEDULE: '排班取消',
  DISABLE_USER: '账号停用',
  ENABLE_USER: '账号启用',
  RESET_PASSWORD: '重置密码',
  DEVICE_MAINTENANCE: '设备维修',
  SCHEDULE_UPDATE: '排班调整',
  SCHEDULE_CREATE: '排班创建',
}

function actionText(action: string): string {
  if (!action) return '--'
  return actionLabels[action] ?? action
}

function targetNameText(log: OperationLog): string {
  const name = log.targetName?.trim()
  if (name) return name
  if (log.targetId === null || log.targetId === undefined) return '--'
  return `#${log.targetId}`
}

function operatorNameText(name: string): string {
  if (!name || !name.trim()) return '系统'
  if (name.trim().toUpperCase() === 'SYSTEM') return '系统'
  return name
}

// 动作徽章样式映射
const actionBadgeMap: Record<string, string> = {
  排班取消: 'badge-warn',
  处方作废: 'badge-danger',
  病历确认: 'badge-info',
  账号停用: 'badge-neutral',
  设备维修: 'badge-warn',
}

function actionBadgeClass(action: string): string {
  return actionBadgeMap[action] ?? 'badge-default'
}

function formatDateTime(iso: string): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return iso
  }
}

const filteredLogs = computed(() => {
  let list = logs.value
  if (actionFilter.value !== 'ALL') {
    if (actionFilter.value === 'OTHER') {
      list = list.filter(
        (l) =>
          !KNOWN_ACTIONS.includes(l.action as (typeof KNOWN_ACTIONS)[number]) &&
          !Object.keys(actionLabels).includes(l.action),
      )
    } else {
      list = list.filter((l) => l.action === actionFilter.value)
    }
  }
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(
      (l) =>
        l.operatorName.toLowerCase().includes(kw) ||
        l.action.toLowerCase().includes(kw) ||
        targetTypeText(l.targetType).toLowerCase().includes(kw) ||
        l.targetType.toLowerCase().includes(kw) ||
        targetNameText(l).toLowerCase().includes(kw) ||
        l.detail.toLowerCase().includes(kw),
    )
  }
  return [...list].sort(
    (a, b) => new Date(b.operatedAt).getTime() - new Date(a.operatedAt).getTime(),
  )
})

async function loadLogs() {
  loading.value = true
  loadError.value = ''
  try {
    logs.value = await getOperationLogs()
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载操作日志失败'
    console.error('[AdminOperationLogs] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  actionFilter.value = 'ALL'
  keyword.value = ''
}

onMounted(loadLogs)
</script>

<template>
  <div class="logs-view">
    <div class="page-header">
      <div class="header-title">操作日志</div>
      <div class="header-sub">记录排班、处方、病历、账号、设备等关键操作，用于操作追溯与合规审计</div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-card">
      <div class="filter-item">
        <label class="filter-label">动作类型</label>
        <select v-model="actionFilter" class="filter-select">
          <option value="ALL">全部</option>
          <option value="排班取消">排班取消</option>
          <option value="处方作废">处方作废</option>
          <option value="病历确认">病历确认</option>
          <option value="账号停用">账号停用</option>
          <option value="设备维修">设备维修</option>
          <option value="OTHER">其他</option>
        </select>
      </div>
      <div class="filter-item filter-item-grow">
        <label class="filter-label">关键字搜索</label>
        <input
          v-model="keyword"
          type="text"
          class="filter-input"
          placeholder="操作人 / 动作 / 目标 / 详情"
        />
      </div>
      <button class="ghost-btn" @click="resetFilter">重置</button>
      <button class="primary-btn" @click="loadLogs">刷新</button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="loading-spinner" />
      <span class="state-text">正在加载操作日志…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error-card">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadLogs">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="filteredLogs.length === 0" class="state-card">
      <div class="state-title">暂无记录</div>
      <div class="state-desc">没有符合筛选条件的操作日志</div>
    </div>

    <!-- 日志表格 -->
    <div v-else class="table-card">
      <div class="table-meta">共 {{ filteredLogs.length }} 条记录</div>
      <div class="table-scroll">
        <table class="log-table">
          <thead>
            <tr>
              <th>操作时间</th>
              <th>操作人</th>
              <th>动作</th>
              <th>目标</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in filteredLogs" :key="log.id">
              <td class="cell-time">{{ formatDateTime(log.operatedAt) }}</td>
              <td class="cell-operator">{{ operatorNameText(log.operatorName) }}</td>
              <td>
                <span class="badge" :class="actionBadgeClass(actionText(log.action))">
                  {{ actionText(log.action) }}
                </span>
              </td>
              <td class="cell-target">
                <div class="target-main">{{ targetTypeText(log.targetType) }}</div>
                <div v-if="targetNameText(log) !== '--'" class="target-sub">
                  {{ targetNameText(log) }}
                </div>
              </td>
              <td class="cell-detail">{{ log.detail }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.logs-view {
  padding: 16px 16px 24px;
  max-width: 1080px;
  margin: 0 auto;
}

.page-header {
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
  line-height: 1.5;
}

/* ============ 筛选栏 ============ */
.filter-card {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-item-grow {
  flex: 1;
  min-width: 200px;
}

.filter-label {
  font-size: 12px;
  font-weight: 500;
  color: #475569;
}

.filter-select,
.filter-input {
  height: 36px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
}

.filter-select {
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 30px;
  min-width: 130px;
}

.filter-select:focus,
.filter-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

.filter-input {
  width: 100%;
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
  height: 36px;
}

.primary-btn:hover {
  opacity: 0.92;
}

.ghost-btn {
  padding: 8px 16px;
  background: #ffffff;
  color: #475569;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  height: 36px;
}

.ghost-btn:hover {
  border-color: #4facfe;
  color: #1a73e8;
}

/* ============ 状态卡 ============ */
.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 40px 20px;
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  text-align: center;
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

.state-text {
  font-size: 14px;
  color: #8e8e93;
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
  line-height: 1.5;
  margin-bottom: 4px;
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
  padding: 12px 16px 0;
}

.table-scroll {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.log-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  min-width: 720px;
}

.log-table thead th {
  text-align: left;
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #8e8e93;
  background: #fafbfc;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.log-table tbody td {
  padding: 12px 16px;
  color: #1a1a1a;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.log-table tbody tr:last-child td {
  border-bottom: none;
}

.log-table tbody tr:hover {
  background: #fafbfc;
}

.cell-time {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: #475569;
}

.cell-operator {
  font-weight: 500;
  white-space: nowrap;
}

.cell-target-type {
  color: #475569;
  white-space: nowrap;
}

.cell-target {
  color: #475569;
  white-space: nowrap;
}

.target-main {
  font-weight: 500;
  color: #1a1a1a;
}

.target-sub {
  font-size: 12px;
  color: #8e8e93;
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}

.cell-target-id {
  color: #475569;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.cell-detail {
  max-width: 320px;
  color: #475569;
  line-height: 1.5;
}

/* ============ 动作徽章 ============ */
.badge {
  display: inline-block;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 10px;
  white-space: nowrap;
}

.badge-warn {
  background: #fff7e6;
  color: #d48806;
}

.badge-danger {
  background: #fff1f0;
  color: #cf1322;
}

.badge-info {
  background: #e3f0ff;
  color: #1a73e8;
}

.badge-neutral {
  background: #f5f5f5;
  color: #8e8e93;
}

.badge-default {
  background: #f0f5ff;
  color: #2f54eb;
}
</style>
