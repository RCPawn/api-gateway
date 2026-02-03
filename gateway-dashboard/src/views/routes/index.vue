<template>
  <div class="route-container">
    <div class="action-bar">
      <div class="bar-left"></div>
      <div class="bar-right">
        <div class="search-box">
          <el-input v-model="searchKeyword" placeholder="搜索路径或服务名..." clearable class="glass-input">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>
        <div class="btn-group">
          <el-button type="primary" plain class="glass-btn" @click="fetchData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
          <el-button type="primary" class="glow-btn" @click="handleAdd">
            <el-icon><Plus /></el-icon> 新增路由
          </el-button>
        </div>
      </div>
    </div>

    <div class="card-grid" v-loading="loading">
      <div v-for="item in filteredList" :key="item.id" class="route-card">
        <div class="card-header">
          <el-tooltip :content="item.id" placement="top" :show-after="500">
            <div class="route-id"><span class="hash">#</span> {{ item.id }}</div>
          </el-tooltip>
          <el-tag size="small" effect="dark" :type="item.order === 0 ? 'danger' : 'info'" round>
            Order: {{ item.order }}
          </el-tag>
        </div>

        <div class="flow-visual">
          <div class="flow-node source">
            <div class="node-label">请求入口</div>
            <el-tooltip :content="getPathPredicate(item) || '* (任意路径)'" placement="top" :show-after="500">
              <div class="node-val" v-if="getPathPredicate(item)">{{ getPathPredicate(item) }}</div>
              <div class="node-val empty" v-else>* (任意路径)</div>
            </el-tooltip>
          </div>
          <div class="flow-arrow">
            <div class="arrow-body"></div>
            <div class="arrow-head-modern"></div>
          </div>
          <div class="flow-node target">
            <div class="node-label">转发目标</div>
            <el-tooltip :content="item.uri" placement="top" :show-after="500">
              <div class="node-val highlight">{{ formatUri(item.uri) }}</div>
            </el-tooltip>
          </div>
        </div>

        <div class="card-footer">
          <div class="details">
            <span class="detail-item" v-if="item.filters?.length">
              <el-icon><Filter /></el-icon> {{ item.filters.length }} Filters
            </span>
            <span class="detail-item" v-else>
              <el-icon><Check /></el-icon> Direct
            </span>
          </div>
          <div class="actions">
            <el-button circle text type="primary" @click="handleEdit(item)"><el-icon><EditPen /></el-icon></el-button>
            <el-button circle text type="danger" @click="handleDelete(item.id)"><el-icon><Delete /></el-icon></el-button>
          </div>
        </div>
      </div>
      <el-empty v-if="filteredList.length === 0 && !loading" description="暂无路由配置" style="width: 100%" />
    </div>

    <el-dialog
        :title="isEdit ? '🛠️ 编辑路由规则' : '🚀 新增路由规则'"
        v-model="dialogVisible"
        width="950px"
        class="glass-dialog custom-dialog"
        destroy-on-close
        :close-on-click-modal="false"
    >
      <!-- 新增滚动容器，解决内容溢出问题 -->
      <div class="dialog-scroll-container">
        <div class="dialog-content">
          <div class="panel panel-form">
            <div class="section-title">基础配置</div>
            <el-form :model="form" label-position="top">
              <el-form-item label="路由 ID">
                <el-input v-model="form.id" placeholder="例如: user-service" :disabled="isEdit" />
              </el-form-item>
              <el-form-item label="目标 URI">
                <el-input v-model="form.uri" placeholder="例如: lb://user-service" />
              </el-form-item>
              <el-form-item label="执行优先级">
                <el-input-number v-model="form.order" :min="0" style="width: 100%" />
              </el-form-item>
            </el-form>
            <div class="dialog-tips">
              <p>💡 提示</p>
              <small>ID 必须唯一，URI 支持负载均衡协议 (lb://)；高级规则需遵循 JSON 数组格式。</small>
            </div>
          </div>

          <div class="panel panel-json">
            <div v-for="key in ['predicates', 'filters']" :key="key" class="editor-wrapper">
              <div class="json-header">
                <span class="section-title">{{ key === 'predicates' ? '断言规则 (Predicates)' : '过滤器 (Filters)' }}</span>
                <div class="header-actions">
                  <el-button link size="small" @click="formatJson(key)">格式化</el-button>
                  <el-button link size="small" @click="copyJson(key)">复制</el-button>
                </div>
              </div>
              <el-input
                  v-model="jsonState[key]"
                  type="textarea"
                  :rows="7"
                  class="json-textarea"
                  placeholder="请输入 JSON 配置..."
              />
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="custom-footer">
          <el-button @click="dialogVisible = false" class="cancel-btn">取消</el-button>
          <el-button type="primary" class="glow-btn save-btn" @click="submitForm">保存配置</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, EditPen, Delete, Filter, Check, Search } from '@element-plus/icons-vue'
import { getRoutes, saveRoute, deleteRoute } from '@/api/route'

const rawList = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const form = ref({ id: '', uri: '', order: 0 })
const jsonState = reactive({ predicates: '[]', filters: '[]' })

const filteredList = computed(() => {
  if (!searchKeyword.value) return rawList.value
  const key = searchKeyword.value.toLowerCase()
  return rawList.value.filter(item => item.id.toLowerCase().includes(key) || item.uri.toLowerCase().includes(key))
})

const fetchData = async () => {
  loading.value = true
  try {
    rawList.value = await getRoutes() || []
    ElMessage.success('数据同步成功')
  } finally { loading.value = false }
}

const openDialog = (item = null) => {
  isEdit.value = !!item
  form.value = item ? { ...item } : { id: '', uri: 'lb://', order: 0 }
  jsonState.predicates = JSON.stringify(item?.predicates || [{ name: 'Path', args: { pattern: '/api/**' } }], null, 2)
  jsonState.filters = JSON.stringify(item?.filters || [{ name: 'StripPrefix', args: { parts: '1' } }], null, 2)
  dialogVisible.value = true
}

const handleAdd = () => openDialog()
const handleEdit = (row) => openDialog(row)

const formatJson = (key) => {
  try {
    jsonState[key] = JSON.stringify(JSON.parse(jsonState[key]), null, 2)
  } catch (e) { ElMessage.error('JSON 格式错误，无法格式化') }
}

const copyJson = (key) => {
  navigator.clipboard.writeText(jsonState[key])
  ElMessage.success('已复制到剪贴板')
}

const submitForm = async () => {
  try {
    const payload = {
      ...form.value,
      predicates: JSON.parse(jsonState.predicates),
      filters: JSON.parse(jsonState.filters)
    }
    await saveRoute(payload)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } catch (e) { ElMessage.error('JSON 语法校验失败，请检查格式') }
}

const handleDelete = (id) => {
  ElMessageBox.confirm(`确定永久删除路由 [${id}] 吗?`, '高危操作', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    await deleteRoute(id); ElMessage.success('已移除'); fetchData()
  })
}

const getPathPredicate = (item) => {
  const p = item.predicates?.find(p => p.name === 'Path')
  return p?.args?.pattern || p?.args?._genkey_0
}
const formatUri = (uri) => uri.startsWith('lb://') ? uri.replace('lb://', '☁️ ') : '🌐 ' + uri

onMounted(fetchData)
</script>

<style scoped>
/* --- 保持原样：主页面样式 --- */
.route-container { padding: 20px 40px; color: var(--text-main); }
.action-bar { display: flex; justify-content: flex-end; align-items: center; margin-bottom: 30px; }
.bar-right { display: flex; align-items: center; gap: 16px; }
.search-box { width: 240px; transition: 0.3s; }
.search-box:focus-within { width: 300px; }
:deep(.glass-input .el-input__wrapper) { background-color: var(--bg-glass); box-shadow: 0 0 0 1px var(--border-color) inset; border-radius: 10px; height: 36px; }
.btn-group { display: flex; gap: 10px; }
.glass-btn { background: var(--bg-glass) !important; border: 1px solid var(--border-color) !important; color: var(--text-main) !important; border-radius: 8px; }
.glow-btn { background: var(--text-highlight) !important; border: none !important; border-radius: 8px; color: #fff !important; box-shadow: 0 4px 12px rgba(14, 165, 233, 0.3); transition: 0.3s; }
.glow-btn:hover { opacity: 0.9; transform: translateY(-1px); }
.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 24px; }
.route-card { background: var(--bg-card); border: 1px solid var(--border-color); border-radius: 16px; padding: 20px; transition: 0.3s; backdrop-filter: blur(10px); box-shadow: var(--card-shadow); }
.route-card:hover { transform: translateY(-4px); border-color: var(--text-highlight); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.route-id { font-weight: 700; color: var(--text-main); font-size: 15px; }
.flow-visual { display: flex; align-items: center; justify-content: space-between; background: var(--bg-glass); padding: 16px; border-radius: 14px; margin-bottom: 18px; border: 1px solid var(--border-color); }
.flow-node { flex: 0 0 130px; display: flex; flex-direction: column; }
.target { text-align: right; }
.node-label { font-size: 10px; color: var(--text-secondary); margin-bottom: 4px; }
.node-val { font-size: 13px; font-weight: 600; color: var(--text-main); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-val.highlight { color: var(--text-highlight); }
.flow-arrow { flex: 0 1 40px; display: flex; align-items: center; position: relative; margin: 0 10px; }
.arrow-body { width: 100%; height: 2px; background: linear-gradient(90deg, var(--border-color) 0%, var(--text-highlight) 100%); }
.arrow-head-modern { width: 6px; height: 6px; border-top: 2px solid var(--text-highlight); border-right: 2px solid var(--text-highlight); transform: rotate(45deg); position: absolute; right: 0; }
.card-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 12px; border-top: 1px solid var(--border-color); }
.details { font-size: 11px; color: var(--text-secondary); display: flex; gap: 8px; }

/* --- 优化：对话框样式 --- */
:deep(.custom-dialog) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color);
  border-radius: 20px;
  max-height: 85vh; /* 限制最大高度，避免超出视口 */
  display: flex;
  flex-direction: column;
}
:deep(.el-dialog__header) { margin: 0; padding: 20px 24px; border-bottom: 1px solid var(--border-color); }
:deep(.el-dialog__title) { color: var(--text-main); font-size: 18px; font-weight: bold; }
:deep(.el-dialog__body) {
  flex: 1;
  padding: 0;
  overflow: hidden; /* 隐藏溢出，让内部滚动容器生效 */
}

/* 新增滚动容器 */
.dialog-scroll-container {
  height: 100%;
  overflow-y: auto;
  padding: 24px;
  /* 优化滚动条样式 */
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-track { background: var(--bg-glass); border-radius: 3px; }
  &::-webkit-scrollbar-thumb { background: var(--border-color); border-radius: 3px; }
  &::-webkit-scrollbar-thumb:hover { background: var(--text-secondary); }
}

.dialog-content {
  display: grid;
  grid-template-columns: 280px 1fr; /* 优化左右宽度比例，减少左侧留白 */
  gap: 32px; /* 缩小间距 */
}

.section-title { font-size: 14px; font-weight: bold; color: var(--text-main); margin-bottom: 16px; display: flex; align-items: center; }
.section-title::before { content: ''; width: 4px; height: 14px; background: var(--text-highlight); border-radius: 2px; margin-right: 8px; }

/* 左侧表单区域 */
.panel-form { display: flex; flex-direction: column; }
:deep(.el-form-item__label) { color: var(--text-secondary) !important; font-weight: 600; padding-bottom: 4px !important; }
:deep(.el-form-item) { margin-bottom: 16px; } /* 缩小表单元素间距 */

.dialog-tips {
  margin-top: auto;
  padding: 10px; /* 缩小内边距 */
  background: var(--bg-glass);
  border-radius: 10px;
  border: 1px dashed var(--border-color);
}
.dialog-tips p { margin: 0 0 4px; font-size: 12px; font-weight: bold; color: var(--text-main); }
.dialog-tips small { color: var(--text-secondary); line-height: 1.4; display: block; }

/* 右侧 JSON 区域 */
.panel-json { display: flex; flex-direction: column; gap: 20px; /* 缩小编辑器间距 */ }
.json-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.json-header .section-title { margin-bottom: 0; }

:deep(.json-textarea .el-textarea__inner) {
  font-family: 'Fira Code', 'Monaco', monospace;
  font-size: 13px;
  background-color: var(--bg-body) !important;
  color: var(--text-highlight) !important;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 12px;
  transition: 0.3s;
  min-height: 120px; /* 确保编辑器有最小高度 */
  max-height: 200px; /* 限制编辑器最大高度，避免过高 */
  resize: vertical; /* 允许用户手动调整高度 */
}
:deep(.json-textarea .el-textarea__inner:focus) { border-color: var(--text-highlight); box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.1); }

/* 页脚按钮 */
.custom-footer { padding: 10px 0; display: flex; gap: 12px; justify-content: flex-end; }
.save-btn { padding: 10px 24px; font-weight: bold; }
.cancel-btn { border-radius: 8px; border: 1px solid var(--border-color); background: transparent; color: var(--text-secondary); }
.cancel-btn:hover { color: var(--text-main); border-color: var(--text-main); }

@media (max-width: 950px) {
  .dialog-content { grid-template-columns: 1fr; gap: 24px; }
  :deep(.custom-dialog) { width: 95% !important; }
}
</style>