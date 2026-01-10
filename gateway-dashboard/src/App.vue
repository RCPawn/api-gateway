<template>
  <div class="app-container">
    <h1>🚀 网关路由管理控制台</h1>

    <!-- 顶部操作栏 -->
    <div class="header">
      <el-button type="primary" @click="handleRefresh">🔄 刷新列表</el-button>
      <el-button type="success" @click="handleAdd">➕ 新增路由</el-button>
    </div>

    <!-- 数据表格 -->
    <el-card class="box-card">
      <el-table :data="tableData" style="width: 100%" stripe border v-loading="loading">
        <el-table-column prop="id" label="路由 ID" width="180" />
        <el-table-column prop="uri" label="转发目标 URI" width="200"/>
        <el-table-column prop="order" label="优先级" width="80" align="center"/>

        <!-- 修改后的：断言列 (显示参数) -->
        <el-table-column label="断言 (Predicates)" min-width="250">
          <template #default="scope">
            <div v-for="(p, i) in scope.row.predicates" :key="i" style="margin-bottom: 5px">
              <!-- 标签显示名字 -->
              <el-tag size="small">{{ p.name }}</el-tag>
              <!-- 后面跟上具体参数 -->
              <span style="font-size: 12px; margin-left: 8px; color: #666;">
                {{ p.args }}
              </span>
            </div>
          </template>
        </el-table-column>

        <!-- 修改后的：过滤器列 (显示参数) -->
        <el-table-column label="过滤器 (Filters)" min-width="250">
          <template #default="scope">
            <div v-for="(f, i) in scope.row.filters" :key="i" style="margin-bottom: 5px">
              <el-tag type="warning" size="small">{{ f.name }}</el-tag>
              <span style="font-size: 12px; margin-left: 8px; color: #666;">
                {{ f.args }}
              </span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="handleEdit(scope.row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 弹窗 -->
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

        <!-- 简化版：这里暂时用JSON输入，后续可以做成动态表单 -->
        <el-form-item label="断言配置">
          <el-input
              v-model="predicatesJson"
              type="textarea"
              :rows="4"
              placeholder='JSON格式，例: [{"name":"Path","args":{"pattern":"/user/**"}}]'
          />
          <div class="tip">请输入标准的 JSON 数组格式</div>
        </el-form-item>

        <el-form-item label="过滤器配置">
          <el-input
              v-model="filtersJson"
              type="textarea"
              :rows="4"
              placeholder='JSON格式，例: [{"name":"StripPrefix","args":{"parts":"1"}}]'
          />
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
import { getRoutes, saveRoute, deleteRoute } from '@/api/route' // 引入我们封装的API

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
  filtersJson.value = '[]'
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

    await saveRoute(submitData)
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    fetchData()
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
.app-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.header {
  margin-bottom: 20px;
  display: flex;
  gap: 10px;
}
.tip {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
}
.mx-1 {
  margin-right: 5px;
  margin-bottom: 5px;
}
</style>