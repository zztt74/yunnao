<script setup lang="ts">
// 患者端检查检验（§10）
// 设计来源：product/11_功能需求.md §10、roles/12_前端开发AI任务书.md
// 修复 UF-02：不再只展示已审核结果，按状态展示完整流程：
//   ORDERED/IN_PROGRESS/RESULT_ENTERED/REVIEWED/CANCELLED
//   每个状态显示明确的下一步提示
// 字段范围与权限仍由后端 DTO 与 controller 控制；前端不做权限/字段越权推断
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getExaminationById, getMyExaminations } from '@/api/examination'
import type {
  ExaminationResponse,
  ExaminationStatus,
  ExaminationType,
} from '@/types/examination'

const loading = ref(false)
const list = ref<ExaminationResponse[]>([])
const showDetail = ref(false)
const detailRecord = ref<ExaminationResponse | null>(null)
const typeFilter = ref<'ALL' | ExaminationType>('ALL')
const statusFilter = ref<'ALL' | ExaminationStatus>('ALL')

/** 日期范围筛选（§14.3） */
const dateFrom = ref<string>('') // yyyy-MM-dd
const dateTo = ref<string>('')

const dateFromPickerOptions = computed(() => ({
  disabledDate: (d: Date) => dateTo.value && d > dayjs(dateTo.value).toDate(),
}))
const dateToPickerOptions = computed(() => ({
  disabledDate: (d: Date) => dateFrom.value && d < dayjs(dateFrom.value).toDate(),
}))

const dateFilterActive = computed(
  () => Boolean(dateFrom.value || dateTo.value),
)

// ---- 状态元信息（标签、徽章颜色、下一步提示） ----
interface StatusMeta {
  label: string
  badgeClass: string
  nextStep: string
}

const statusMetaMap: Record<ExaminationStatus, StatusMeta> = {
  ORDERED: {
    label: '待检查',
    badgeClass: 'status-ordered',
    nextStep: '医生已为您开立此检查，请按照医嘱前往指定地点完成检查。',
  },
  IN_PROGRESS: {
    label: '检查中',
    badgeClass: 'status-in-progress',
    nextStep: '检查正在进行中，请耐心等候。完成检查后，结果将由相关人员录入系统。',
  },
  RESULT_ENTERED: {
    label: '待审核',
    badgeClass: 'status-result-entered',
    nextStep: '检查结果已录入，正在等待医生审核。审核完成后您可在此查看完整报告。',
  },
  REVIEWED: {
    label: '已审核',
    badgeClass: 'status-reviewed',
    nextStep: '报告已由医生审核，可点击查看完整结果与 AI 辅助解读。',
  },
  CANCELLED: {
    label: '已取消',
    badgeClass: 'status-cancelled',
    nextStep: '该检查已取消。如有疑问，请联系您的接诊医生。',
  },
}

const statusFilterOptions: Array<{
  value: 'ALL' | ExaminationStatus
  label: string
}> = [
  { value: 'ALL', label: '全部状态' },
  { value: 'ORDERED', label: '待检查' },
  { value: 'IN_PROGRESS', label: '检查中' },
  { value: 'RESULT_ENTERED', label: '待审核' },
  { value: 'REVIEWED', label: '已审核' },
  { value: 'CANCELLED', label: '已取消' },
]

function statusMeta(s: ExaminationStatus): StatusMeta {
  return statusMetaMap[s] || {
    label: s,
    badgeClass: '',
    nextStep: '',
  }
}

async function loadList() {
  loading.value = true
  try {
    list.value = await getMyExaminations({
      fromDate: dateFrom.value || undefined,
      toDate: dateTo.value || undefined,
    })
  } catch (e) {
    console.error('加载检查检验失败：', e)
    ElMessage.error('加载检查检验失败')
  } finally {
    loading.value = false
  }
}

function clearDateFilter() {
  dateFrom.value = ''
  dateTo.value = ''
}

const filteredList = computed(() => {
  let result = list.value
  if (typeFilter.value !== 'ALL') {
    result = result.filter((i) => i.type === typeFilter.value)
  }
  if (statusFilter.value !== 'ALL') {
    result = result.filter((i) => i.status === statusFilter.value)
  }
  return result
})

const typeMeta: Record<ExaminationType, { label: string; icon: string; color: string }> = {
  EXAMINATION: { label: '检查', icon: '🩻', color: '#1a73e8' },
  LABORATORY: { label: '检验', icon: '🧪', color: '#67c23a' },
}

async function viewDetail(r: ExaminationResponse) {
  detailRecord.value = r
  showDetail.value = true
  if (r.status !== 'REVIEWED') return
  try {
    const detail = await getExaminationById(r.id)
    detailRecord.value = {
      ...detail,
      doctorName: r.doctorName || detail.doctorName,
      departmentName: r.departmentName || detail.departmentName,
      departmentLocation: r.departmentLocation,
      nextAction: r.nextAction,
      deviceName: r.deviceName,
      deviceLocation: r.deviceLocation,
    }
  } catch (e) {
    console.error('加载检查报告详情失败：', e)
    ElMessage.warning('报告详情暂时无法加载，请稍后重试')
  }
}

function closeDetail() {
  showDetail.value = false
  setTimeout(() => {
    detailRecord.value = null
  }, 250)
}

function abnormalLabel(flag: 'NORMAL' | 'HIGH' | 'LOW'): string {
  if (flag === 'HIGH') return '↑ 高'
  if (flag === 'LOW') return '↓ 低'
  return '正常'
}

const abnormalStats = computed(() => {
  if (!detailRecord.value?.labItems) return null
  const items = detailRecord.value.labItems
  return {
    total: items.length,
    abnormal: items.filter((i) => i.abnormalFlag !== 'NORMAL').length,
  }
})

// 详情页：根据状态决定显示"流程提示卡"还是"完整报告"
// 真实报告（labItems/findings/impression/aiInterpretation）仅在 REVIEWED 时展示
const detailIsReviewed = computed(
  () => detailRecord.value?.status === 'REVIEWED',
)
const detailStatusMeta = computed(() =>
  detailRecord.value ? statusMeta(detailRecord.value.status) : null,
)

onMounted(() => {
  loadList()
})

// 日期变化时重新加载
watch([dateFrom, dateTo], () => {
  loadList()
})
</script>

<template>
  <div v-loading="loading" class="page-wrapper">
    <Transition name="page-push" mode="out-in">
      <!-- ============ 列表 ============ -->
      <div v-if="!showDetail" key="list" class="pane pane-list">
        <div class="filter-row">
          <div
            class="filter-chip"
            :class="{ active: typeFilter === 'ALL' }"
            @click="typeFilter = 'ALL'"
          >
            全部
          </div>
          <div
            class="filter-chip"
            :class="{ active: typeFilter === 'EXAMINATION' }"
            @click="typeFilter = 'EXAMINATION'"
          >
            🩻 检查
          </div>
          <div
            class="filter-chip"
            :class="{ active: typeFilter === 'LABORATORY' }"
            @click="typeFilter = 'LABORATORY'"
          >
            🧪 检验
          </div>
        </div>

        <!-- 状态筛选（按检查流程追踪） -->
        <div class="status-row">
          <div
            v-for="opt in statusFilterOptions"
            :key="opt.value"
            class="status-chip"
            :class="['status-chip-' + opt.value.toLowerCase(), { active: statusFilter === opt.value }]"
            @click="statusFilter = opt.value"
          >
            {{ opt.label }}
          </div>
        </div>

        <!-- 日期范围筛选 -->
        <div class="date-row">
          <span class="date-label">日期</span>
          <input
            v-model="dateFrom"
            type="date"
            class="date-input"
            :max="dateTo || undefined"
            placeholder="开始"
          />
          <span class="date-sep">至</span>
          <input
            v-model="dateTo"
            type="date"
            class="date-input"
            :min="dateFrom || undefined"
            placeholder="结束"
          />
          <button
            v-if="dateFilterActive"
            class="date-clear-btn"
            type="button"
            @click="clearDateFilter"
          >
            清除
          </button>
        </div>

        <div v-if="filteredList.length === 0" class="empty-state">
          <div class="empty-icon">🔬</div>
          <div class="empty-text">暂无检查检验记录</div>
          <div class="empty-tip">医生开立后会在此显示完整流程与结果</div>
        </div>

        <div v-else class="exam-list">
          <div
            v-for="r in filteredList"
            :key="r.id"
            class="exam-card"
            :class="['exam-card-' + r.status.toLowerCase()]"
            @click="viewDetail(r)"
          >
            <div class="exam-card-left" :style="{ color: typeMeta[r.type].color }">
              <div class="exam-icon">{{ typeMeta[r.type].icon }}</div>
              <div class="exam-type">{{ typeMeta[r.type].label }}</div>
            </div>
            <div class="exam-card-body">
              <div class="exam-name">{{ r.itemName }}</div>
              <div class="exam-purpose">目的：{{ r.purpose || '常规检查' }}</div>
              <div v-if="r.nextAction" class="exam-next-action">{{ r.nextAction }}</div>
              <div class="exam-meta">
                <span class="meta-item">
                  <span class="meta-icon">👨‍⚕️</span>
                  {{ r.doctorName }}
                </span>
                <span class="meta-item">
                  <span class="meta-icon">📅</span>
                  {{ dayjs(r.orderedAt).format('YYYY-MM-DD HH:mm') }}
                </span>
              </div>
            </div>
            <div class="exam-card-right">
              <div class="status-badge" :class="statusMeta(r.status).badgeClass">
                {{ statusMeta(r.status).label }}
              </div>
              <div class="arrow">›</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 详情 ============ -->
      <div v-else key="detail" class="pane pane-detail">
        <div v-if="detailRecord" class="detail-page">
          <div class="detail-header">
            <button class="back-btn" @click="closeDetail">‹ 返回</button>
            <div class="detail-title">
              {{ detailRecord.itemName }}
            </div>
            <div class="detail-spacer"></div>
          </div>

          <div class="detail-content">
            <!-- 状态提示卡：让患者清楚知道当前在哪一步、下一步是什么 -->
            <div
              v-if="detailStatusMeta"
              class="detail-section status-step-card"
              :class="detailStatusMeta.badgeClass"
            >
              <div class="status-step-head">
                <span class="status-step-badge" :class="detailStatusMeta.badgeClass">
                  {{ detailStatusMeta.label }}
                </span>
                <span class="status-step-title">当前进度</span>
              </div>
              <div class="status-step-text">
                {{ detailRecord.nextAction || detailStatusMeta.nextStep }}
              </div>
            </div>

            <!-- 基本信息 -->
            <div class="detail-section">
              <div class="info-grid">
                <div class="info-row">
                  <span class="info-label">类型</span>
                  <span class="info-value">
                    {{ typeMeta[detailRecord.type].label }}
                  </span>
                </div>
                <div class="info-row">
                  <span class="info-label">就诊科室</span>
                  <span class="info-value">{{ detailRecord.departmentName || '--' }}</span>
                </div>
                <div v-if="detailRecord.departmentLocation" class="info-row">
                  <span class="info-label">检查地点</span>
                  <span class="info-value">{{ detailRecord.departmentLocation }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">申请医生</span>
                  <span class="info-value">{{ detailRecord.doctorName || '--' }}</span>
                </div>
                <div v-if="detailRecord.deviceName || detailRecord.deviceLocation" class="info-row">
                  <span class="info-label">相关设备</span>
                  <span class="info-value">
                    {{ [detailRecord.deviceName, detailRecord.deviceLocation].filter(Boolean).join(' / ') }}
                  </span>
                </div>
                <div class="info-row">
                  <span class="info-label">目的</span>
                  <span class="info-value">{{ detailRecord.purpose || '常规检查' }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">申请时间</span>
                  <span class="info-value">
                    {{ dayjs(detailRecord.orderedAt).format('YYYY-MM-DD HH:mm') }}
                  </span>
                </div>
                <div v-if="detailRecord.reporterName" class="info-row">
                  <span class="info-label">报告医生</span>
                  <span class="info-value">{{ detailRecord.reporterName }}</span>
                </div>
                <div v-if="detailRecord.reviewedAt" class="info-row">
                  <span class="info-label">审核时间</span>
                  <span class="info-value">
                    {{ dayjs(detailRecord.reviewedAt).format('YYYY-MM-DD HH:mm') }}
                  </span>
                </div>
                <div v-if="detailRecord.cancelReason" class="info-row">
                  <span class="info-label">取消原因</span>
                  <span class="info-value">{{ detailRecord.cancelReason }}</span>
                </div>
              </div>
            </div>

            <!-- 真实报告内容：仅在 REVIEWED 后展示，避免给患者看到未审核的草稿 -->
            <template v-if="detailIsReviewed">
              <!-- 检查所见 -->
              <div v-if="detailRecord.findings" class="detail-section">
                <div class="section-label">检查所见</div>
                <div class="section-text">{{ detailRecord.findings }}</div>
              </div>

              <!-- 检验指标 -->
              <div v-if="detailRecord.labItems && detailRecord.labItems.length" class="detail-section">
                <div class="section-header">
                  <div class="section-title">检验指标</div>
                  <div v-if="abnormalStats" class="abnormal-stat">
                    <span class="abnormal-num">{{ abnormalStats.abnormal }}</span>
                    <span class="abnormal-text"> / {{ abnormalStats.total }} 异常</span>
                  </div>
                </div>
                <div class="lab-table">
                  <div class="lab-row lab-header">
                    <div class="lab-cell name">项目</div>
                    <div class="lab-cell value">结果</div>
                    <div class="lab-cell range">参考</div>
                    <div class="lab-cell flag">提示</div>
                  </div>
                  <div
                    v-for="item in detailRecord.labItems"
                    :key="item.id"
                    class="lab-row"
                    :class="{ abnormal: item.abnormalFlag !== 'NORMAL' }"
                  >
                    <div class="lab-cell name">{{ item.itemName }}</div>
                    <div
                      class="lab-cell value"
                      :class="{
                        high: item.abnormalFlag === 'HIGH',
                        low: item.abnormalFlag === 'LOW',
                      }"
                    >
                      {{ item.resultValue }} {{ item.unit }}
                    </div>
                    <div class="lab-cell range">{{ item.referenceRange }}</div>
                    <div
                      class="lab-cell flag"
                      :class="{
                        high: item.abnormalFlag === 'HIGH',
                        low: item.abnormalFlag === 'LOW',
                      }"
                    >
                      {{ abnormalLabel(item.abnormalFlag) }}
                    </div>
                  </div>
                </div>
              </div>

              <!-- 印象/结论 -->
              <div v-if="detailRecord.impression" class="detail-section">
                <div class="section-label">印象 / 结论</div>
                <div class="section-text">{{ detailRecord.impression }}</div>
              </div>

              <!-- AI 解读 -->
              <div v-if="detailRecord.aiInterpretation" class="detail-section">
                <div class="section-label">
                  <span class="ai-tag">AI</span>
                  AI 辅助解读
                </div>
                <div class="ai-content">
                  <div class="ai-icon">🤖</div>
                  <div class="ai-text">{{ detailRecord.aiInterpretation }}</div>
                </div>
                <div class="ai-tip">AI 解读仅供参考，具体诊断请以医生意见为准</div>
              </div>
            </template>

            <div v-else class="detail-section locked-section">
              <div class="locked-icon">🔒</div>
              <div class="locked-text">完整报告将在医生审核完成后开放</div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ============ 页面容器 ============ */
.page-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f5f7;
}

.pane {
  width: 100%;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.pane-list {
  padding: 16px 16px 24px;
  overflow-y: auto;
}

/* ============ push 动画 ============ */
.page-push-enter-from {
  transform: translateX(100%);
}
.page-push-leave-to {
  transform: translateX(-30%);
  opacity: 0;
}
.page-push-enter-active,
.page-push-leave-active {
  transition: transform 0.28s ease, opacity 0.28s ease;
}

/* ============ 类型筛选 ============ */
.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.filter-chip {
  padding: 6px 14px;
  font-size: 13px;
  color: #475569;
  background: #ffffff;
  border-radius: 16px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  border: 1px solid transparent;
  user-select: none;
  -webkit-user-select: none;
}

.filter-chip.active {
  background: #e3f0ff;
  color: #1a73e8;
  border-color: #4facfe;
  font-weight: 500;
}

/* ============ 日期范围筛选 ============ */
.date-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  padding: 8px 12px;
  background: #ffffff;
  border-radius: 10px;
  flex-wrap: wrap;
}

.date-label {
  font-size: 12px;
  color: #8e8e93;
  font-weight: 500;
}

.date-input {
  flex: 1;
  min-width: 100px;
  height: 32px;
  padding: 0 6px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  font-size: 12px;
  color: #1a1a1a;
  background: #f8f9fa;
  outline: none;
  font-family: inherit;
}

.date-input:focus {
  border-color: #4facfe;
  background: #fff;
}

.date-sep {
  font-size: 12px;
  color: #8e8e93;
}

.date-clear-btn {
  font-size: 12px;
  padding: 4px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  color: #4facfe;
  cursor: pointer;
  transition: all 0.15s;
}

.date-clear-btn:active {
  background: #e3f0ff;
}

/* ============ 列表 ============ */
.exam-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exam-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: transform 0.15s;
  user-select: none;
  -webkit-user-select: none;
}

.exam-card:active {
  transform: scale(0.99);
}

.exam-card-left {
  width: 56px;
  text-align: center;
  flex-shrink: 0;
}

.exam-icon {
  font-size: 28px;
  margin-bottom: 2px;
}

.exam-type {
  font-size: 12px;
  font-weight: 500;
}

.exam-card-body {
  flex: 1;
  min-width: 0;
}

.exam-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.exam-purpose {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.exam-next-action {
  font-size: 12px;
  color: #475569;
  background: #f8fafc;
  border-left: 3px solid #91d5ff;
  border-radius: 6px;
  padding: 6px 8px;
  margin-bottom: 8px;
  line-height: 1.5;
}

.exam-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: #8e8e93;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 2px;
}

.exam-card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  background: #f6ffed;
  color: #389e0d;
  border-radius: 8px;
  font-weight: 500;
}

.status-badge.status-ordered {
  background: #fff7e6;
  color: #d48806;
}

.status-badge.status-in-progress {
  background: #e6f7ff;
  color: #1890ff;
}

.status-badge.status-result-entered {
  background: #f9f0ff;
  color: #722ed1;
}

.status-badge.status-reviewed {
  background: #f6ffed;
  color: #389e0d;
}

.status-badge.status-cancelled {
  background: #f5f5f7;
  color: #8e8e93;
}

/* 卡片左边色随状态变化 */
.exam-card.ordered {
  border-left: 3px solid #fa8c16;
}

.exam-card.in_progress {
  border-left: 3px solid #1890ff;
}

.exam-card.result_entered {
  border-left: 3px solid #722ed1;
}

.exam-card.reviewed {
  border-left: 3px solid #52c41a;
}

.exam-card.cancelled {
  border-left: 3px solid #d9d9d9;
  opacity: 0.75;
}

/* ============ 状态筛选 ============ */
.status-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.status-chip {
  padding: 6px 12px;
  font-size: 12px;
  color: #475569;
  background: #ffffff;
  border-radius: 16px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.15s;
  border: 1px solid #e2e8f0;
  user-select: none;
  -webkit-user-select: none;
}

.status-chip:hover {
  border-color: #4facfe;
  color: #1a73e8;
}

.status-chip.active {
  background: #e3f0ff;
  color: #1a73e8;
  border-color: #4facfe;
  font-weight: 500;
}

.arrow {
  font-size: 18px;
  color: #c0c4cc;
  line-height: 1;
}

/* ============ 空状态 ============ */
.empty-state {
  background: #ffffff;
  border-radius: 14px;
  padding: 40px 20px;
  text-align: center;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
  opacity: 0.5;
}

.empty-text {
  font-size: 14px;
  color: #475569;
  margin-bottom: 4px;
}

.empty-tip {
  font-size: 12px;
  color: #8e8e93;
}

/* ============ 详情 ============ */
.pane-detail {
  background: #f5f5f7;
}

.detail-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f7;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  padding: 0 12px;
  background: #ffffff;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.back-btn {
  border: none;
  background: none;
  color: #1a73e8;
  font-size: 14px;
  cursor: pointer;
  padding: 6px 8px;
  user-select: none;
  -webkit-user-select: none;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-spacer {
  width: 60px;
}

.detail-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  -webkit-overflow-scrolling: touch;
}

.detail-content::-webkit-scrollbar {
  width: 0;
  display: none;
}

.detail-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.abnormal-stat {
  font-size: 12px;
  color: #8e8e93;
}

.abnormal-num {
  color: #f56c6c;
  font-weight: 600;
  font-size: 14px;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #8e8e93;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-tag {
  padding: 1px 6px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  font-size: 10px;
  font-weight: 600;
  border-radius: 4px;
}

.section-text {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.info-label {
  color: #8e8e93;
}

.info-value {
  color: #1a1a1a;
  font-weight: 500;
}

/* ============ 检验指标表 ============ */
.lab-table {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  overflow: hidden;
}

.lab-row {
  display: grid;
  grid-template-columns: 1.2fr 1.1fr 1fr 0.8fr;
  font-size: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.lab-row:last-child {
  border-bottom: none;
}

.lab-row.lab-header {
  background: #f5f5f7;
  font-weight: 600;
  color: #475569;
}

.lab-row.abnormal {
  background: #fffbe6;
}

.lab-cell {
  padding: 8px 6px;
  text-align: center;
  border-right: 1px solid #f0f0f0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lab-cell:last-child {
  border-right: none;
}

.lab-cell.name {
  text-align: left;
  padding-left: 10px;
  font-weight: 500;
  color: #1a1a1a;
}

.lab-cell.value {
  font-weight: 600;
  color: #1a1a1a;
}

.lab-cell.value.high,
.lab-cell.flag.high {
  color: #cf1322;
}

.lab-cell.value.low,
.lab-cell.flag.low {
  color: #2f54eb;
}

.lab-cell.flag {
  font-weight: 500;
}

/* ============ AI 解读 ============ */
.ai-content {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: linear-gradient(135deg, #e3f0ff 0%, #f0f9ff 100%);
  border-radius: 10px;
  margin-bottom: 6px;
}

.ai-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.ai-text {
  flex: 1;
  font-size: 13px;
  color: #1a1a1a;
  line-height: 1.6;
}

.ai-tip {
  font-size: 11px;
  color: #8e8e93;
  text-align: center;
  padding: 4px 0;
}

/* ============ 状态步骤卡（详情页顶部） ============ */
.status-step-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-step-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-step-badge {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.status-step-badge.status-ordered {
  background: #fff7e6;
  color: #d48806;
}

.status-step-badge.status-in-progress {
  background: #e6f7ff;
  color: #1890ff;
}

.status-step-badge.status-result-entered {
  background: #f9f0ff;
  color: #722ed1;
}

.status-step-badge.status-reviewed {
  background: #f6ffed;
  color: #389e0d;
}

.status-step-badge.status-cancelled {
  background: #f5f5f7;
  color: #8e8e93;
}

.status-step-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
}

.status-step-text {
  font-size: 13px;
  color: #1a1a1a;
  line-height: 1.6;
}

.status-step-card.status-ordered {
  background: #fffbe6;
}

.status-step-card.status-in-progress {
  background: #e6f7ff;
}

.status-step-card.status-result-entered {
  background: #f9f0ff;
}

.status-step-card.status-reviewed {
  background: #f6ffed;
}

.status-step-card.status-cancelled {
  background: #f5f5f7;
}

/* 状态步骤卡左侧色条：代替死代码 border-color（detail-section 无 border-width） */
.status-step-card {
  border-left: 4px solid;
}

.status-step-card.status-ordered {
  border-left-color: #fa8c16;
}

.status-step-card.status-in-progress {
  border-left-color: #1890ff;
}

.status-step-card.status-result-entered {
  border-left-color: #722ed1;
}

.status-step-card.status-reviewed {
  border-left-color: #52c41a;
}

.status-step-card.status-cancelled {
  border-left-color: #d9d9d9;
}

/* ============ 锁定提示（未审核时） ============ */
.locked-section {
  text-align: center;
  padding: 32px 16px;
}

.locked-icon {
  font-size: 32px;
  margin-bottom: 8px;
  opacity: 0.6;
}

.locked-text {
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.6;
}
</style>
