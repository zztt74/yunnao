<script setup lang="ts">
// 病历生成与编辑
// 设计来源：product/11_功能需求.md §11、product/12_业务流程与状态机.md §8
// 规则（§11.6）：
// - AI 只能生成草稿（AI_GENERATED），不写入正式病历
// - 正式病历必须由医生确认（DRAFT/AI_GENERATED → CONFIRMED）
// - 病历不可物理删除，仅可修改/确认
// - AI 失败时返回降级标记，医生可手工填写（§11.5、§14）
import { ref, computed, onMounted, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getEncounterMedicalRecord,
  generateMedicalRecordDraft,
  saveMedicalRecord,
  confirmMedicalRecord,
} from '@/api/medical-record'
import { useEncounterStore } from '@/stores/encounter'
import type { MedicalRecord, MedicalRecordStatus } from '@/types/medical-record'

const route = useRoute()
const encounterStore = useEncounterStore()
const { activeEncounter, consultationNotes, consultationDialogue } = storeToRefs(encounterStore)

const encounterId = computed(() => Number(route.params.id))

const loading = ref(true)
const aiGenerating = ref(false)
const saving = ref(false)
const confirming = ref(false)

const medicalRecord = ref<MedicalRecord | null>(null)
const aiFailReason = ref('')

// 可编辑表单
const form = ref({
  chiefComplaint: '',
  presentIllness: '',
  pastHistory: '',
  physicalExam: '',
  preliminaryDiagnosis: '',
  treatmentAdvice: '',
})

const status = computed<MedicalRecordStatus | null>(() => medicalRecord.value?.status ?? null)
const isConfirmed = computed(() => status.value === 'CONFIRMED')
const isReadOnly = computed(() => isConfirmed.value)
const hasRecord = computed(() => medicalRecord.value !== null)

function statusText(s: MedicalRecordStatus | null): string {
  switch (s) {
    case 'DRAFT':
      return '草稿'
    case 'AI_GENERATED':
      return 'AI 草稿'
    case 'CONFIRMED':
      return '已确认'
    case 'AMENDED':
      return '已修订'
    default:
      return '未生成'
  }
}

function statusClass(s: MedicalRecordStatus | null): string {
  switch (s) {
    case 'DRAFT':
      return 'tag-draft'
    case 'AI_GENERATED':
      return 'tag-ai'
    case 'CONFIRMED':
      return 'tag-confirmed'
    case 'AMENDED':
      return 'tag-amended'
    default:
      return 'tag-none'
  }
}

function syncFormFromRecord() {
  if (medicalRecord.value) {
    form.value = {
      chiefComplaint: medicalRecord.value.chiefComplaint || '',
      presentIllness: medicalRecord.value.presentIllness || '',
      pastHistory: medicalRecord.value.pastHistory || '',
      physicalExam: medicalRecord.value.physicalExam || '',
      preliminaryDiagnosis: medicalRecord.value.preliminaryDiagnosis || '',
      treatmentAdvice: medicalRecord.value.treatmentAdvice || '',
    }
  }
}

function syncFormFromNotes() {
  // 从问诊记录回填（AI 生成或手工参考）
  form.value.chiefComplaint = consultationNotes.value.chiefComplaint || form.value.chiefComplaint
  form.value.presentIllness = consultationNotes.value.presentIllness || form.value.presentIllness
  form.value.pastHistory = consultationNotes.value.pastHistory || form.value.pastHistory
  form.value.physicalExam = consultationNotes.value.physicalExam || form.value.physicalExam
}

async function loadRecord() {
  loading.value = true
  try {
    const mr = await getEncounterMedicalRecord(encounterId.value)
    medicalRecord.value = mr
    if (mr) syncFormFromRecord()
  } catch (e) {
    console.error('[MedicalRecord] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

/** AI 生成病历草稿（§11.4） */
async function handleAiGenerate() {
  if (!consultationNotes.value.chiefComplaint?.trim()) {
    ElMessage.warning('请先在「概览」页填写主诉后再生成病历')
    return
  }
  aiGenerating.value = true
  aiFailReason.value = ''
  try {
    const res = await generateMedicalRecordDraft({
      encounterId: encounterId.value,
      chiefComplaint: consultationNotes.value.chiefComplaint,
      presentIllness: consultationNotes.value.presentIllness,
      pastHistory: consultationNotes.value.pastHistory,
      physicalExam: consultationNotes.value.physicalExam,
      consultationTranscript: consultationDialogue.value?.trim() || undefined,
    })
    if (res.aiStatus === 'FAILED') {
      aiFailReason.value = res.aiFailureReason || 'AI 病历生成失败'
      ElMessage.warning('AI 生成失败，请手工填写病历')
      // 仍回填主诉等基础信息便于手工编辑
      syncFormFromNotes()
    } else {
      form.value = {
        chiefComplaint: res.chiefComplaint,
        presentIllness: res.presentIllness,
        pastHistory: res.pastHistory,
        physicalExam: res.physicalExam,
        preliminaryDiagnosis: res.preliminaryDiagnosis,
        treatmentAdvice: res.treatmentAdvice,
      }
      ElMessage.success('AI 已生成病历草稿，请核对后确认')
    }
  } catch (e) {
    // 失败时保留医生已输入的内容，不丢失
    console.error('[MedicalRecord] AI 生成失败：', e)
    ElMessage.error(e instanceof Error ? e.message : 'AI 生成请求失败')
  } finally {
    aiGenerating.value = false
  }
}

/** 保存为草稿（DRAFT / AI_GENERATED） */
async function handleSaveDraft() {
  if (!form.value.chiefComplaint.trim()) {
    ElMessage.warning('主诉不能为空')
    return
  }
  if (!activeEncounter.value) {
    ElMessage.error('就诊信息丢失，请返回工作台')
    return
  }
  saving.value = true
  try {
    const mr = await saveMedicalRecord(activeEncounter.value, {
      encounterId: encounterId.value,
      chiefComplaint: form.value.chiefComplaint,
      presentIllness: form.value.presentIllness,
      pastHistory: form.value.pastHistory,
      physicalExam: form.value.physicalExam,
      preliminaryDiagnosis: form.value.preliminaryDiagnosis,
      treatmentAdvice: form.value.treatmentAdvice,
      status: medicalRecord.value?.status === 'AI_GENERATED' ? 'AI_GENERATED' : 'DRAFT',
    })
    medicalRecord.value = mr
    ElMessage.success('病历草稿已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

/** 确认正式病历（DRAFT/AI_GENERATED → CONFIRMED，§11.6） */
async function handleConfirm() {
  if (!form.value.chiefComplaint.trim()) {
    ElMessage.warning('主诉不能为空')
    return
  }
  if (!form.value.preliminaryDiagnosis.trim()) {
    ElMessage.warning('初步诊断不能为空')
    return
  }
  try {
    await ElMessageBox.confirm(
      '确认后病历将作为正式病历归档，患者可见。确认后仍可修订但不可删除。是否继续？',
      '确认正式病历',
      { confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  if (!activeEncounter.value) {
    ElMessage.error('就诊信息丢失，请返回工作台')
    return
  }
  confirming.value = true
  try {
    const mr = await confirmMedicalRecord(activeEncounter.value, {
      encounterId: encounterId.value,
      chiefComplaint: form.value.chiefComplaint,
      presentIllness: form.value.presentIllness,
      pastHistory: form.value.pastHistory,
      physicalExam: form.value.physicalExam,
      preliminaryDiagnosis: form.value.preliminaryDiagnosis,
      treatmentAdvice: form.value.treatmentAdvice,
      status: 'CONFIRMED',
    })
    medicalRecord.value = mr
    ElMessage.success('病历已确认归档')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '确认失败')
  } finally {
    confirming.value = false
  }
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

onMounted(loadRecord)

// 当 encounterStore 中的就诊变化时重新加载
watch(activeEncounter, (enc) => {
  if (enc && enc.id !== medicalRecord.value?.encounterId) {
    loadRecord()
  }
})
</script>

<template>
  <div class="mr-view">
    <!-- 头部状态 -->
    <div class="mr-header">
      <div class="header-left">
        <span class="mr-label">病历状态</span>
        <span class="status-tag" :class="statusClass(status)">
          {{ statusText(status) }}
        </span>
        <span v-if="medicalRecord?.confirmedAt" class="mr-time">
          确认于 {{ formatDateTime(medicalRecord.confirmedAt) }}
        </span>
      </div>
      <div class="header-right">
        <button
          v-if="!isConfirmed"
          class="ghost-btn"
          :disabled="aiGenerating"
          @click="handleAiGenerate"
        >
          <span v-if="aiGenerating" class="btn-spinner small" />
          {{ aiGenerating ? 'AI 生成中…' : (hasRecord ? 'AI 重新生成' : 'AI 生成草稿') }}
        </button>
      </div>
    </div>

    <!-- AI 失败提示 -->
    <div v-if="aiFailReason" class="ai-failed-banner">
      <div class="failed-title">AI 病历生成失败</div>
      <div class="failed-desc">{{ aiFailReason }}</div>
      <div class="failed-hint">请手工填写下方病历内容，主诉等已从问诊记录回填。</div>
    </div>

    <div v-if="loading" class="loading-inline">
      <span class="mini-spinner" /> 加载中…
    </div>

    <template v-else>
      <!-- F3: 问诊对话记录（F3 课程要求 - 作为 AI 病历生成上下文） -->
      <div class="block dialogue-block">
        <div class="block-title">
          💬 问诊对话记录
          <span class="block-hint">F3：录入医患对话原文，AI 生成病历时作为上下文</span>
        </div>
        <textarea
          v-model="consultationDialogue"
          class="form-textarea dialogue-textarea"
          rows="5"
          :disabled="isReadOnly"
          placeholder="可粘贴或手动录入医患对话原文，例如：&#10;医生：您好，请问哪里不舒服？&#10;患者：我最近三天一直发烧，38度左右，还有点咳嗽。&#10;医生：有痰吗？什么颜色？&#10;……"
        />
        <div class="dialogue-tip">
          留空时，AI 将仅基于主诉/现病史/既往史/体格检查等结构化字段生成病历。
        </div>
      </div>

      <!-- 病历表单 -->
      <div class="block" :class="{ readonly: isReadOnly }">
        <div class="form-group">
          <label class="form-label">主诉 <span class="required">*</span></label>
          <textarea
            v-model="form.chiefComplaint"
            class="form-textarea"
            rows="2"
            :disabled="isReadOnly"
            placeholder="例如：发热伴咳嗽 3 天"
          />
        </div>
        <div class="form-group">
          <label class="form-label">现病史 <span class="required">*</span></label>
          <textarea
            v-model="form.presentIllness"
            class="form-textarea"
            rows="4"
            :disabled="isReadOnly"
            placeholder="起病情况、症状演变、伴随症状、诊治经过等"
          />
        </div>
        <div class="form-row">
          <div class="form-group half">
            <label class="form-label">既往史</label>
            <textarea
              v-model="form.pastHistory"
              class="form-textarea"
              rows="3"
              :disabled="isReadOnly"
              placeholder="慢性病、手术史、过敏史等"
            />
          </div>
          <div class="form-group half">
            <label class="form-label">体格检查</label>
            <textarea
              v-model="form.physicalExam"
              class="form-textarea"
              rows="3"
              :disabled="isReadOnly"
              placeholder="生命体征、阳性体征等"
            />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">初步诊断 <span class="required">*</span></label>
          <input
            v-model="form.preliminaryDiagnosis"
            class="form-input"
            :disabled="isReadOnly"
            placeholder="如 急性上呼吸道感染"
          />
        </div>
        <div class="form-group">
          <label class="form-label">治疗建议</label>
          <textarea
            v-model="form.treatmentAdvice"
            class="form-textarea"
            rows="3"
            :disabled="isReadOnly"
            placeholder="处理意见、用药建议、复诊安排等"
          />
        </div>
      </div>

      <!-- 操作按钮 -->
      <div v-if="!isReadOnly" class="action-bar">
        <button
          class="ghost-btn"
          :disabled="saving || confirming"
          @click="handleSaveDraft"
        >
          <span v-if="saving" class="btn-spinner small" />
          {{ saving ? '保存中…' : '保存草稿' }}
        </button>
        <button
          class="primary-btn"
          :disabled="saving || confirming"
          @click="handleConfirm"
        >
          <span v-if="confirming" class="btn-spinner" />
          {{ confirming ? '确认中…' : '确认正式病历' }}
        </button>
      </div>

      <!-- 已确认提示 -->
      <div v-else class="confirmed-tip">
        ✓ 病历已确认归档，患者可见。完成就诊前可继续修订。
      </div>
    </template>
  </div>
</template>

<style scoped>
.mr-view {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mr-header {
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

.mr-label {
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

.tag-ai {
  background: #f9f0ff;
  color: #9b59b6;
}

.tag-confirmed {
  background: #f0fff4;
  color: #67c23a;
}

.tag-amended {
  background: #fff7e6;
  color: #fa8c16;
}

.mr-time {
  font-size: 13px;
  color: #8e8e93;
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
  border-color: #9b59b6;
  color: #9b59b6;
}

.ghost-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.primary-btn {
  padding: 8px 20px;
  background: linear-gradient(135deg, #67c23a 0%, #4facfe 100%);
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

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.ai-failed-banner {
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 10px;
  padding: 14px 16px;
}

.failed-title {
  font-size: 15px;
  font-weight: 600;
  color: #f56c6c;
  margin-bottom: 6px;
}

.failed-desc {
  font-size: 14px;
  color: #595959;
  margin-bottom: 4px;
}

.failed-hint {
  font-size: 13px;
  color: #8e8e93;
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

.block {
  background: #fafbfc;
  border-radius: 12px;
  padding: 16px 18px;
}

.block.readonly {
  background: #f5f5f5;
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

.form-input {
  width: 100%;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 14px;
  color: #1a1a1a;
  background: #ffffff;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #4facfe;
  box-shadow: 0 0 0 2px rgb(79 172 254 / 12%);
}

.form-input:disabled {
  background: #f5f5f5;
  color: #8e8e93;
  cursor: not-allowed;
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

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirmed-tip {
  background: #f0fff4;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 14px;
  color: #67c23a;
}

/* F3: 问诊对话记录 */
.dialogue-block {
  background: linear-gradient(135deg, #f0f9ff 0%, #faf5ff 100%);
  border: 1px dashed #b5d6ff;
}

.dialogue-block .block-title {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 10px;
}

.block-hint {
  font-size: 12px;
  font-weight: 400;
  color: #8e8e93;
}

.dialogue-textarea {
  min-height: 110px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  line-height: 1.6;
  white-space: pre-wrap;
}

.dialogue-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #8e8e93;
  line-height: 1.5;
}
</style>
