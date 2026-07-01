<script setup lang="ts">
// AI 辅助诊断
// 设计来源：product/11_功能需求.md §9、product/12_业务流程与状态机.md §7
// 规则（§9.4 诊断隔离）：
// - AI 仅产生 PRELIMINARY + AI_SUGGESTION 候选诊断，不写入正式诊断
// - 医生可采纳候选并补充最终诊断（FINAL + DOCTOR）
// - AI 失败时返回降级标记，医生可手工诊断（§9.5、§14）
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getEncounterDiagnoses,
  assistDiagnosis,
  addAIDiagnosis,
  addDoctorDiagnosis,
} from '@/api/encounter'
import { useEncounterStore } from '@/stores/encounter'
import type {
  EncounterDiagnosisResponse,
  AiCandidateDiagnosis,
  AiDiagnosisResponse,
} from '@/types/encounter'

const route = useRoute()
const encounterStore = useEncounterStore()
const { consultationNotes } = storeToRefs(encounterStore)

const encounterId = computed(() => Number(route.params.id))

const loading = ref(true)
const diagnoses = ref<EncounterDiagnosisResponse[]>([])

const aiLoading = ref(false)
const aiResult = ref<AiDiagnosisResponse | null>(null)
const aiError = ref('')

const adoptedCodes = ref<Set<string>>(new Set())

// 医生添加最终诊断表单
const doctorForm = ref({
  diagnosisCode: '',
  diagnosisName: '',
  notes: '',
})
const submitting = ref(false)

const aiCandidates = computed<AiCandidateDiagnosis[]>(
  () => aiResult.value?.candidates ?? [],
)

const aiDiagnoses = computed(() =>
  diagnoses.value.filter((d) => d.source === 'AI_SUGGESTION'),
)
const doctorDiagnoses = computed(() =>
  diagnoses.value.filter((d) => d.source === 'DOCTOR'),
)

function hasChiefComplaint(): boolean {
  return Boolean(consultationNotes.value.chiefComplaint?.trim())
}

function aiContextLength(): number {
  return [
    consultationNotes.value.chiefComplaint,
    consultationNotes.value.presentIllness,
    consultationNotes.value.pastHistory,
    consultationNotes.value.physicalExam,
  ].reduce((total, value) => total + (value?.trim().length ?? 0), 0)
}

function hasEnoughAiContext(): boolean {
  return hasChiefComplaint() && aiContextLength() >= 20
}

async function loadDiagnoses() {
  loading.value = true
  try {
    diagnoses.value = await getEncounterDiagnoses(encounterId.value)
  } catch (e) {
    console.error('[Diagnosis] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

/** 触发 AI 辅助诊断（§9） */
async function runAiAssist() {
  if (!hasChiefComplaint()) {
    ElMessage.warning('请先在「概览」页填写主诉后再进行 AI 辅助诊断')
    return
  }
  if (!hasEnoughAiContext()) {
    ElMessage.warning('问诊信息过少，请补充现病史、既往史或体格检查后再进行 AI 辅助诊断')
    return
  }
  aiLoading.value = true
  aiError.value = ''
  aiResult.value = null
  try {
    const res = await assistDiagnosis({
      encounterId: encounterId.value,
      chiefComplaint: consultationNotes.value.chiefComplaint,
      presentIllness: consultationNotes.value.presentIllness,
      pastHistory: consultationNotes.value.pastHistory,
      physicalExam: consultationNotes.value.physicalExam,
    })
    aiResult.value = res
    if (res.aiStatus === 'FAILED') {
      ElMessage.warning('AI 服务暂不可用，请进行手工诊断')
    } else if (res.candidates.length === 0) {
      ElMessage.warning('AI 未生成候选诊断，请补充现病史、体格检查或检查结果后重试')
    } else {
      ElMessage.success(`AI 返回 ${res.candidates.length} 条候选诊断`)
    }
  } catch (e) {
    aiError.value = e instanceof Error ? e.message : 'AI 诊断请求失败'
    ElMessage.error(aiError.value)
  } finally {
    aiLoading.value = false
  }
}

/** 采纳 AI 候选诊断（持久化为 PRELIMINARY + AI_SUGGESTION，§9.4） */
async function adoptCandidate(c: AiCandidateDiagnosis) {
  if (adoptedCodes.value.has(c.diagnosisCode)) {
    ElMessage.info('该候选诊断已采纳')
    return
  }
  try {
    const diag = await addAIDiagnosis(encounterId.value, {
      diagnosisCode: c.diagnosisCode,
      diagnosisName: c.diagnosisName,
      type: 'PRELIMINARY',
      source: 'AI_SUGGESTION',
      notes: `AI 置信度 ${(c.confidence * 100).toFixed(0)}%`,
    })
    diagnoses.value.push(diag)
    adoptedCodes.value.add(c.diagnosisCode)
    ElMessage.success(`已采纳候选诊断：${c.diagnosisName}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '采纳失败')
  }
}

/** 医生添加最终诊断（FINAL + DOCTOR，§9.4） */
async function submitDoctorDiagnosis() {
  if (!doctorForm.value.diagnosisName.trim()) {
    ElMessage.warning('请输入诊断名称')
    return
  }
  if (!doctorForm.value.diagnosisCode.trim()) {
    doctorForm.value.diagnosisCode = `MANUAL_${Date.now()}`
  }
  submitting.value = true
  try {
    const diag = await addDoctorDiagnosis(encounterId.value, {
      diagnosisCode: doctorForm.value.diagnosisCode,
      diagnosisName: doctorForm.value.diagnosisName,
      type: 'FINAL',
      source: 'DOCTOR',
      notes: doctorForm.value.notes || undefined,
    })
    diagnoses.value.push(diag)
    doctorForm.value = { diagnosisCode: '', diagnosisName: '', notes: '' }
    ElMessage.success('已添加医生最终诊断')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    submitting.value = false
  }
}

function fillFromCandidate(c: AiCandidateDiagnosis) {
  doctorForm.value.diagnosisCode = c.diagnosisCode
  doctorForm.value.diagnosisName = c.diagnosisName
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

onMounted(loadDiagnoses)
</script>

<template>
  <div class="diagnosis-view">
    <!-- AI 辅助诊断区 -->
    <div class="block">
      <div class="block-title">
        AI 辅助诊断
        <span class="block-sub">基于问诊记录生成候选诊断，仅供参考</span>
      </div>

      <div v-if="!hasChiefComplaint()" class="warn-inline">
        请先在「概览」页填写主诉
      </div>
      <div v-else-if="!hasEnoughAiContext()" class="warn-inline">
        当前问诊信息较少，请补充现病史、既往史或体格检查后再发起 AI 辅助诊断
      </div>

      <button
        class="primary-btn"
        :disabled="aiLoading || !hasEnoughAiContext()"
        @click="runAiAssist"
      >
        <span v-if="aiLoading" class="btn-spinner" />
        {{ aiLoading ? 'AI 分析中…' : '发起 AI 辅助诊断' }}
      </button>

      <!-- AI 加载中 -->
      <div v-if="aiLoading" class="ai-loading">
        <span class="loading-spinner" />
        <span>AI 正在分析问诊信息，请稍候…</span>
      </div>

      <!-- AI 失败 -->
      <div v-else-if="aiResult?.aiStatus === 'FAILED'" class="ai-failed">
        <div class="failed-title">AI 服务暂不可用</div>
        <div class="failed-desc">
          {{ aiResult.aiFailureReason || 'AI 诊断失败，请进行手工诊断。' }}
        </div>
        <div class="failed-hint">您可在下方「医生最终诊断」中手工录入。</div>
      </div>

      <!-- AI 候选结果 -->
      <div v-else-if="aiCandidates.length > 0" class="candidates">
        <div
          v-for="(c, idx) in aiCandidates"
          :key="idx"
          class="candidate-card"
        >
          <div class="cand-head">
            <div class="cand-name">{{ c.diagnosisName }}</div>
            <div class="cand-code">{{ c.diagnosisCode }}</div>
            <div class="cand-conf" :class="c.confidence >= 0.7 ? 'high' : c.confidence >= 0.5 ? 'mid' : 'low'">
              置信度 {{ (c.confidence * 100).toFixed(0) }}%
            </div>
          </div>
          <div class="cand-reason">{{ c.reason }}</div>
          <div v-if="c.riskFactors.length > 0" class="cand-section">
            <span class="cand-label">风险因素：</span>
            <span class="cand-tags">
              <span v-for="r in c.riskFactors" :key="r" class="tag-risk">{{ r }}</span>
            </span>
          </div>
          <div v-if="c.informationGaps.length > 0" class="cand-section">
            <span class="cand-label">信息缺口：</span>
            <span class="cand-text">{{ c.informationGaps.join('；') }}</span>
          </div>
          <div v-if="c.recommendedExaminations.length > 0" class="cand-section">
            <span class="cand-label">建议检查：</span>
            <span class="cand-tags">
              <span v-for="e in c.recommendedExaminations" :key="e" class="tag-exam">{{ e }}</span>
            </span>
          </div>
          <div class="cand-actions">
            <button
              class="ghost-btn"
              :disabled="adoptedCodes.has(c.diagnosisCode)"
              @click="adoptCandidate(c)"
            >
              {{ adoptedCodes.has(c.diagnosisCode) ? '已采纳' : '采纳为候选' }}
            </button>
            <button class="ghost-btn" @click="fillFromCandidate(c)">用作最终诊断</button>
          </div>
        </div>
      </div>

      <div v-else-if="aiResult?.aiStatus === 'SUCCESS'" class="ai-failed">
        <div class="failed-title">AI 未生成候选诊断</div>
        <div class="failed-desc">
          当前信息不足以形成候选诊断，请补充现病史、既往史、体格检查或检查结果后重试。
        </div>
        <div class="failed-hint">也可以在下方手工录入医生最终诊断。</div>
      </div>

      <!-- AI 错误 -->
      <div v-else-if="aiError" class="ai-failed">
        <div class="failed-title">AI 诊断请求失败</div>
        <div class="failed-desc">{{ aiError }}</div>
      </div>
    </div>

    <!-- 医生最终诊断录入 -->
    <div class="block">
      <div class="block-title">
        医生最终诊断
        <span class="block-sub">最终诊断须由医生确认（FINAL + DOCTOR）</span>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">诊断编码</label>
          <input
            v-model="doctorForm.diagnosisCode"
            class="form-input"
            placeholder="如 J00，可留空自动生成"
          />
        </div>
        <div class="form-group flex-2">
          <label class="form-label">诊断名称 <span class="required">*</span></label>
          <input
            v-model="doctorForm.diagnosisName"
            class="form-input"
            placeholder="如 急性鼻咽炎"
          />
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">诊断说明</label>
        <textarea
          v-model="doctorForm.notes"
          class="form-textarea"
          rows="2"
          placeholder="诊断依据、鉴别要点等"
        />
      </div>
      <div class="form-actions">
        <button
          class="primary-btn"
          :disabled="submitting"
          @click="submitDoctorDiagnosis"
        >
          <span v-if="submitting" class="btn-spinner" />
          {{ submitting ? '提交中…' : '添加最终诊断' }}
        </button>
      </div>
    </div>

    <!-- 已有诊断列表 -->
    <div class="block">
      <div class="block-title">
        诊断列表
        <span class="block-sub">AI 候选与医生最终诊断隔离展示</span>
      </div>
      <div v-if="loading" class="loading-inline">
        <span class="mini-spinner" /> 加载中…
      </div>
      <div v-else-if="diagnoses.length === 0" class="empty-inline">
        暂无诊断记录
      </div>
      <template v-else>
        <!-- 医生最终诊断 -->
        <div v-if="doctorDiagnoses.length > 0" class="diag-section">
          <div class="diag-section-title">医生最终诊断</div>
          <div
            v-for="d in doctorDiagnoses"
            :key="d.id"
            class="diag-item doctor-diag"
          >
            <div class="diag-head">
              <span class="diag-name">{{ d.diagnosisName }}</span>
              <span class="tag-final">最终</span>
            </div>
            <div class="diag-meta">
              {{ d.diagnosisCode }} · {{ formatDateTime(d.confirmedAt) }}
            </div>
            <div v-if="d.notes" class="diag-notes">{{ d.notes }}</div>
          </div>
        </div>
        <!-- AI 候选诊断 -->
        <div v-if="aiDiagnoses.length > 0" class="diag-section">
          <div class="diag-section-title">AI 候选诊断（仅供参考）</div>
          <div
            v-for="d in aiDiagnoses"
            :key="d.id"
            class="diag-item ai-diag"
          >
            <div class="diag-head">
              <span class="diag-name">{{ d.diagnosisName }}</span>
              <span class="tag-ai">AI 候选</span>
            </div>
            <div class="diag-meta">
              {{ d.diagnosisCode }} · {{ formatDateTime(d.createdAt) }}
            </div>
            <div v-if="d.notes" class="diag-notes">{{ d.notes }}</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.diagnosis-view {
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

.warn-inline {
  display: inline-block;
  font-size: 13px;
  color: #d46b08;
  background: #fff7e6;
  padding: 4px 10px;
  border-radius: 6px;
  margin-bottom: 12px;
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

.btn-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgb(255 255 255 / 40%);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
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

.ai-loading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 24px 0;
  font-size: 14px;
  color: #8e8e93;
}

.ai-failed {
  background: #fff1f0;
  border: 1px solid #ffa39e;
  border-radius: 10px;
  padding: 14px 16px;
  margin-top: 14px;
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

.candidates {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 14px;
}

.candidate-card {
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  padding: 14px 16px;
}

.cand-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.cand-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.cand-code {
  font-size: 12px;
  color: #8e8e93;
  background: #f0f0f0;
  padding: 1px 8px;
  border-radius: 4px;
}

.cand-conf {
  font-size: 13px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
}

.cand-conf.high {
  background: #f0fff4;
  color: #67c23a;
}

.cand-conf.mid {
  background: #fff7e6;
  color: #fa8c16;
}

.cand-conf.low {
  background: #fff1f0;
  color: #f56c6c;
}

.cand-reason {
  font-size: 14px;
  color: #595959;
  line-height: 1.5;
  margin-bottom: 8px;
}

.cand-section {
  font-size: 13px;
  margin-bottom: 6px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  flex-wrap: wrap;
}

.cand-label {
  color: #8e8e93;
  flex-shrink: 0;
}

.cand-text {
  color: #595959;
}

.cand-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag-risk {
  background: #fff1f0;
  color: #f56c6c;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.tag-exam {
  background: #e6f7ff;
  color: #1890ff;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.cand-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.ghost-btn {
  padding: 5px 14px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #4a5568;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.ghost-btn:hover:not(:disabled) {
  border-color: #4facfe;
  color: #4facfe;
}

.ghost-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.form-row {
  display: flex;
  gap: 14px;
  margin-bottom: 14px;
}

.form-group {
  flex: 1;
}

.form-group.flex-2 {
  flex: 2;
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

.empty-inline {
  font-size: 14px;
  color: #8e8e93;
  padding: 16px 0;
  text-align: center;
}

.diag-section {
  margin-bottom: 16px;
}

.diag-section:last-child {
  margin-bottom: 0;
}

.diag-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0f0f0;
}

.diag-item {
  background: #ffffff;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 8px;
  border: 1px solid #f0f0f0;
}

.doctor-diag {
  border-left: 3px solid #67c23a;
}

.ai-diag {
  border-left: 3px solid #9b59b6;
  background: #faf5ff;
}

.diag-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.diag-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.tag-final {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #f0fff4;
  color: #67c23a;
  font-weight: 500;
}

.tag-ai {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #f9f0ff;
  color: #9b59b6;
  font-weight: 500;
}

.diag-meta {
  font-size: 13px;
  color: #8e8e93;
}

.diag-notes {
  font-size: 13px;
  color: #595959;
  margin-top: 4px;
  line-height: 1.5;
}
</style>
