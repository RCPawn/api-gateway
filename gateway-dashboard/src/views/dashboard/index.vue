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

          <div class="panel-header mt-4">
            <span class="panel-title">拦截日志</span>
            <span class="badge">{{ logs.length }}</span>
          </div>

          <div class="log-terminal">
            <ul class="log-list" ref="logListRef">
              <li v-for="(log,i) in logs" :key="i" class="log-item">
                <span class="time">{{ log.time }}</span>
                <span class="tag" :class="log.type">{{ log.tag }}</span>
                <span class="msg">{{ log.msg }}</span>
              </li>
            </ul>
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
    const res = await getRecentLogs()
    if (res && Array.isArray(res)) {
      logs.length = 0
      res.forEach(log => logs.push({
        time: formatLogTime(log.timestamp || log.time),
        tag: mapLogLevel(log.level || log.tag),
        type: mapLogType(log.level || log.type),
        msg: log.message || log.msg || log.path || ''
      }))
      if (logs.length > 100) logs.splice(100)
    }
  } catch (e) {
    console.warn('日志数据拉取失败:', e)
  }
}

const formatLogTime = (timestamp) => {
  if (!timestamp) return new Date().toLocaleTimeString()
  if (typeof timestamp === 'number') return new Date(timestamp).toLocaleTimeString()
  if (typeof timestamp === 'string' && timestamp.includes(':')) return timestamp
  try { return new Date(timestamp).toLocaleTimeString() } catch { return new Date().toLocaleTimeString() }
}

const mapLogLevel = (level) => {
  const m = { block: 'BLOCK', blocked: 'BLOCK', warn: 'WARN', warning: 'WARN', info: 'INFO', error: 'ERROR', danger: 'BLOCK' }
  return m[(level||'info').toLowerCase()] || 'INFO'
}
const mapLogType = (level) => {
  const m = { block: 'danger', blocked: 'danger', warn: 'warn', warning: 'warn', info: 'info', error: 'danger', danger: 'danger' }
  return m[(level||'info').toLowerCase()] || 'info'
}

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
    grid: { left: 25, right: 10, top: 10, bottom: 20, containLabel: false },
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

.dashboard-container{height:calc(100vh - 100px);width:100%;position:relative;overflow:hidden;background-color:var(--bg-body);font-family:'Rajdhani','Segoe UI',sans-serif;color:var(--text-main)}
.horizon-grid{position:absolute;bottom:0;left:0;width:100%;height:40%;background:linear-gradient(to bottom,transparent 0%,var(--bg-body) 100%),linear-gradient(0deg,var(--grid-line) 1px,transparent 1px),linear-gradient(90deg,var(--grid-line) 1px,transparent 1px);background-size:100% 100%,40px 40px,40px 40px;transform:perspective(500px) rotateX(60deg);transform-origin:bottom;opacity:0.3;z-index:0;pointer-events:none}
.ui-layer{position:absolute;inset:0;z-index:10;padding:20px 40px;display:flex;justify-content:space-between;pointer-events:none}
.hud-panel{width:380px;height:100%;pointer-events:auto;display:flex;flex-direction:column;perspective:800px;overflow:hidden}
.left-wing{transform:rotateY(8deg) translateZ(10px)}.right-wing{transform:rotateY(-8deg) translateZ(10px)}
.metric-card,.control-matrix,.log-terminal{background:var(--glass-bg-strong);backdrop-filter:var(--glass-backdrop);border:1px solid var(--glass-border-strong);padding:18px;margin-bottom:16px;border-radius:6px;box-shadow:var(--card-shadow);transition:all .3s ease}
.chart-card{padding:16px}.qps-display{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:12px}.mini-chart{width:100%;height:100px;margin-top:8px}
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
.value{font-size:26px;font-weight:700;font-family:Consolas,monospace;color:var(--text-main)}
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
.matrix-btn{background:var(--btn-base-bg);border:1px solid var(--btn-base-border);height:75px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px;cursor:pointer;transition:all .2s ease;border-radius:6px;font-size:12px;color:var(--btn-base-text);font-family:'Orbitron',sans-serif;letter-spacing:1px;box-shadow:var(--btn-base-shadow);pointer-events:auto}
.matrix-btn:hover{background:var(--btn-hover-primary-bg);border-color:var(--btn-hover-primary-border);color:var(--cyber-primary);box-shadow:var(--btn-hover-primary-shadow);transform:translateY(-2px)}
.matrix-btn.success:hover{background:var(--btn-hover-success-bg);border-color:var(--btn-hover-success-border);color:var(--cyber-success);box-shadow:var(--btn-hover-success-shadow)}
.matrix-btn.danger:hover{background:var(--btn-hover-danger-bg);border-color:var(--btn-hover-danger-border);color:var(--cyber-danger);box-shadow:var(--btn-hover-danger-shadow)}
.matrix-btn.warning:hover{background:var(--btn-hover-warning-bg);border-color:var(--btn-hover-warning-border);color:var(--cyber-warning);box-shadow:var(--btn-hover-warning-shadow)}
.matrix-btn .icon{font-size:22px;transition:all .2s ease}.matrix-btn:hover .icon{transform:scale(1.15)}

.log-terminal{flex:1;overflow-y:auto;font-family:Consolas,monospace;font-size:11px;padding-right:4px;min-height:0}
.log-terminal::-webkit-scrollbar{width:4px}.log-terminal::-webkit-scrollbar-track{background:rgba(255,255,255,0.05);border-radius:2px}.log-terminal::-webkit-scrollbar-thumb{background:var(--cyber-primary);border-radius:2px;opacity:.5}.log-terminal::-webkit-scrollbar-thumb:hover{opacity:.8}
.log-list{margin:0;padding:0;list-style:none}.log-item{margin-bottom:8px;display:flex;gap:10px;opacity:.9;transition:all .2s ease}.log-item:hover{opacity:1;transform:translateX(2px)}
.time{color:var(--text-secondary);min-width:70px;font-size:10px}.tag{min-width:50px;font-weight:700;font-size:10px}.tag.warn{color:var(--cyber-warning)}.tag.danger{color:var(--cyber-danger)}.tag.info{color:var(--cyber-primary)}.msg{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}

.bottom-dock{position:absolute;bottom:30px;left:50%;transform:translateX(-50%);display:flex;gap:24px;pointer-events:auto;align-items:center;z-index:20}
.main-btn{background:var(--main-btn-bg);color:var(--main-btn-text);border:none;padding:14px 32px;font-weight:bold;font-family:'Orbitron',sans-serif;letter-spacing:1.5px;font-size:14px;clip-path:polygon(10px 0,100% 0,100% calc(100% - 10px),calc(100% - 10px) 100%,0 100%,0 10px);cursor:pointer;display:flex;align-items:center;gap:10px;transition:all .3s ease;box-shadow:var(--main-btn-shadow);pointer-events:auto}
.main-btn:hover{transform:translateY(-2px) scale(1.02);box-shadow:var(--main-btn-hover-shadow)}.main-btn:active{transform:translateY(0) scale(.98)}
.btn-decor{font-size:18px;animation:pulse 2s infinite}

.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.9);backdrop-filter:blur(8px);z-index:9999;display:flex;align-items:flex-start;justify-content:center;padding-top:100px;overflow-y:auto}
.modal-window{width:90%;max-width:1400px;height:calc(100vh - 120px);background:var(--bg-card);border:1px solid var(--border-color);display:flex;flex-direction:column;box-shadow:var(--card-shadow);border-radius:8px;overflow:hidden}
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
@media (max-width:1200px){.hud-panel{width:340px}.bottom-dock{flex-direction:column;gap:12px}}
@media (max-width:768px){.ui-layer{flex-direction:column;padding:10px 20px}.hud-panel{width:100%;height:auto;margin-bottom:20px}.left-wing,.right-wing{transform:none}.modal-window{width:95%;height:calc(100vh - 140px)}}
</style>
