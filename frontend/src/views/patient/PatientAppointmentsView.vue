<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import {
  getAvailableSchedules,
  getMyAppointments,
  createAppointment,
  cancelAppointment,
} from '@/api/appointment'
import { getPatientInfo } from '@/api/patient'
import { getDepartments } from '@/api/department'
import { useAppointmentStore } from '@/stores/appointment'
import type { AppointmentResponse, ScheduleResponse, AppointmentStatus } from '@/types/appointment'

const route = useRoute()
const router = useRouter()
const appointmentStore = useAppointmentStore()

/* ============ 子 Tab ============ */
type SubTab = 'mine' | 'book'
const subTab = ref<SubTab>('mine')

/* ============ 我的挂号 ============ */
const loadingMine = ref(false)
const appointments = ref<AppointmentResponse[]>([])
const statusFilter = ref<'ALL' | AppointmentStatus>('ALL')

const filteredAppointments = computed(() => {
  let list = appointments.value
  if (statusFilter.value !== 'ALL') {
    list = list.filter((a) => a.status === statusFilter.value)
  }
  return [...list].sort(
    (a, b) => new Date(b.bookedAt).getTime() - new Date(a.bookedAt).getTime(),
  )
})

const statusOptions: Array<{ value: 'ALL' | AppointmentStatus; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'BOOKED', label: '待就诊' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

const statusMeta: Record<
  AppointmentStatus,
  { label: string; bg: string; color: string; border: string }
> = {
  BOOKED: { label: '待就诊', bg: '#e3f0ff', color: '#1a73e8', border: '#a8cfff' },
  CHECKED_IN: { label: '已签到', bg: '#fff7e6', color: '#d48806', border: '#ffd591' },
  IN_PROGRESS: { label: '就诊中', bg: '#fff7e6', color: '#d48806', border: '#ffd591' },
  WAITING_EXAM: { label: '等待检查', bg: '#f0f5ff', color: '#2f54eb', border: '#adc6ff' },
  COMPLETED: { label: '已完成', bg: '#f6ffed', color: '#389e0d', border: '#b7eb8f' },
  CANCELLED: { label: '已取消', bg: '#f5f5f5', color: '#8e8e93', border: '#d9d9d9' },
  NO_SHOW: { label: '爽约', bg: '#fff1f0', color: '#cf1322', border: '#ffa39e' },
}

async function loadAppointments() {
  loadingMine.value = true
  try {
    appointments.value = await getMyAppointments()
  } catch (e) {
    console.error('加载挂号列表失败：', e)
    ElMessage.error('加载挂号列表失败')
  } finally {
    loadingMine.value = false
  }
}

async function handleCancel(
  item: AppointmentResponse,
  onSuccess?: () => void,
) {
  if (['IN_PROGRESS', 'WAITING_EXAM', 'COMPLETED'].includes(item.status)) {
    ElMessage.warning('当前状态不可取消')
    return
  }
  let reason = '患者主动取消'
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消挂号', {
      confirmButtonText: '确认取消',
      cancelButtonText: '不取消',
      inputPlaceholder: '例如：临时有事',
      inputValidator: (val) => (val && val.trim().length > 0) || '请填写取消原因',
    })
    reason = value
  } catch {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认取消「${item.doctorName} 医生 ${formatTimeRange(item)}」的预约吗？`,
      '提示',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await cancelAppointment(item.id, { reason })
    ElMessage.success('取消成功')
    // 刷新当前列表 / 详情
    if (detailRecord.value?.id === item.id) {
      detailRecord.value = { ...detailRecord.value, status: 'CANCELLED' }
    }
    await loadAppointments()
    // 仅在用户真正确认取消后再执行 onSuccess（避免在弹窗里点取消时也触发）
    onSuccess?.()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}

const showDetail = ref(false)
const detailRecord = ref<AppointmentResponse | null>(null)

function viewDetail(item: AppointmentResponse) {
  detailRecord.value = item
  showDetail.value = true
}

function closeDetail() {
  showDetail.value = false
  setTimeout(() => {
    detailRecord.value = null
  }, 250)
}

/* ============ 预约挂号 ============ */
const loadingBook = ref(false)
const submittingBook = ref(false)
const departments = ref<Array<{ id: number; name: string }>>([])
const bookForm = reactive({
  departmentId: null as number | null,
  date: '' as string,
})
const schedules = ref<ScheduleResponse[]>([])
const selectedSchedule = ref<ScheduleResponse | null>(null)
const confirmDialogVisible = ref(false)
/** F1: 预选医生 ID（分诊跳转来时高亮） */
const preselectedDoctorId = ref<number | null>(null)
/** F1: 预选 scheduleId（如来自分诊结果卡），自动展开确认 */
const preselectedScheduleId = ref<number | null>(null)

async function loadDepartments() {
  try {
    departments.value = (await getDepartments())
      .filter((d) => d.status === 'ENABLED')
      .map((d) => ({ id: d.id, name: d.name }))
  } catch (e) {
    console.error('加载科室失败：', e)
    ElMessage.error('加载科室失败')
  }
}

async function loadSchedules() {
  if (!bookForm.departmentId) {
    schedules.value = []
    return
  }
  loadingBook.value = true
  try {
    schedules.value = await getAvailableSchedules({
      departmentId: bookForm.departmentId,
      date: bookForm.date || undefined,
    })
  } catch (e) {
    console.error('加载排班失败：', e)
    ElMessage.error('加载排班失败')
  } finally {
    loadingBook.value = false
  }
}

function pickSchedule(s: ScheduleResponse) {
  if (s.remainingCount <= 0) {
    ElMessage.warning('该时段号源已满')
    return
  }
  if (s.status === 'CANCELLED') {
    ElMessage.warning('该排班已取消')
    return
  }
  selectedSchedule.value = s
  confirmDialogVisible.value = true
}

async function confirmBooking() {
  if (!selectedSchedule.value) return
  submittingBook.value = true
  try {
    const patient = await getPatientInfo().catch(() => ({ id: 1 } as any))
    await createAppointment({
      patientId: (patient as any).id ?? 1,
      scheduleId: selectedSchedule.value.id,
    })
    ElMessage.success('预约成功！')
    confirmDialogVisible.value = false
    selectedSchedule.value = null
    preselectedDoctorId.value = null
    preselectedScheduleId.value = null
    appointmentStore.clearPreSelection()
    // 切换到「我的挂号」并刷新
    subTab.value = 'mine'
    await loadAppointments()
  } catch (e: any) {
    ElMessage.error(e?.message || '预约失败')
  } finally {
    submittingBook.value = false
  }
}

/* ============ 工具方法 ============ */
function formatDateLabel(date: string): string {
  const d = dayjs(date)
  const today = dayjs()
  if (d.isSame(today, 'day')) return '今天'
  if (d.isSame(today.add(1, 'day'), 'day')) return '明天'
  if (d.isSame(today.add(2, 'day'), 'day')) return '后天'
  return d.format('MM-DD')
}

function formatWeek(date: string): string {
  const weekMap = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return weekMap[dayjs(date).day()]
}

function formatTimeRange(
  item: AppointmentResponse | { bookedAt: string; scheduleId: number },
): string {
  return dayjs(item.bookedAt).format('YYYY-MM-DD HH:mm')
}

function formatScheduleTime(s: ScheduleResponse): string {
  const start = dayjs(s.startTime)
  const end = dayjs(s.endTime)
  return `${start.format('HH:mm')} - ${end.format('HH:mm')}`
}

/* ============ 监听：分诊跳转过来时预选科室/医生/排班 ============ */
function readPreselection() {
  // 优先从 store 取（跳转时已写入），否则从 query 兜底
  const fromStore = appointmentStore.preSelection
  const fromQuery = appointmentStore.buildPreSelectionFromQuery(
    route.query as Record<string, unknown>,
  )
  return fromStore ?? fromQuery
}

/** 初始化预选阶段：避免 watch 重复触发 loadSchedules */
let isInitializing = false

onMounted(async () => {
  await loadAppointments()
  const pre = readPreselection()
  if (pre?.departmentId) {
    subTab.value = 'book'
    await loadDepartments()
    preselectedDoctorId.value = pre.doctorId ?? null
    preselectedScheduleId.value = pre.scheduleId ?? null
    // 在初始化标记内统一设置表单值，避免 watch 重复触发
    isInitializing = true
    bookForm.departmentId = pre.departmentId
    bookForm.date = ''
    isInitializing = false
    await loadSchedules()
    // 若分诊结果直接带了 scheduleId 且命中排班列表，自动打开确认弹窗
    if (preselectedScheduleId.value != null) {
      const hit = schedules.value.find((s) => s.id === preselectedScheduleId.value)
      if (hit && hit.remainingCount > 0 && hit.status !== 'CANCELLED') {
        selectedSchedule.value = hit
        confirmDialogVisible.value = true
      }
    }
  } else {
    await loadDepartments()
  }
})

watch(
  () => [bookForm.departmentId, bookForm.date],
  () => {
    if (isInitializing) return
    selectedSchedule.value = null
    loadSchedules()
  },
)

// 切换子 tab 时也加载
watch(subTab, async (v) => {
  if (v === 'mine') {
    await loadAppointments()
  } else if (v === 'book' && !departments.value.length) {
    await loadDepartments()
  }
})

const today = dayjs().format('YYYY-MM-DD')
const maxDate = dayjs().add(6, 'day').format('YYYY-MM-DD')
</script>

<template>
  <div class="page-wrapper">
    <Transition name="page-push" mode="out-in">
      <!-- ============ 列表（含子 Tab） ============ -->
      <div v-if="!showDetail" key="list" class="pane pane-list">
        <!-- 子 Tab 切换 -->
        <div class="sub-tabs">
          <div
            class="sub-tab"
            :class="{ active: subTab === 'mine' }"
            @click="subTab = 'mine'"
          >
            我的挂号
          </div>
          <div
            class="sub-tab"
            :class="{ active: subTab === 'book' }"
            @click="subTab = 'book'"
          >
            预约挂号
          </div>
        </div>

      <!-- ============ 我的挂号 ============ -->
      <div v-if="subTab === 'mine'" v-loading="loadingMine" class="tab-pane">
      <div class="filter-row">
        <div
          v-for="opt in statusOptions"
          :key="opt.value"
          class="filter-chip"
          :class="{ active: statusFilter === opt.value }"
          @click="statusFilter = opt.value"
        >
          {{ opt.label }}
        </div>
      </div>

      <div v-if="filteredAppointments.length === 0 && !loadingMine" class="empty-state">
        <div class="empty-icon">📅</div>
        <div class="empty-text">暂无挂号记录</div>
        <div class="empty-tip">
          切换到「预约挂号」开始第一次预约
        </div>
      </div>

      <div v-else class="appt-list">
        <div
          v-for="item in filteredAppointments"
          :key="item.id"
          class="appt-card"
          @click="viewDetail(item)"
        >
          <div class="appt-card-top">
            <div class="appt-dept">{{ item.departmentName }}</div>
            <div
              class="appt-status"
              :style="{
                background: statusMeta[item.status].bg,
                color: statusMeta[item.status].color,
                borderColor: statusMeta[item.status].border,
              }"
            >
              {{ statusMeta[item.status].label }}
            </div>
          </div>
          <div class="appt-doctor">
            <div class="doctor-avatar">{{ item.doctorName.charAt(0) }}</div>
            <div class="doctor-info">
              <div class="doctor-name">{{ item.doctorName }} 医生</div>
              <div class="appt-no">挂号编号：{{ item.appointmentNumber }}</div>
            </div>
          </div>
          <div class="appt-time">
            <span class="time-icon">⏰</span>
            {{ formatTimeRange(item) }}
          </div>
          <div v-if="item.cancellationReason" class="appt-cancel-reason">
            取消原因：{{ item.cancellationReason }}
          </div>
          <div v-if="item.status === 'BOOKED'" class="appt-actions">
            <button
              class="appt-action-btn cancel"
              @click.stop="handleCancel(item)"
            >
              取消预约
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 预约挂号 ============ -->
    <div v-else class="tab-pane">
      <div class="book-filter-card">
        <div class="form-item">
          <label class="form-label">就诊科室</label>
          <select v-model="bookForm.departmentId" class="form-select">
            <option :value="null" disabled>请选择科室</option>
            <option v-for="d in departments" :key="d.id" :value="d.id">
              {{ d.name }}
            </option>
          </select>
        </div>
        <div class="form-item">
          <label class="form-label">就诊日期</label>
          <select v-model="bookForm.date" class="form-select">
            <option value="">全部日期</option>
            <option v-for="i in 7" :key="i" :value="dayjs().add(i - 1, 'day').format('YYYY-MM-DD')">
              {{ dayjs().add(i - 1, 'day').format('MM-DD') }}
              ({{ formatWeek(dayjs().add(i - 1, 'day').format('YYYY-MM-DD')) }})
            </option>
          </select>
        </div>
      </div>

      <div v-loading="loadingBook" class="schedule-list-wrap">
        <div v-if="!bookForm.departmentId" class="empty-state">
          <div class="empty-icon">🏥</div>
          <div class="empty-text">请先选择科室</div>
        </div>
        <div v-else-if="schedules.length === 0 && !loadingBook" class="empty-state">
          <div class="empty-icon">😔</div>
          <div class="empty-text">暂无符合条件排班</div>
          <div class="empty-tip">请尝试切换其他科室或日期</div>
        </div>
        <div v-else>
          <div class="section-title">
            可选排班（{{ schedules.length }}）
            <span v-if="preselectedDoctorId" class="preselect-tip">
              已为您预选
              <template v-if="schedules.find((x) => x.doctorId === preselectedDoctorId)">
                {{ schedules.find((x) => x.doctorId === preselectedDoctorId)?.doctorName }} 医生
              </template>
            </span>
          </div>
          <div class="schedule-list">
            <div
              v-for="s in schedules"
              :key="s.id"
              class="schedule-card"
              :class="{
                full: s.remainingCount <= 0,
                selected: selectedSchedule?.id === s.id,
                preselected: preselectedDoctorId === s.doctorId,
              }"
              @click="pickSchedule(s)"
            >
              <div class="schedule-head">
                <div class="schedule-date">
                  <span class="date-label">{{ formatDateLabel(s.scheduleDate) }}</span>
                  <span class="date-week">{{ formatWeek(s.scheduleDate) }}</span>
                </div>
                <div class="schedule-time">{{ formatScheduleTime(s) }}</div>
              </div>
              <div class="schedule-body">
                <div class="schedule-doctor">
                  <span class="doctor-avatar small">{{ s.doctorName.charAt(0) }}</span>
                  <span class="doctor-name">{{ s.doctorName }} 医生</span>
                  <span v-if="preselectedDoctorId === s.doctorId" class="preselect-tag">
                    推荐
                  </span>
                </div>
                <div class="schedule-remaining">
                  <template v-if="s.remainingCount <= 0">
                    <span class="remaining-full">已满</span>
                  </template>
                  <template v-else>
                    <span class="remaining-num">{{ s.remainingCount }}</span>
                    <span class="remaining-text">/ {{ s.maxAppointments }} 剩余</span>
                  </template>
                </div>
              </div>
              <div class="schedule-action">
                <span v-if="s.remainingCount <= 0" class="action-disabled">不可预约</span>
                <span v-else class="action-text">点击预约 →</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 预约确认弹窗 ============ -->
    <el-dialog
      v-model="confirmDialogVisible"
      title="确认预约"
      width="320px"
      :show-close="false"
      center
    >
      <div v-if="selectedSchedule" class="confirm-body">
        <div class="confirm-row">
          <span class="confirm-label">科室</span>
          <span class="confirm-value">{{ selectedSchedule.departmentName }}</span>
        </div>
        <div class="confirm-row">
          <span class="confirm-label">医生</span>
          <span class="confirm-value">{{ selectedSchedule.doctorName }}</span>
        </div>
        <div class="confirm-row">
          <span class="confirm-label">日期</span>
          <span class="confirm-value">
            {{ selectedSchedule.scheduleDate }}
            ({{ formatWeek(selectedSchedule.scheduleDate) }})
          </span>
        </div>
        <div class="confirm-row">
          <span class="confirm-label">时段</span>
          <span class="confirm-value">{{ formatScheduleTime(selectedSchedule) }}</span>
        </div>
        <div class="confirm-row">
          <span class="confirm-label">剩余号源</span>
          <span class="confirm-value highlight">
            {{ selectedSchedule.remainingCount }} 个
          </span>
        </div>
        <div class="confirm-tip">
          预约后将自动扣减号源，请按时就诊
        </div>
      </div>
      <template #footer>
        <el-button @click="confirmDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submittingBook"
          @click="confirmBooking"
        >
          确认预约
        </el-button>
      </template>
    </el-dialog>
      </div>

      <!-- ============ 详情 ============ -->
      <div v-else key="detail" class="pane pane-detail">
        <div v-if="detailRecord" class="detail-page">
          <div class="detail-header">
            <button class="back-btn" @click="closeDetail">‹ 返回</button>
            <div class="detail-title">挂号详情</div>
            <div class="detail-spacer"></div>
          </div>

          <div class="detail-content">
            <!-- 状态卡 -->
            <div
              class="status-card"
              :class="{
                cancelled: detailRecord.status === 'CANCELLED',
                done: detailRecord.status === 'COMPLETED',
              }"
            >
              <div class="status-text">{{ statusMeta[detailRecord.status].label }}</div>
              <div class="status-no">挂号编号：{{ detailRecord.appointmentNumber }}</div>
            </div>

            <!-- 基本信息 -->
            <div class="detail-section">
              <div class="section-label">预约信息</div>
              <div class="info-row">
                <span class="info-label">就诊科室</span>
                <span class="info-value">{{ detailRecord.departmentName }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">就诊医生</span>
                <span class="info-value">{{ detailRecord.doctorName }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">预约时间</span>
                <span class="info-value">
                  {{ formatTimeRange(detailRecord) }}
                </span>
              </div>
              <div v-if="detailRecord.checkInTime" class="info-row">
                <span class="info-label">签到时间</span>
                <span class="info-value">
                  {{ dayjs(detailRecord.checkInTime).format('YYYY-MM-DD HH:mm') }}
                </span>
              </div>
              <div v-if="detailRecord.cancellationReason" class="info-row">
                <span class="info-label">取消原因</span>
                <span class="info-value">{{ detailRecord.cancellationReason }}</span>
              </div>
            </div>

            <!-- 操作 -->
            <div v-if="detailRecord.status === 'BOOKED'" class="detail-actions">
              <button
                class="action-btn cancel"
                @click="handleCancel(detailRecord, () => closeDetail())"
              >
                取消预约
              </button>
            </div>

            <div class="detail-footer">
              <div>预约时间：{{ dayjs(detailRecord.createdAt).format('YYYY-MM-DD HH:mm') }}</div>
              <div v-if="detailRecord.cancelledAt">
                取消时间：{{ dayjs(detailRecord.cancelledAt).format('YYYY-MM-DD HH:mm') }}
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

.status-card {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border-radius: 14px;
  padding: 18px 16px;
  margin-bottom: 12px;
  box-shadow: 0 4px 12px rgb(79 172 254 / 25%);
  text-align: center;
}

.status-card.cancelled {
  background: linear-gradient(135deg, #909399 0%, #b1b3b8 100%);
  box-shadow: 0 4px 12px rgb(144 147 153 / 25%);
}

.status-card.done {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  box-shadow: 0 4px 12px rgb(103 194 58 / 25%);
}

.status-text {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 6px;
}

.status-no {
  font-size: 12px;
  opacity: 0.9;
}

.detail-section {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #8e8e93;
  margin-bottom: 10px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  padding: 6px 0;
  border-bottom: 1px dashed #f1f5f9;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: #8e8e93;
}

.info-value {
  color: #1a1a1a;
  font-weight: 500;
}

.detail-actions {
  margin-top: 4px;
  margin-bottom: 12px;
}

.action-btn {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn.cancel {
  background: #fff1f0;
  color: #cf1322;
  border: 1px solid #ffa39e;
}

.action-btn.cancel:active {
  background: #ffccc7;
}

.detail-footer {
  text-align: center;
  font-size: 11px;
  color: #8e8e93;
  padding: 16px 8px;
  line-height: 1.8;
}

/* ============ 子 Tab ============ */
.sub-tabs {
  display: flex;
  background: #ffffff;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.sub-tab {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 14px;
  font-weight: 500;
  color: #8e8e93;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
  -webkit-user-select: none;
}

.sub-tab.active {
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  box-shadow: 0 2px 6px rgb(79 172 254 / 25%);
}

/* ============ 状态筛选 ============ */
.filter-row {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  overflow-x: auto;
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

/* ============ 挂号卡片 ============ */
.appt-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.appt-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: transform 0.15s;
}

.appt-card:active {
  transform: scale(0.99);
}

.appt-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.appt-dept {
  font-size: 13px;
  color: #8e8e93;
}

.appt-status {
  padding: 3px 10px;
  font-size: 12px;
  font-weight: 500;
  border: 1px solid;
  border-radius: 12px;
}

.appt-doctor {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.doctor-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.doctor-avatar.small {
  width: 30px;
  height: 30px;
  font-size: 13px;
}

.doctor-info {
  flex: 1;
  min-width: 0;
}

.doctor-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 2px;
}

.appt-no {
  font-size: 12px;
  color: #8e8e93;
}

.appt-time {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #475569;
  padding: 8px 12px;
  background: #f5f5f7;
  border-radius: 8px;
  margin-bottom: 10px;
}

.time-icon {
  font-size: 14px;
}

.appt-cancel-reason {
  font-size: 12px;
  color: #8e8e93;
  padding: 6px 0;
}

.appt-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
  margin-top: 4px;
}

.appt-action-btn {
  padding: 6px 16px;
  border: 1px solid #f56c6c;
  background: #ffffff;
  color: #f56c6c;
  border-radius: 16px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.appt-action-btn.cancel:active {
  background: #fff1f0;
}

/* ============ 预约表单 ============ */
.book-filter-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.form-item:last-child {
  margin-bottom: 0;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
}

.form-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 32px;
}

.form-select:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

/* ============ 排班列表 ============ */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 12px;
  padding-left: 4px;
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.schedule-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: all 0.15s;
  border: 1.5px solid transparent;
}

.schedule-card:active {
  transform: scale(0.99);
}

.schedule-card.full {
  background: #fafafa;
  cursor: not-allowed;
}

.schedule-card.selected {
  border-color: #4facfe;
  box-shadow: 0 2px 12px rgb(79 172 254 / 25%);
}

.schedule-card.preselected {
  border-color: #9b59b6;
  box-shadow: 0 0 0 2px rgb(155 89 182 / 12%);
}

.preselect-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: #f9f0ff;
  color: #9b59b6;
  border-radius: 6px;
  margin-left: 4px;
  font-weight: 500;
}

.preselect-tip {
  font-size: 12px;
  color: #9b59b6;
  font-weight: 400;
  margin-left: 6px;
}

.schedule-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #f0f0f0;
}

.schedule-date {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.date-label {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.date-week {
  font-size: 12px;
  color: #8e8e93;
}

.schedule-time {
  font-size: 13px;
  color: #1a73e8;
  font-weight: 500;
  padding: 2px 8px;
  background: #e3f0ff;
  border-radius: 6px;
}

.schedule-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.schedule-doctor {
  display: flex;
  align-items: center;
  gap: 8px;
}

.schedule-remaining {
  font-size: 12px;
  color: #8e8e93;
}

.remaining-num {
  font-size: 16px;
  font-weight: 600;
  color: #67c23a;
  margin-right: 2px;
}

.remaining-full {
  color: #f56c6c;
  font-weight: 500;
}

.schedule-action {
  text-align: right;
  font-size: 12px;
  color: #1a73e8;
}

.action-disabled {
  color: #c0c4cc;
}

/* ============ 确认弹窗 ============ */
.confirm-body {
  padding: 4px 0;
}

.confirm-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
  font-size: 14px;
}

.confirm-row:last-of-type {
  border-bottom: none;
}

.confirm-label {
  color: #8e8e93;
}

.confirm-value {
  color: #1a1a1a;
  font-weight: 500;
}

.confirm-value.highlight {
  color: #67c23a;
  font-size: 16px;
}

.confirm-tip {
  margin-top: 12px;
  padding: 8px 12px;
  background: #fff7e6;
  border-radius: 8px;
  font-size: 12px;
  color: #d48806;
  text-align: center;
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
</style>
