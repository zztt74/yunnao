<script setup lang="ts">
// 处方开立与审核
// 设计来源：product/11_功能需求.md §12、product/12_业务流程与状态机.md §9
// 规则（§12.6）：
// - 确定性规则（过敏/相互作用/剂量）先执行，不被 AI 覆盖
// - AI 负责解释风险与补充建议；AI 失败时返回 FAILED，确定性规则结果保留
// - 高风险处方需二次确认
// - 处方不可物理删除，仅可作废（CONFIRMED → VOIDED）
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getEncounterPrescription,
  savePrescriptionDraft,
  aiReviewPrescription,
  confirmPrescription,
  voidPrescription,
  getPatientPrescriptions,
} from '@/api/prescription'
import { getDrugOptions, type DrugOption } from '@/api/drug'
import { getPatientDetail } from '@/api/patient'
import { useEncounterStore } from '@/stores/encounter'
import { usePrescriptionStore } from '@/stores/prescription'
import type {
  PrescriptionResponse,
  PrescriptionItemRequest,
  PrescriptionRiskLevel,
  PrescriptionStatus,
  PrescriptionAiReviewStatus,
} from '@/types/prescription'

const route = useRoute()
const encounterStore = useEncounterStore()
const prescriptionStore = usePrescriptionStore()
const { activeEncounter } = storeToRefs(encounterStore)

const encounterId = computed(() => Number(route.params.id))

const loading = ref(true)
const saving = ref(false)
const reviewing = ref(false)
const confirming = ref(false)
const voiding = ref(false)

const prescription = ref<PrescriptionResponse | null>(null)
const drugs = ref<DrugOption[]>([])
const patientAllergyText = ref('无')

// F2: 历史处方（折叠区）
const historyExpanded = ref(true)
const historyList = ref<PrescriptionResponse[]>([])
const historyLoading = ref(false)
const historyLoadedFor = ref<number | null>(null)

// 草稿表单
const diagnosis = ref('')
const remark = ref('')
const items = ref<PrescriptionItemRequest[]>([])

// 选药下拉
const selectedDrugId = ref<number | null>(null)

// 患者过敏史（用于 AI 审核与展示）
const patientAllergies = computed(() => {
  if (!activeEncounter.value) return '无'
  return patientAllergyText.value || '无'
})

const hasAllergyRisk = computed(
  () => patientAllergies.value && patientAllergies.value !== '无',
)

const drugOptions = computed(() => drugs.value)

const status = computed(() => prescription.value?.status ?? null)
const isDraft = computed(() => status.value === 'DRAFT')
const isConfirmed = computed(() => status.value === 'CONFIRMED')
const isVoided = computed(() => status.value === 'VOIDED')
const isReadOnly = computed(() => isConfirmed.value || isVoided.value)

const aiReview = computed(() => prescription.value?.aiReview ?? null)
const aiReviewStatus = computed(() => prescription.value?.aiReviewStatus ?? 'NOT_REQUESTED')
const riskLevel = computed<PrescriptionRiskLevel | null>(() => aiReview.value?.riskLevel ?? null)
const isHighRisk = computed(() => riskLevel.value === 'HIGH')

function statusText(s: PrescriptionStatus | null): string {
  switch (s) {
    case 'DRAFT':
      return '草稿'
    case 'CONFIRMED':
      return '已确认'
    case 'VOIDED':
      return '已作废'
    default:
      return '未开立'
  }
}

function statusClass(s: PrescriptionStatus | null): string {
  switch (s) {
    case 'DRAFT':
      return 'tag-draft'
    case 'CONFIRMED':
      return 'tag-confirmed'
    case 'VOIDED':
      return 'tag-voided'
    default:
      return 'tag-none'
  }
}

function riskText(r: PrescriptionRiskLevel): string {
  switch (r) {
    case 'LOW':
      return '低风险'
    case 'MEDIUM':
      return '中风险'
    case 'HIGH':
      return '高风险'
    default:
      return r
  }
}

function riskClass(r: PrescriptionRiskLevel): string {
  switch (r) {
    case 'LOW':
      return 'risk-low'
    case 'MEDIUM':
      return 'risk-medium'
    case 'HIGH':
      return 'risk-high'
    default:
      return ''
  }
}

function syncFormFromPrescription() {
  if (prescription.value) {
    diagnosis.value = prescription.value.diagnosis || ''
    remark.value = prescription.value.remark || ''
    items.value = prescription.value.items.map((it) => ({
      drugId: it.drugId,
      drugCode: it.drugCode,
      drugName: it.drugName,
      strength: it.strength,
      unit: it.unit,
      dosage: it.dosage,
      frequency: it.frequency,
      usage: it.usage,
      duration: it.duration,
      remark: it.remark || '',
    }))
  }
}

async function loadPrescription() {
  loading.value = true
  try {
    const [pres, drugList] = await Promise.all([
      getEncounterPrescription(encounterId.value),
      getDrugOptions(),
    ])
    prescription.value = pres
    drugs.value = drugList
    prescriptionStore.setCurrentPrescription(pres, {
      encounterId: encounterId.value,
      patientId: activeEncounter.value?.patientId,
    })
    await loadPatientAllergies()
    if (pres) syncFormFromPrescription()
    // F2: 加载历史处方（异步，不阻塞当前页）
    void loadHistoryPrescriptions()
  } catch (e) {
    console.error('[Prescription] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

async function loadPatientAllergies() {
  if (!activeEncounter.value) return
  try {
    const patient = await getPatientDetail(activeEncounter.value.patientId)
    patientAllergyText.value = patient.allergies || '无'
  } catch (e) {
    patientAllergyText.value = '无'
    console.warn('[Prescription] patient allergy unavailable', e)
  }
}

/** F2: 加载当前患者的历史处方（不含草稿） */
async function loadHistoryPrescriptions(force = false) {
  const patientId = activeEncounter.value?.patientId
  if (!patientId) return
  if (!force && historyLoadedFor.value === patientId && historyList.value.length > 0) {
    return
  }
  historyLoading.value = true
  try {
    const list = await getPatientPrescriptions(patientId, { includeDraft: false })
    // 排除当前正在编辑的就诊处方
    const cur = prescription.value?.id
    historyList.value = list.filter((p) => p.id !== cur)
    historyLoadedFor.value = patientId
    prescriptionStore.setHistory(patientId, historyList.value)
  } catch (e) {
    console.error('[Prescription] 历史处方加载失败：', e)
    // 不弹错误，保持空态即可
  } finally {
    historyLoading.value = false
  }
}

function toggleHistory() {
  historyExpanded.value = !historyExpanded.value
}

/** F2: 历史处方摘要展示辅助 */
function historyStatusText(s: PrescriptionStatus): string {
  if (s === 'CONFIRMED') return '已确认'
  if (s === 'VOIDED') return '已作废'
  return '草稿'
}

function historyAiReviewText(s: PrescriptionAiReviewStatus): string {
  if (s === 'REVIEWED') return '已审核'
  if (s === 'PENDING') return '审核中'
  if (s === 'FAILED') return 'AI 失败'
  return '未审核'
}

function aiReviewTagClass(s: PrescriptionAiReviewStatus): string {
  if (s === 'REVIEWED') return 'ai-reviewed'
  if (s === 'PENDING') return 'ai-pending'
  if (s === 'FAILED') return 'ai-failed'
  return 'ai-none'
}

function drugSummary(items: PrescriptionResponse['items']): string {
  if (!items.length) return '无药品'
  const names = items.map((it) => it.drugName).filter(Boolean)
  if (names.length <= 2) return names.join('、')
  return `${names.slice(0, 2).join('、')} 等 ${names.length} 种`
}

function firstWarning(p: PrescriptionResponse): string {
  if (!p.aiReview?.warnings?.length) return ''
  return p.aiReview.warnings[0]
}

/** 从药品字典添加药品（预填默认用法用量） */
function addDrug() {
  if (!selectedDrugId.value) {
    ElMessage.warning('请先选择药品')
    return
  }
  const drug = drugs.value.find((d) => d.id === selectedDrugId.value)
  if (!drug) return
  // 避免重复添加
  if (items.value.some((it) => it.drugId === drug.id)) {
    ElMessage.info('该药品已在处方中')
    return
  }
  items.value.push({
    drugId: drug.id,
    drugCode: drug.code,
    drugName: drug.name,
    strength: drug.strength,
    unit: drug.unit,
    dosage: drug.defaultDosage,
    frequency: drug.defaultFrequency,
    usage: drug.defaultUsage,
    duration: drug.defaultDuration,
    remark: '',
  })
  selectedDrugId.value = null
}

function removeItem(idx: number) {
  items.value.splice(idx, 1)
}

function buildSavePayload() {
  return {
    encounterId: encounterId.value,
    diagnosis: diagnosis.value,
    items: items.value,
    remark: remark.value || undefined,
  }
}

/** 保存草稿（DRAFT） */
async function handleSaveDraft() {
  if (!diagnosis.value.trim()) {
    ElMessage.warning('请填写诊断')
    return
  }
  if (items.value.length === 0) {
    ElMessage.warning('请至少添加一种药品')
    return
  }
  if (!activeEncounter.value) {
    ElMessage.error('就诊信息丢失，请返回工作台')
    return
  }
  saving.value = true
  try {
    const pres = await savePrescriptionDraft(activeEncounter.value, buildSavePayload())
    prescription.value = pres
    ElMessage.success('处方草稿已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

/** AI 审核（§12.6：确定性规则 + AI 建议） */
async function handleAiReview() {
  if (!prescription.value) {
    ElMessage.warning('请先保存处方草稿')
    return
  }
  reviewing.value = true
  try {
    const pres = await aiReviewPrescription(
      prescription.value.id,
      patientAllergies.value,
    )
    prescription.value = pres
    if (pres.aiReviewStatus === 'FAILED') {
      ElMessage.warning('AI 审核失败，但确定性规则检查已完成，请参考下方提示')
    } else {
      ElMessage.success('AI 审核完成')
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '审核失败')
  } finally {
    reviewing.value = false
  }
}

/** 确认处方（DRAFT → CONFIRMED，高风险需二次确认） */
async function handleConfirm() {
  if (!prescription.value) return
  // 高风险二次确认（§12.6）
  if (isHighRisk.value) {
    try {
      await ElMessageBox.confirm(
        '该处方被判定为高风险（存在过敏禁忌或严重相互作用）。确认继续开具吗？此操作需二次确认。',
        '高风险处方二次确认',
        {
          confirmButtonText: '确认开具（高风险）',
          cancelButtonText: '取消',
          type: 'error',
          confirmButtonClass: 'el-button--danger',
        },
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(
        '确认开具该处方吗？确认后患者可见，且不可再修改（仅可作废）。',
        '确认处方',
        { confirmButtonText: '确认开具', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  }
  confirming.value = true
  try {
    const pres = await confirmPrescription(prescription.value.id, isHighRisk.value)
    prescription.value = pres
    ElMessage.success('处方已确认开具')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '确认失败')
  } finally {
    confirming.value = false
  }
}

/** 作废处方（CONFIRMED → VOIDED） */
async function handleVoid() {
  if (!prescription.value) return
  let reason = ''
  try {
    const res = await ElMessageBox.prompt('请填写作废原因', '作废处方', {
      confirmButtonText: '确认作废',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '作废原因（必填）',
      inputValidator: (val) => (val && val.trim() ? true : '请填写作废原因'),
    })
    reason = res.value
  } catch {
    return
  }
  voiding.value = true
  try {
    const pres = await voidPrescription(prescription.value.id, reason)
    prescription.value = pres
    ElMessage.success('处方已作废')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '作废失败')
  } finally {
    voiding.value = false
  }
}

function frequencyOptions() {
  return ['QD', 'BID', 'TID', 'QID', 'QN', 'PRN']
}

function formatDateTime(iso?: string | null): string {
  if (!iso) return '--'
  try {
    return new Date(iso).toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return '--'
  }
}

onMounted(loadPrescription)
</script>

<template>
  <div class="pres-view">
    <!-- 头部状态 -->
    <div class="pres-header">
      <div class="header-left">
        <span class="pres-label">处方状态</span>
        <span class="status-tag" :class="statusClass(status)">
          {{ statusText(status) }}
        </span>
        <span v-if="prescription?.confirmedAt" class="pres-time">
          确认于 {{ formatDateTime(prescription.confirmedAt) }}
        </span>
        <span v-if="prescription?.voidedAt" class="pres-time">
          作废于 {{ formatDateTime(prescription.voidedAt) }}
        </span>
      </div>
    </div>

    <!-- 过敏史警示 -->
    <div v-if="hasAllergyRisk" class="allergy-banner">
      患者过敏史：{{ patientAllergies }}（系统将自动校验药品过敏禁忌）
    </div>

    <div v-if="loading" class="loading-inline">
      <span class="mini-spinner" /> 加载中…
    </div>

    <template v-else>
      <!-- 诊断与药品 -->
      <div class="block" :class="{ readonly: isReadOnly }">
        <div class="form-group">
          <label class="form-label">诊断 <span class="required">*</span></label>
          <input
            v-model="diagnosis"
            class="form-input"
            :disabled="isReadOnly"
            placeholder="如 急性上呼吸道感染"
          />
        </div>

        <!-- 药品列表 -->
        <div class="form-group">
          <div class="label-row">
            <label class="form-label">药品明细 <span class="required">*</span></label>
            <div v-if="!isReadOnly" class="drug-add">
              <select v-model="selectedDrugId" class="form-select drug-select">
                <option :value="null">选择药品…</option>
                <option v-for="d in drugOptions" :key="d.id" :value="d.id">
                  {{ d.name }}（{{ d.strength }}）
                </option>
              </select>
              <button class="ghost-btn sm" :disabled="!selectedDrugId" @click="addDrug">
                + 添加
              </button>
            </div>
          </div>

          <div v-if="items.length === 0" class="empty-inline">
            尚未添加药品
          </div>
          <div v-else class="item-list">
            <div
              v-for="(item, idx) in items"
              :key="idx"
              class="item-card"
            >
              <div class="item-head">
                <span class="item-name">{{ item.drugName }}</span>
                <span class="item-strength">{{ item.strength }} / {{ item.unit }}</span>
                <button
                  v-if="!isReadOnly"
                  class="remove-btn"
                  @click="removeItem(idx)"
                >
                  移除
                </button>
              </div>
              <div class="item-fields">
                <div class="field">
                  <span class="field-label">单次剂量</span>
                  <input v-model="item.dosage" class="field-input" :disabled="isReadOnly" />
                </div>
                <div class="field">
                  <span class="field-label">频次</span>
                  <select v-model="item.frequency" class="field-input" :disabled="isReadOnly">
                    <option v-for="f in frequencyOptions()" :key="f" :value="f">{{ f }}</option>
                  </select>
                </div>
                <div class="field">
                  <span class="field-label">用法</span>
                  <input v-model="item.usage" class="field-input" :disabled="isReadOnly" />
                </div>
                <div class="field">
                  <span class="field-label">疗程</span>
                  <input v-model="item.duration" class="field-input" :disabled="isReadOnly" />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="form-group">
          <label class="form-label">备注</label>
          <textarea
            v-model="remark"
            class="form-textarea"
            rows="2"
            :disabled="isReadOnly"
            placeholder="用药交代、注意事项等"
          />
        </div>
      </div>

      <!-- AI 审核结果 -->
      <div v-if="aiReview" class="block review-block">
        <div class="block-title">
          AI 处方审核结果
          <span v-if="aiReviewStatus === 'FAILED'" class="ai-failed-tag">AI 失败（规则已执行）</span>
        </div>
        <div class="risk-row">
          <span class="risk-label">风险等级</span>
          <span class="risk-tag" :class="riskClass(riskLevel!)">{{ riskText(riskLevel!) }}</span>
          <span class="risk-time">审核于 {{ formatDateTime(aiReview.reviewedAt) }}</span>
        </div>
        <div class="warnings">
          <div class="warn-title">审核提示</div>
          <div
            v-for="(w, idx) in aiReview.warnings"
            :key="idx"
            class="warn-item"
            :class="{ 'warn-danger': w.includes('过敏禁忌') }"
          >
            • {{ w }}
          </div>
        </div>
        <div v-if="aiReview.advice" class="advice">
          <span class="advice-label">AI 建议：</span>{{ aiReview.advice }}
        </div>
      </div>

      <!-- F2: 历史处方 -->
      <div class="block history-block">
        <div class="history-header" @click="toggleHistory">
          <div class="history-title">
            <span class="caret" :class="{ open: historyExpanded }">▸</span>
            <span class="title-text">📋 历史处方</span>
            <span class="history-count">
              <template v-if="historyLoading">加载中…</template>
              <template v-else>共 {{ historyList.length }} 条</template>
            </span>
          </div>
          <button
            class="ghost-btn sm"
            :disabled="historyLoading"
            @click.stop="loadHistoryPrescriptions(true)"
          >
            刷新
          </button>
        </div>

        <div v-if="historyExpanded" class="history-body">
          <div v-if="historyLoading" class="history-skeleton">
            <div v-for="i in 2" :key="i" class="skeleton-row"></div>
          </div>
          <div v-else-if="historyList.length === 0" class="history-empty">
            <div class="empty-icon">📭</div>
            <div class="empty-text">暂无历史处方</div>
            <div class="empty-tip">该患者当前没有已确认/已作废的处方记录</div>
          </div>
          <div v-else class="history-table">
            <div class="history-row history-row-head">
              <div class="col col-time">时间</div>
              <div class="col col-status">状态</div>
              <div class="col col-drugs">药品摘要</div>
              <div class="col col-ai">AI 审核</div>
              <div class="col col-risk">风险</div>
            </div>
            <div
              v-for="h in historyList"
              :key="h.id"
              class="history-row"
            >
              <div class="col col-time">
                {{ formatDateTime(h.confirmedAt || h.createdAt) }}
              </div>
              <div class="col col-status">
                <span class="h-status" :class="`h-status-${h.status.toLowerCase()}`">
                  {{ historyStatusText(h.status) }}
                </span>
              </div>
              <div class="col col-drugs">{{ drugSummary(h.items) }}</div>
              <div class="col col-ai">
                <span class="ai-tag" :class="aiReviewTagClass(h.aiReviewStatus)">
                  {{ historyAiReviewText(h.aiReviewStatus) }}
                </span>
                <div v-if="firstWarning(h)" class="ai-warn" :title="firstWarning(h)">
                  ⚠ {{ firstWarning(h) }}
                </div>
              </div>
              <div class="col col-risk">
                <span
                  v-if="h.aiReview?.riskLevel"
                  class="risk-tag"
                  :class="riskClass(h.aiReview.riskLevel)"
                >
                  {{ riskText(h.aiReview.riskLevel) }}
                </span>
                <span v-else class="risk-empty">—</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div v-if="!isReadOnly" class="action-bar">
        <button
          class="ghost-btn"
          :disabled="saving || reviewing || confirming"
          @click="handleSaveDraft"
        >
          <span v-if="saving" class="btn-spinner small" />
          {{ saving ? '保存中…' : '保存草稿' }}
        </button>
        <button
          v-if="prescription && !aiReview"
          class="ghost-btn ai-btn"
          :disabled="reviewing"
          @click="handleAiReview"
        >
          <span v-if="reviewing" class="btn-spinner small" />
          {{ reviewing ? '查询中…' : '查看 AI 审核结果' }}
        </button>
        <button
          v-else-if="prescription && aiReview"
          class="ghost-btn ai-btn"
          :disabled="reviewing"
          @click="handleAiReview"
        >
          <span v-if="reviewing" class="btn-spinner small" />
          {{ reviewing ? '刷新中…' : '刷新 AI 审核结果' }}
        </button>
        <button
          v-if="prescription"
          class="primary-btn"
          :disabled="confirming"
          @click="handleConfirm"
        >
          <span v-if="confirming" class="btn-spinner" />
          {{ isHighRisk ? '确认开具（高风险）' : '确认开具' }}
        </button>
      </div>

      <!-- 已确认后操作 -->
      <div v-if="isConfirmed" class="action-bar">
        <button
          class="danger-btn"
          :disabled="voiding"
          @click="handleVoid"
        >
          <span v-if="voiding" class="btn-spinner small" />
          {{ voiding ? '作废中…' : '作废处方' }}
        </button>
      </div>

      <!-- 状态提示 -->
      <div v-if="isVoided" class="voided-tip">
        处方已作废{{ prescription?.voidedReason ? `（原因：${prescription.voidedReason}）` : '' }}
      </div>
    </template>
  </div>
</template>

<style scoped>
.pres-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.pres-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pres-label {
  font-size: 14px;
  color: #4a5568;
  font-weight: 500;
}

.status-tag {
  font-size: 13px;
  padding: 3px 12px;
  border-radius: 12px;
  font-weight: 500;
}

.tag-none {
  background: #f0f0f0;
  color: #8e8e93;
}

.tag-draft {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-confirmed {
  background: #f0fff4;
  color: #67c23a;
}

.tag-voided {
  background: #fff1f0;
  color: #f56c6c;
}

.pres-time {
  font-size: 13px;
  color: #8e8e93;
}

.allergy-banner {
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 14px;
  color: #d46b08;
  font-weight: 500;
}

.loading-inline {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #8e8e93;
  padding: 16px 0;
}

.mini-spinner {
  width: 14px;
  height: 14px;
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

.block {
  background: #fafbfc;
  border-radius: 12px;
  padding: 16px 18px;
}

.block.readonly {
  background: #f5f5f5;
}

.block-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 14px;
}

.ai-failed-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #fff1f0;
  color: #f56c6c;
  font-weight: 500;
}

.form-group {
  margin-bottom: 14px;
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;
}

.form-label {
  display: block;
  font-size: 14px;
  color: #4a5568;
  font-weight: 500;
  margin-bottom: 6px;
}

.required {
  color: #f56c6c;
}

.form-input,
.form-select {
  width: 100%;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
}

.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.form-input:disabled,
.form-select:disabled {
  background: #f5f5f5;
  color: #8e8e93;
  cursor: not-allowed;
}

.drug-add {
  display: flex;
  gap: 8px;
  align-items: center;
}

.drug-select {
  width: auto;
  min-width: 220px;
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
}

.form-textarea:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.form-textarea:disabled {
  background: #f5f5f5;
  color: #8e8e93;
  cursor: not-allowed;
}

.empty-inline {
  font-size: 14px;
  color: #8e8e93;
  padding: 12px 0;
  text-align: center;
}

.item-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.item-card {
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 12px 14px;
}

.item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.item-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.item-strength {
  font-size: 13px;
  color: #8e8e93;
}

.remove-btn {
  padding: 2px 10px;
  background: transparent;
  border: 1px solid #ffa39e;
  border-radius: 4px;
  color: #f56c6c;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.remove-btn:hover {
  background: #fff1f0;
}

.item-fields {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 12px;
  color: #8e8e93;
}

.field-input {
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 13px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
}

.field-input:focus {
  outline: none;
  border-color: #4facfe;
}

.field-input:disabled {
  background: #f5f5f5;
  color: #8e8e93;
  cursor: not-allowed;
}

.review-block {
  background: #faf5ff;
  border: 1px solid #d3adf7;
}

.risk-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.risk-label {
  font-size: 14px;
  color: #4a5568;
  font-weight: 500;
}

.risk-tag {
  font-size: 14px;
  padding: 3px 14px;
  border-radius: 12px;
  font-weight: 600;
}

.risk-low {
  background: #f0fff4;
  color: #67c23a;
}

.risk-medium {
  background: #fff7e6;
  color: #fa8c16;
}

.risk-high {
  background: #fff1f0;
  color: #f56c6c;
}

.risk-time {
  font-size: 13px;
  color: #8e8e93;
  margin-left: auto;
}

.warnings {
  margin-bottom: 12px;
}

.warn-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
}

.warn-item {
  font-size: 14px;
  color: #595959;
  line-height: 1.7;
  padding: 2px 0;
}

.warn-item.warn-danger {
  color: #f56c6c;
  font-weight: 500;
}

.advice {
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  border-radius: 8px;
  padding: 10px 12px;
  line-height: 1.5;
}

.advice-label {
  font-weight: 600;
  color: #9b59b6;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.ghost-btn {
  padding: 8px 18px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  color: #4a5568;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.ghost-btn:hover:not(:disabled) {
  border-color: #4facfe;
  color: #4facfe;
}

.ghost-btn.sm {
  padding: 6px 14px;
  font-size: 13px;
}

.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ai-btn:hover:not(:disabled) {
  border-color: #9b59b6;
  color: #9b59b6;
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
  gap: 6px;
}

.primary-btn:hover:not(:disabled) {
  opacity: 0.92;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.danger-btn {
  padding: 8px 20px;
  background: #ffffff;
  border: 1px solid #f56c6c;
  border-radius: 8px;
  color: #f56c6c;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.danger-btn:hover:not(:disabled) {
  background: #fff1f0;
}

.danger-btn:disabled {
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

.btn-spinner.small {
  width: 10px;
  height: 10px;
  border-color: rgb(74 85 104 / 30%);
  border-top-color: #4a5568;
}

.voided-tip {
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 14px;
  color: #f56c6c;
}

/* F2: 历史处方 */
.history-block {
  padding: 14px 16px;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.history-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.history-title .caret {
  display: inline-block;
  font-size: 14px;
  color: #8e8e93;
  transition: transform 0.2s;
}

.history-title .caret.open {
  transform: rotate(90deg);
}

.history-count {
  font-size: 12px;
  color: #8e8e93;
  font-weight: 400;
}

.history-body {
  margin-top: 12px;
}

.history-skeleton {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-row {
  height: 40px;
  border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f0 0%, #f8f8f8 50%, #f0f0f0 100%);
  background-size: 200% 100%;
  animation: hist-shine 1.4s ease-in-out infinite;
}

@keyframes hist-shine {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.history-empty {
  background: #ffffff;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  padding: 24px 12px;
  text-align: center;
}

.history-empty .empty-icon {
  font-size: 28px;
  margin-bottom: 4px;
}

.history-empty .empty-text {
  font-size: 14px;
  color: #475569;
  margin-bottom: 2px;
}

.history-empty .empty-tip {
  font-size: 12px;
  color: #8e8e93;
}

.history-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-row {
  display: grid;
  grid-template-columns: 130px 80px 1fr 110px 80px;
  align-items: center;
  background: #ffffff;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
  border: 1px solid #f0f0f0;
  gap: 8px;
}

.history-row-head {
  background: transparent;
  border: none;
  font-size: 12px;
  color: #8e8e93;
  font-weight: 500;
  padding: 4px 12px;
}

.col {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-drugs {
  white-space: normal;
  word-break: break-word;
  font-size: 12px;
  color: #1a1a1a;
}

.h-status {
  display: inline-block;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.h-status-confirmed {
  background: #f0fff4;
  color: #67c23a;
}

.h-status-voided {
  background: #fff1f0;
  color: #f56c6c;
}

.h-status-draft {
  background: #e6f7ff;
  color: #1890ff;
}

.ai-tag {
  display: inline-block;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  font-weight: 500;
}

.ai-reviewed {
  background: #f0fff4;
  color: #67c23a;
}

.ai-pending {
  background: #fff7e6;
  color: #d48806;
}

.ai-failed {
  background: #fff1f0;
  color: #f56c6c;
}

.ai-none {
  background: #f0f0f0;
  color: #8e8e93;
}

.ai-warn {
  font-size: 11px;
  color: #d4380d;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.risk-empty {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
