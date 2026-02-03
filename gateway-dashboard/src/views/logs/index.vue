<template>
  <div class="app-container">
    <div class="stats-overview">
      <div class="stat-item">
        <div class="stat-label">请求总数</div>
        <div class="stat-value">{{ total }}</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">异常率</div>
        <div class="stat-value warn">2.4%</div>
      </div>
      <div class="stat-item">
        <div class="stat-label">平均响应时间</div>
        <div class="stat-value highlight">124ms</div>
      </div>
    </div>

    <div class="action-bar">
      <div class="bar-left">
        <h2 class="page-title">
          <el-icon><Document /></el-icon> 审计日志
        </h2>
      </div>

      <div class="bar-right">
        <div class="search-box">
          <el-input
              v-model="queryParams.path"
              placeholder="搜索请求路径..."
              clearable
              class="glass-input"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>

        <div class="btn-group">
          <el-button type="primary" plain class="glass-btn" @click="handleSearch">
            <el-icon><Filter /></el-icon> 筛选查询
          </el-button>
          <el-button type="primary" class="glow-btn" @click="fetchData">
            <el-icon><Refresh /></el-icon> 刷新同步
          </el-button>
        </div>
      </div>
    </div>

    <div class="log-table-wrapper" v-loading="loading">
      <el-table
          :data="tableData"
          style="width: 100%"
          class="custom-table"
          :header-cell-style="{ background: 'transparent' }"
      >
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <div class="status-glow-badge" :class="getStatusClass(scope.row.status)">
              <span class="dot"></span>
              <span class="code">{{ scope.row.status }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="method" label="方法" width="100">
          <template #default="scope">
            <span class="method-text" :class="scope.row.method.toLowerCase()">
              {{ scope.row.method }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="path" label="请求路径" min-width="300">
          <template #default="scope">
            <code class="path-code">{{ scope.row.path }}</code>
          </template>
        </el-table-column>

        <el-table-column prop="ip" label="来源 IP" width="150">
          <template #default="scope">
            <span class="ip-address">{{ scope.row.ip }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="userId" label="操作人" width="120">
          <template #default="scope">
            <div class="user-chip">
              <el-avatar :size="20" src="https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png" />
              <span>{{ scope.row.userId || '游客' }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="responseTime" label="耗时" width="120" sortable>
          <template #default="scope">
            <div class="time-tag" :class="{ 'slow': scope.row.responseTime > 500 }">
              <el-icon><Timer /></el-icon>
              {{ scope.row.responseTime }}ms
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="requestTime" label="时间" width="200">
          <template #default="scope">
            <span class="time-stamp">{{ formatTime(scope.row.requestTime) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
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
import { ref, reactive, onMounted } from 'vue'
import { getLogList } from '@/api/log'
import { Search, Refresh, Filter, Document, Timer } from '@element-plus/icons-vue'

const tableData = ref([])
const total = ref(0)
const loading = ref(false)

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
    total.value = res.total || 0
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
  if (status >= 200 && status < 300) return 's-success'
  if (status >= 400 && status < 500) return 's-warning'
  return 's-error'
}

const formatTime = (isoStr) => {
  if (!isoStr) return ''
  const date = new Date(isoStr)
  return `${date.getMonth()+1}/${date.getDate()} ${date.getHours()}:${date.getMinutes().toString().padStart(2, '0')}:${date.getSeconds().toString().padStart(2, '0')}`
}

onMounted(fetchData)
</script>

<style scoped>
.app-container {
  padding: 20px 40px;
  background-color: var(--bg-body);
}

/* 顶部统计组件 */
.stats-overview {
  display: flex;
  gap: 20px;
  margin-bottom: 30px;
}

.stat-item {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 20px;
  border-radius: 16px;
  backdrop-filter: blur(10px);
}

.stat-label { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-main); }
.stat-value.highlight { color: var(--text-highlight); }
.stat-value.warn { color: #f43f5e; }

/* 操作栏对齐 */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
}

.bar-right { display: flex; align-items: center; gap: 16px; }

.search-box { width: 240px; transition: all 0.3s ease; }
.search-box:focus-within { width: 320px; }

:deep(.glass-input .el-input__wrapper) {
  background-color: var(--bg-glass);
  box-shadow: 0 0 0 1px var(--border-color) inset;
  border-radius: 10px;
  height: 38px;
}

.glass-btn {
  background: var(--bg-glass) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-main) !important;
  border-radius: 8px;
}

.glow-btn {
  background: var(--text-highlight) !important;
  border: none !important;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(14, 165, 233, 0.3);
  color: #fff;
}

/* 表格深度定制 */
.log-table-wrapper {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 10px;
  backdrop-filter: blur(10px);
}

:deep(.custom-table) {
  background: transparent !important;
  --el-table-border-color: var(--border-color);
  --el-table-header-text-color: var(--text-highlight);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.05);
}

:deep(.el-table__row) { background: transparent !important; }
:deep(.el-table__cell) { border-bottom: 1px solid var(--border-color) !important; }

/* 状态 Badge */
.status-glow-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.05);
}

.status-glow-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.s-success { color: #10b981; }
.s-success .dot { background: #10b981; box-shadow: 0 0 8px #10b981; }

.s-warning { color: #f59e0b; }
.s-warning .dot { background: #f59e0b; box-shadow: 0 0 8px #f59e0b; }

.s-error { color: #f43f5e; }
.s-error .dot { background: #f43f5e; box-shadow: 0 0 8px #f43f5e; }

/* 方法类型字体 */
.method-text { font-weight: 800; font-size: 12px; }
.method-text.post { color: #10b981; }
.method-text.get { color: var(--text-highlight); }
.method-text.delete { color: #f43f5e; }

/* 路径与用户 */
.path-code {
  background: rgba(0,0,0,0.3);
  padding: 4px 8px;
  border-radius: 6px;
  color: var(--text-secondary);
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #a5f3fc;
  font-size: 13px;
}

/* 时间与耗时 */
.time-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #4ade80;
}
.time-tag.slow { color: #f87171; font-weight: bold; }

.time-stamp { color: var(--text-secondary); font-size: 12px; }

/* 分页适配 */
.pagination-container {
  margin-top: 20px;
  padding: 10px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-pagination button) { background: transparent !important; color: var(--text-main) !important; }
:deep(.el-pagination .el-pager li) { background: transparent !important; color: var(--text-secondary); }
:deep(.el-pagination .el-pager li.is-active) { color: var(--text-highlight) !important; font-weight: 900; }
</style>