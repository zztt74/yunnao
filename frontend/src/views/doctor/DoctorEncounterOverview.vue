<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useEncounterStore } from '@/stores/encounter'
import { getEncounterDiagnoses } from '@/api/encounter'
import { getEncounterMedicalRecord } from '@/api/medical-record'
import { getEncounterExaminations } from '@/api/examination'
import { getEncounterPrescription } from '@/api/prescription'
import type { EncounterDiagnosisResponse } from '@/types/encounter'
import type { MedicalRecord } from '@/types/medical-record'
import type { ExaminationResponse } from '@/types/examination'
import type { PrescriptionResponse } from '@/types/prescription'

const route = useRoute()
const encounterStore = useEncounterStore()
const { consultationNotes } = storeToRefs(encounterStore)

const encounterId = computed(() => Number(route.params.id))

const checking = ref(true)
const diagnoses = ref<EncounterDiagnosisResponse[]>([])
const medicalRecord = ref<MedicalRecord | null>(null)
const examinations = ref<ExaminationResponse[]>([])
const prescription = ref<PrescriptionResponse | null>(null)

const notes = consultationNotes

const hasFinalDiagnosis = computed(() =>
  diagnoses.value.some((diagnosis) => diagnosis.type === 'FINAL' && diagnosis.source === 'DOCTOR'),
)
const hasConfirmedMedicalRecord = computed(() => medicalRecord.value?.status === 'CONFIRMED')
const hasPendingExams = computed(() =>
  examinations.value.some((exam) =>
    exam.status === 'ORDERED' ||
    exam.status === 'IN_PROGRESS' ||
    exam.status === 'RESULT_ENTERED',
  ),
)
const hasDraftPrescription = computed(() => prescription.value?.status === 'DRAFT')

const checklist = computed(() => [
  {
    label: '已确认正式病历',
    state: hasConfirmedMedicalRecord.value ? 'done' as const : 'pending' as const,
    hint: medicalRecord.value
      ? medicalRecord.value.status === 'CONFIRMED'
        ? '病历已确认'
        : `病历状态：${medicalRecord.value.status}`
      : '尚未生成病历',
  },
  {
    label: '已下达医生最终诊断',
    state: hasFinalDiagnosis.value ? 'done' as const : 'pending' as const,
    hint: hasFinalDiagnosis.value
      ? `共 ${diagnoses.value.filter((diagnosis) => diagnosis.type === 'FINAL').length} 条最终诊断`
      : '至少需要一条医生最终诊断',
  },
  {
    label: '检查检验处理状态',
    // 空列表 = 本次未开具 = 不适用；其它情况按是否有 pending 项判定
    state: examinations.value.length === 0
      ? ('notApplicable' as const)
      : hasPendingExams.value
        ? ('pending' as const)
        : ('done' as const),
    hint: examinations.value.length === 0
      ? '本次未开具检查检验'
      : hasPendingExams.value
        ? `${examinations.value.filter((exam) => exam.status !== 'REVIEWED' && exam.status !== 'CANCELLED').length} 项待处理`
        : '全部已审核或已取消',
  },
  {
    label: '处方处理状态',
    // 处方不存在 = 本次未开具 = 不适用；存在时按是否有草稿判定
    state: prescription.value === null
      ? ('notApplicable' as const)
      : hasDraftPrescription.value
        ? ('pending' as const)
        : ('done' as const),
    hint: prescription.value
      ? prescription.value.status === 'DRAFT'
        ? '存在未确认的处方草稿'
        : `处方状态：${prescription.value.status}`
      : '本次未开具处方',
  },
])

const allDone = computed(() =>
  checklist.value.every(
    (item) => item.state === 'done' || item.state === 'notApplicable',
  ),
)

const STATE_META: Record<
  'done' | 'pending' | 'notApplicable',
  { icon: string; cls: string }
> = {
  done: { icon: '✓', cls: 'state-done' },
  pending: { icon: '•', cls: 'state-pending' },
  notApplicable: { icon: '–', cls: 'state-na' },
}

function saveNotes() {
  encounterStore.setConsultationNotes({ ...notes.value })
  ElMessage.success('问诊记录已暂存')
}

async function loadChecklist() {
  checking.value = true
  try {
    const [diags, mr, exams, pres] = await Promise.all([
      getEncounterDiagnoses(encounterId.value),
      getEncounterMedicalRecord(encounterId.value),
      getEncounterExaminations(encounterId.value),
      getEncounterPrescription(encounterId.value),
    ])
    diagnoses.value = diags
    medicalRecord.value = mr
    examinations.value = exams
    prescription.value = pres
  } catch (e) {
    console.error('[Overview] 加载检查清单失败', e)
  } finally {
    checking.value = false
  }
}

onMounted(loadChecklist)
</script>

<template>
  <div class="overview">
    <div class="block">
      <div class="block-title">
        问诊记录
        <span class="block-sub">用于 AI 辅助诊断与病历生成</span>
      </div>
      <div class="form-group">
        <label class="form-label">主诉 <span class="required">*</span></label>
        <textarea
          v-model="notes.chiefComplaint"
          class="form-textarea"
          rows="2"
          placeholder="例如：发热伴咳嗽 3 天"
        />
      </div>
      <div class="form-group">
        <label class="form-label">现病史</label>
        <textarea
          v-model="notes.presentIllness"
          class="form-textarea"
          rows="3"
          placeholder="起病情况、症状演变、伴随症状、已做处理等"
        />
      </div>
      <div class="form-row">
        <div class="form-group half">
          <label class="form-label">既往史</label>
          <textarea
            v-model="notes.pastHistory"
            class="form-textarea"
            rows="2"
            placeholder="慢性病、手术史、过敏史等"
          />
        </div>
        <div class="form-group half">
          <label class="form-label">体格检查</label>
          <textarea
            v-model="notes.physicalExam"
            class="form-textarea"
            rows="2"
            placeholder="体温、血压、阳性体征等"
          />
        </div>
      </div>
      <div class="form-actions">
        <button class="primary-btn" type="button" @click="saveNotes">暂存问诊记录</button>
      </div>
    </div>

    <div class="block">
      <div class="block-title">
        完成就诊检查清单
        <span class="block-sub">需全部满足方可完成就诊</span>
      </div>
      <div v-if="checking" class="loading-inline">
        <span class="mini-spinner" /> 正在检查...
      </div>
      <div v-else class="checklist">
        <div
          v-for="item in checklist"
          :key="item.label"
          class="check-item"
          :class="STATE_META[item.state].cls"
        >
          <span class="check-icon">{{ STATE_META[item.state].icon }}</span>
          <div class="check-body">
            <div class="check-label">{{ item.label }}</div>
            <div class="check-hint">{{ item.hint }}</div>
          </div>
        </div>
      </div>
      <div v-if="!checking" class="check-summary" :class="{ ready: allDone }">
        <template v-if="allDone">所有条件已满足，可完成就诊</template>
        <template v-else>仍有未完成项，请在对应子页面处理后重试</template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.block {
  background: #fafbfc;
  border-radius: 12px;
  padding: 16px 18px;
}

.block-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 14px;
}

.block-sub {
  font-size: 13px;
  color: #8e8e93;
  font-weight: 400;
}

.form-group {
  margin-bottom: 14px;
}

.form-group.half {
  flex: 1;
}

.form-row {
  display: flex;
  gap: 14px;
}

.form-label {
  display: block;
  font-size: 14px;
  color: #4a5568;
  margin-bottom: 6px;
  font-weight: 500;
}

.required {
  color: #f56c6c;
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

.form-actions {
  display: flex;
  justify-content: flex-end;
}

.primary-btn {
  padding: 8px 20px;
  background: #1677ff;
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
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

.checklist {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.check-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 12px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.check-item.done {
  background: #f0fff4;
  border-color: #b7eb8f;
}

.check-icon {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #8e8e93;
  flex-shrink: 0;
  margin-top: 1px;
}

.check-item.done .check-icon {
  color: #67c23a;
  font-weight: 700;
}

.check-body {
  flex: 1;
}

.check-label {
  font-size: 14px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 2px;
}

.check-hint {
  font-size: 13px;
  color: #8e8e93;
}

.check-summary {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  background: #fff7e6;
  color: #d46b08;
}

.check-summary.ready {
  background: #f0fff4;
  color: #67c23a;
}
</style>
