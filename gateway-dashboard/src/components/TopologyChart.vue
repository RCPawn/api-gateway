<template>
  <div ref="chartRef" class="visual-engine"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { techIcons } from '@/assets/techIcons.js'

// 定义props（暂未使用，保留扩展能力）
const props = defineProps({
  topologyData: {
    type: Object,
    default: () => ({ nodes: [], links: [] })
  }
})

// 拓扑图核心变量
const chartRef = ref(null)
let chartInstance = null

// 工具函数：清理SVG空白字符（复用原有逻辑）
const cleanSvgString = (svgStr) => {
  if (!svgStr) return ''
  // 移除换行、多余空格，保留XML必要结构
  return svgStr.replace(/\s+/g, ' ').replace(/>\s+</g, '><').trim()
}

// 新增：异步获取SVG文件内容
const getSvgContent = async (iconKey) => {
  try {
    // 导入对应SVG文件（返回Promise）
    const svgModule = await techIcons[iconKey]()
    // 清理SVG内容并返回
    return cleanSvgString(svgModule.default)
  } catch (err) {
    console.warn(`加载${iconKey}图标失败`, err)
    return '' // 失败时返回空，降级显示默认emoji
  }
}

// emoji/图标映射函数（改造为异步）
const getIconContent = async (node) => {
  const type = (node.type || '').toLowerCase()
  const name = (node.name || '').toLowerCase()
  // 用户
  if (type.includes('user') || name.includes('user')) {
    return { type: 'svg', content: await getSvgContent('users')}
  }
  // 网关
  if (type.includes('gateway') || name.includes('gateway')) {
    return { type: 'svg', content: await getSvgContent('gateway')}
  }
  // MySQL
  if (type.includes('mysql') || type.includes('db') || type.includes('database') || name.includes('mysql') || name.includes('db')) {
    return { type: 'svg', content: await getSvgContent('mysql')}
  }
  // Redis
  if (type.includes('redis') || name.includes('redis')) {
    return { type: 'svg', content: await getSvgContent('redis')}
  }
  // 服务
  return { type: 'svg', content: await getSvgContent('cloud') }
}

// 初始化拓扑图表（不变）
const initChart = () => {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      show: true,
      formatter: params => {
        if (params.dataType === 'node') {
          const d = params.data || {}
          return `<div style="padding:8px;background:rgba(15,23,42,0.95);border:1px solid rgba(56,189,248,0.3);border-radius:4px;">
            <div style="color:#38bdf8;font-weight:bold;margin-bottom:4px;">${d.name || d.id || ''}</div>
            <div style="color:#94a3b8;font-size:12px;">节点类型: ${d.type || '服务'}</div>
            ${d.detail ? `<div style="color:#94a3b8;font-size:11px;margin-top:4px;">${d.detail}</div>` : ''}
          </div>`
        } else if (params.dataType === 'edge') {
          const t = params.data || {}
          return `<div style="padding:8px;background:rgba(15,23,42,0.95);border:1px solid rgba(56,189,248,0.3);border-radius:4px;">
            <div style="color:#38bdf8;font-size:12px;">${t.source} → ${t.target}</div>
            ${t.desc ? `<div style="color:#94a3b8;font-size:11px;margin-top:4px;">${t.desc}</div>` : ''}
          </div>`
        }
        return params.name
      },
      backgroundColor: 'transparent',
      borderWidth: 0
    },
    series: [{
      name: 'topology',
      type: 'graph',
      layout: 'force',
      force: {
        repulsion: 600,
        edgeLength: [150, 250],
        gravity: 0.1,
        layoutAnimation: true
      },
      roam: true,
      draggable: true,
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 3,
          opacity: 1
        }
      },
      label: {
        show: true,
        position: 'bottom',
        color: '#94a3b8',
        fontSize: 11,
        fontFamily: 'Rajdhani, sans-serif'
      },
      data: [],
      links: [],
      lineStyle: {
        color: '#475569',
        width: 2,
        curveness: 0.2,
        opacity: 0.6
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 8
    }]
  }
  chartInstance.setOption(option)
}

// 更新拓扑数据（核心改造：异步获取SVG内容）
const updateTopology = async (data) => { // 改为async函数
  if (!chartInstance || !data) return
  let nodes = data.nodes || []
  let links = data.links || []

  // 异步处理每个节点的图标
  nodes = Array.isArray(nodes) ? await Promise.all(nodes.map(async (n) => {
    if (!n) return null
    if (typeof n === 'string' || typeof n === 'number') {
      n = { id: String(n), name: String(n) }
    }

    // 异步获取图标内容（SVG文件/Emoji）
    const iconResult = await getIconContent(n)
    let symbol = ''

    // 根据类型生成symbol（复用原有逻辑，仅替换SVG来源）
    if (iconResult.type === 'svg' && iconResult.content) {
      // 直接使用下载的SVG文件内容生成data URI
      symbol = `image://data:image/svg+xml;utf8,${encodeURIComponent(iconResult.content)}`
    } else {
      // Emoji仍用原有text标签逻辑
      symbol = `image://data:image/svg+xml;utf8,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" width="60" height="60"><text x="30" y="45" font-size="40" text-anchor="middle">${iconResult.content}</text></svg>`)}`
    }

    return {
      id: n.id || n.name,
      name: n.name || n.id,
      symbol: symbol,
      symbolSize: n.symbolSize || (n.type && /gateway/i.test(n.type) ? 60 : 50),
      type: n.type || 'service',
      detail: n.detail || n.tooltip || ''
    }
  })).then(res => res.filter(Boolean)) : []

  // 链接处理逻辑不变
  links = Array.isArray(links) ? links.map(l => {
    if (!l) return null
    const source = l.source || l.from || l.src
    const target = l.target || l.to || l.dst
    return {
      source,
      target,
      lineStyle: l.lineStyle || {
        color: (l.status && l.status === 'slow') ? '#f43f5e' : '#5eead4',
        width: l.weight || 2,
        curveness: 0.2,
        opacity: 0.8
      },
      desc: l.desc || l.metric || ''
    }
  }).filter(Boolean) : []

  if (nodes.length === 0 && links.length === 0) return

  chartInstance.setOption({
    series: [{
      data: nodes,
      links: links,
      force: {
        repulsion: Math.max(300, 800 - nodes.length * 5),
        edgeLength: [150, 250],
        gravity: 0.1
      }
    }]
  }, { notMerge: false, lazyUpdate: true })
}

// 窗口resize处理（不变）
const handleResize = () => {
  if (chartInstance) chartInstance.resize()
}

// 生命周期（不变）
onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  window.removeEventListener('resize', handleResize)
})

// 暴露方法（不变）
defineExpose({
  initChart,
  updateTopology, // 注意：现在是异步函数，父组件调用时需加await
  handleResize
})
</script>

<style scoped>
.visual-engine {
  position: absolute;
  inset: 0;
  z-index: 0;
}

@media (max-width: 768px) {
  .visual-engine {
    inset: 20px;
  }
}
</style>