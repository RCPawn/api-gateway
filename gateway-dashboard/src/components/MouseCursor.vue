<template>
  <!-- 自定义光标系统：单例容器 -->
  <div class="custom-cursor-system">
    <!-- 锁定框：单例+精准样式控制 -->
    <div
        class="lock-frame"
        :style="lockStyle"
        :class="{ 'lock-frame--active': isHover }"
    >
      <!-- 四个角（独立节点，方向精准） -->
      <div class="corner corner-tl"></div>
      <div class="corner corner-tr"></div>
      <div class="corner corner-bl"></div>
      <div class="corner corner-br"></div>

      <!-- 扫描线 -->
      <div class="scan-line"></div>

      <!-- 中心准星 -->
      <div class="crosshair">
        <div class="crosshair-h"></div>
        <div class="crosshair-v"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { useDark } from '@vueuse/core'

const isDark = useDark()

// ========== 1. 自定义光标SVG（完全还原你原来的） ==========
const createCursorSVG = (color) => {
  const strokeColor = document.documentElement.classList.contains('dark') ? '#ffffff' : '#000000';
  return `
<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path
    d="M3 3L10.5 21L13 13L21 10.5L3 3Z"
    stroke="${strokeColor}"
    stroke-width="2"
    stroke-linejoin="round"
    fill="none"
  />
  <path
    d="M3 3L10.5 21L13 13L21 10.5L3 3Z"
    fill="${color}"
    fill-opacity="0.8"
  />
</svg>
`;
}

// 注入全局光标样式（完全还原你原来的）
const injectGlobalCursor = () => {
  const color = isDark.value ? '#60a5fa' : '#2563eb';
  const svgString = createCursorSVG(color);
  const blob = new Blob([svgString], { type: 'image/svg+xml' });
  const url = URL.createObjectURL(blob);

  const oldStyleEl = document.getElementById('global-cursor-override');
  if (oldStyleEl) {
    oldStyleEl.remove();
  }

  const styleEl = document.createElement('style');
  styleEl.id = 'global-cursor-override';
  document.head.appendChild(styleEl);

  // 👉 只在这里加了一句：输入框/编辑区用原生 I 光标
  styleEl.innerHTML = `
    *:not(input):not(textarea):not([contenteditable]) {
      cursor: url('${url}') 3 3, auto !important;
      cursor: url('${url}') 3 3, pointer !important;
    }
    input, textarea, [contenteditable] {
      cursor: text !important;
    }
  `;
}

watch(isDark, () => nextTick(injectGlobalCursor), { immediate: true })

// ========== 2. 核心状态管理（完全不变） ==========
const mouseX = ref(0)
const mouseY = ref(0)
const lockX = ref(0)
const lockY = ref(0)

const isHover = ref(false)
const isClick = ref(false)

const LOCK_LERP = 0.15

let lastTarget = null
let lastIsInteractive = false

let rafId = null

// ========== 3. 硬件加速动画循环（完全不变） ==========
const animate = () => {
  lockX.value += (mouseX.value - lockX.value) * LOCK_LERP
  lockY.value += (mouseY.value - lockY.value) * LOCK_LERP
  rafId = requestAnimationFrame(animate)
}

const lockStyle = computed(() => ({
  transform: `translate3d(${lockX.value}px, ${lockY.value}px, 0) scale(${isClick.value ? 0.85 : 1})`,
  pointerEvents: 'none'
}))

// ========== 4. 交互判断（只加：输入框不触发锁定框） ==========
const checkIsInteractive = (target) => {
  if (!target || target.nodeType !== 1) return false
  if (target === lastTarget) return lastIsInteractive

  // 👉 新增：输入框/编辑区 → 不显示锁定框
  const tag = target.tagName.toLowerCase()
  if (['input', 'textarea'].includes(tag) || target.hasAttribute('contenteditable')) {
    lastTarget = target
    lastIsInteractive = false
    return false
  }

  lastTarget = target

  // 下面完全和你原来一样
  if (['a', 'button', 'select', 'option'].includes(tag)) {
    lastIsInteractive = true
    return true
  }
  if (target.closest('a, button, [role="button"], [onclick]')) {
    lastIsInteractive = true
    return true
  }
  if (target.classList.contains('clickable') || target.classList.contains('cursor-hover')) {
    lastIsInteractive = true
    return true
  }
  try {
    const style = window.getComputedStyle(target)
    lastIsInteractive = style.cursor === 'pointer' && style.display !== 'none'
    return lastIsInteractive
  } catch (e) {
    lastIsInteractive = false
    return false
  }
}

// ========== 5. 事件（完全不变） ==========
const onMouseMove = (e) => {
  mouseX.value = e.clientX
  mouseY.value = e.clientY
  isHover.value = checkIsInteractive(e.target)
}

let clickDebounceTimer = null
const onMouseDown = () => {
  clearTimeout(clickDebounceTimer)
  isClick.value = true
}
const onMouseUp = () => {
  clickDebounceTimer = setTimeout(() => {
    isClick.value = false
  }, 80)
}

const onMouseLeave = () => {
  isHover.value = false
  lastTarget = null
}

// ========== 6. 生命周期（完全不变） ==========
onMounted(() => {
  if (window.matchMedia('(hover: hover)').matches) {
    document.addEventListener('mousemove', onMouseMove, { passive: true })
    document.addEventListener('mousedown', onMouseDown, { passive: true })
    document.addEventListener('mouseup', onMouseUp, { passive: true })
    document.addEventListener('mouseleave', onMouseLeave, { passive: true })
    rafId = requestAnimationFrame(animate)
  }
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onMouseMove)
  document.removeEventListener('mousedown', onMouseDown)
  document.removeEventListener('mouseup', onMouseUp)
  document.removeEventListener('mouseleave', onMouseLeave)
  if (rafId) cancelAnimationFrame(rafId)

  const styleEl = document.getElementById('global-cursor-override')
  if (styleEl) styleEl.remove()

  clearTimeout(clickDebounceTimer)
  lastTarget = null
})
</script>

<style scoped>
/* 样式 100% 完全还原你原来的，一行没多改 */
.custom-cursor-system {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  pointer-events: none;
  z-index: 999999;
  user-select: none;
}

@media (hover: none), (max-width: 767px) {
  .custom-cursor-system { display: none !important; }
}

.lock-frame {
  position: absolute;
  top: 0;
  left: 0;
  width: 60px;
  height: 60px;
  margin: -30px 0 0 -30px;
  opacity: 0;
  visibility: hidden;
  will-change: transform, opacity;
  transition:
      opacity 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
      visibility 0s 0.2s;
}

.lock-frame--active {
  opacity: 1;
  visibility: visible;
  transition:
      opacity 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
      visibility 0s;
}

.corner {
  position: absolute;
  width: 12px;
  height: 12px;
  border: 2px solid var(--primary-color);
  opacity: 1;
}

.corner-tl {
  top: 0;
  left: 0;
  border-right: none;
  border-bottom: none;
  animation: cornerPulse 1.5s ease-in-out infinite;
}
.corner-tr {
  top: 0;
  right: 0;
  border-left: none;
  border-bottom: none;
  animation: cornerPulse 1.5s ease-in-out 0.1s infinite;
}
.corner-bl {
  bottom: 0;
  left: 0;
  border-right: none;
  border-top: none;
  animation: cornerPulse 1.5s ease-in-out 0.2s infinite;
}
.corner-br {
  bottom: 0;
  right: 0;
  border-left: none;
  border-top: none;
  animation: cornerPulse 1.5s ease-in-out 0.3s infinite;
}

@keyframes cornerPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.7; transform: scale(1.15); }
}

.scan-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--primary-color), transparent);
  opacity: 0.9;
  animation: scan 2s linear infinite;
}
@keyframes scan {
  0% { transform: translateY(0); opacity: 0; }
  10% { opacity: 0.9; }
  90% { opacity: 0.9; }
  100% { transform: translateY(60px); opacity: 0; }
}

.crosshair {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 20px;
  height: 20px;
  margin: -10px 0 0 -10px;
}
.crosshair-h, .crosshair-v {
  position: absolute;
  background: var(--primary-color);
  opacity: 0.7;
}
.crosshair-h {
  top: 50%;
  left: 0;
  width: 100%;
  height: 1px;
  margin-top: -0.5px;
}
.crosshair-v {
  top: 0;
  left: 50%;
  width: 1px;
  height: 100%;
  margin-left: -0.5px;
}

:root:not(.dark) .corner {
  border-color: #2563eb;
}
:root:not(.dark) .scan-line {
  background: linear-gradient(90deg, transparent, #2563eb, transparent);
}
:root:not(.dark) .crosshair-h,
:root:not(.dark) .crosshair-v {
  background: #2563eb;
}
</style>