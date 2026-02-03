<template>
  <div class="app-container">
    <div class="page-header">
      <h1>📜 审计日志 (Audit Logs)</h1>
      <!-- 搜索栏 -->
      <div class="filter-box">
        <el-input
            v-model="queryParams.path"
            placeholder="搜索请求路径..."
            prefix-icon="Search"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
            class="search-input"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>
    </div>

    <!-- 日志表格 -->
    <el-card class="glass-card">
      <el-table
          :data="tableData"
          style="width: 100%"
          v-loading="loading"
          element-loading-background="rgba(0, 0, 0, 0.5)"
      >
        <!-- 状态码 (带颜色小圆点) -->
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="scope">
            <div class="status-badge" :class="getStatusClass(scope.row.status)">
              <span class="dot"></span>
              {{ scope.row.status }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="method" label="方法" width="100">
          <template #default="scope">
            <el-tag :type="getMethodType(scope.row.method)">{{ scope.row.method }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="path" label="请求路径" min-width="250" show-overflow-tooltip />

        <el-table-column prop="ip" label="来源 IP" width="140" />

        <el-table-column prop="userId" label="操作人" width="120">
          <template #default="scope">
            <span style="color: #a5f3fc">{{ scope.row.userId || '游客' }}</span>
          </template>
        </el-table-column>

        <!-- 耗时 (慢请求标红) -->
        <el-table-column prop="responseTime" label="耗时" width="120" sortable>
          <template #default="scope">
            <span :style="{ color: scope.row.responseTime > 500 ? '#f87171' : '#4ade80' }">
              {{ scope.row.responseTime }} ms
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="requestTime" label="请求时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.requestTime) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页组件 -->
      <div class="pagination-container">
        <el-pagination
            v-model:current-page="queryParams.page"
            v-model:page-size="queryParams.size"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="handleSearch"
            @current-change="fetchData"
            background
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLogList } from '@/api/log'
import { Search } from '@element-plus/icons-vue'

// 数据状态
const tableData = ref([])
const total = ref(0)
const loading = ref(false)

// 查询参数
const queryParams = reactive({
  page: 1,
  size: 10,
  path: ''
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getLogList(queryParams)
    // 根据后端返回结构调整，假设是 { records: [], total: 100 }
    tableData.value = res.records
    total.value = res.total
  } catch (error) {
    // 错误处理已在拦截器做过，这里可忽略
  } finally {
    loading.value = false
  }
}

// 搜索按钮
const handleSearch = () => {
  queryParams.page = 1 // 重置到第一页
  fetchData()
}

// 辅助函数：状态码样式
const getStatusClass = (status) => {
  if (status >= 200 && status < 300) return 'success'
  if (status >= 400 && status < 500) return 'warning'
  return 'error'
}

// 辅助函数：Method 标签颜色
const getMethodType = (method) => {
  const map = { GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger' }
  return map[method] || 'info'
}

// 辅助函数：简单时间格式化
const formatTime = (isoStr) => {
  if (!isoStr) return ''
  return new Date(isoStr).toLocaleString()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.app-container {
  padding: 30px;
  max-width: 1400px;
  margin: 0 auto;
  background-color: var(--bg-body); /* 确保背景同步 */
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 25px;
}

h1 {
  color: var(--text-main);
  font-size: 24px;
  /* 发光效果适配高亮变量 */
  text-shadow: 0 0 10px rgba(var(--text-highlight), 0.3);
}

.filter-box {
  display: flex;
  gap: 10px;
}
.search-input {
  width: 300px;
}

/* 玻璃卡片：去掉写死的深色，改用变量 */
.glass-card {
  background: var(--bg-header); /* 使用全局透明背景变量 */
  border: 1px solid var(--border-color);
  backdrop-filter: blur(12px) saturate(180%);
  border-radius: 12px;
  box-shadow: var(--card-shadow);
}

/* 状态小圆点：保持业务色，但背景适配变量感 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 20px;
  font-weight: 600;
  font-size: 13px;
  border: 1px solid transparent;
}
.status-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

/* 业务状态颜色：利用变量混合或保留明确的语义色 */
.success {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border-color: rgba(16, 185, 129, 0.2);
}
.success .dot { background: #10b981; box-shadow: 0 0 6px #10b981; }

.warning {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  border-color: rgba(245, 158, 11, 0.2);
}
.warning .dot { background: #f59e0b; box-shadow: 0 0 6px #f59e0b; }

.error {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.2);
}
.error .dot { background: #ef4444; box-shadow: 0 0 6px #ef4444; }

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 强制覆盖 Element 表格样式 (适配全主题) */
:deep(.el-table) {
  background-color: transparent !important;
  color: var(--text-main); /* 正文使用主色 */
  --el-table-row-hover-bg-color: var(--bg-glass) !important;
  --el-table-border-color: var(--border-color);
}

:deep(.el-table th.el-table__cell) {
  background-color: var(--bg-glass) !important;
  color: var(--text-highlight) !important; /* 表头高亮 */
  font-weight: bold;
}

:deep(.el-table tr),
:deep(.el-table td.el-table__cell) {
  background-color: transparent !important;
  border-bottom: 1px solid var(--border-color) !important;
}

/* 输入框适配 */
:deep(.el-input__wrapper) {
  background-color: var(--bg-glass) !important;
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--text-highlight) inset !important;
}

:deep(.el-input__inner) {
  color: var(--text-main) !important;
}

/* 分页组件适配 (可选) */
:deep(.el-pagination button) {
  background-color: transparent !important;
  color: var(--text-secondary) !important;
}
:deep(.el-pagination .is-active) {
  color: var(--text-highlight) !important;
  font-weight: bold;
}
</style>