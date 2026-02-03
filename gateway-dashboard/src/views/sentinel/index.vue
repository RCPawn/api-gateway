<template>
  <div class="app-container">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="title-block">
        <h1>🛡️ 流量防卫控制台</h1>
        <p class="subtitle">Sentinel 实时流量治理与熔断降级中心</p>
      </div>
      <div class="action-block">
        <el-button type="primary" size="large" @click="handleRefresh">🔄 刷新状态</el-button>
        <el-button type="success" size="large" @click="handleAddNew">➕ 新增资源保护</el-button>
      </div>
    </div>

    <!-- 卡片矩阵区域 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in list" :key="item.resource" class="card-col">
        <el-card class="defense-card" shadow="hover">
          <!-- 卡片头部: 资源名 -->
          <template #header>
            <div class="card-header">
              <span class="resource-name" :title="item.resource">
                <el-icon class="icon"><Connection /></el-icon>
                {{ formatResource(item.resource) }}
              </span>
              <el-tag type="info" size="small" effect="plain">RESOURCE</el-tag>
            </div>
          </template>

          <!-- 卡片主体: 规则概览 -->
          <div class="card-body">

            <!-- 1. 限流板块 -->
            <div class="status-row" :class="{ 'active': item.flowRule }">
              <div class="label">
                <el-icon><Odometer /></el-icon> 流控 (Flow)
              </div>
              <div class="value" v-if="item.flowRule">
                <span class="highlight">{{ item.flowRule.count }}</span>
                <span class="unit">{{ item.flowRule.grade === 1 ? 'QPS' : 'Thread' }}</span>
              </div>
              <div class="value disabled" v-else>未配置</div>
            </div>
            <!-- 模拟进度条效果 -->
            <el-progress
                v-if="item.flowRule"
                :percentage="Math.min(item.flowRule.count / 20, 100)"
                :show-text="false"
                :status="item.flowRule.count > 500 ? 'exception' : 'success'"
                class="mini-progress"
            />

            <!-- 2. 降级板块 -->
            <div class="status-row mt-3" :class="{ 'active': item.degradeRule }">
              <div class="label">
                <el-icon><SwitchButton /></el-icon> 熔断 (Fuse)
              </div>
              <div class="value" v-if="item.degradeRule">
                <el-tag size="small" type="danger" effect="dark">
                  {{ formatDegradeGrade(item.degradeRule.grade) }}
                </el-tag>
              </div>
              <div class="value disabled" v-else>未配置</div>
            </div>
            <div class="desc-text" v-if="item.degradeRule">
              阈值 {{ item.degradeRule.count }} / 窗口 {{ item.degradeRule.timeWindow }}s
            </div>

          </div>

          <!-- 卡片底部: 操作按钮 -->
          <div class="card-footer">
            <el-button link type="primary" @click="handleEdit(item)">⚙️ 规则配置</el-button>
            <el-button link type="danger" @click="handleDelete(item.resource)">🗑️ 移除</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 抽屉: 编辑规则 -->
    <el-drawer
        v-model="drawerVisible"
        :title="isEditMode ? '⚙️ 配置规则策略' : '➕ 新增资源保护'"
        size="500px"
        destroy-on-close
    >
      <div class="drawer-content">
        <!-- 资源名称 (只读或输入) -->
        <el-form label-position="top">
          <el-form-item label="目标资源 (Resource URI)">
            <el-input
                v-model="currentResourceName"
                placeholder="例如: lb://service-provider/api/hello"
                :disabled="isEditMode"
            >
              <template #prefix><el-icon><Link /></el-icon></template>
            </el-input>
          </el-form-item>
        </el-form>

        <el-divider content-position="left">策略配置</el-divider>

        <el-tabs type="border-card" class="rule-tabs">

          <!-- Tab 1: 流量控制 -->
          <el-tab-pane label="🚀 流量控制">
            <el-form :model="flowForm" label-width="120px">
              <el-form-item label="阈值类型">
                <el-radio-group v-model="flowForm.grade">
                  <el-radio :label="1">QPS (每秒请求数)</el-radio>
                  <el-radio :label="0">并发线程数</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="限流阈值">
                <el-input-number v-model="flowForm.count" :min="1" :step="10" />
              </el-form-item>
              <el-form-item label="流控效果">
                <el-select v-model="flowForm.controlBehavior">
                  <el-option label="快速失败 (Default)" :value="0" />
                  <el-option label="Warm Up (预热)" :value="1" />
                  <el-option label="排队等待" :value="2" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="submitFlow">💾 保存流控规则</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <!-- Tab 2: 熔断降级 -->
          <el-tab-pane label="🔌 熔断降级">
            <el-form :model="degradeForm" label-width="120px">
              <el-form-item label="熔断策略">
                <el-select v-model="degradeForm.grade">
                  <el-option label="慢调用比例 (响应时间)" :value="0" />
                  <el-option label="异常比例" :value="1" />
                  <el-option label="异常数" :value="2" />
                </el-select>
              </el-form-item>

              <el-form-item label="触发阈值">
                <el-input v-model="degradeForm.count" type="number">
                  <template #append>
                    {{ degradeForm.grade === 0 ? 'ms' : (degradeForm.grade === 1 ? 'Ratio(0-1)' : '次') }}
                  </template>
                </el-input>
                <div class="tip" v-if="degradeForm.grade === 0">当响应时间超过此值，记为慢调用</div>
              </el-form-item>

              <el-form-item label="熔断时长(s)">
                <el-input-number v-model="degradeForm.timeWindow" :min="1" />
              </el-form-item>

              <el-form-item label="最小请求数">
                <el-input-number v-model="degradeForm.minRequestAmount" :min="1" />
              </el-form-item>

              <el-form-item label="统计时长(ms)">
                <el-input-number v-model="degradeForm.statIntervalMs" :min="1000" :step="1000" />
              </el-form-item>

              <el-form-item>
                <el-button type="danger" @click="submitDegrade">💾 保存熔断规则</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Odometer, SwitchButton, Link } from '@element-plus/icons-vue'
import { getSentinelResources, saveFlowRule, saveDegradeRule, deleteResource } from '@/api/sentinel'

// --- 状态定义 ---
const loading = ref(false)
const list = ref([])
const drawerVisible = ref(false)
const isEditMode = ref(false)
const currentResourceName = ref('')

// 表单数据 (默认值)
const flowForm = ref({
  grade: 1,
  count: 10,
  controlBehavior: 0,
  limitApp: 'default',
  strategy: 0
})

const degradeForm = ref({
  grade: 0,
  count: 1000,
  timeWindow: 10,
  minRequestAmount: 5,
  statIntervalMs: 1000,
  slowRatioThreshold: 0.6
})

// --- 核心逻辑 ---

// 1. 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const data = await getSentinelResources()
    list.value = data || []
  } finally {
    loading.value = false
  }
}

// 2. 打开新增/编辑 抽屉
const handleAddNew = () => {
  isEditMode.value = false
  currentResourceName.value = ''
  resetForms()
  drawerVisible.value = true
}

const handleEdit = (item) => {
  isEditMode.value = true
  currentResourceName.value = item.resource

  // 回显数据 (如果有配置，用配置的；没配置，用默认值)
  if (item.flowRule) {
    flowForm.value = { ...item.flowRule }
  } else {
    resetFlowForm()
  }

  if (item.degradeRule) {
    degradeForm.value = { ...item.degradeRule }
  } else {
    resetDegradeForm()
  }

  drawerVisible.value = true
}

// 3. 提交流控
const submitFlow = async () => {
  if (!currentResourceName.value) return ElMessage.warning('请填写资源名称')
  try {
    const payload = { ...flowForm.value, resource: currentResourceName.value }
    await saveFlowRule(payload)
    ElMessage.success('流控规则已更新')
    fetchData()
  } catch (e) { console.error(e) }
}

// 4. 提交熔断
const submitDegrade = async () => {
  if (!currentResourceName.value) return ElMessage.warning('请填写资源名称')
  try {
    const payload = { ...degradeForm.value, resource: currentResourceName.value }
    await saveDegradeRule(payload)
    ElMessage.success('熔断规则已更新')
    fetchData()
  } catch (e) { console.error(e) }
}

// 5. 删除
const handleDelete = (resource) => {
  ElMessageBox.confirm(`确定要移除对资源 [${resource}] 的所有保护吗?`, '高危操作', {
    confirmButtonText: '确定移除',
    confirmButtonClass: 'el-button--danger',
    type: 'warning'
  }).then(async () => {
    await deleteResource(resource)
    ElMessage.success('资源防护已卸载')
    fetchData()
  })
}

// --- 工具函数 ---
const handleRefresh = () => fetchData()

// 重置表单到默认值
const resetForms = () => { resetFlowForm(); resetDegradeForm() }
const resetFlowForm = () => flowForm.value = { grade: 1, count: 20, controlBehavior: 0, limitApp: 'default', strategy: 0 }
const resetDegradeForm = () => degradeForm.value = { grade: 0, count: 500, timeWindow: 5, minRequestAmount: 5, statIntervalMs: 1000, slowRatioThreshold: 0.5 }

// 格式化显示
const formatResource = (str) => {
  // 如果太长，截断显示
  return str.length > 30 ? '...' + str.slice(-25) : str
}

const formatDegradeGrade = (g) => {
  const map = { 0: '慢调用(RT)', 1: '异常比例', 2: '异常数' }
  return map[g] || '未知'
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
/* 容器样式 */
.app-container {
  padding: 24px;
  background-color: var(--bg-body);
  min-height: 100vh;
}

/* 顶部 Header */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  background: var(--bg-card);
  padding: 20px;
  border-radius: 8px;
  box-shadow: var(--card-shadow);
}
.title-block h1 { margin: 0; font-size: 24px; color: var(--text-main); }
.subtitle { margin: 5px 0 0; color: var(--text-secondary); font-size: 13px; }

/* 卡片样式 */
.card-col { margin-bottom: 20px; }
.defense-card {
  border-radius: 8px;
  border: 1px solid var(--border-color);
  transition: all 0.3s;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}
.defense-card:hover { transform: translateY(-5px); box-shadow: var(--card-shadow); }

/* 卡片头部 */
.card-header { display: flex; justify-content: space-between; align-items: center; }
.resource-name { font-weight: bold; font-size: 15px; color: var(--text-main); display: flex; align-items: center; gap: 5px;}
.icon { font-size: 16px; color: var(--text-highlight); }

/* 卡片 Body */
.card-body { flex: 1; padding: 10px 0; }
.status-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; color: var(--text-secondary); }
.status-row.active { color: var(--text-main); font-weight: 500; }
.status-row.mt-3 { margin-top: 15px; }

.label { display: flex; align-items: center; gap: 5px; font-size: 14px; color: var(--text-main); }
.value { font-size: 14px; color: var(--text-main); }
.value.disabled { color: var(--text-secondary); font-size: 12px; }
.highlight { font-size: 18px; color: #67C23A; font-weight: bold; margin-right: 4px; }
.unit { font-size: 12px; color: var(--text-secondary); }

.mini-progress { margin-top: 5px; }
.desc-text { font-size: 12px; color: var(--text-secondary); margin-top: 5px; text-align: right; }

/* 卡片底部 */
.card-footer {
  border-top: 1px solid var(--border-color);
  padding-top: 10px;
  margin-top: 10px;
  text-align: right;
}

/* 强制让抽屉背景变深色 */
:deep(.el-drawer) {
  background-color: var(--bg-card) !important;
  border-left: 1px solid var(--border-color);
}

/* 抽屉标题栏 */
:deep(.el-drawer__header) {
  color: var(--text-main);
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 0;
  padding: 20px;
}
:deep(.el-drawer__title) {
  color: var(--text-main); /* 确保标题文字也是亮的 */
}

/* 抽屉主体 */
:deep(.el-drawer__body) {
  padding: 20px;
  background-color: var(--bg-card) !important; /* 双重保险 */
}

/* 解决遮罩层位置问题 (避让导航栏) */
:deep(.el-overlay) {
  top: 80px !important; /* 稍微多留一点距离，防止贴着导航栏 */
  height: calc(100% - 80px) !important;
}

/* 去掉整个 Tab 组件的边框和背景 */
:deep(.el-tabs--border-card) {
  background-color: transparent !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: none !important;
}

/* 修复 Tab 头部 (选项卡那一栏) */
:deep(.el-tabs--border-card > .el-tabs__header) {
  background-color: rgba(0, 0, 0, 0.2) !important; /* 给表头一点深色底，区分层次 */
  border-bottom: 1px solid var(--border-color) !important;
}

/* 修复 Tab 每一个选项卡 (未选中状态) */
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) {
  color: var(--text-secondary) !important;
  border-right: 1px solid var(--border-color) !important;
  transition: all 0.3s;
}

/* 修复 Tab 每一个选项卡 (选中状态) */
:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  color: var(--text-highlight) !important;
  background-color: var(--bg-card) !important; /* 选中后背景变成抽屉的颜色，看起来融合 */
  border-right-color: var(--border-color) !important;
  border-left-color: var(--border-color) !important;
}

/* 修复 Tab 内容区域 */
:deep(.el-tabs--border-card > .el-tabs__content) {
  padding: 20px;
  background-color: transparent !important; /* 内容区透明，透出抽屉底色 */
}

/* 输入框 */
:deep(.el-input__wrapper),
:deep(.el-input-number__decrease),
:deep(.el-input-number__increase) {
  background-color: var(--bg-glass) !important; /* 使用玻璃底色 */
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
  color: var(--text-main);
}

/* 输入框内文字 */
:deep(.el-input__inner) {
  color: var(--text-main) !important;
}

/* 表单 Label */
:deep(.el-form-item__label) {
  color: var(--text-secondary) !important;
}

/* 单选框文字 */
:deep(.el-radio) {
  color: var(--text-main) !important;
}

/* 其他辅助文字 */
.tip {
  font-size: 12px;
  color: var(--text-secondary);
  opacity: 0.8;
  margin-top: 8px;
}
</style>