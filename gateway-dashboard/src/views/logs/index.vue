<template>
  <div class="app-container">
    <div class="action-bar">
      <div class="bar-left">
        <h2 class="page-title">
          <el-icon><Document /></el-icon> 审计日志
        </h2>
        <p class="page-sub">访问落库记录 · 按时间倒序</p>
      </div>

      <div class="bar-right">
        <el-input
          v-model="queryParams.path"
          placeholder="路径关键字…"
          clearable
          class="glass-input search-input"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" class="glow-btn" @click="fetchData">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <div class="log-table-wrapper" v-loading="loading">
      <el-table
        v-if="total > 0 || loading"
        :data="tableData"
        style="width: 100%"
        class="custom-table"
        :header-cell-style="{ background: 'transparent' }"
        empty-text="暂无数据"
      >
        <el-table-column label="时间" width="168" fixed>
          <template #default="{ row }">
            <span class="time-stamp">{{ formatTime(row.requestTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="请求" min-width="280">
          <template #default="{ row }">
            <div class="req-cell">
              <span class="method-text" :class="methodClass(row.method)">{{ row.method || '—' }}</span>
              <code class="path-code" :title="row.path">{{ row.path || '—' }}</code>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="108" align="center">
          <template #default="{ row }">
            <div class="status-glow-badge" :class="getStatusClass(row.status)">
              <span class="dot"></span>
              <span class="code">{{ row.status != null ? row.status : '—' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="耗时" width="100" align="right" sortable :sort-method="sortByResponseTime">
          <template #default="{ row }">
            <span class="latency" :class="{ slow: Number(row.responseTime) > 500 }">
              {{ row.responseTime != null ? `${row.responseTime}ms` : '—' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="ip" label="客户端" width="140">
          <template #default="{ row }">
            <span class="mono-muted">{{ row.ip || '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="用户" width="120">
          <template #default="{ row }">
            <span class="user-text">{{ row.userId ? row.userId : '—' }}</span>
          </template>
        </el-table-column>

        <el-table-column v-if="hasAnyTrace" label="Trace" min-width="120">
          <template #default="{ row }">
            <span class="trace-line" :title="row.traceId || ''">{{ row.traceId || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && total === 0" description="暂无访问日志" class="empty-block" />

      <div v-if="total > 0" class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="handleSearch"
          @current-change="fetchData"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { getLogList } from '@/api/log'
import { Search, Refresh, Document } from '@element-plus/icons-vue'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)

const hasAnyTrace = computed(() => tableData.value.some((r) => r.traceId))

const queryParams = reactive({
  page: 1,
  size: 10,
  path: ''
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getLogList(queryParams)
    tableData.value = res.records || []
    total.value = res.total != null ? res.total : 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  fetchData()
}

const getStatusClass = (status) => {
  const n = Number(status)
  if (Number.isNaN(n)) return 's-muted'
  if (n >= 200 && n < 300) return 's-success'
  if (n >= 400 && n < 500) return 's-warning'
  return 's-error'
}

const methodClass = (m) => {
  const x = String(m || '').toLowerCase()
  if (x === 'post') return 'post'
  if (x === 'get') return 'get'
  if (x === 'delete') return 'delete'
  if (x === 'put' || x === 'patch') return 'put'
  return ''
}

const sortByResponseTime = (a, b) => {
  const na = Number(a.responseTime)
  const nb = Number(b.responseTime)
  return (Number.isNaN(na) ? 0 : na) - (Number.isNaN(nb) ? 0 : nb)
}

const pad2 = (n) => String(n).padStart(2, '0')

const formatTime = (v) => {
  if (v == null || v === '') return '—'
  const date = v instanceof Date ? v : new Date(v)
  if (Number.isNaN(date.getTime())) return '—'
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
}

onMounted(fetchData)
</script>

<style scoped>
.app-container {
  padding: 20px 40px;
  background-color: var(--bg-body);
  max-width: 1400px;
  margin: 0 auto;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 4px;
}

.page-sub {
  margin: 0;
  padding-left: 30px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.4;
}

.bar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: min(320px, 42vw);
}

:deep(.glass-input .el-input__wrapper) {
  background-color: var(--bg-glass);
  box-shadow: 0 0 0 1px var(--border-color) inset;
  border-radius: 10px;
  height: 38px;
}

.glow-btn {
  background: var(--text-highlight) !important;
  border: none !important;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(14, 165, 233, 0.3);
  color: #fff;
}

.log-table-wrapper {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 10px;
  backdrop-filter: blur(10px);
  min-height: 200px;
}

.empty-block {
  padding: 48px 16px;
}

:deep(.custom-table) {
  background: transparent !important;
  --el-table-border-color: var(--border-color);
  --el-table-header-text-color: var(--text-highlight);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

:deep(.el-table__row) {
  background: transparent !important;
}
:deep(.el-table__cell) {
  border-bottom: 1px solid var(--border-color) !important;
}

.status-glow-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.05);
}

.status-glow-badge .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.s-success {
  color: #10b981;
}
.s-success .dot {
  background: #10b981;
  box-shadow: 0 0 6px #10b981;
}

.s-warning {
  color: #f59e0b;
}
.s-warning .dot {
  background: #f59e0b;
  box-shadow: 0 0 6px #f59e0b;
}

.s-error {
  color: #f43f5e;
}
.s-error .dot {
  background: #f43f5e;
  box-shadow: 0 0 6px #f43f5e;
}

.s-muted {
  color: var(--text-secondary);
}
.s-muted .dot {
  background: var(--text-secondary);
}

.method-text {
  font-weight: 800;
  font-size: 11px;
  min-width: 44px;
  text-align: center;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.25);
}
.method-text.post {
  color: #10b981;
}
.method-text.get {
  color: var(--text-highlight);
}
.method-text.delete {
  color: #f43f5e;
}
.method-text.put {
  color: #a78bfa;
}

.req-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.path-code {
  flex: 1;
  min-width: 0;
  background: rgba(0, 0, 0, 0.22);
  padding: 4px 8px;
  border-radius: 6px;
  color: var(--text-secondary);
  font-family: ui-monospace, 'Fira Code', Consolas, monospace;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono-muted {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--text-secondary);
}

.user-text {
  font-size: 13px;
  color: var(--text-main);
}

.trace-line {
  display: block;
  font-family: ui-monospace, Consolas, monospace;
  font-size: 11px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.latency {
  font-size: 13px;
  color: #4ade80;
  font-variant-numeric: tabular-nums;
}
.latency.slow {
  color: #f87171;
  font-weight: 700;
}

.time-stamp {
  color: var(--text-secondary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.pagination-container {
  margin-top: 16px;
  padding: 6px 4px 4px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-pagination button) {
  background: transparent !important;
  color: var(--text-main) !important;
}
:deep(.el-pagination .el-pager li) {
  background: transparent !important;
  color: var(--text-secondary);
}
:deep(.el-pagination .el-pager li.is-active) {
  color: var(--text-highlight) !important;
  font-weight: 900;
}

@media (max-width: 768px) {
  .app-container {
    padding: 16px;
  }
  .search-input {
    width: 100%;
  }
  .bar-right {
    width: 100%;
  }
}
</style>
