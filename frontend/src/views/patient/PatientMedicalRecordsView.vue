<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getMyMedicalRecords } from '@/api/medical-record'
import type { MedicalRecord } from '@/types/medical-record'

const loading = ref(false)
const records = ref<MedicalRecord[]>([])
const showDetail = ref(false)
const detailRecord = ref<MedicalRecord | null>(null)
const dateRange = ref<[string, string] | null>(null)
const dateFrom = ref<string>('')
const dateTo = ref<string>('')

async function loadRecords() {
  loading.value = true
  try {
    records.value = await getMyMedicalRecords({
      fromDate: dateFrom.value || dateRange.value?.[0],
      toDate: dateTo.value || dateRange.value?.[1],
    })
  } catch (e) {
    console.error('加载病历失败：', e)
    ElMessage.error('加载病历失败')
  } finally {
    loading.value = false
  }
}

function viewDetail(r: MedicalRecord) {
  detailRecord.value = r
  showDetail.value = true
}

function closeDetail() {
  showDetail.value = false
  setTimeout(() => {
    detailRecord.value = null
  }, 250)
}

function setQuickRange(range: 'week' | 'month' | 'all' | null) {
  if (range === null) {
    dateRange.value = null
  } else if (range === 'week') {
    dateRange.value = [dayjs().subtract(7, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')]
  } else if (range === 'month') {
    dateRange.value = [dayjs().subtract(30, 'day').format('YYYY-MM-DD'), dayjs().format('YYYY-MM-DD')]
  } else if (range === 'all') {
    dateRange.value = null
  }
  // 自定义日期优先级更高
  if (!dateFrom.value && !dateTo.value) {
    loadRecords()
  }
}

function onPickRange(value: 'week' | 'month' | 'all' | null) {
  activeRange.value = value
  setQuickRange(value)
}

function clearCustomDate() {
  dateFrom.value = ''
  dateTo.value = ''
  activeRange.value = 'all'
  dateRange.value = null
  loadRecords()
}

const quickRanges: Array<{ value: 'week' | 'month' | 'all' | null; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'week', label: '近 7 天' },
  { value: 'month', label: '近 30 天' },
]

const activeRange = ref<'week' | 'month' | 'all' | null>('all')
const customDateActive = computed(() => Boolean(dateFrom.value || dateTo.value))

const finalDiagnoses = computed(() => {
  if (!detailRecord.value) return []
  return detailRecord.value.diagnoses.filter(
    (d) => d.type === 'FINAL' && d.source === 'DOCTOR',
  )
})

onMounted(() => {
  loadRecords()
})

// 自定义日期变化时重新加载
watch([dateFrom, dateTo], () => {
  loadRecords()
})
</script>

<template>
  <div v-loading="loading" class="page-wrapper">
    <Transition name="page-push" mode="out-in">
      <!-- ============ 列表 ============ -->
      <div v-if="!showDetail" key="list" class="pane pane-list">
        <div class="filter-row">
          <div
            v-for="opt in quickRanges"
            :key="String(opt.value)"
            class="filter-chip"
            :class="{ active: activeRange === opt.value && !customDateActive }"
            @click="onPickRange(opt.value)"
          >
            {{ opt.label }}
          </div>
        </div>

        <!-- 自定义日期范围 -->
        <div class="date-row">
          <span class="date-label">自定义</span>
          <input
            v-model="dateFrom"
            type="date"
            class="date-input"
            :max="dateTo || undefined"
          />
          <span class="date-sep">至</span>
          <input
            v-model="dateTo"
            type="date"
            class="date-input"
            :min="dateFrom || undefined"
          />
          <button
            v-if="customDateActive"
            class="date-clear-btn"
            type="button"
            @click="clearCustomDate"
          >
            清除
          </button>
        </div>

        <div v-if="records.length === 0" class="empty-state">
          <div class="empty-icon">📋</div>
          <div class="empty-text">暂无病历记录</div>
          <div class="empty-tip">完成就诊后病历将在此显示</div>
        </div>

        <div v-else class="record-list">
          <div
            v-for="r in records"
            :key="r.id"
            class="record-card"
            @click="viewDetail(r)"
          >
            <div class="record-top">
              <div class="record-date">
                <div class="date-main">{{ dayjs(r.encounterDate).format('MM-DD') }}</div>
                <div class="date-year">{{ dayjs(r.encounterDate).format('YYYY') }}</div>
              </div>
              <div class="record-info">
                <div class="record-dept">{{ r.departmentName }}</div>
                <div class="record-doctor">{{ r.doctorName }} 医生</div>
                <div class="record-complaint">主诉：{{ r.chiefComplaint }}</div>
              </div>
              <div class="record-arrow">›</div>
            </div>
            <div class="record-bottom">
              <div class="record-diagnosis">
                <span class="diag-label">初步诊断：</span>
                <span class="diag-value">{{ r.preliminaryDiagnosis || '—' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 详情 ============ -->
      <div v-else key="detail" class="pane pane-detail">
        <div v-if="detailRecord" class="detail-page">
          <div class="detail-header">
            <button class="back-btn" @click="closeDetail">‹ 返回</button>
            <div class="detail-title">病历详情</div>
            <div class="detail-spacer"></div>
          </div>

          <div class="detail-content">
            <!-- 基本信息 -->
            <div class="detail-section">
              <div class="section-header">
                <div class="section-title">就诊信息</div>
              </div>
              <div class="info-grid">
                <div class="info-row">
                  <span class="info-label">就诊科室</span>
                  <span class="info-value">{{ detailRecord.departmentName }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">就诊医生</span>
                  <span class="info-value">{{ detailRecord.doctorName }}</span>
                </div>
                <div class="info-row">
                  <span class="info-label">就诊时间</span>
                  <span class="info-value">
                    {{ dayjs(detailRecord.encounterDate).format('YYYY-MM-DD HH:mm') }}
                  </span>
                </div>
                <div v-if="detailRecord.confirmedAt" class="info-row">
                  <span class="info-label">确认时间</span>
                  <span class="info-value">
                    {{ dayjs(detailRecord.confirmedAt).format('YYYY-MM-DD HH:mm') }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 主诉 -->
            <div class="detail-section">
              <div class="section-label">主诉</div>
              <div class="section-text">{{ detailRecord.chiefComplaint }}</div>
            </div>

            <!-- 现病史 -->
            <div class="detail-section">
              <div class="section-label">现病史</div>
              <div class="section-text">{{ detailRecord.presentIllness }}</div>
            </div>

            <!-- 既往史 -->
            <div v-if="detailRecord.pastHistory" class="detail-section">
              <div class="section-label">既往史</div>
              <div class="section-text">{{ detailRecord.pastHistory }}</div>
            </div>

            <!-- 体格检查 -->
            <div v-if="detailRecord.physicalExam" class="detail-section">
              <div class="section-label">体格检查</div>
              <div class="section-text">{{ detailRecord.physicalExam }}</div>
            </div>

            <!-- 初步诊断 -->
            <div v-if="detailRecord.preliminaryDiagnosis" class="detail-section">
              <div class="section-label">初步诊断</div>
              <div class="section-text">{{ detailRecord.preliminaryDiagnosis }}</div>
            </div>

            <!-- 医生最终诊断 -->
            <div v-if="finalDiagnoses.length" class="detail-section">
              <div class="section-label">
                医生最终诊断
                <span class="section-tag">已确认</span>
              </div>
              <div
                v-for="d in finalDiagnoses"
                :key="d.id"
                class="diagnosis-item"
              >
                <div class="diagnosis-name">
                  {{ d.diagnosisName }}
                  <span v-if="d.diagnosisCode" class="diagnosis-code">{{ d.diagnosisCode }}</span>
                </div>
                <div v-if="d.description" class="diagnosis-desc">{{ d.description }}</div>
              </div>
            </div>

            <!-- 治疗建议 -->
            <div v-if="detailRecord.treatmentAdvice" class="detail-section">
              <div class="section-label">治疗建议</div>
              <div class="section-text">{{ detailRecord.treatmentAdvice }}</div>
            </div>

            <div class="detail-footer">
              本病历由 {{ detailRecord.doctorName }} 医生确认于
              {{ dayjs(detailRecord.confirmedAt || detailRecord.updatedAt).format('YYYY-MM-DD HH:mm') }}
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

/* ============ 时间筛选 ============ */
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
}

.filter-chip.active {
  background: #e3f0ff;
  color: #1a73e8;
  border-color: #4facfe;
  font-weight: 500;
}

/* ============ 自定义日期 ============ */
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

/* ============ 病历卡片 ============ */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: transform 0.15s;
  user-select: none;
  -webkit-user-select: none;
}

.record-card:active {
  transform: scale(0.99);
}

.record-top {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #f0f0f0;
}

.record-date {
  width: 50px;
  text-align: center;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border-radius: 10px;
  padding: 6px 0;
  flex-shrink: 0;
}

.date-main {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.2;
}

.date-year {
  font-size: 10px;
  opacity: 0.9;
}

.record-info {
  flex: 1;
  min-width: 0;
}

.record-dept {
  font-size: 13px;
  color: #1a73e8;
  font-weight: 500;
  margin-bottom: 4px;
}

.record-doctor {
  font-size: 14px;
  color: #1a1a1a;
  font-weight: 500;
  margin-bottom: 4px;
}

.record-complaint {
  font-size: 12px;
  color: #8e8e93;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.record-arrow {
  font-size: 24px;
  color: #c0c4cc;
  font-weight: 300;
}

.record-bottom {
  padding-left: 64px;
}

.record-diagnosis {
  display: flex;
  font-size: 12px;
  line-height: 1.5;
}

.diag-label {
  color: #8e8e93;
  flex-shrink: 0;
}

.diag-value {
  color: #475569;
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

.section-tag {
  padding: 2px 8px;
  background: #f6ffed;
  color: #389e0d;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 500;
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

.diagnosis-item {
  padding: 10px 12px;
  background: #f5f5f7;
  border-radius: 10px;
  margin-bottom: 8px;
}

.diagnosis-item:last-child {
  margin-bottom: 0;
}

.diagnosis-name {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.diagnosis-code {
  padding: 1px 6px;
  background: #e3f0ff;
  color: #1a73e8;
  font-size: 11px;
  font-weight: 500;
  border-radius: 4px;
}

.diagnosis-desc {
  font-size: 12px;
  color: #8e8e93;
  line-height: 1.5;
}

.detail-footer {
  text-align: center;
  font-size: 11px;
  color: #8e8e93;
  padding: 16px 8px;
  line-height: 1.6;
}
</style>
