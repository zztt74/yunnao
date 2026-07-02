<script setup lang="ts">
// 患者管理（F-HW-06：原"患者查询"统一改为"患者管理"）
// 设计来源：product/11_功能需求.md §3.3
// 功能：默认加载全部患者列表；支持按姓名 / 手机号 / 患者编号 / 状态筛选；
// 卡片仅展示"查看档案详情"这一只读操作；不渲染任何后端未支撑的写操作。
import { ref, computed, onMounted } from 'vue'
import { getAdminPatients } from '@/api/admin'
import type { PatientDetailResponse } from '@/types/patient'
import type { PageResult } from '@/types/admin'

const loading = ref(true)
const loadError = ref('')
const patients = ref<PatientDetailResponse[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const statusFilter = ref<'ACTIVE' | 'INACTIVE' | ''>('ACTIVE')
const expandedId = ref<number | null>(null)

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize.value)),
)

async function loadPatients() {
  loading.value = true
  loadError.value = ''
  try {
    const res: PageResult<PatientDetailResponse> = await getAdminPatients({
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    patients.value = res.list
    total.value = res.total
  } catch (e) {
    const msg = e instanceof Error && e.message ? e.message : '加载患者列表失败'
    loadError.value = msg
    console.error('[AdminPatients] 加载失败：', e)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  expandedId.value = null
  loadPatients()
}

function onReset() {
  keyword.value = ''
  statusFilter.value = 'ACTIVE'
  page.value = 1
  expandedId.value = null
  loadPatients()
}

function prevPage() {
  if (page.value <= 1) return
  page.value--
  expandedId.value = null
  loadPatients()
}

function nextPage() {
  if (page.value >= totalPages.value) return
  page.value++
  expandedId.value = null
  loadPatients()
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

function genderText(g: PatientDetailResponse['gender']): string {
  return g === 'MALE' ? '男' : g === 'FEMALE' ? '女' : '--'
}

function genderClass(g: PatientDetailResponse['gender']): string {
  return g === 'MALE' ? 'gender-male' : 'gender-female'
}

function formatDate(iso: string): string {
  if (!iso) return '--'
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  } catch {
    return '--'
  }
}

function textOrNone(text: string | null | undefined): string {
  return text && text.trim() ? text : '无'
}

onMounted(loadPatients)
</script>

<template>
  <div class="admin-patients">
    <div class="page-header">
      <div class="page-title">患者管理</div>
      <div class="page-sub">查看与检索平台内所有患者档案，支持按姓名 / 手机号 / 患者编号 / 状态筛选</div>
    </div>

    <!-- 筛选栏 -->
    <div class="search-card">
      <input
        v-model="keyword"
        type="text"
        class="search-input"
        placeholder="输入姓名 / 手机号 / 患者编号"
        @keyup.enter="onSearch"
      />
      <select v-model="statusFilter" class="search-select" @change="onSearch">
        <option value="">全部状态</option>
        <option value="ACTIVE">在档</option>
        <option value="INACTIVE">已停用</option>
      </select>
      <button class="primary-btn" @click="onSearch">搜索</button>
      <button class="ghost-btn" @click="onReset">重置</button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="state-card">
      <span class="spinner" />
      <span class="state-text">正在加载患者列表…</span>
    </div>

    <!-- 加载失败 -->
    <div v-else-if="loadError" class="state-card error">
      <div class="state-title">加载失败</div>
      <div class="state-desc">{{ loadError }}</div>
      <button class="primary-btn" @click="loadPatients">重新加载</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="patients.length === 0" class="state-card">
      <div class="state-title">暂无患者记录</div>
      <div class="state-desc">
        {{ keyword || statusFilter ? '没有符合筛选条件的患者，请更换关键字或状态重试' : '当前没有任何患者档案' }}
      </div>
    </div>

    <!-- 患者卡片列表 -->
    <template v-else>
      <div class="patient-list">
        <div
          v-for="p in patients"
          :key="p.id"
          class="patient-card"
          :class="{ expanded: expandedId === p.id }"
          @click="toggleExpand(p.id)"
        >
          <div class="card-head">
            <div class="head-left">
              <div class="avatar">{{ p.name.charAt(0) }}</div>
              <div class="head-info">
                <div class="name-row">
                  <span class="name">{{ p.name }}</span>
                  <span class="gender-tag" :class="genderClass(p.gender)">
                    {{ genderText(p.gender) }}
                  </span>
                  <span class="age">{{ p.age }} 岁</span>
                </div>
                <div class="sub-row">
                  <span class="phone">{{ p.phone }}</span>
                  <span class="sep">·</span>
                  <span class="created">建档 {{ formatDate(p.createdAt) }}</span>
                </div>
              </div>
            </div>
            <span class="expand-toggle">
              {{ expandedId === p.id ? '收起' : '展开' }}
            </span>
          </div>

          <!-- 常显摘要 -->
          <div class="card-preview">
            <div class="preview-item">
              <span class="preview-label">过敏史</span>
              <span class="preview-text">{{ textOrNone(p.allergies) }}</span>
            </div>
            <div class="preview-item">
              <span class="preview-label">既往史</span>
              <span class="preview-text">{{ textOrNone(p.medicalHistory) }}</span>
            </div>
          </div>

          <!-- 展开详情 -->
          <div v-if="expandedId === p.id" class="card-detail" @click.stop>
            <div class="detail-title">档案详情</div>
            <div class="detail-row">
              <span class="detail-label">出生日期</span>
              <span class="detail-value">{{ formatDate(p.birthDate) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">联系电话</span>
              <span class="detail-value">{{ p.phone }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">详细地址</span>
              <span class="detail-value">{{ textOrNone(p.address) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">紧急联系人</span>
              <span class="detail-value">{{ textOrNone(p.emergencyContact) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">紧急联系电话</span>
              <span class="detail-value">{{ textOrNone(p.emergencyPhone) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">完整过敏史</span>
              <span class="detail-value">{{ textOrNone(p.allergies) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">完整既往史</span>
              <span class="detail-value">{{ textOrNone(p.medicalHistory) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <button
          class="ghost-btn"
          :disabled="page <= 1"
          @click="prevPage"
        >
          上一页
        </button>
        <span class="page-info">
          第 {{ page }} / {{ totalPages }} 页 · 共 {{ total }} 条
        </span>
        <button
          class="ghost-btn"
          :disabled="page >= totalPages"
          @click="nextPage"
        >
          下一页
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.admin-patients {
  padding: 20px 24px 32px;
  max-width: 1100px;
  margin: 0 auto;
}

/* ============ 页头 ============ */
.page-header {
  margin-bottom: 18px;
}

.page-title {
  font-size: 19px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.page-sub {
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.5;
}

/* ============ 按钮 ============ */
.primary-btn {
  padding: 8px 18px;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  white-space: nowrap;
}

.primary-btn:hover {
  opacity: 0.92;
}

.ghost-btn {
  padding: 8px 18px;
  background: #ffffff;
  color: #475569;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
  white-space: nowrap;
}

.ghost-btn:hover:not(:disabled) {
  border-color: #4facfe;
  color: #4facfe;
}

.ghost-btn:disabled {
  color: #c0c4cc;
  border-color: #f0f0f0;
  cursor: not-allowed;
}

/* ============ 搜索栏 ============ */
.search-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 16px 18px;
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.search-input {
  flex: 1;
  height: 36px;
  padding: 0 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
  box-sizing: border-box;
}

.search-input:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

.search-select {
  height: 36px;
  padding: 0 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 13px;
  color: #1a1a1a;
  outline: none;
  background: #f8f9fa;
  cursor: pointer;
  min-width: 120px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%238e8e93' d='M6 8L2 4h8z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 10px center;
  padding-right: 26px;
}

.search-select:focus {
  border-color: #4facfe;
  box-shadow: 0 0 0 3px rgb(79 172 254 / 12%);
  background-color: #ffffff;
}

/* ============ 状态卡片（加载/错误/空） ============ */
.state-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 48px 24px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid #e0e0e0;
  border-top-color: #4facfe;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 4px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state-text {
  font-size: 13px;
  color: #8e8e93;
}

.state-card.error .state-title {
  color: #f56c6c;
}

.state-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.state-desc {
  font-size: 13px;
  color: #8e8e93;
  line-height: 1.5;
  margin-bottom: 6px;
}

/* ============ 患者卡片列表 ============ */
.patient-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.patient-card {
  background: #ffffff;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(0 0 0 / 4%);
  padding: 16px 18px;
  cursor: pointer;
  transition: box-shadow 0.15s;
  border: 1px solid transparent;
}

.patient-card:hover {
  box-shadow: 0 4px 14px rgb(0 0 0 / 8%);
}

.patient-card.expanded {
  border-color: #4facfe;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4facfe 0%, #00c6ff 100%);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;
}

.head-info {
  min-width: 0;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.name {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}

.gender-tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 8px;
  font-weight: 500;
}

.gender-male {
  background: #e3f0ff;
  color: #1a73e8;
}

.gender-female {
  background: #ffeef5;
  color: #c03576;
}

.age {
  font-size: 12px;
  color: #8e8e93;
}

.sub-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8e8e93;
}

.sep {
  color: #d9d9d9;
}

.expand-toggle {
  font-size: 12px;
  color: #4facfe;
  white-space: nowrap;
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 6px;
  background: #e3f0ff;
}

/* ============ 常显摘要 ============ */
.card-preview {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  background: #f8f9fa;
  border-radius: 10px;
}

.preview-item {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.5;
}

.preview-label {
  flex-shrink: 0;
  width: 56px;
  color: #8e8e93;
}

.preview-text {
  color: #475569;
  word-break: break-all;
}

/* ============ 展开详情 ============ */
.card-detail {
  margin-top: 12px;
  padding: 14px 16px;
  background: #fafcff;
  border: 1px solid #e3f0ff;
  border-radius: 10px;
}

.detail-title {
  font-size: 13px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 10px;
}

.detail-row {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed #f0f0f0;
  font-size: 13px;
  line-height: 1.5;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-label {
  flex-shrink: 0;
  width: 84px;
  color: #8e8e93;
}

.detail-value {
  color: #1a1a1a;
  word-break: break-all;
}

/* ============ 分页 ============ */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
}

.page-info {
  font-size: 13px;
  color: #8e8e93;
}
</style>
