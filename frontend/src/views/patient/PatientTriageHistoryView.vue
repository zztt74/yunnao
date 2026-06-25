<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getMyTriageRecords } from '@/api/triage'
import type { TriageResultResponse, TriagePriority } from '@/types/triage'

const loading = ref(false)
const list = ref<TriageResultResponse[]>([])
const selected = ref<TriageResultResponse | null>(null)
const showDetail = ref(false)

const priorityMeta: Record<
  TriagePriority,
  { label: string; bg: string; color: string; icon: string }
> = {
  LOW: { label: '低', bg: '#f6ffed', color: '#389e0d', icon: '🟢' },
  MEDIUM: { label: '中', bg: '#fff7e6', color: '#d48806', icon: '🟡' },
  HIGH: { label: '高', bg: '#fff1f0', color: '#cf1322', icon: '🟠' },
  EMERGENCY: { label: '急诊', bg: '#fff1f0', color: '#a8071a', icon: '🔴' },
}

async function loadList() {
  loading.value = true
  try {
    list.value = await getMyTriageRecords()
  } catch (e: any) {
    console.error('加载分诊历史失败：', e)
    ElMessage.error(e?.message || '加载分诊历史失败')
  } finally {
    loading.value = false
  }
}

function openDetail(item: TriageResultResponse) {
  selected.value = item
  showDetail.value = true
}

function closeDetail() {
  showDetail.value = false
  setTimeout(() => {
    selected.value = null
  }, 250)
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div class="page-wrapper">
    <Transition name="page-push" mode="out-in">
      <!-- 列表页 -->
      <div v-if="!showDetail" key="list" class="pane pane-list">
        <div class="page-header">
          <h1 class="page-title">分诊记录</h1>
          <div class="page-sub">您过往的 AI 智能问诊与分诊历史</div>
        </div>

        <div v-loading="loading" class="list-wrap">
          <div v-if="list.length === 0 && !loading" class="empty-state">
            <div class="empty-icon">🩺</div>
            <div class="empty-text">暂无分诊记录</div>
            <div class="empty-tip">到「AI 智能问诊」开始第一次分诊</div>
          </div>

          <div v-else class="triage-list">
            <div
              v-for="item in list"
              :key="item.id"
              class="triage-card"
              @click="openDetail(item)"
            >
              <div class="triage-top">
                <div
                  class="priority-tag"
                  :style="{
                    background: priorityMeta[item.priority].bg,
                    color: priorityMeta[item.priority].color,
                  }"
                >
                  {{ priorityMeta[item.priority].icon }}
                  {{ priorityMeta[item.priority].label }}优先级
                </div>
                <div class="triage-time">
                  {{ dayjs(item.createdAt).format('YYYY-MM-DD HH:mm') }}
                </div>
              </div>
              <div class="triage-dept">
                <span class="dept-label">推荐科室</span>
                <span class="dept-name">{{ item.recommendedDepartmentName }}</span>
              </div>
              <div class="triage-reason">{{ item.reason }}</div>
              <div class="triage-arrow">查看详情 ›</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 详情页 -->
      <div v-else key="detail" class="pane pane-detail">
        <div class="detail-header">
          <button class="back-btn" @click="closeDetail">‹ 返回</button>
          <h2 class="detail-title">分诊详情</h2>
        </div>

        <div v-if="selected" class="detail-content">
          <div
            class="detail-priority"
            :style="{
              background: priorityMeta[selected.priority].bg,
              color: priorityMeta[selected.priority].color,
            }"
          >
            <span class="p-icon">{{ priorityMeta[selected.priority].icon }}</span>
            <div>
              <div class="p-label">
                {{ priorityMeta[selected.priority].label }}优先级
              </div>
              <div class="p-time">
                {{ dayjs(selected.createdAt).format('YYYY-MM-DD HH:mm') }}
              </div>
            </div>
          </div>

          <div class="detail-section">
            <div class="section-label">推荐科室</div>
            <div class="section-value dept-highlight">
              {{ selected.recommendedDepartmentName }}
            </div>
          </div>

          <div class="detail-section">
            <div class="section-label">推荐理由</div>
            <div class="section-value">{{ selected.reason }}</div>
          </div>

          <div class="detail-section">
            <div class="section-label">安全提示</div>
            <div class="section-value">{{ selected.safetyAdvice }}</div>
          </div>

          <div v-if="selected.emergencyAdvice" class="detail-section emergency">
            <div class="section-label">⚠️ 急诊建议</div>
            <div class="section-value">{{ selected.emergencyAdvice }}</div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.page-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.pane {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.page-header {
  padding: 18px 16px 12px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #fff;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-sub {
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.85;
}

.list-wrap {
  padding: 12px 16px 24px;
  flex: 1;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #8e8e93;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-text {
  font-size: 15px;
  font-weight: 500;
  color: #1a1a1a;
  margin-bottom: 6px;
}

.empty-tip {
  font-size: 12px;
  color: #8e8e93;
}

.triage-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.triage-card {
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 4%);
  cursor: pointer;
  transition: transform 0.15s;
}

.triage-card:active {
  transform: scale(0.99);
}

.triage-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.priority-tag {
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 500;
}

.triage-time {
  font-size: 12px;
  color: #8e8e93;
}

.triage-dept {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.dept-label {
  font-size: 12px;
  color: #8e8e93;
}

.dept-name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.triage-reason {
  font-size: 13px;
  color: #475569;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.triage-arrow {
  margin-top: 8px;
  font-size: 12px;
  color: #4facfe;
  text-align: right;
}

/* ============ 详情页 ============ */
.pane-detail {
  background: #f5f7fa;
}

.detail-header {
  display: flex;
  align-items: center;
  padding: 14px 12px;
  background: #fff;
  border-bottom: 1px solid #f1f5f9;
  position: sticky;
  top: 0;
  z-index: 1;
}

.back-btn {
  background: none;
  border: none;
  color: #4facfe;
  font-size: 16px;
  padding: 4px 10px;
  cursor: pointer;
}

.detail-title {
  margin: 0 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.detail-content {
  padding: 14px 16px 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-priority {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 4%);
}

.p-icon {
  font-size: 28px;
}

.p-label {
  font-size: 15px;
  font-weight: 600;
}

.p-time {
  font-size: 12px;
  color: #8e8e93;
  margin-top: 2px;
}

.detail-section {
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  box-shadow: 0 1px 4px rgb(0 0 0 / 4%);
}

.detail-section.emergency {
  background: #fff1f0;
  border-left: 3px solid #cf1322;
}

.section-label {
  font-size: 12px;
  color: #8e8e93;
  margin-bottom: 6px;
}

.section-value {
  font-size: 14px;
  color: #1a1a1a;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.dept-highlight {
  font-size: 18px;
  font-weight: 600;
  color: #4facfe;
}

/* 推入式过渡 */
.page-push-enter-active,
.page-push-leave-active {
  transition: transform 0.25s ease, opacity 0.2s ease;
}
.page-push-enter-from {
  transform: translateX(100%);
  opacity: 0.6;
}
.page-push-leave-to {
  transform: translateX(-30%);
  opacity: 0;
}
</style>
