<template>
  <div class="dashboard-container">
    <!-- 引入拓扑子组件 -->
    <TopologyChart ref="topologyChartRef" />
    <div class="horizon-grid"></div>

    <div class="ui-layer">
      <transition name="slide-left" appear>
        <aside class="hud-panel left-wing">
          <div class="panel-header">
            <span class="panel-title">系统指标</span>
            <div class="decor-line"></div>
          </div>

          <div class="metric-card chart-card">
            <div class="metric-label"><el-icon><Odometer /></el-icon> QPS 实时监控</div>
            <div class="qps-display">
              <span class="value glitch-text" :data-text="metrics.qps">{{ metrics.qps }}</span>
              <span class="trend up">▲ LIVE</span>
            </div>
            <div ref="qpsChartRef" class="mini-chart"></div>
          </div>

          <div class="dual-metrics">
            <div class="compact-metric">
              <div class="compact-icon"><el-icon><Timer /></el-icon></div>
              <div class="compact-info">
                <div class="compact-label">平均延迟</div>
                <div class="compact-value text-warning">{{ metrics.latency }}<span class="unit-sm">ms</span></div>
                <div class="progress-rail sm"><div class="progress-fill neon-warning" :style="{ width: Math.min(metrics.latency,100) + '%' }"></div></div>
              </div>
            </div>

            <div class="compact-metric">
              <div class="compact-icon" :class="isHighError ? 'danger' : 'success'"><el-icon><WarningFilled /></el-icon></div>
              <div class="compact-info">
                <div class="compact-label">错误率</div>
                <div class="compact-value" :class="isHighError ? 'text-danger' : 'text-success'">{{ metrics.errorRate }}</div>
                <div class="progress-rail sm"><div class="progress-fill neon-red" :style="{ width: errorRateNum * 10 + '%' }"></div></div>
              </div>
            </div>
          </div>

          <div class="metric-card scrollable-card">
            <div class="metric-label"><el-icon><DataAnalysis /></el-icon> 热门路由 <span class="badge">{{ topRoutes.length }}</span></div>
            <div class="top-list-container">
              <div class="top-list">
                <div v-for="(route, index) in topRoutes" :key="index" class="top-item">
                  <div class="top-info">
                    <span class="top-rank">#{{ index + 1 }}</span>
                    <span class="top-name">{{ route.name }}</span>
                    <span class="top-val">{{ route.count }}</span>
                  </div>
                  <div class="progress-rail sm"><div class="progress-fill neon-info" :style="{ width: route.percent + '%' }"></div></div>
                </div>
              </div>
            </div>
          </div>
        </aside>
      </transition>

      <transition name="slide-right" appear>
        <aside class="hud-panel right-wing">
          <div class="panel-header">
            <span class="panel-title">运维控制</span>
            <div class="decor-line"></div>
          </div>

          <div class="control-matrix">
            <div class="matrix-btn success" @click="handleAction('refresh')" @mouseenter="playHoverSound"><el-icon class="icon"><Refresh /></el-icon><span>刷新</span></div>
            <div class="matrix-btn warning" @click="handleAction('waf')" @mouseenter="playHoverSound"><el-icon class="icon"><Umbrella /></el-icon><span>启用 WAF</span></div>
            <div class="matrix-btn" @click="handleAction('log')" @mouseenter="playHoverSound"><el-icon class="icon"><Aim /></el-icon><span>采样</span></div>
            <div class="matrix-btn danger" @click="handleAction('clean')" @mouseenter="playHoverSound"><el-icon class="icon"><DeleteFilled /></el-icon><span>清理</span></div>
          </div>

          <div class="panel-header mt-4 intercept-log-title-row">
            <span class="panel-title">拦截日志</span>
            <span class="badge" :title="interceptBadgeTooltip">{{ aggregatedLogGroups.length }}</span>
            <div class="decor-line"></div>
          </div>

          <p v-if="logs.length === 0" class="log-empty-hint">暂无拦截。限流、熔断、WAF、鉴权触发后将按「细分类型 + 接口 + 客户端」自动合并重复项。</p>

          <div v-else class="log-terminal">
            <div class="log-table-head" aria-hidden="true">
              <span class="th-time">时间</span>
              <span class="th-type">类型</span>
              <span class="th-n">次数</span>
              <span class="th-src">来源</span>
            </div>
            <div class="log-list-scroll">
            <ul class="log-list" ref="logListRef">
              <li
                v-for="(g, i) in aggregatedLogGroups"
                :key="g.key + i"
                class="log-item"
              >
                <el-tooltip
                  placement="left"
                  :show-after="280"
                  :max-width="320"
                  popper-class="intercept-log-tooltip"
                >
                  <template #content>
                    <div class="intercept-tooltip-box">
                      <p class="it-head">{{ (g.displayCount ?? g.count) > 1 ? `同条件合并 ${g.displayCount ?? g.count} 次` : '拦截明细' }}</p>
                      <dl class="it-dl">
                        <template v-if="(g.displayCount ?? g.count) > 1 && g.timeNewestMs !== g.timeOldestMs">
                          <dt>时间范围</dt>
                          <dd>最早 {{ formatFullDateTime(Math.min(g.timeNewestMs, g.timeOldestMs)) }} ～ 最近 {{ formatFullDateTime(Math.max(g.timeNewestMs, g.timeOldestMs)) }}</dd>
                        </template>
                        <template v-else>
                          <dt>时间</dt>
                          <dd>{{ formatFullDateTime(g.timeNewestMs) }}</dd>
                        </template>
                        <dt>类型</dt>
                        <dd>{{ g.typeLabel }}</dd>
                        <dt>接口</dt>
                        <dd class="it-mono">{{ g.sourcePath }}</dd>
                        <dt v-if="g.clientIp">客户端</dt>
                        <dd v-if="g.clientIp" class="it-mono">{{ g.clientIp }}</dd>
                        <template v-if="g.status">
                          <dt>HTTP</dt>
                          <dd>{{ g.status }}</dd>
                        </template>
                        <template v-if="g.ruleLine">
                          <dt>规则</dt>
                          <dd class="it-wrap">{{ g.ruleLine }}</dd>
                        </template>
                        <template v-if="g.msgLine">
                          <dt>说明</dt>
                          <dd class="it-wrap">{{ g.msgLine }}</dd>
                        </template>
                      </dl>
                    </div>
                  </template>
                  <div class="log-row-grid">
                    <div class="col-datetime">
                      <span class="dt-date">{{ g.newestParts.date }}</span>
                      <span class="dt-clock">{{ g.newestParts.clock }}</span>
                    </div>
                    <span class="col-type" :class="g.tagClass">{{ g.typeLabel }}</span>
                    <span class="col-n">{{ g.displayCount ?? g.count }}</span>
                    <span class="col-src" :title="g.sourcePath">{{ g.sourcePath }}</span>
                  </div>
                </el-tooltip>
              </li>
            </ul>
            </div>
          </div>
        </aside>
      </transition>

      <transition name="fade-up" appear>
        <div class="bottom-dock">
          <button class="main-btn" @click="toggleKnife4j" @mouseenter="playHoverSound"><span class="btn-decor">⚡</span> 打开 API 终端</button>
        </div>
      </transition>
    </div>

    <transition name="zoom">
      <div v-if="showKnife4j" class="modal-overlay" @click.self="toggleKnife4j">
        <div class="modal-window">
          <div class="modal-bar">
            <div class="traffic-lights"><span></span><span></span><span></span></div>
            <span class="modal-title">安全接口文档</span>
            <button class="modal-close" @click="toggleKnife4j">×</button>
          </div>
          <iframe src="http://localhost:9000/doc.html" frameborder="0"></iframe>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { Aim, DataAnalysis, DeleteFilled, Odometer, Refresh, Timer, Umbrella, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
// 引入拓扑子组件
import TopologyChart from '@/components/TopologyChart.vue'
import { cleanMetrics, getDashboardMetrics, getTopologyData, refreshRoutes, startSampling, toggleWaf } from '@/api/dashboard'
import { getRecentLogs } from '@/api/log'

// 移除原chartRef，新增拓扑组件ref
const topologyChartRef = ref(null)
const qpsChartRef = ref(null)
const logListRef = ref(null)
const showKnife4j = ref(false)
// 移除原chartInstance，保留qpsChartInstance
let qpsChartInstance = null
let metricsTimer = null
let logsTimer = null

const metrics = reactive({ qps: 0, latency: 0, errorRate: '0.00%' })
const qpsHistory = reactive({ timestamps: [], values: [] })
const topRoutes = ref([])
const logs = reactive([])
/** 与后端 Redis Hash 对齐：按 coarseKey 累计总触发次数（不受列表挤出影响） */
const interceptTotals = ref({})
const isWafActive = ref(false)
const audioCtx = ref(null)

const errorRateNum = computed(() => {
  return parseFloat(String(metrics.errorRate).replace('%', '')) || 0
})
const isHighError = computed(() => errorRateNum.value > 5)

// ---------- 数据拉取与拓扑更新 ----------
const fetchData = async () => {
  try {
    // 并行拉取指标与拓扑（拓扑从后端代理获取）
    const [metricRes, topoRes] = await Promise.allSettled([getDashboardMetrics(), getTopologyData()])

    if (metricRes.status === 'fulfilled' && metricRes.value) {
      const res = metricRes.value
      // 兼容不同后端包装：直接 data、或者直接返回对象
      const payload = res.data ? res.data : res
      metrics.qps = payload.qps || 0
      metrics.latency = payload.latency || 0
      metrics.errorRate = payload.errorRate || '0.00%'
      topRoutes.value = payload.topRoutes || []
      updateQPSHistory(metrics.qps)
    } else {
      console.warn('拉取指标失败:', metricRes)
    }

    // 调用子组件的updateTopology更新拓扑数据
    if (topoRes.status === 'fulfilled' && topoRes.value) {
      const t = topoRes.value
      // 兼容后端多种返回结构：Result 包装 || 直接 {nodes, links} || {data:{nodes,links}}
      let topoPayload = t
      if (t && t.data) topoPayload = t.data
      if (topoPayload && (topoPayload.nodes || topoPayload.links)) {
        if (topologyChartRef.value) {
          topologyChartRef.value.updateTopology(topoPayload)
        }
      } else {
        // 某些后端把真实对象包在 data.result 之类里，尝试展开（容错）
        const maybe = topoPayload?.result || topoPayload?.data?.result
        if (maybe && (maybe.nodes || maybe.links)) {
          if (topologyChartRef.value) {
            topologyChartRef.value.updateTopology(maybe)
          }
        }
      }
    } else {
      // 不要每次都报警，非关键时可忽略
      // console.warn('拉取拓扑失败或无数据:', topoRes)
    }
  } catch (e) {
    console.error('fetchData 异常:', e)
  }
}

const fetchLogs = async () => {
  try {
    const payload = await getRecentLogs()
    let list = []
    let totals = {}
    if (Array.isArray(payload)) {
      list = payload
    } else if (payload && Array.isArray(payload.logs)) {
      list = payload.logs
      totals = payload.interceptTotals || {}
    }
    logs.length = 0
    interceptTotals.value = totals
    list.forEach((raw, idx) => logs.push(normalizeInterceptLog(raw, idx)))
    if (logs.length > 100) logs.splice(100)
  } catch (e) {
    console.warn('日志数据拉取失败:', e)
  }
}

function pad2(n) {
  return String(n).padStart(2, '0')
}

/** 分解为日期 / 时钟，与表头「时间」列两行对齐 */
function splitDateTimeParts(ms) {
  const d = new Date(ms)
  return {
    date: `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`,
    clock: `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  }
}

function formatFullDateTime(ms) {
  const { date, clock } = splitDateTimeParts(ms)
  return `${date} ${clock}`
}

/** 优先 ts(ms)；其次解析含日期的 time；仅 HH:mm:ss 时按当日拼接（兼容旧日志） */
function parseInterceptInstant(raw) {
  if (raw.ts != null && raw.ts !== '') {
    const n = Number(raw.ts)
    if (!Number.isNaN(n) && n > 0) return n
  }
  if (typeof raw.time === 'string') {
    const t = raw.time.trim()
    if (/^\d{4}-\d{2}-\d{2}/.test(t)) {
      const d = new Date(t.replace(/-/g, '/'))
      if (!isNaN(d.getTime())) return d.getTime()
    }
    const m = t.match(/^(\d{1,2}):(\d{2}):(\d{2})$/)
    if (m) {
      const d = new Date()
      d.setHours(Number(m[1]), Number(m[2]), Number(m[3]), 0)
      return d.getTime()
    }
  }
  return Date.now()
}

/** 展示用接口路径（无前导 /，与路由习惯一致） */
const normalizeSourcePath = (raw) => {
  let p = String(raw.path || '').trim()
  const pathStripped = p.replace(/^\/+/, '')
  if (!p || pathStripped === '') {
    const msg = String(raw.msg || raw.message || '')
    const colon = msg.match(/:\s*(\/[\w\-./]+)/)
    if (colon) p = colon[1]
    else {
      const mp = msg.match(/\b(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s+(\/[\S]+)/i)
      if (mp) p = mp[2]
    }
  }
  // Sentinel 摘要里的 resource=…（URI 为空、仅为 / 或未写入 path 时仍可展示来源）
  const p2 = String(p || '').trim()
  if (!p2 || p2.replace(/^\/+/, '') === '') {
    const rule = String(raw.rule || '')
    const rm = rule.match(/resource=([^\s,]+)/)
    if (rm) {
      let r = rm[1].trim()
      if (/^route:/i.test(r)) r = r.slice(6)
      p = r.startsWith('/') ? r : `/${r}`
    } else {
      const msgOnly = String(raw.msg || raw.message || '')
      const rmMsg = msgOnly.match(/resource=([^\s,]+)/)
      if (rmMsg) {
        let r = rmMsg[1].trim()
        if (/^route:/i.test(r)) r = r.slice(6)
        p = r.startsWith('/') ? r : `/${r}`
      }
    }
  }
  if (!p) return '—'
  return p.replace(/^\/+/, '')
}

/** 与 LogBuffer.buildCoarseKey 一致，用于累计次数查询 */
const buildCoarseInterceptKey = (rawType, sourcePath, clientIp, status) => {
  const t = String(rawType || 'UNKNOWN').toUpperCase()
  const seg = sourcePath === '—' || !sourcePath ? '—' : sourcePath
  const ip = clientIp || ''
  const st = status != null && status !== '' ? String(status) : '0'
  return [t, seg, ip, st].join('\u0001')
}

/** 细分类型：如 QPS 限流、熔断（与 raw.type 区分展示） */
const buildInterceptTypeLabel = (rawType, raw) => {
  const rule = String(raw.rule || '')
  const msg = String(raw.msg || raw.message || '')
  if (/QPS\s*Limit/i.test(msg)) return 'QPS 限流'
  if (rawType === 'FLOW') {
    if (/阈值QPS|grade\s*=\s*1/i.test(rule)) return 'QPS 限流'
    if (/线程|grade\s*=\s*0/i.test(rule)) return '并发限流'
    return '限流'
  }
  if (rawType === 'PARAM_FLOW') return '热点参数限流'
  if (rawType === 'FUSE') return '熔断降级'
  if (rawType === 'WAF') return 'WAF'
  if (rawType === 'AUTH') return '鉴权'
  return mapInterceptTag(rawType)
}

/** 将后端结构化拦截日志转为驾驶舱行（兼容旧版仅有 time/source/type/msg） */
const normalizeInterceptLog = (raw, idx) => {
  const timeMs = parseInterceptInstant(raw)
  const rawType = String(raw.type || raw.level || 'UNKNOWN').toUpperCase()
  const tagClass = mapInterceptTagClass(rawType)
  const typeLabel = buildInterceptTypeLabel(rawType, raw)
  const sourcePath = normalizeSourcePath(raw)
  const clientIp = raw.clientIp || raw.source || ''
  const status = raw.status != null && raw.status !== '' ? String(raw.status) : ''
  const rule = raw.rule || ''
  const msg = raw.msg || raw.message || ''
  const ruleLine = rule || null
  const msgLine = msg && msg.trim() && msg !== ruleLine ? msg : null
  const coarseKey = raw.coarseKey || buildCoarseInterceptKey(rawType, sourcePath, clientIp, status)
  const keyId = `${timeMs}-${idx}-${clientIp}-${sourcePath}`
  return {
    keyId,
    timeMs,
    typeLabel,
    tagClass,
    rawType,
    status,
    clientIp,
    sourcePath,
    ruleLine,
    msgLine,
    coarseKey
  }
}

const mapInterceptTag = (type) => {
  const m = {
    FLOW: '限流',
    PARAM_FLOW: '热点',
    FUSE: '熔断',
    WAF: 'WAF',
    AUTH: '鉴权'
  }
  return m[type] || (type === 'UNKNOWN' ? '其他' : type)
}

const mapInterceptTagClass = (type) => {
  if (type === 'FLOW' || type === 'PARAM_FLOW') return 'flow'
  if (type === 'FUSE') return 'fuse'
  if (type === 'WAF') return 'waf'
  if (type === 'AUTH') return 'auth'
  return 'info'
}

/** 按「细分类型 + 接口 + 客户端 + 状态 + 规则 + 说明」合并重复拦截（窗口内列表从新到旧） */
const aggregatedLogGroups = computed(() => {
  const order = []
  const map = new Map()
  for (const log of logs) {
    const key = [
      log.rawType,
      log.typeLabel,
      log.sourcePath,
      log.clientIp || '',
      log.status || '',
      log.ruleLine || '',
      log.msgLine || ''
    ].join('\u0001')
    const existing = map.get(key)
    if (!existing) {
      const parts = splitDateTimeParts(log.timeMs)
      const g = {
        key,
        typeLabel: log.typeLabel,
        tagClass: log.tagClass,
        rawType: log.rawType,
        coarseKey: log.coarseKey,
        sourcePath: log.sourcePath,
        clientIp: log.clientIp,
        status: log.status,
        ruleLine: log.ruleLine,
        msgLine: log.msgLine,
        timeNewestMs: log.timeMs,
        timeOldestMs: log.timeMs,
        newestParts: parts,
        oldestParts: { ...parts },
        count: 1
      }
      map.set(key, g)
      order.push(key)
    } else {
      existing.count++
      existing.timeOldestMs = log.timeMs
      existing.oldestParts = splitDateTimeParts(log.timeMs)
    }
  }
  return order.map((k) => {
    const g = map.get(k)
    const totalsMap = interceptTotals.value
    const t = Number(totalsMap[g.coarseKey] ?? 0)
    const displayCount = t > 0 ? Math.max(g.count, t) : g.count
    return { ...g, displayCount }
  })
})

const interceptBadgeTooltip = computed(() => {
  const n = logs.length
  const g = aggregatedLogGroups.value.length
  if (!n) return ''
  if (n === g) return `共 ${n} 条（无重复可合并）`
  return `窗口内 ${n} 条记录，已合并为 ${g} 组（悬停查看规则与说明）`
})

// QPS 历史（小图）
const updateQPSHistory = (qps) => {
  const now = new Date()
  const timeStr = `${now.getHours()}:${String(now.getMinutes()).padStart(2,'0')}:${String(now.getSeconds()).padStart(2,'0')}`
  qpsHistory.timestamps.push(timeStr); qpsHistory.values.push(qps)
  if (qpsHistory.timestamps.length > 30) { qpsHistory.timestamps.shift(); qpsHistory.values.shift() }
  if (qpsChartInstance) qpsChartInstance.setOption({ xAxis: { data: qpsHistory.timestamps }, series: [{ data: qpsHistory.values }] })
}

// ---------- ECharts QPS小图初始化 ----------
const initQPSChart = () => {
  if (!qpsChartRef.value) return
  qpsChartInstance = echarts.init(qpsChartRef.value)
  qpsChartInstance.setOption({
    backgroundColor: 'transparent',
    grid: { left: 32, right: 10, top: 10, bottom: 20, containLabel: false },
    xAxis: { type: 'category', data: qpsHistory.timestamps, boundaryGap: false, axisLine: { lineStyle: { color: 'rgba(148,163,184,0.2)' } }, axisLabel: { show: false }, axisTick: { show: false } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: 'rgba(148,163,184,0.1)', type: 'dashed' } }, axisLabel: { color: '#94a3b8', fontSize: 10 } },
    series: [{ type: 'line', data: qpsHistory.values, smooth: true, symbol: 'none', lineStyle: { color: '#0ea5e9', width: 2 },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(14,165,233,0.3)' }, { offset: 1, color: 'rgba(14,165,233,0.05)' }] } } }]
  })
}

// ---------- 操作按钮（保留原逻辑） ----------
const handleAction = async (action) => {
  playHoverSound()
  try {
    if (action === 'refresh') { await refreshRoutes(); ElMessage.success('路由配置已刷新') }
    else if (action === 'waf') { isWafActive.value = !isWafActive.value; await toggleWaf(isWafActive.value); if (isWafActive.value) ElMessage.warning('防火墙已激活！'); else ElMessage.info('防火墙已关闭') }
    else if (action === 'log') { await startSampling(); ElMessage.success('全量日志采样已开启 (60秒)') }
    else if (action === 'clean') {
      try {
        await ElMessageBox.confirm('此操作将清空所有监控数据（QPS、延迟、错误率、热门路由、拦截日志），是否继续？','⚠️ 危险操作',{
          confirmButtonText:'确认清除', cancelButtonText:'取消', type:'warning', customClass: 'cyber-message-box', confirmButtonClass:'confirm-danger-btn', cancelButtonClass:'cancel-btn'
        })
        await cleanMetrics()
        metrics.qps = 0; metrics.latency = 0; metrics.errorRate = '0.00%'; topRoutes.value = []; logs.length = 0
        qpsHistory.timestamps.length = 0; qpsHistory.values.length = 0
        ElMessage.success('监控数据已重置')
      } catch (e) { if (e !== 'cancel') throw e }
    }
  } catch (e) {
    console.error(e)
    if (e?.message && e !== 'cancel') ElMessage.error('操作失败: ' + (e.message || '未知错误'))
  }
}

// ---------- 音效（保留） ----------
const initAudio = () => {
  try { audioCtx.value = new (window.AudioContext || window.webkitAudioContext)() } catch (e) { console.warn('音频上下文初始化失败:', e) }
}
const playHoverSound = () => {
  if (!audioCtx.value) return
  if (audioCtx.value.state === 'suspended') audioCtx.value.resume()
  const osc = audioCtx.value.createOscillator(), gain = audioCtx.value.createGain()
  osc.frequency.setValueAtTime(600, audioCtx.value.currentTime)
  gain.gain.setValueAtTime(0.05, audioCtx.value.currentTime)
  gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.value.currentTime + 0.1)
  osc.connect(gain); gain.connect(audioCtx.value.destination); osc.start(); osc.stop(audioCtx.value.currentTime + 0.1)
}

const toggleKnife4j = () => { showKnife4j.value = !showKnife4j.value }

// ---------- 生命周期 ----------
onMounted(() => {
  // 初始化QPS小图
  initQPSChart()
  // 拉取初始数据
  fetchData(); fetchLogs()
  // metric + topology 每 2s 拉取一次（与之前相同节奏），日志每 3s
  metricsTimer = setInterval(fetchData, 2000)
  logsTimer = setInterval(fetchLogs, 3000)
  document.addEventListener('click', initAudio, { once: true })

  const resizeHandler = () => {
    // QPS图表resize
    if (qpsChartInstance) qpsChartInstance.resize()
    // 拓扑图表resize（调用子组件方法）
    if (topologyChartRef.value) topologyChartRef.value.handleResize()
  }
  window.addEventListener('resize', resizeHandler)

  onBeforeUnmount(() => {
    if (metricsTimer) { clearInterval(metricsTimer); metricsTimer = null }
    if (logsTimer) { clearInterval(logsTimer); logsTimer = null }
    // 销毁QPS图表
    if (qpsChartInstance) { qpsChartInstance.dispose(); qpsChartInstance = null }
    window.removeEventListener('resize', resizeHandler)
  })
})
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;600;700&family=Rajdhani:wght@400;500;700&display=swap');

.dashboard-container{min-height:0;height:calc(100vh - var(--cockpit-main-pt, 100px));height:calc(100dvh - var(--cockpit-main-pt, 100px));width:100%;max-width:100%;position:relative;overflow:hidden;background-color:var(--bg-body);font-family:'Rajdhani','Segoe UI',sans-serif;color:var(--text-main);box-sizing:border-box}
.horizon-grid{position:absolute;bottom:0;left:0;width:100%;height:clamp(28%, 36vh, 40%);background:linear-gradient(to bottom,transparent 0%,var(--bg-body) 100%),linear-gradient(0deg,var(--grid-line) 1px,transparent 1px),linear-gradient(90deg,var(--grid-line) 1px,transparent 1px);background-size:100% 100%,clamp(28px,3.5vmin,40px) clamp(28px,3.5vmin,40px),clamp(28px,3.5vmin,40px) clamp(28px,3.5vmin,40px);transform:perspective(500px) rotateX(60deg);transform-origin:bottom;opacity:0.3;z-index:0;pointer-events:none}
.ui-layer{position:absolute;inset:0;z-index:10;padding:var(--cockpit-ui-pad-y,20px) var(--cockpit-ui-pad-x,40px);display:flex;justify-content:space-between;align-items:stretch;gap:clamp(8px,1.5vw,24px);pointer-events:none;box-sizing:border-box}
.hud-panel{width:var(--cockpit-hud-width,380px);max-width:min(var(--cockpit-hud-width,380px),calc(50vw - var(--cockpit-ui-pad-x) - 14px));min-width:0;height:100%;pointer-events:auto;display:flex;flex-direction:column;perspective:800px;overflow:hidden;box-sizing:border-box}
.left-wing{transform:rotateY(var(--cockpit-hud-rotate, 8deg)) translateZ(10px)}.right-wing{transform:rotateY(calc(-1 * var(--cockpit-hud-rotate, 8deg))) translateZ(10px)}
.metric-card,.control-matrix{background:var(--glass-bg-strong);backdrop-filter:var(--glass-backdrop);border:1px solid var(--glass-border-strong);padding:18px;margin-bottom:16px;border-radius:6px;box-shadow:var(--card-shadow);transition:all .3s ease}
.chart-card{padding:clamp(12px,1.8vmin,16px)}.qps-display{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:12px}.mini-chart{width:100%;height:clamp(72px,11vh,120px);margin-top:8px;min-height:72px}
.dual-metrics{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:16px}
.compact-metric{background:var(--glass-bg-strong);backdrop-filter:var(--glass-backdrop);border:1px solid var(--glass-border-strong);padding:14px;border-radius:6px;box-shadow:var(--card-shadow);display:flex;gap:12px;align-items:flex-start}
.compact-icon{width:36px;height:36px;border-radius:6px;background:rgba(56,189,248,0.15);display:flex;align-items:center;justify-content:center;flex-shrink:0}
.compact-icon.danger{background:rgba(248,113,113,0.15)}.compact-icon.success{background:rgba(52,211,153,0.15)}
.compact-icon .el-icon{font-size:18px;color:var(--cyber-primary)}.compact-icon.danger .el-icon{color:var(--cyber-danger)}.compact-icon.success .el-icon{color:var(--cyber-success)}
.compact-info{flex:1;min-width:0}.compact-label{font-size:11px;color:var(--text-secondary);margin-bottom:6px;text-transform:uppercase;letter-spacing:.5px}
.compact-value{font-size:20px;font-weight:700;font-family:Consolas,monospace;margin-bottom:8px;display:block}.unit-sm{font-size:12px;margin-left:2px;opacity:.7}
.scrollable-card{flex:1;display:flex;flex-direction:column;min-height:0}
.top-list-container{flex:1;overflow-y:auto;overflow-x:hidden;margin:0 -6px;padding:0 6px}
.top-list-container::-webkit-scrollbar{width:4px}.top-list-container::-webkit-scrollbar-track{background:rgba(255,255,255,0.05);border-radius:2px}.top-list-container::-webkit-scrollbar-thumb{background:var(--cyber-primary);border-radius:2px;opacity:.5}.top-list-container::-webkit-scrollbar-thumb:hover{opacity:.8}
.panel-header{margin-bottom:16px;display:flex;align-items:center;gap:12px}.panel-header.mt-4{margin-top:16px}
.panel-title{font-size:14px;font-weight:700;letter-spacing:2px;color:var(--cyber-primary);text-shadow:0 0 10px rgba(56,189,248,.3);font-family:'Orbitron',sans-serif}
.badge{background:rgba(56,189,248,.2);color:var(--cyber-primary);padding:2px 8px;border-radius:10px;font-size:11px;font-weight:600;font-family:Consolas,monospace}
.decor-line{flex:1;height:1px;background:linear-gradient(90deg,var(--cyber-primary),transparent)}
.metric-label{display:flex;align-items:center;gap:8px;font-size:12px;color:var(--text-secondary);margin-bottom:10px}
.value{font-size:clamp(20px,2.4vmin,28px);font-weight:700;font-family:Consolas,monospace;color:var(--text-main)}
.glitch-text{position:relative;animation:glitch 2s infinite}.glitch-text::before,.glitch-text::after{content:attr(data-text);position:absolute;top:0;left:0;width:100%;height:100%}
.glitch-text::before{left:2px;text-shadow:-2px 0 var(--cyber-danger);clip:rect(44px,450px,56px,0);animation:glitch-anim 5s infinite linear alternate-reverse}
.glitch-text::after{left:-2px;text-shadow:-2px 0 var(--cyber-primary);clip:rect(44px,450px,56px,0);animation:glitch-anim2 5s infinite linear alternate-reverse}
.trend.up{color:var(--cyber-success);font-size:12px;font-weight:600}
.progress-rail{height:4px;background:rgba(148,163,184,0.2);border-radius:2px;overflow:hidden;position:relative}
.progress-rail.sm{height:3px}.progress-fill{height:100%;box-shadow:0 0 10px currentColor;animation:breathe 3s infinite ease-in-out;transition:width .3s ease}
.neon-primary{background:var(--cyber-primary);color:var(--cyber-primary)}.neon-warning{background:var(--cyber-warning);color:var(--cyber-warning)}.neon-red{background:var(--cyber-danger);color:var(--cyber-danger)}.neon-info{background:var(--cyber-info);color:var(--cyber-info)}
.text-warning{color:var(--cyber-warning)}.text-danger{color:var(--cyber-danger)}.text-success{color:var(--cyber-success)}
.top-list{display:flex;flex-direction:column;gap:12px;padding-bottom:4px}.top-item{width:100%}.top-info{display:flex;align-items:center;gap:8px;font-size:12px;margin-bottom:6px;color:var(--text-secondary)}
.top-rank{color:var(--cyber-primary);font-weight:700;font-family:'Orbitron',sans-serif;min-width:28px}.top-name{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.top-val{color:var(--cyber-primary);font-family:monospace;font-weight:600}

.control-matrix{display:grid;grid-template-columns:1fr 1fr;gap:12px;background:transparent;padding:0;border:none;box-shadow:none}
.matrix-btn{background:var(--btn-base-bg);border:1px solid var(--btn-base-border);min-height:clamp(56px,9vh,75px);height:auto;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:clamp(4px,1vmin,8px);cursor:pointer;transition:all .2s ease;border-radius:6px;font-size:clamp(10px,1.2vmin,12px);color:var(--btn-base-text);font-family:'Orbitron',sans-serif;letter-spacing:1px;box-shadow:var(--btn-base-shadow);pointer-events:auto;padding:8px 6px}
.matrix-btn:hover{background:var(--btn-hover-primary-bg);border-color:var(--btn-hover-primary-border);color:var(--cyber-primary);box-shadow:var(--btn-hover-primary-shadow);transform:translateY(-2px)}
.matrix-btn.success:hover{background:var(--btn-hover-success-bg);border-color:var(--btn-hover-success-border);color:var(--cyber-success);box-shadow:var(--btn-hover-success-shadow)}
.matrix-btn.danger:hover{background:var(--btn-hover-danger-bg);border-color:var(--btn-hover-danger-border);color:var(--cyber-danger);box-shadow:var(--btn-hover-danger-shadow)}
.matrix-btn.warning:hover{background:var(--btn-hover-warning-bg);border-color:var(--btn-hover-warning-border);color:var(--cyber-warning);box-shadow:var(--btn-hover-warning-shadow)}
.matrix-btn .icon{font-size:22px;transition:all .2s ease}.matrix-btn:hover .icon{transform:scale(1.15)}

.intercept-log-title-row{align-items:center}
.log-terminal{flex:1;display:flex;flex-direction:column;min-height:0;min-width:0;margin:0;padding:0;background:transparent;border:none;box-shadow:none;font-family:Consolas,monospace;font-size:10px;--log-time:minmax(58px,0.52fr);--log-type:minmax(56px,1fr);--log-n:32px;--log-src:minmax(0,1.55fr);--log-cols:var(--log-time) var(--log-type) var(--log-n) var(--log-src);--log-gap:clamp(6px,1.4vw,11px)}
.log-list-scroll{flex:1;overflow-y:auto;min-height:0;padding-right:2px;margin:0}
.log-list-scroll::-webkit-scrollbar{width:4px}.log-list-scroll::-webkit-scrollbar-track{background:rgba(255,255,255,0.04);border-radius:2px}.log-list-scroll::-webkit-scrollbar-thumb{background:var(--cyber-primary);border-radius:2px;opacity:.45}.log-list-scroll::-webkit-scrollbar-thumb:hover{opacity:.75}
.log-table-head,.log-row-grid{display:grid;grid-template-columns:var(--log-cols);column-gap:var(--log-gap);align-items:center;min-width:0}
/* 表头：略增高并与下方首行时间块视觉对齐（与两行日期+时钟的垂直中心大致重合） */
.log-table-head{min-height:38px;padding:0 4px 10px;margin:0 0 6px;align-items:center;border-bottom:1px solid rgba(56,189,248,0.14);box-sizing:border-box}
.log-table-head span{font-size:9px;font-weight:600;letter-spacing:1.1px;font-family:'Orbitron','Rajdhani',sans-serif;text-transform:uppercase;color:rgba(186,230,253,0.78);line-height:1.2}
.th-time{justify-self:start;text-align:left;align-self:center}
.th-type{justify-self:center;text-align:center;align-self:center}
.th-n{justify-self:center;text-align:center;align-self:center}
.th-src{justify-self:start;text-align:left;min-width:0;align-self:center}
.log-list{margin:0;padding:0;list-style:none}
.log-item{margin:0;padding:0;border:none}
.log-item:hover{background:rgba(56,189,248,0.05)}
.log-row-grid{padding:7px 4px;cursor:default}
.log-row-grid .col-type{justify-self:center;align-self:center;text-align:center;max-width:100%}
.log-row-grid .col-n{justify-self:center;text-align:center}
.log-row-grid .col-src{justify-self:start;min-width:0}
.col-datetime{display:flex;flex-direction:column;gap:1px;min-width:0;max-width:100%;justify-self:start;align-self:center}
.dt-date{font-size:9px;color:var(--text-secondary);line-height:1.12;font-variant-numeric:tabular-nums;letter-spacing:-0.02em}
.dt-clock{font-size:10px;font-variant-numeric:tabular-nums;color:var(--text-main);line-height:1.12;letter-spacing:-0.02em}
.col-n{font-weight:700;font-size:10px;color:rgba(245,158,11,.88);font-family:Consolas,monospace}
.col-type{font-weight:700;font-size:9px;line-height:1.2;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.col-type.flow{color:#f59e0b}.col-type.fuse{color:#f87171}.col-type.waf{color:#fb7185}.col-type.auth{color:#a78bfa}.col-type.info{color:var(--cyber-primary)}
.col-src{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:var(--text-main);font-size:10px}
.intercept-tooltip-box{min-width:0;max-width:300px;padding:2px 0}
.it-head{margin:0 0 8px;font-size:11px;font-weight:700;color:var(--cyber-primary);letter-spacing:0.5px;font-family:'Orbitron','Rajdhani',sans-serif}
.it-dl{display:grid;grid-template-columns:auto 1fr;gap:6px 12px;margin:0;font-size:11px;line-height:1.45}
.it-dl dt{margin:0;color:var(--text-secondary);font-weight:600;white-space:nowrap}
.it-dl dd{margin:0;color:var(--text-main);min-width:0}
.it-mono{font-family:Consolas,monospace;font-size:10px}
.it-wrap{word-break:break-word;white-space:pre-wrap}
.log-empty-hint{font-size:11px;color:var(--text-secondary);margin:0 0 12px;line-height:1.45;opacity:.88}

.bottom-dock{position:absolute;bottom:var(--cockpit-bottom-dock,30px);left:50%;transform:translateX(-50%);display:flex;gap:clamp(12px,2vw,24px);pointer-events:auto;align-items:center;z-index:20;max-width:calc(100% - 2 * var(--cockpit-ui-pad-x));padding:0 8px;box-sizing:border-box}
.main-btn{background:var(--main-btn-bg);color:var(--main-btn-text);border:none;padding:clamp(10px,1.6vmin,14px) clamp(20px,3vw,32px);font-weight:bold;font-family:'Orbitron',sans-serif;letter-spacing:1.5px;font-size:clamp(12px,1.3vmin,14px);clip-path:polygon(10px 0,100% 0,100% calc(100% - 10px),calc(100% - 10px) 100%,0 100%,0 10px);cursor:pointer;display:flex;align-items:center;gap:10px;transition:all .3s ease;box-shadow:var(--main-btn-shadow);pointer-events:auto;white-space:nowrap}
.main-btn:hover{transform:translateY(-2px) scale(1.02);box-shadow:var(--main-btn-hover-shadow)}.main-btn:active{transform:translateY(0) scale(.98)}
.btn-decor{font-size:18px;animation:pulse 2s infinite}

.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.9);backdrop-filter:blur(8px);z-index:9999;display:flex;align-items:flex-start;justify-content:center;padding:clamp(48px,10vmin,100px) 12px 24px;overflow-y:auto;box-sizing:border-box}
.modal-window{width:min(92vw,1400px);max-width:100%;height:min(calc(100dvh - clamp(80px,14vmin,140px)),calc(100vh - clamp(80px,14vmin,140px)));background:var(--bg-card);border:1px solid var(--border-color);display:flex;flex-direction:column;box-shadow:var(--card-shadow);border-radius:8px;overflow:hidden}
.modal-bar{height:40px;background:var(--bg-header);display:flex;align-items:center;padding:0 20px;justify-content:space-between;border-bottom:1px solid var(--border-color)}
.traffic-lights{display:flex;gap:8px}.traffic-lights span{width:12px;height:12px;border-radius:50%}
.traffic-lights span:nth-child(1){background:var(--cyber-danger)}.traffic-lights span:nth-child(2){background:var(--cyber-warning)}.traffic-lights span:nth-child(3){background:var(--cyber-success)}
.modal-title{color:var(--text-secondary);font-size:12px;font-family:'Orbitron',sans-serif;letter-spacing:1px}
.modal-close{background:none;border:none;color:var(--text-secondary);font-size:22px;cursor:pointer;width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;transition:all .2s ease}
.modal-close:hover{background:rgba(56,189,248,.15);color:var(--cyber-primary)}
iframe{width:100%;height:calc(100% - 40px);background:var(--bg-body);border:none}

/* animations & transitions */
@keyframes scan{0%{top:0}100%{top:100%}}@keyframes breathe{0%,100%{opacity:.8}50%{opacity:1}}@keyframes pulse{0%,100%{transform:scale(1);opacity:1}50%{transform:scale(1.1);opacity:.8}}
@keyframes glitch{0%,100%{transform:translate(0)}20%{transform:translate(-2px,2px)}40%{transform:translate(-2px,-2px)}60%{transform:translate(2px,2px)}80%{transform:translate(2px,-2px)}}
@keyframes glitch-anim{0%,100%{clip:rect(44px,450px,56px,0)}20%{clip:rect(12px,450px,59px,0)}40%{clip:rect(48px,450px,29px,0)}60%{clip:rect(22px,450px,73px,0)}80%{clip:rect(54px,450px,98px,0)}}
@keyframes glitch-anim2{0%,100%{clip:rect(65px,450px,109px,0)}20%{clip:rect(79px,450px,19px,0)}40%{clip:rect(75px,450px,5px,0)}60%{clip:rect(67px,450px,61px,0)}80%{clip:rect(9px,450px,43px,0)}}
.slide-left-enter-active,.slide-right-enter-active{transition:all .8s cubic-bezier(.16,1,.3,1)}.slide-left-enter-from{transform:translateX(-80px) rotateY(8deg) translateZ(10px);opacity:0}.slide-right-enter-from{transform:translateX(80px) rotateY(-8deg) translateZ(10px);opacity:0}
.fade-up-enter-active{transition:all .8s ease .3s}.fade-up-enter-from{transform:translateY(30px);opacity:0}
.zoom-enter-active,.zoom-leave-active{transition:all .4s cubic-bezier(.16,1,.3,1)}.zoom-enter-from{transform:scale(.95);opacity:0}.zoom-leave-to{transform:scale(1.05);opacity:0}

/* responsive */
@media (max-width:1200px){.compact-value{font-size:clamp(16px,2.2vmin,20px)}.bottom-dock{flex-direction:column;gap:12px}}
@media (max-width:768px){.ui-layer{flex-direction:column;padding:var(--cockpit-ui-pad-y) var(--cockpit-ui-pad-x);overflow-y:auto;align-items:stretch;justify-content:flex-start}.hud-panel{width:100%;max-width:100%;height:auto;max-height:none;min-height:min(42vh,420px)}.left-wing,.right-wing{transform:none}.dashboard-container{height:auto;min-height:calc(100dvh - var(--cockpit-main-pt));min-height:calc(100vh - var(--cockpit-main-pt))}.bottom-dock{position:relative;bottom:auto;left:auto;transform:none;margin-top:auto;padding-top:16px}.modal-window{width:95%;height:min(calc(100dvh - 100px),calc(100vh - 100px))}}
</style>
<style>
/* el-tooltip 挂载到 body，边框与背景需全局写 */
.intercept-log-tooltip.el-popper {
  background: var(--glass-bg-strong, rgba(15, 23, 42, 0.92)) !important;
  border: 1px solid var(--glass-border-strong, rgba(56, 189, 248, 0.28)) !important;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35) !important;
  backdrop-filter: blur(12px);
  border-radius: 8px;
  padding: 10px 12px !important;
}
.intercept-log-tooltip .el-popper__arrow::before {
  border-color: var(--glass-border-strong, rgba(56, 189, 248, 0.28)) !important;
}
</style>
