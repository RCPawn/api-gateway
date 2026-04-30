<template>
  <div class="app-container">
    <div class="action-bar">
      <div class="bar-left"></div>
      <div class="bar-right">
        <div class="search-box">
          <el-input
              v-model="searchKeyword"
              placeholder="搜索路径或资源名..."
              clearable
              class="glass-input"
          >
            <template #prefix>
              <el-icon>
                <Search/>
              </el-icon>
            </template>
          </el-input>
        </div>

        <div class="btn-group">
          <el-button type="primary" plain class="glass-btn" @click="handleRefresh">
            <el-icon>
              <Refresh/>
            </el-icon>
            刷新
          </el-button>
          <el-button type="primary" class="glow-btn" @click="handleAddNew">
            <el-icon>
              <Plus/>
            </el-icon>
            新增规则
          </el-button>
        </div>
      </div>
    </div>

    <div class="card-grid" v-loading="loading">
      <div v-for="item in filteredList" :key="item.resource" class="defense-card">

        <div class="card-header">
          <el-tooltip :content="item.resource" placement="top" :show-after="500">
            <div class="resource-id">
              <el-icon class="icon">
                <Connection/>
              </el-icon>
              {{ formatResource(item.resource) }}
            </div>
          </el-tooltip>
          <el-tag size="small" effect="dark" type="info" round class="order-tag">RESOURCE</el-tag>
        </div>

        <div class="status-comparison">
          <div class="status-node" :class="{ 'active-node': item.flowRule }">
            <div class="node-label">流控 (Flow)</div>
            <div class="node-val">
              <template v-if="item.flowRule">
                <span class="highlight">{{ item.flowRule.count }}</span>
                <span class="unit">{{ item.flowRule.grade === 1 ? 'QPS' : '线程' }}</span>
              </template>
              <span v-else class="empty-val">未开启</span>
            </div>
            <el-progress
                v-if="item.flowRule"
                :percentage="Math.min(item.flowRule.count / 2, 100)"
                :show-text="false"
                :stroke-width="2"
                :status="item.flowRule.count > 100 ? 'exception' : 'success'"
                class="node-progress"
            />
          </div>

          <div class="divider-line"></div>

          <div class="status-node target" :class="{ 'active-node': item.degradeRule }">
            <div class="node-label">熔断 (Fuse)</div>
            <div class="node-val">
              <template v-if="item.degradeRule">
                <el-tag size="small" type="danger" effect="plain" class="fuse-tag">
                  {{ formatDegradeGrade(item.degradeRule.grade) }}
                </el-tag>
              </template>
              <span v-else class="empty-val">未开启</span>
            </div>
            <div class="node-desc" v-if="item.degradeRule">
              {{ item.degradeRule.count }}{{ item.degradeRule.grade === 0 ? 'ms' : '阈值' }}
            </div>
          </div>
        </div>

        <div class="card-footer">
          <div class="details">
            <span class="detail-item" v-if="item.flowRule">
              <el-icon><Odometer/></el-icon> 监控中
            </span>
            <span class="detail-item" v-if="item.degradeRule">
              <el-icon><SwitchButton/></el-icon> 已降级
            </span>
          </div>

          <div class="actions">
            <el-button circle text type="primary" @click="handleEdit(item)">
              <el-icon>
                <EditPen/>
              </el-icon>
            </el-button>
            <el-button circle text type="danger" @click="handleDelete(item.resource)">
              <el-icon>
                <Delete/>
              </el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <el-empty v-if="filteredList.length === 0 && !loading" description="未发现匹配的防护资源" style="width: 100%"/>
    </div>

    <el-dialog
        v-model="dialogVisible"
        :title="isEditMode ? '🛠️ 配置规则策略' : '🚀 新增资源保护'"
        width="580px"
        class="glass-dialog"
    >
      <div class="dialog-content">
        <el-form label-position="top">
          <el-form-item label="目标资源 (Resource URI)">
            <el-input v-model="currentResourceName" :disabled="isEditMode"
                      placeholder="例如: lb://user-service/api/info">
              <template #prefix>
                <el-icon>
                  <Link/>
                </el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-tabs v-model="activeRuleTab" type="border-card" class="rule-tabs mt-4">
            <el-tab-pane label="🚀 流量控制" name="flow">
              <el-form :model="flowForm" label-width="120px" class="inner-form">
                <el-form-item label="阈值类型">
                  <el-radio-group v-model="flowForm.grade">
                    <el-radio :label="1">QPS (每秒请求)</el-radio>
                    <el-radio :label="0">并发线程数</el-radio>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="单机阈值">
                  <el-input-number v-model="flowForm.count" :min="1"/>
                </el-form-item>
                <el-form-item label="流控效果">
                  <el-select v-model="flowForm.controlBehavior" class="w-full">
                    <el-option label="快速失败" :value="0"/>
                    <el-option label="Warm Up (预热)" :value="1"/>
                    <el-option label="排队等待" :value="2"/>
                  </el-select>
                </el-form-item>
                <el-button type="primary" class="glow-btn w-full mt-4" @click="submitFlow">💾 保存流控规则</el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="🔌 熔断降级" name="degrade">
              <el-form :model="degradeForm" label-width="120px" class="inner-form">
                <el-form-item label="熔断策略">
                  <el-select v-model="degradeForm.grade" class="w-full">
                    <el-option label="慢调用比例 (RT)" :value="0"/>
                    <el-option label="异常比例" :value="1"/>
                    <el-option label="异常数" :value="2"/>
                  </el-select>
                </el-form-item>
                <el-form-item label="触发阈值">
                  <el-input v-model="degradeForm.count" type="number">
                    <template #append>{{ degradeForm.grade === 0 ? 'ms' : '阈值' }}</template>
                  </el-input>
                </el-form-item>
                <el-form-item label="熔断时长(s)">
                  <el-input-number v-model="degradeForm.timeWindow" :min="1"/>
                </el-form-item>
                <el-form-item label="最小请求数">
                  <el-input-number v-model="degradeForm.minRequestAmount" :min="1"/>
                </el-form-item>
                <el-form-item label="统计时长(ms)">
                  <el-input-number v-model="degradeForm.statIntervalMs" :min="1000" :step="1000"/>
                </el-form-item>
                <el-button type="danger" class="glow-btn-danger w-full mt-2" @click="submitDegrade">💾 保存熔断规则
                </el-button>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, computed, onMounted} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {Connection, Odometer, SwitchButton, Link, Refresh, Plus, EditPen, Delete, Search} from '@element-plus/icons-vue'
import {getSentinelResources, saveFlowRule, saveDegradeRule, deleteResource} from '@/api/sentinel'

const loading = ref(false)
const list = ref([])
const searchKeyword = ref('')
const dialogVisible = ref(false)
const isEditMode = ref(false)
const currentResourceName = ref('')
/** 配置弹窗内当前页签：保存限流后切到熔断，便于连续配置 */
const activeRuleTab = ref('flow')

// 搜索过滤：路径和资源名均可搜索
const filteredList = computed(() => {
  if (!searchKeyword.value) return list.value
  const key = searchKeyword.value.toLowerCase()
  return list.value.filter(item => item.resource.toLowerCase().includes(key))
})

// 表单默认值定义
const flowForm = ref({grade: 1, count: 10, controlBehavior: 0, limitApp: 'default', strategy: 0})
const degradeForm = ref({
  grade: 0,
  count: 1000,
  timeWindow: 10,
  minRequestAmount: 5,
  statIntervalMs: 1000,
  slowRatioThreshold: 0.6
})

const fetchData = async (silent = false) => {
  if (!silent) loading.value = true
  try {
    const data = await getSentinelResources()
    list.value = data || []
  } finally {
    if (!silent) loading.value = false
  }
}

/** Nacos 写入后立刻拉取可能仍是旧数据，用表单结果合并到列表以保证卡片即时更新 */
const num = (v, fallback = 0) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : fallback
}

const buildFlowPayload = () => ({
  ...flowForm.value,
  resource: currentResourceName.value,
  count: num(flowForm.value.count, 1)
})

const buildDegradePayload = () => ({
  ...degradeForm.value,
  resource: currentResourceName.value,
  count: num(degradeForm.value.count, 0),
  grade: degradeForm.value.grade,
  timeWindow: degradeForm.value.timeWindow,
  minRequestAmount: degradeForm.value.minRequestAmount,
  statIntervalMs: degradeForm.value.statIntervalMs,
  slowRatioThreshold: num(degradeForm.value.slowRatioThreshold, 0.5)
})

const mergeResourceSnapshot = (resource, { flowRule, degradeRule }) => {
  const idx = list.value.findIndex((i) => i.resource === resource)
  if (idx < 0) {
    list.value = [...list.value, { resource, flowRule: flowRule ?? null, degradeRule: degradeRule ?? null }]
    return
  }
  list.value = list.value.map((item, i) => {
    if (i !== idx) return item
    return {
      ...item,
      ...(flowRule !== undefined ? { flowRule } : {}),
      ...(degradeRule !== undefined ? { degradeRule } : {})
    }
  })
}

const handleAddNew = () => {
  isEditMode.value = false
  currentResourceName.value = ''
  activeRuleTab.value = 'flow'
  resetForms()
  dialogVisible.value = true
}

const handleEdit = (item) => {
  isEditMode.value = true
  currentResourceName.value = item.resource
  activeRuleTab.value = 'flow'
  if (item.flowRule) flowForm.value = {...item.flowRule}
  else resetFlowForm()
  if (item.degradeRule) degradeForm.value = {...item.degradeRule}
  else resetDegradeForm()
  dialogVisible.value = true
}

const submitFlow = async () => {
  if (!currentResourceName.value) return ElMessage.warning('资源名不能为空')
  const payload = buildFlowPayload()
  await saveFlowRule(payload)
  ElMessage.success('流控规则已更新')
  const prev = list.value.find((i) => i.resource === currentResourceName.value)
  mergeResourceSnapshot(currentResourceName.value, {
    flowRule: payload,
    degradeRule: prev?.degradeRule ?? null
  })
  isEditMode.value = true
  await fetchData(true)
  mergeResourceSnapshot(currentResourceName.value, {
    flowRule: payload,
    degradeRule: list.value.find((i) => i.resource === currentResourceName.value)?.degradeRule ?? prev?.degradeRule ?? null
  })
  activeRuleTab.value = 'degrade'
}

const submitDegrade = async () => {
  if (!currentResourceName.value) return ElMessage.warning('资源名不能为空')
  const payload = buildDegradePayload()
  await saveDegradeRule(payload)
  ElMessage.success('熔断规则已更新')
  const prev = list.value.find((i) => i.resource === currentResourceName.value)
  mergeResourceSnapshot(currentResourceName.value, {
    flowRule: prev?.flowRule ?? null,
    degradeRule: payload
  })
  isEditMode.value = true
  await fetchData(true)
  mergeResourceSnapshot(currentResourceName.value, {
    flowRule: list.value.find((i) => i.resource === currentResourceName.value)?.flowRule ?? prev?.flowRule ?? null,
    degradeRule: payload
  })
}

const handleDelete = (resource) => {
  ElMessageBox.confirm(`确定要移除对 [${resource}] 的所有保护吗?`, '高危操作', {
    confirmButtonText: '确定移除', confirmButtonClass: 'el-button--danger', type: 'warning'
  }).then(async () => {
    await deleteResource(resource)
    ElMessage.success('资源防护已卸载');
    fetchData()
  })
}

// 辅助工具
const resetForms = () => {
  resetFlowForm();
  resetDegradeForm()
}
const resetFlowForm = () => flowForm.value = {grade: 1, count: 20, controlBehavior: 0, limitApp: 'default', strategy: 0}
const resetDegradeForm = () => degradeForm.value = {
  grade: 0,
  count: 500,
  timeWindow: 5,
  minRequestAmount: 5,
  statIntervalMs: 1000,
  slowRatioThreshold: 0.5
}
const formatResource = (str) => str.length > 22 ? str.substring(0, 10) + '...' + str.slice(-10) : str
const formatDegradeGrade = (g) => ({0: '慢调用(RT)', 1: '异常比例', 2: '异常数'}[g] || '开启')
const handleRefresh = () => fetchData()

onMounted(fetchData)
</script>

<style scoped>
.app-container {
  padding: 20px 40px;
}

/* 顶部操作栏对齐路由界面 */
.action-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  margin-bottom: 30px;
}

.bar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.search-box {
  width: 240px;
  transition: all 0.3s ease;
}

.search-box:focus-within {
  width: 300px;
}

:deep(.glass-input .el-input__wrapper) {
  background-color: var(--bg-glass);
  box-shadow: 0 0 0 1px var(--border-color) inset;
  border-radius: 10px;
  height: 36px;
}

.btn-group {
  display: flex;
  gap: 10px;
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

.glow-btn-danger {
  background: #f43f5e !important;
  border: none !important;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(244, 63, 94, 0.3);
  color: #fff;
}

/* 卡片布局对齐路由界面 */
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 24px;
}

.defense-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 20px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  backdrop-filter: blur(10px);
}

.defense-card:hover {
  transform: translateY(-4px);
  border-color: var(--text-highlight);
  box-shadow: 0 12px 24px -8px rgba(0, 0, 0, 0.15);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.resource-id {
  font-weight: 700;
  color: var(--text-main);
  font-size: 15px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon {
  color: var(--text-highlight);
}

/* 核心可视化：并排对齐且固定高度 */
.status-comparison {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.03);
  padding: 16px;
  border-radius: 14px;
  margin-bottom: 18px;
  border: 1px solid var(--border-color);
  min-height: 85px; /* 固定高度防止抖动 */
}

.status-node {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.status-node.target {
  text-align: right;
  align-items: flex-end;
}

.node-label {
  font-size: 10px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.node-val {
  height: 26px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.highlight {
  font-size: 18px;
  color: var(--text-highlight);
  font-weight: 700;
}

.unit {
  font-size: 10px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.empty-val {
  font-size: 12px;
  color: var(--text-secondary);
  opacity: 0.4;
}

.node-progress {
  width: 80%;
  margin-top: 8px;
}

.node-desc {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
}

.divider-line {
  width: 1px;
  height: 35px;
  background: var(--border-color);
  margin: 0 20px;
}

/* 页脚与按钮 */
.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.details {
  font-size: 11px;
  color: var(--text-secondary);
  display: flex;
  gap: 8px;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 弹窗样式调整 */
:deep(.glass-dialog) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color);
  border-radius: 16px;
  backdrop-filter: blur(20px);
}

:deep(.el-dialog__header) {
  padding-bottom: 0;
}

.inner-form {
  padding: 10px 5px;
}

.w-full {
  width: 100%;
}

.mt-4 {
  margin-top: 16px;
}

.mt-2 {
  margin-top: 8px;
}

:deep(.rule-tabs) {
  background: transparent !important;
  border: 1px solid var(--border-color) !important;
  border-radius: 12px;
  overflow: hidden;
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background: rgba(0, 0, 0, 0.2) !important;
  border-bottom: 1px solid var(--border-color);
}
</style>