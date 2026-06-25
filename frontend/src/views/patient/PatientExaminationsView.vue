<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getMyExaminations } from '@/api/examination'
import type { ExaminationResponse, ExaminationType } from '@/types/examination'

const loading = ref(false)
const list = ref<ExaminationResponse[]>([])
const showDetail = ref(false)
const detailRecord = ref<ExaminationResponse | null>(null)
const typeFilter = ref<'ALL' | ExaminationType>('ALL')

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
  if (typeFilter.value === 'ALL') return list.value
  return list.value.filter((i) => i.type === typeFilter.value)
})

const typeMeta: Record<ExaminationType, { label: string; icon: string; color: string }> = {
  EXAMINATION: { label: '检查', icon: '🩻', color: '#1a73e8' },
  LABORATORY: { label: '检验', icon: '🧪', color: '#67c23a' },
}

function viewDetail(r: ExaminationResponse) {
  detailRecord.value = r
  showDetail.value = true
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
          <div class="empty-tip">医生开立并审核后将在此显示</div>
        </div>

        <div v-else class="exam-list">
          <div
            v-for="r in filteredList"
            :key="r.id"
            class="exam-card"
            @click="viewDetail(r)"
          >
            <div class="exam-card-left" :style="{ color: typeMeta[r.type].color }">
              <div class="exam-icon">{{ typeMeta[r.type].icon }}</div>
              <div class="exam-type">{{ typeMeta[r.type].label }}</div>
            </div>
            <div class="exam-card-body">
              <div class="exam-name">{{ r.itemName }}</div>
              <div class="exam-purpose">目的：{{ r.purpose }}</div>
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
              <div class="status-badge">已审核</div>
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
                  <span class="info-value">{{ detailRecord.departmentName }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">申请医生</span>
                  <span class="info-value">{{ detailRecord.doctorName }}</span>
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
              </div>
            </div>

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
</style>
