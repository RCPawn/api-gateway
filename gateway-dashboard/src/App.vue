<template>
  <div class="app-container">
    <h1>🚀 网关路由管理控制台</h1>

    <!-- 顶部操作栏 -->
    <div class="header">
      <el-button type="primary" @click="fetchRoutes">🔄 刷新列表</el-button>
      <el-button type="success">➕ 新增路由 (待开发)</el-button>
    </div>

    <!-- 数据表格 -->
    <el-card class="box-card">
      <el-table :data="tableData" style="width: 100%" stripe border>
        <el-table-column prop="id" label="路由 ID" width="180" />
        <el-table-column prop="uri" label="转发目标 URI" width="180"/>
        <el-table-column prop="order" label="优先级" width="80" align="center"/>

        <!-- 展示断言 (Predicates) -->
        <el-table-column label="断言规则 (Predicates)" width="200">
          <template #default="scope">
            <div v-for="(item, index) in scope.row.predicates" :key="index">
              <el-tag size="small">{{ item.name }}</el-tag>
              {{ item.args }}
            </div>
          </template>
        </el-table-column>

        <!-- 展示过滤器 (Filters) -->
        <el-table-column label="过滤器 (Filters)" width="200">
          <template #default="scope">
            <div v-for="(item, index) in scope.row.filters" :key="index">
              <el-tag type="warning" size="small">{{ item.name }}</el-tag>
              {{ item.args }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="100" align="center">
          <template #default>
            <el-button link type="primary" size="small">编辑</el-button>
            <el-button link type="danger" size="small">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// 表格数据
const tableData = ref([])

// 获取路由列表的方法
const fetchRoutes = async () => {
  try {
    // 调用后端的接口
    const res = await axios.get('http://localhost:9000/admin/routes')
    tableData.value = res.data
    ElMessage.success('数据加载成功')
  } catch (error) {
    console.error(error)
    ElMessage.error('加载失败，请检查后端是否启动或跨域配置')
  }
}

// 页面加载时自动调用
onMounted(() => {
  fetchRoutes()
})
</script>

<style>
.app-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}
.header {
  margin-bottom: 20px;
}
</style>