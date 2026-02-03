<template>
  <div class="app-container">
    <!-- 🟢 头部布局：Flex 两端对齐 + 玻璃按钮 -->
    <div class="page-header">
      <div class="title-area">
        <span class="icon">🚀</span>
        <h1>路由矩阵控制台</h1>
      </div>

      <div class="action-area">
        <el-button type="primary" plain @click="handleRefresh">
          <el-icon class="el-icon--left"><Refresh /></el-icon>刷新列表
        </el-button>
        <el-button type="success" @click="handleAdd">
          <el-icon class="el-icon--left"><Plus /></el-icon>新增路由
        </el-button>
      </div>
    </div>

    <!-- 数据表格 -->
    <el-card class="box-card">
      <el-table :data="tableData" style="width: 100%" stripe border v-loading="loading">
        <el-table-column prop="id" label="路由 ID" width="180" />
        <el-table-column prop="uri" label="转发目标 URI" width="200"/>
        <el-table-column prop="order" label="优先级" width="80" align="center"/>

        <!-- 断言列 -->
        <el-table-column label="断言 (Predicates)" min-width="250">
          <template #default="scope">
            <div v-for="(p, i) in scope.row.predicates" :key="i" style="margin-bottom: 5px">
              <el-tag size="small">{{ p.name }}</el-tag>
              <span style="font-size: 12px; margin-left: 8px; color: #94a3b8;">
                {{ p.args }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- 过滤器列 -->
        <el-table-column label="过滤器 (Filters)" min-width="200">
          <template #default="scope">
            <div v-for="(f, i) in scope.row.filters" :key="i" style="margin-bottom: 5px">
              <el-tag type="warning" size="small">{{ f.name }}</el-tag>
              <span style="font-size: 12px; margin-left: 8px; color: #94a3b8;">
                {{ f.args }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 弹窗保持不变 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="路由 ID">
          <el-input v-model="form.id" placeholder="例如: user-service" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="目标 URI">
          <el-input v-model="form.uri" placeholder="例如: lb://user-service" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.order" :min="0" />
        </el-form-item>
        <el-form-item label="断言配置">
          <el-input v-model="predicatesJson" type="textarea" :rows="4" placeholder='JSON格式' />
          <div class="tip">请输入标准的 JSON 数组格式</div>
        </el-form-item>
        <el-form-item label="过滤器配置">
          <el-input v-model="filtersJson" type="textarea" :rows="4" placeholder='JSON格式' />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoutes, saveRoute, deleteRoute } from '@/api/route'
import {Plus, Refresh} from "@element-plus/icons-vue"; // 引入我们封装的API

// 数据状态
const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)

// 表单数据
const form = ref({
  id: '',
  uri: '',
  order: 0,
  predicates: [],
  filters: []
})
// 为了方便编辑，这里把复杂对象转为JSON字符串处理
const predicatesJson = ref('[]')
const filtersJson = ref('[]')

// 1. 获取列表
const fetchData = async () => {
  loading.value = true
  try {
    // request 工具已经帮我们剥离了外层 Result，这里直接拿到 List
    const data = await getRoutes()
    tableData.value = data || []
    ElMessage.success('数据刷新成功')
  } finally {
    loading.value = false
  }
}

// 刷新按钮
const handleRefresh = () => {
  fetchData()
}

// 2. 删除逻辑
const handleDelete = (id) => {
  ElMessageBox.confirm(`确定要删除路由 [${id}] 吗?`, '警告', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await deleteRoute(id)
    ElMessage.success('删除成功，网关配置已更新')
    fetchData() // 重新加载列表
  })
}

// 3. 新增逻辑
const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '新增路由'
  form.value = { id: '', uri: '', order: 0 }
  // 默认给一个 Path 断言模板
  predicatesJson.value = JSON.stringify([{
    name: 'Path',
    args: { pattern: '/api/demo/**' }
  }], null, 2)

  // 默认 Filter 模板
  filtersJson.value = JSON.stringify([{
    name: 'StripPrefix',
    args: { parts: '1' }
  }], null, 2)
  dialogVisible.value = true
}

// 4. 编辑逻辑
const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑路由'
  // 深拷贝，防止修改表单时表格跟着变
  form.value = JSON.parse(JSON.stringify(row))
  // 转为 JSON 字符串供编辑
  predicatesJson.value = JSON.stringify(row.predicates || [], null, 2)
  filtersJson.value = JSON.stringify(row.filters || [], null, 2)
  dialogVisible.value = true
}

// 5. 提交表单
const submitForm = async () => {
  try {
    // 组装数据
    const submitData = {
      ...form.value,
      predicates: JSON.parse(predicatesJson.value),
      filters: JSON.parse(filtersJson.value)
    }

    await saveRoute(submitData) // 1. 提交保存
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    // 2. 延迟 800ms 再刷新列表，给 Nacos 一点同步时间
    setTimeout(() => {
      fetchData()
    }, 800)
  } catch (e) {
    ElMessage.error('JSON 格式错误或网络异常，请检查输入')
    console.error(e)
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
/* === 基础容器 === */
.app-container {
  padding: 30px;
  max-width: 1400px;
  margin: 0 auto;
  background-color: var(--bg-body); /* 确保背景色同步 */
}

/* === 头部容器 === */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 30px;
  padding: 0 10px;
}

/* 左侧标题区 */
.title-area {
  display: flex;
  align-items: center;
  gap: 15px;
}

.title-area .icon {
  font-size: 32px;
  /* 使用高亮变量实现发光感 */
  filter: drop-shadow(0 0 8px var(--text-highlight));
  color: var(--text-highlight);
}

h1 {
  margin: 0;
  font-size: 24px;
  color: var(--text-main);
  font-weight: 700;
  letter-spacing: 1px;
}

/* 右侧按钮区 */
.action-area {
  display: flex;
  gap: 15px;
}

/* --- 按钮样式：使用变量进行透明度重组 --- */

/* 1. 刷新按钮 (基于高亮色变量) */
:deep(.el-button--primary.is-plain) {
  background: var(--bg-glass) !important;
  border: 1px solid var(--text-highlight) !important;
  color: var(--text-highlight) !important;
  transition: all 0.3s ease;
}

:deep(.el-button--primary.is-plain:hover) {
  background: var(--text-highlight) !important;
  color: #ffffff !important; /* 强制白色以保证对比度 */
  box-shadow: var(--card-shadow);
  transform: translateY(-2px);
}

/* 2. 新增按钮 (使用变量保持统调) */
:deep(.el-button--success) {
  /* 成功按钮推荐保持绿色系，但边框和文字可微调适配全局变量感 */
  background: rgba(16, 185, 129, 0.2);
  border: 1px solid rgba(16, 185, 129, 0.5);
  color: #10b981;
}

:deep(.el-button--success:hover) {
  background: #10b981;
  color: #ffffff;
  box-shadow: var(--card-shadow);
  transform: translateY(-2px);
}

/* 方形圆角统一 */
:deep(.el-button) {
  border-radius: 6px;
  padding: 10px 18px;
  font-weight: 600;
}

/* --- 表格区域样式优化 (彻底变量化) --- */
.box-card {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 12px;
  backdrop-filter: blur(16px);
  box-shadow: var(--card-shadow);
}

:deep(.el-table) {
  background-color: transparent !important;
  color: var(--text-main) !important; /* 主文字颜色 */
  --el-table-border-color: var(--border-color);
  --el-table-header-bg-color: var(--bg-glass);
  --el-table-row-hover-bg-color: var(--bg-glass) !important;
}

/* 表头颜色：使用高亮色变量 */
:deep(.el-table th.el-table__cell) {
  background-color: var(--bg-glass) !important;
  color: var(--text-highlight) !important;
  font-weight: bold;
  border-bottom: 2px solid var(--border-color) !important;
}

/* 表格行与单元格 */
:deep(.el-table tr),
:deep(.el-table td.el-table__cell) {
  background-color: transparent !important;
  border-bottom: 1px solid var(--border-color) !important;
}

/* 单元格文字颜色适配 */
:deep(.el-table .cell) {
  white-space: nowrap;
  color: var(--text-main);
}

/* --- 标签样式 --- */
:deep(.el-tag) {
  background-color: var(--bg-glass) !important;
  border: 1px solid var(--border-color);
  color: var(--text-highlight);
}

/* 警告标签：由于变量里没定义警告色，采用 rgba 模拟并绑定变量感 */
:deep(.el-tag--warning) {
  background-color: rgba(245, 158, 11, 0.1);
  border-color: rgba(245, 158, 11, 0.3);
  color: #f59e0b;
}

/* 操作列文字按钮 */
:deep(.el-button--text),
:deep(.el-button.is-text) {
  color: var(--text-highlight) !important;
  font-weight: bold;
}

:deep(.el-button--text:hover),
:deep(.el-button.is-text:hover) {
  opacity: 0.8;
  text-decoration: underline;
}

/* 辅助说明文字 */
.tip {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-top: 10px;
}

/* 适配深色模式下的表格斑马纹或特殊背景 */
html.dark :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
}
</style>
