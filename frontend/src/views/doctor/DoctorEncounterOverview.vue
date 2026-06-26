<script setup lang="ts">
// 接诊概览（工作台默认子页面）
// 设计来源：product/11_功能需求.md §8.4、product/12_业务流程与状态机.md §6
// 功能：
// - 问诊记录录入（主诉/现病史/既往史/体格检查），双向绑定 encounterStore，供 AI 诊断与病历生成复用
// - 完成就诊前置条件检查清单（病历/诊断/检查检验/处方）
// - AI 降级演示开关（§14：模拟 AI 失败以验证降级流程）
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage } from 'element-plus'
import { useEncounterStore } from '@/stores/encounter'
import { useRoute } from 'vue-router'
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
const { activeEncounter, consultationNotes } = storeToRefs(encounterStore)

const encounterId = computed(() => Number(route.params.id))

const checking = ref(true)
const diagnoses = ref<EncounterDiagnosisResponse[]>([])
const medicalRecord = ref<MedicalRecord | null>(null)
const examinations = ref<ExaminationResponse[]>([])
const prescription = ref<PrescriptionResponse | null>(null)

const notes = consultationNotes

// 前置条件检查
const hasFinalDiagnosis = computed(() =>
  diagnoses.value.some((d) => d.type === 'FINAL' && d.source === 'DOCTOR'),
)
const hasConfirmedMedicalRecord = computed(
  () => medicalRecord.value?.status === 'CONFIRMED',
)
const hasPendingExams = computed(
  () =>
    examinations.value.some(
      (x) =>
        x.status === 'ORDERED' ||
        x.status === 'IN_PROGRESS' ||
        x.status === 'RESULT_ENTERED',
    ),
)
const hasDraftPrescription = computed(() => prescription.value?.status === 'DRAFT')

const checklist = computed(() => [
  {
    label: '已确认正式病历',
    done: hasConfirmedMedicalRecord.value,
    hint: medicalRecord.value
      ? medicalRecord.value.status === 'CONFIRMED'
        ? '病历已确认'
        : `病历状态：${medicalRecord.value.status}`
      : '尚未生成病历',
  },
  {
    label: '已下达医生最终诊断',
    done: hasFinalDiagnosis.value,
    hint: hasFinalDiagnosis.value
      ? `共 ${diagnoses.value.filter((d) => d.type === 'FINAL').length} 条最终诊断`
      : '至少需要一条医生最终诊断',
  },
  {
    label: '检查检验已全部审核',
    done: examinations.value.length > 0 ? !hasPendingExams.value : true,
    hint:
      examinations.value.length === 0
        ? '未开具检查检验'
        : hasPendingExams.value
          ? `${examinations.value.filter((x) => x.status !== 'REVIEWED' && x.status !== 'CANCELLED').length} 项待处理`
          : '全部已审核或已取消',
  },
  {
    label: '处方已确认或作废',
    done: !hasDraftPrescription.value,
    hint: prescription.value
      ? prescription.value.status === 'DRAFT'
        ? '存在未确认的处方草稿'
        : `处方状态：${prescription.value.status}`
      : '未开具处方',
  },
])

const allDone = computed(() => checklist.value.every((c) => c.done))

// AI 降级演示开关
const aiFailEnabled = ref(false)

function toggleAiFail(val: boolean | string | number) {
  const enabled = Boolean(val)
  aiFailEnabled.value = enabled
  if (enabled) {
    sessionStorage.setItem('cloud-brain.mock-ai-fail', '1')
    ElMessage.info('已开启 AI 降级演示：下一次 AI 调用将模拟失败')
  } else {
    sessionStorage.removeItem('cloud-brain.mock-ai-fail')
  }
}

function saveNotes() {
  // 问诊记录暂存于 store，AI 诊断与病历生成时读取
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
    console.error('[Overview] 加载检查清单失败：', e)
  } finally {
    checking.value = false
  }
}

onMounted(loadChecklist)
</script>

<template>
  <div class="overview">
    <!-- 问诊记录 -->
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
        <button class="primary-btn" @click="saveNotes">暂存问诊记录</button>
      </div>
    </div>

    <!-- 完成就诊前置条件 -->
    <div class="block">
      <div class="block-title">
        完成就诊检查清单
        <span class="block-sub">需全部满足方可完成就诊</span>
      </div>
      <div v-if="checking" class="loading-inline">
        <span class="mini-spinner" /> 正在检查…
      </div>
      <div v-else class="checklist">
        <div
          v-for="item in checklist"
          :key="item.label"
          class="check-item"
          :class="{ done: item.done }"
        >
          <span class="check-icon">{{ item.done ? '✓' : '○' }}</span>
          <div class="check-body">
            <div class="check-label">{{ item.label }}</div>
            <div class="check-hint">{{ item.hint }}</div>
          </div>
        </div>
      </div>
      <div v-if="!checking" class="check-summary" :class="{ ready: allDone }">
        <template v-if="allDone">✓ 所有条件已满足，可完成就诊</template>
        <template v-else>仍有未完成项，请在对应子页面处理后重试</template>
      </div>
    </div>

    <!-- AI 降级演示 -->
    <div class="block demo-block">
      <div class="block-title">
        AI 降级演示
        <span class="block-sub">§14：模拟 AI 服务不可用，验证降级流程</span>
      </div>
      <div class="demo-row">
        <span class="demo-label">下次 AI 调用模拟失败</span>
        <label class="switch">
          <input
            type="checkbox"
            :checked="aiFailEnabled"
            @change="toggleAiFail(($event.target as HTMLInputElement).checked)"
          />
          <span class="slider" />
        </label>
        <span class="demo-hint">
          开启后下一次 AI 诊断 / 病历生成 / 处方审核 / 检查解读 将返回失败，医生可手工继续
        </span>
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
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: opacity 0.15s;
}

.primary-btn:hover {
  opacity: 0.92;
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

.demo-block {
  background: #fffbe6;
}

.demo-row {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.demo-label {
  font-size: 14px;
  color: #1a1a1a;
  font-weight: 500;
}

.switch {
  position: relative;
  display: inline-block;
  width: 42px;
  height: 22px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background: #d9d9d9;
  border-radius: 22px;
  transition: 0.2s;
}

.slider::before {
  content: '';
  position: absolute;
  height: 16px;
  width: 16px;
  left: 3px;
  bottom: 3px;
  background: #ffffff;
  border-radius: 50%;
  transition: 0.2s;
}

.switch input:checked + .slider {
  background: #fa8c16;
}

.switch input:checked + .slider::before {
  transform: translateX(20px);
}

.demo-hint {
  font-size: 13px;
  color: #8e8e93;
  flex: 1;
  min-width: 200px;
}
</style>
