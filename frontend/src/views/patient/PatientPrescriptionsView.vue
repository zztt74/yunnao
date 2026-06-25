<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getMyPrescriptions } from '@/api/prescription'
import type { PrescriptionResponse, PrescriptionStatus, PrescriptionRiskLevel } from '@/types/prescription'

const loading = ref(false)
const list = ref<PrescriptionResponse[]>([])
const showDetail = ref(false)
const detailRecord = ref<PrescriptionResponse | null>(null)

/** 日期范围筛选（§14.3） */
const dateFrom = ref<string>('')
const dateTo = ref<string>('')
const dateFilterActive = computed(
  () => Boolean(dateFrom.value || dateTo.value),
)

const statusMeta: Record<PrescriptionStatus, { label: string; bg: string; color: string; border: string }> = {
  DRAFT: { label: '草稿', bg: '#f5f5f5', color: '#8e8e93', border: '#d9d9d9' },
  CONFIRMED: { label: '已开具', bg: '#e3f0ff', color: '#1a73e8', border: '#a8cfff' },
  VOIDED: { label: '已作废', bg: '#fff1f0', color: '#cf1322', border: '#ffa39e' },
}

const riskMeta: Record<PrescriptionRiskLevel, { label: string; bg: string; color: string; icon: string }> = {
  LOW: { label: '低风险', bg: '#f6ffed', color: '#389e0d', icon: '✓' },
  MEDIUM: { label: '中风险', bg: '#fffbe6', color: '#d48806', icon: '⚠️' },
  HIGH: { label: '高风险', bg: '#fff1f0', color: '#cf1322', icon: '🚨' },
}

const frequencyMap: Record<string, string> = {
  QD: '每日 1 次',
  BID: '每日 2 次',
  TID: '每日 3 次',
  QID: '每日 4 次',
  QN: '每晚 1 次',
}

async function loadList() {
  loading.value = true
  try {
    list.value = await getMyPrescriptions({
      fromDate: dateFrom.value || undefined,
      toDate: dateTo.value || undefined,
    })
  } catch (e) {
    console.error('加载处方失败：', e)
    ElMessage.error('加载处方失败')
  } finally {
    loading.value = false
  }
}

function clearDateFilter() {
  dateFrom.value = ''
  dateTo.value = ''
}

const totalItems = computed(() => {
  return list.value.reduce((sum, p) => sum + p.items.length, 0)
})

function viewDetail(r: PrescriptionResponse) {
  detailRecord.value = r
  showDetail.value = true
}

function closeDetail() {
  showDetail.value = false
  setTimeout(() => {
    detailRecord.value = null
  }, 250)
}

onMounted(() => {
  loadList()
})

watch([dateFrom, dateTo], () => {
  loadList()
})
</script>

<template>
  <div v-loading="loading" class="page-wrapper">
    <Transition name="page-push" mode="out-in">
      <!-- ============ 列表 ============ -->
      <div v-if="!showDetail" key="list" class="pane pane-list">
        <!-- 顶部概览 -->
        <div v-if="list.length" class="overview-card">
          <div class="overview-item">
            <div class="overview-num">{{ list.length }}</div>
            <div class="overview-label">张处方</div>
          </div>
          <div class="overview-divider"></div>
          <div class="overview-item">
            <div class="overview-num">{{ totalItems }}</div>
            <div class="overview-label">种药品</div>
          </div>
          <div class="overview-divider"></div>
          <div class="overview-item">
            <div class="overview-num">
              {{ list.filter((p) => p.status === 'CONFIRMED').length }}
            </div>
            <div class="overview-label">有效中</div>
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

        <div v-if="list.length === 0" class="empty-state">
          <div class="empty-icon">💊</div>
          <div class="empty-text">暂无处方记录</div>
          <div class="empty-tip">医生开具并确认后将在此显示</div>
        </div>

        <div v-else class="pres-list">
          <div
            v-for="r in list"
            :key="r.id"
            class="pres-card"
            :class="{ voided: r.status === 'VOIDED' }"
            @click="viewDetail(r)"
          >
            <div class="pres-card-top">
              <div class="pres-dept">{{ r.departmentName }}</div>
              <div
                class="pres-status"
                :style="{
                  background: statusMeta[r.status].bg,
                  color: statusMeta[r.status].color,
                  borderColor: statusMeta[r.status].border,
                }"
              >
                {{ statusMeta[r.status].label }}
              </div>
            </div>
            <div class="pres-diagnosis">诊断：{{ r.diagnosis }}</div>
            <div class="pres-drugs">
              <div
                v-for="item in r.items.slice(0, 3)"
                :key="item.id"
                class="drug-tag"
              >
                {{ item.drugName }}
                <span class="drug-spec">({{ item.strength }})</span>
              </div>
              <div v-if="r.items.length > 3" class="drug-more">
                等 {{ r.items.length }} 种药品
              </div>
            </div>
            <div class="pres-card-bottom">
              <div class="pres-doctor">
                <span class="doctor-avatar">{{ r.doctorName.charAt(0) }}</span>
                <span class="doctor-name">{{ r.doctorName }} 医生</span>
              </div>
              <div class="pres-time">
                {{ dayjs(r.confirmedAt || r.createdAt).format('YYYY-MM-DD') }}
              </div>
            </div>
            <div v-if="r.aiReview && r.status === 'CONFIRMED'" class="pres-risk">
              <span
                class="risk-badge"
                :style="{
                  background: riskMeta[r.aiReview.riskLevel].bg,
                  color: riskMeta[r.aiReview.riskLevel].color,
                }"
              >
                {{ riskMeta[r.aiReview.riskLevel].icon }}
                AI 审核：{{ riskMeta[r.aiReview.riskLevel].label }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 详情 ============ -->
      <div v-else key="detail" class="pane pane-detail">
        <div v-if="detailRecord" class="detail-page">
          <div class="detail-header">
            <button class="back-btn" @click="closeDetail">‹ 返回</button>
            <div class="detail-title">处方详情</div>
            <div class="detail-spacer"></div>
          </div>

          <div class="detail-content">
            <!-- 顶部状态卡 -->
            <div
              class="status-card"
              :class="{ voided: detailRecord.status === 'VOIDED' }"
            >
              <div class="status-row">
                <div class="status-text">{{ statusMeta[detailRecord.status].label }}</div>
                <div class="status-doctor">{{ detailRecord.doctorName }} 医生</div>
              </div>
              <div class="status-meta">
                {{ detailRecord.departmentName }} ·
                {{ dayjs(detailRecord.confirmedAt || detailRecord.createdAt).format('YYYY-MM-DD HH:mm') }}
              </div>
              <div v-if="detailRecord.status === 'VOIDED' && detailRecord.voidedReason" class="voided-reason">
                作废原因：{{ detailRecord.voidedReason }}
              </div>
            </div>

            <!-- 诊断 -->
            <div class="detail-section">
              <div class="section-label">临床诊断</div>
              <div class="section-text">{{ detailRecord.diagnosis }}</div>
            </div>

            <!-- 药品列表 -->
            <div class="detail-section">
              <div class="section-header">
                <div class="section-title">药品明细（{{ detailRecord.items.length }}）</div>
              </div>
              <div
                v-for="(item, idx) in detailRecord.items"
                :key="item.id"
                class="drug-item"
              >
                <div class="drug-item-head">
                  <div class="drug-index">{{ idx + 1 }}</div>
                  <div class="drug-item-info">
                    <div class="drug-item-name">
                      {{ item.drugName }}
                      <span class="drug-item-spec">{{ item.strength }}</span>
                    </div>
                  </div>
                </div>
                <div class="drug-item-grid">
                  <div class="drug-field">
                    <span class="field-label">用法</span>
                    <span class="field-value">{{ item.usage }}</span>
                  </div>
                  <div class="drug-field">
                    <span class="field-label">剂量</span>
                    <span class="field-value">{{ item.dosage }}</span>
                  </div>
                  <div class="drug-field">
                    <span class="field-label">频次</span>
                    <span class="field-value">
                      {{ frequencyMap[item.frequency] || item.frequency }}
                    </span>
                  </div>
                  <div class="drug-field">
                    <span class="field-label">疗程</span>
                    <span class="field-value">{{ item.duration }}</span>
                  </div>
                </div>
                <div v-if="item.remark" class="drug-remark">
                  备注：{{ item.remark }}
                </div>
              </div>
            </div>

            <!-- 医生备注 -->
            <div v-if="detailRecord.remark" class="detail-section">
              <div class="section-label">医嘱</div>
              <div class="section-text">{{ detailRecord.remark }}</div>
            </div>

            <!-- AI 审核提示 -->
            <div v-if="detailRecord.aiReview && detailRecord.status === 'CONFIRMED'" class="detail-section">
              <div class="section-label">
                <span class="ai-tag">AI</span>
                AI 处方审核
              </div>
              <div
                class="risk-summary"
                :style="{
                  background: riskMeta[detailRecord.aiReview.riskLevel].bg,
                  color: riskMeta[detailRecord.aiReview.riskLevel].color,
                }"
              >
                {{ riskMeta[detailRecord.aiReview.riskLevel].icon }}
                风险等级：{{ riskMeta[detailRecord.aiReview.riskLevel].label }}
              </div>
              <div v-if="detailRecord.aiReview.warnings.length" class="ai-warnings">
                <div class="warnings-title">⚠️ 提示</div>
                <ul class="warnings-list">
                  <li
                    v-for="(w, i) in detailRecord.aiReview.warnings"
                    :key="i"
                  >
                    {{ w }}
                  </li>
                </ul>
              </div>
              <div v-if="detailRecord.aiReview.advice" class="ai-advice">
                <div class="advice-title">💡 建议</div>
                <div class="advice-text">{{ detailRecord.aiReview.advice }}</div>
              </div>
              <div class="ai-tip">AI 审核仅供参考，最终以医生确认结果为准</div>
            </div>

            <div class="detail-footer">
              <div>本处方由 {{ detailRecord.doctorName }} 医生开具</div>
              <div v-if="detailRecord.confirmedAt">
                确认时间：{{ dayjs(detailRecord.confirmedAt).format('YYYY-MM-DD HH:mm') }}
              </div>
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

/* ============ 概览卡 ============ */
.overview-card {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  border-radius: 14px;
  padding: 18px 12px;
  margin-bottom: 16px;
  color: #ffffff;
  box-shadow: 0 4px 12px rgb(79 172 254 / 25%);
}

.overview-item {
  flex: 1;
  text-align: center;
}

.overview-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  margin-bottom: 2px;
}

.overview-label {
  font-size: 12px;
  opacity: 0.9;
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

.overview-divider {
  width: 1px;
  height: 28px;
  background: rgb(255 255 255 / 30%);
}

/* ============ 列表 ============ */
.pres-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pres-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: transform 0.15s;
  border-left: 3px solid #1a73e8;
  user-select: none;
  -webkit-user-select: none;
}

.pres-card:active {
  transform: scale(0.99);
}

.pres-card.voided {
  border-left-color: #c0c4cc;
  opacity: 0.85;
}

.pres-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #f0f0f0;
}

.pres-dept {
  font-size: 13px;
  color: #1a73e8;
  font-weight: 500;
}

.pres-status {
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid;
  border-radius: 12px;
}

.pres-diagnosis {
  font-size: 14px;
  color: #1a1a1a;
  font-weight: 500;
  margin-bottom: 10px;
}

.pres-drugs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.drug-tag {
  padding: 4px 10px;
  background: #f5f5f7;
  color: #475569;
  border-radius: 8px;
  font-size: 12px;
}

.drug-spec {
  color: #8e8e93;
  font-size: 11px;
}

.drug-more {
  font-size: 12px;
  color: #8e8e93;
  padding: 4px 0;
}

.pres-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid #f5f5f5;
}

.pres-doctor {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8e8e93;
}

.doctor-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
}

.doctor-name {
  color: #475569;
}

.pres-time {
  font-size: 12px;
  color: #8e8e93;
}

.pres-risk {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f5f5f5;
}

.risk-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
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

/* 顶部状态卡 */
.status-card {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 4px 12px rgb(79 172 254 / 25%);
}

.status-card.voided {
  background: linear-gradient(135deg, #909399 0%, #b1b3b8 100%);
  box-shadow: 0 4px 12px rgb(144 147 153 / 25%);
}

.status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.status-text {
  font-size: 18px;
  font-weight: 600;
}

.status-doctor {
  font-size: 13px;
  opacity: 0.9;
}

.status-meta {
  font-size: 12px;
  opacity: 0.85;
}

.voided-reason {
  margin-top: 8px;
  padding: 8px 10px;
  background: rgb(255 255 255 / 20%);
  border-radius: 8px;
  font-size: 12px;
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
}

/* 药品明细 */
.drug-item {
  padding: 12px;
  background: #f5f5f7;
  border-radius: 10px;
  margin-bottom: 10px;
}

.drug-item:last-child {
  margin-bottom: 0;
}

.drug-item-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e5e5e7;
}

.drug-index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #4facfe;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.drug-item-info {
  flex: 1;
}

.drug-item-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.drug-item-spec {
  color: #8e8e93;
  font-weight: 400;
  font-size: 12px;
  margin-left: 4px;
}

.drug-item-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.drug-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.field-label {
  font-size: 11px;
  color: #8e8e93;
}

.field-value {
  font-size: 13px;
  color: #1a1a1a;
  font-weight: 500;
}

.drug-remark {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e5e5e7;
  font-size: 12px;
  color: #8e8e93;
}

/* AI 审核 */
.risk-summary {
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 10px;
  text-align: center;
}

.ai-warnings {
  padding: 10px 12px;
  background: #fff7e6;
  border-radius: 10px;
  margin-bottom: 10px;
}

.warnings-title {
  font-size: 12px;
  font-weight: 600;
  color: #d48806;
  margin-bottom: 6px;
}

.warnings-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: #1a1a1a;
  line-height: 1.7;
}

.ai-advice {
  padding: 10px 12px;
  background: #f0f9ff;
  border-radius: 10px;
  margin-bottom: 10px;
}

.advice-title {
  font-size: 12px;
  font-weight: 600;
  color: #1a73e8;
  margin-bottom: 4px;
}

.advice-text {
  font-size: 12px;
  color: #1a1a1a;
  line-height: 1.6;
}

.ai-tip {
  font-size: 11px;
  color: #8e8e93;
  text-align: center;
  padding: 4px 0;
}

.detail-footer {
  text-align: center;
  font-size: 11px;
  color: #8e8e93;
  padding: 16px 8px;
  line-height: 1.8;
}
</style>
