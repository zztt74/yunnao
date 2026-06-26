<script setup lang="ts">
// 检查检验开立
// 设计来源：product/11_功能需求.md §10、product/12_业务流程与状态机.md §10
// 功能：
// - 开立检查/检验申请（ORDERED）
// - 模拟结果录入（演示，§10.4）
// - 医生审核结果（RESULT_ENTERED → REVIEWED，§10 状态机）
// - AI 解读（仅 REVIEWED 后，不修改原始结果，§10.3）；AI 失败时原始结果仍可查看（§10.7）
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getEncounterExaminations,
  createExamination,
  simulateEnterResult,
  reviewExamination,
  aiInterpretExamination,
} from '@/api/examination'
import {
  getEncounterDeviceUsages,
  getAvailableDevices,
  createDeviceUsage,
  endDeviceUsage,
} from '@/api/device'
import { useEncounterStore } from '@/stores/encounter'
import type {
  ExaminationResponse,
  ExaminationType,
  LabItem,
} from '@/types/examination'
import type {
  DeviceUsageResponse,
  DeviceResponse,
} from '@/types/device'

const route = useRoute()
const encounterStore = useEncounterStore()
const { activeEncounter } = storeToRefs(encounterStore)

const encounterId = computed(() => Number(route.params.id))

const loading = ref(true)
const exams = ref<ExaminationResponse[]>([])

// 新建表单
const form = ref({
  type: 'LABORATORY' as ExaminationType,
  itemName: '',
  purpose: '',
})
const creating = ref(false)

// 操作中状态
const actingIds = ref<Set<number>>(new Set())

function isActing(id: number): boolean {
  return actingIds.value.has(id)
}

function setActing(id: number, on: boolean) {
  if (on) actingIds.value.add(id)
  else actingIds.value.delete(id)
}

async function loadExams() {
  loading.value = true
  try {
    exams.value = await getEncounterExaminations(encounterId.value)
  } catch (e) {
    console.error('[Examinations] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

/** 开立检查检验 */
async function handleCreate() {
  if (!form.value.itemName.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  if (!activeEncounter.value) {
    ElMessage.error('就诊信息丢失，请返回工作台')
    return
  }
  creating.value = true
  try {
    const exam = await createExamination(activeEncounter.value, {
      encounterId: encounterId.value,
      type: form.value.type,
      itemName: form.value.itemName,
      purpose: form.value.purpose || undefined,
    })
    exams.value.push(exam)
    form.value = { type: 'LABORATORY', itemName: '', purpose: '' }
    ElMessage.success('已开立检查检验申请')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '开立失败')
  } finally {
    creating.value = false
  }
}

/** 模拟结果录入（演示，§10.4） */
async function handleSimulateResult(exam: ExaminationResponse) {
  setActing(exam.id, true)
  try {
    const updated = await simulateEnterResult(exam.id)
    const idx = exams.value.findIndex((x) => x.id === exam.id)
    if (idx >= 0) exams.value[idx] = updated
    ElMessage.success('结果已录入（模拟）')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '录入失败')
  } finally {
    setActing(exam.id, false)
  }
}

/** 医生审核结果（RESULT_ENTERED → REVIEWED） */
async function handleReview(exam: ExaminationResponse) {
  setActing(exam.id, true)
  try {
    const updated = await reviewExamination(exam.id)
    const idx = exams.value.findIndex((x) => x.id === exam.id)
    if (idx >= 0) exams.value[idx] = updated
    ElMessage.success('结果已审核')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '审核失败')
  } finally {
    setActing(exam.id, false)
  }
}

/** AI 解读（仅 REVIEWED，§10.3） */
async function handleAiInterpret(exam: ExaminationResponse) {
  setActing(exam.id, true)
  try {
    const updated = await aiInterpretExamination(exam.id)
    const idx = exams.value.findIndex((x) => x.id === exam.id)
    if (idx >= 0) exams.value[idx] = updated
    ElMessage.success('AI 解读完成')
  } catch (e) {
    // AI 解读失败：原始结果仍可查看（§10.7）
    ElMessage.warning(e instanceof Error ? e.message : 'AI 解读失败，原始结果仍可查看')
  } finally {
    setActing(exam.id, false)
  }
}

function statusText(status: string): string {
  switch (status) {
    case 'ORDERED':
      return '已申请'
    case 'IN_PROGRESS':
      return '进行中'
    case 'RESULT_ENTERED':
      return '待审核'
    case 'REVIEWED':
      return '已审核'
    case 'CANCELLED':
      return '已取消'
    default:
      return status
  }
}

function statusClass(status: string): string {
  switch (status) {
    case 'ORDERED':
      return 'tag-ordered'
    case 'IN_PROGRESS':
      return 'tag-in-progress'
    case 'RESULT_ENTERED':
      return 'tag-pending-review'
    case 'REVIEWED':
      return 'tag-reviewed'
    case 'CANCELLED':
      return 'tag-cancelled'
    default:
      return ''
  }
}

function typeText(type: string): string {
  return type === 'LABORATORY' ? '检验' : '检查'
}

function abnormalText(flag: string): string {
  switch (flag) {
    case 'HIGH':
      return '↑ 偏高'
    case 'LOW':
      return '↓ 偏低'
    default:
      return '正常'
  }
}

function abnormalClass(flag: string): string {
  if (flag === 'HIGH' || flag === 'LOW') return 'abnormal'
  return 'normal'
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

// ===== 异常指标汇总（§10.3：标记异常指标，数值与参考范围对比）=====

/** 统计某检验的异常指标项 */
function abnormalItems(exam: ExaminationResponse): LabItem[] {
  return (exam.labItems || []).filter((it) => it.abnormalFlag !== 'NORMAL')
}

// ===== 设备使用记录（§13：开立检查关联设备）=====

const deviceUsages = ref<DeviceUsageResponse[]>([])
const showDevicePicker = ref(false)
const availableDevices = ref<DeviceResponse[]>([])
const loadingDevices = ref(false)
const creatingDeviceId = ref<number | null>(null)
const endingUsageId = ref<number | null>(null)
const endForm = ref<{ result: string; deviceEndStatus: 'AVAILABLE' | 'MAINTENANCE' }>({
  result: '',
  deviceEndStatus: 'AVAILABLE',
})

async function loadDeviceUsages() {
  try {
    deviceUsages.value = await getEncounterDeviceUsages(encounterId.value)
  } catch (e) {
    console.error('[DeviceUsages] 加载失败：', e)
  }
}

/** 打开设备选择区，加载可用设备 */
async function openDevicePicker() {
  showDevicePicker.value = !showDevicePicker.value
  if (showDevicePicker.value && availableDevices.value.length === 0) {
    loadingDevices.value = true
    try {
      availableDevices.value = await getAvailableDevices()
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : '加载可用设备失败')
    } finally {
      loadingDevices.value = false
    }
  }
}

/** 创建设备使用记录（§13.3、§13.5） */
async function handleCreateDeviceUsage(device: DeviceResponse) {
  if (!activeEncounter.value) {
    ElMessage.error('就诊信息丢失，请返回工作台')
    return
  }
  creatingDeviceId.value = device.id
  try {
    const usage = await createDeviceUsage({
      deviceId: device.id,
      encounterId: encounterId.value,
      patientId: activeEncounter.value.patientId,
      patientName: activeEncounter.value.patientName,
      purpose: `就诊 ${encounterId.value} 设备使用`,
    })
    deviceUsages.value.unshift(usage)
    // 设备已占用，从可用列表移除
    availableDevices.value = availableDevices.value.filter((d) => d.id !== device.id)
    ElMessage.success(`已创建设备使用记录：${device.name}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建设备使用记录失败')
  } finally {
    creatingDeviceId.value = null
  }
}

/** 显示结束使用表单 */
function startEndUsage(usage: DeviceUsageResponse) {
  endingUsageId.value = usage.id
  endForm.value = { result: '', deviceEndStatus: 'AVAILABLE' }
}

/** 取消结束 */
function cancelEndUsage() {
  endingUsageId.value = null
}

/** 提交结束使用（§13.4：设备恢复空闲或转故障） */
async function handleEndUsage(usage: DeviceUsageResponse) {
  try {
    const updated = await endDeviceUsage(usage.id, {
      result: endForm.value.result || undefined,
      deviceEndStatus: endForm.value.deviceEndStatus,
    })
    const idx = deviceUsages.value.findIndex((u) => u.id === usage.id)
    if (idx >= 0) deviceUsages.value[idx] = updated
    endingUsageId.value = null
    ElMessage.success('设备使用已结束')
    // 刷新可用设备列表（设备恢复后可能重新可用）
    if (endForm.value.deviceEndStatus === 'AVAILABLE') {
      availableDevices.value = await getAvailableDevices()
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '结束设备使用失败')
  }
}

function usageStatusText(status: string): string {
  switch (status) {
    case 'IN_USE':
      return '使用中'
    case 'COMPLETED':
      return '已完成'
    case 'ABNORMAL':
      return '异常结束'
    default:
      return status
  }
}

function usageStatusClass(status: string): string {
  switch (status) {
    case 'IN_USE':
      return 'usage-active'
    case 'COMPLETED':
      return 'usage-done'
    case 'ABNORMAL':
      return 'usage-abnormal'
    default:
      return ''
  }
}

onMounted(() => {
  loadExams()
  loadDeviceUsages()
})
</script>

<template>
  <div class="exam-view">
    <!-- 开立检查检验 -->
    <div class="block">
      <div class="block-title">
        开立检查检验
        <span class="block-sub">申请后状态为「已申请」，等待结果录入</span>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">类型</label>
          <select v-model="form.type" class="form-select">
            <option value="LABORATORY">检验</option>
            <option value="EXAMINATION">检查</option>
          </select>
        </div>
        <div class="form-group flex-2">
          <label class="form-label">项目名称 <span class="required">*</span></label>
          <input
            v-model="form.itemName"
            class="form-input"
            placeholder="如 血常规 / 胸部 X 光"
          />
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">申请目的</label>
        <textarea
          v-model="form.purpose"
          class="form-textarea"
          rows="2"
          placeholder="如 排查感染、明确诊断等"
        />
      </div>
      <div class="form-actions">
        <button
          class="primary-btn"
          :disabled="creating"
          @click="handleCreate"
        >
          <span v-if="creating" class="btn-spinner" />
          {{ creating ? '开立中…' : '开立检查检验' }}
        </button>
      </div>
    </div>

    <!-- 检查检验列表 -->
    <div class="block">
      <div class="block-title">
        检查检验列表
        <span class="block-sub">共 {{ exams.length }} 项</span>
      </div>

      <div v-if="loading" class="loading-inline">
        <span class="mini-spinner" /> 加载中…
      </div>
      <div v-else-if="exams.length === 0" class="empty-inline">
        暂未开立检查检验
      </div>
      <div v-else class="exam-list">
        <div
          v-for="exam in exams"
          :key="exam.id"
          class="exam-card"
        >
          <div class="exam-head">
            <div class="exam-title-row">
              <span class="exam-type">{{ typeText(exam.type) }}</span>
              <span class="exam-name">{{ exam.itemName }}</span>
              <span class="status-tag" :class="statusClass(exam.status)">
                {{ statusText(exam.status) }}
              </span>
            </div>
            <div class="exam-meta">
              <span>申请 {{ formatDateTime(exam.orderedAt) }}</span>
              <span v-if="exam.reportedAt">报告 {{ formatDateTime(exam.reportedAt) }}</span>
              <span v-if="exam.reviewedAt">审核 {{ formatDateTime(exam.reviewedAt) }}</span>
            </div>
            <div v-if="exam.purpose" class="exam-purpose">目的：{{ exam.purpose }}</div>
          </div>

          <!-- 异常指标汇总（§10.3：标记异常指标，汇总提示） -->
          <div v-if="abnormalItems(exam).length > 0" class="abnormal-summary">
            <span class="abnormal-summary-icon">!</span>
            <span class="abnormal-summary-text">
              检出 {{ abnormalItems(exam).length }} 项异常指标：
              {{ abnormalItems(exam).map((i) => i.itemName).join('、') }}
            </span>
          </div>

          <!-- 检验指标 -->
          <div v-if="exam.labItems && exam.labItems.length > 0" class="lab-table">
            <div class="lab-head-row">
              <span class="col-name">项目</span>
              <span class="col-value">结果</span>
              <span class="col-ref">参考范围</span>
              <span class="col-flag">提示</span>
            </div>
            <div
              v-for="item in exam.labItems"
              :key="item.id"
              class="lab-row"
              :class="abnormalClass(item.abnormalFlag)"
            >
              <span class="col-name">{{ item.itemName }}</span>
              <span class="col-value">{{ item.resultValue }} {{ item.unit }}</span>
              <span class="col-ref">{{ item.referenceRange }}</span>
              <span class="col-flag">{{ abnormalText(item.abnormalFlag) }}</span>
            </div>
          </div>

          <!-- 检查所见 -->
          <div v-if="exam.findings" class="exam-section">
            <div class="section-label">检查所见</div>
            <div class="section-text">{{ exam.findings }}</div>
          </div>

          <!-- 印象/结论 -->
          <div v-if="exam.impression" class="exam-section">
            <div class="section-label">印象/结论</div>
            <div class="section-text">{{ exam.impression }}</div>
          </div>

          <!-- AI 解读 -->
          <div v-if="exam.aiInterpretation" class="exam-section ai-section">
            <div class="section-label">AI 解读（仅供参考）</div>
            <div class="section-text">{{ exam.aiInterpretation }}</div>
          </div>

          <!-- 操作按钮 -->
          <div class="exam-actions">
            <!-- ORDERED/IN_PROGRESS：模拟录入结果（演示） -->
            <button
              v-if="exam.status === 'ORDERED' || exam.status === 'IN_PROGRESS'"
              class="ghost-btn"
              :disabled="isActing(exam.id)"
              @click="handleSimulateResult(exam)"
            >
              <span v-if="isActing(exam.id)" class="btn-spinner small" />
              {{ isActing(exam.id) ? '录入中…' : '模拟录入结果' }}
            </button>
            <!-- RESULT_ENTERED：审核 -->
            <button
              v-if="exam.status === 'RESULT_ENTERED'"
              class="primary-btn sm"
              :disabled="isActing(exam.id)"
              @click="handleReview(exam)"
            >
              <span v-if="isActing(exam.id)" class="btn-spinner" />
              {{ isActing(exam.id) ? '审核中…' : '审核结果' }}
            </button>
            <!-- REVIEWED：AI 解读 -->
            <button
              v-if="exam.status === 'REVIEWED' && !exam.aiInterpretation"
              class="ghost-btn"
              :disabled="isActing(exam.id)"
              @click="handleAiInterpret(exam)"
            >
              <span v-if="isActing(exam.id)" class="btn-spinner small" />
              {{ isActing(exam.id) ? 'AI 解读中…' : 'AI 解读' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 设备使用记录（§13：开立检查关联设备） -->
    <div class="block">
      <div class="block-title">
        设备使用记录
        <span class="block-sub">关联就诊的设备使用（§13.6）</span>
        <button class="link-btn" @click="openDevicePicker">
          {{ showDevicePicker ? '收起' : '+ 关联设备' }}
        </button>
      </div>

      <!-- 可用设备选择 -->
      <div v-if="showDevicePicker" class="device-picker">
        <div v-if="loadingDevices" class="loading-inline">
          <span class="mini-spinner" /> 加载可用设备…
        </div>
        <div v-else-if="availableDevices.length === 0" class="empty-inline">
          暂无可用设备
        </div>
        <div v-else class="device-list">
          <div
            v-for="device in availableDevices"
            :key="device.id"
            class="device-card"
          >
            <div class="device-head">
              <span class="device-name">{{ device.name }}</span>
              <span class="device-code">{{ device.code }}</span>
            </div>
            <div class="device-meta">
              <span>{{ device.location }}</span>
              <span v-if="device.applicableItems.length">
                适配：{{ device.applicableItems.join('、') }}
              </span>
            </div>
            <button
              class="primary-btn sm"
              :disabled="creatingDeviceId === device.id"
              @click="handleCreateDeviceUsage(device)"
            >
              <span v-if="creatingDeviceId === device.id" class="btn-spinner" />
              {{ creatingDeviceId === device.id ? '关联中…' : '使用' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 使用记录列表 -->
      <div v-if="deviceUsages.length === 0" class="empty-inline">
        暂无设备使用记录
      </div>
      <div v-else class="usage-list">
        <div
          v-for="usage in deviceUsages"
          :key="usage.id"
          class="usage-card"
          :class="usageStatusClass(usage.status)"
        >
          <div class="usage-head">
            <span class="usage-device">{{ usage.deviceName }}</span>
            <span class="usage-status" :class="usageStatusClass(usage.status)">
              {{ usageStatusText(usage.status) }}
            </span>
          </div>
          <div class="usage-meta">
            <span>编码 {{ usage.deviceCode }}</span>
            <span>开始 {{ formatDateTime(usage.startedAt) }}</span>
            <span v-if="usage.endedAt">结束 {{ formatDateTime(usage.endedAt) }}</span>
          </div>
          <div v-if="usage.purpose" class="usage-purpose">用途：{{ usage.purpose }}</div>
          <div v-if="usage.result" class="usage-result">结果：{{ usage.result }}</div>

          <!-- 结束使用表单（§13.4） -->
          <div v-if="endingUsageId === usage.id" class="end-form">
            <div class="form-group">
              <label class="form-label">使用结果</label>
              <textarea
                v-model="endForm.result"
                class="form-textarea"
                rows="2"
                placeholder="记录设备使用结果"
              />
            </div>
            <div class="form-group">
              <label class="form-label">设备结束后状态</label>
              <select v-model="endForm.deviceEndStatus" class="form-select">
                <option value="AVAILABLE">恢复正常（空闲）</option>
                <option value="MAINTENANCE">转入维修（故障）</option>
              </select>
            </div>
            <div class="form-actions">
              <button class="ghost-btn" @click="cancelEndUsage">取消</button>
              <button class="primary-btn sm" @click="handleEndUsage(usage)">
                确认结束
              </button>
            </div>
          </div>

          <!-- 结束使用按钮 -->
          <div
            v-if="usage.status === 'IN_USE' && endingUsageId !== usage.id"
            class="exam-actions"
          >
            <button class="primary-btn sm" @click="startEndUsage(usage)">
              结束使用
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.exam-view {
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

.form-select {
  cursor: pointer;
}

.form-input:focus,
.form-select:focus {
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

.primary-btn.sm {
  padding: 6px 16px;
  font-size: 13px;
}

.ghost-btn {
  padding: 6px 16px;
  background: #ffffff;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #4a5568;
  font-size: 13px;
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

.ghost-btn:disabled {
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
  border-width: 2px;
  border-color: rgb(74 85 104 / 30%);
  border-top-color: #4a5568;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
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

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.exam-card {
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  padding: 14px 16px;
}

.exam-head {
  margin-bottom: 10px;
}

.exam-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.exam-type {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #f0f0f0;
  color: #8e8e93;
  font-weight: 500;
}

.exam-name {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.status-tag {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.tag-ordered {
  background: #e6f7ff;
  color: #1890ff;
}

.tag-in-progress {
  background: #fff7e6;
  color: #fa8c16;
}

.tag-pending-review {
  background: #fff7e6;
  color: #d46b08;
}

.tag-reviewed {
  background: #f0fff4;
  color: #67c23a;
}

.tag-cancelled {
  background: #f5f5f5;
  color: #8e8e93;
}

.exam-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: #8e8e93;
}

.exam-purpose {
  font-size: 13px;
  color: #8e8e93;
  margin-top: 4px;
}

.lab-table {
  margin: 10px 0;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.lab-head-row,
.lab-row {
  display: grid;
  grid-template-columns: 2fr 1.5fr 1.5fr 1fr;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
}

.lab-head-row {
  background: #fafbfc;
  color: #8e8e93;
  font-weight: 500;
}

.lab-row {
  border-top: 1px solid #f0f0f0;
  color: #1a1a1a;
}

.lab-row.abnormal .col-value,
.lab-row.abnormal .col-flag {
  color: #f56c6c;
  font-weight: 500;
}

.lab-row.normal .col-flag {
  color: #67c23a;
}

.col-flag {
  text-align: center;
}

.exam-section {
  margin-top: 10px;
  padding: 10px 12px;
  background: #fafbfc;
  border-radius: 8px;
}

.ai-section {
  background: #f9f0ff;
}

.section-label {
  font-size: 13px;
  font-weight: 500;
  color: #8e8e93;
  margin-bottom: 4px;
}

.ai-section .section-label {
  color: #9b59b6;
}

.section-text {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.5;
}

.exam-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  justify-content: flex-end;
}

/* ===== 异常指标汇总（§10.3）===== */
.abnormal-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
  padding: 10px 12px;
  background: #fff1f0;
  border: 1px solid #ffccc7;
  border-radius: 8px;
}

.abnormal-summary-icon {
  font-size: 15px;
  color: #f56c6c;
  flex-shrink: 0;
}

.abnormal-summary-text {
  font-size: 14px;
  color: #cf1322;
  line-height: 1.5;
}

/* ===== 链接按钮 ===== */
.link-btn {
  margin-left: auto;
  background: none;
  border: none;
  color: #4facfe;
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.15s;
}

.link-btn:hover {
  opacity: 0.8;
}

/* ===== 设备使用记录（§13）===== */
.device-picker {
  margin-bottom: 14px;
  padding: 12px;
  background: #ffffff;
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
}

.device-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.device-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: #fafbfc;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
}

.device-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.device-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.device-code {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
  background: #f0f0f0;
  color: #8e8e93;
}

.device-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
  color: #8e8e93;
}

.usage-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.usage-card {
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #e8e8e8;
  border-left: 3px solid #d9d9d9;
  border-radius: 8px;
}

.usage-active {
  border-left-color: #4facfe;
}

.usage-done {
  border-left-color: #67c23a;
}

.usage-abnormal {
  border-left-color: #fa8c16;
}

.usage-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.usage-device {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  flex: 1;
}

.usage-status {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  font-weight: 500;
}

.usage-card.usage-active .usage-status {
  background: #e6f7ff;
  color: #1890ff;
}

.usage-card.usage-done .usage-status {
  background: #f0fff4;
  color: #67c23a;
}

.usage-card.usage-abnormal .usage-status {
  background: #fff7e6;
  color: #fa8c16;
}

.usage-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: #8e8e93;
}

.usage-purpose,
.usage-result {
  font-size: 13px;
  color: #4a5568;
  margin-top: 4px;
  line-height: 1.5;
}

.end-form {
  margin-top: 12px;
  padding: 12px;
  background: #fafbfc;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.end-form .form-group {
  margin-bottom: 10px;
}

.end-form .form-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
