<template>
  <div class="cockpit-container">
    <!-- 1. 背景层 -->
    <div class="background-grid"></div>

    <!-- 2. 顶部悬浮指挥台 -->
    <nav class="command-deck">
      <div class="logo">🛡️ GATEWAY</div>

      <div class="nav-items">
        <div
            v-for="item in menuItems"
            :key="item.path"
            :class="['nav-item', { active: currentPath === item.path }]"
            @click="handleNav(item.path)"
        >
          <!-- 这里的图标需要你安装引入，或者暂时用文字代替 -->
          <span>{{ item.label }}</span>
        </div>
      </div>

      <div class="status-indicator">
        <span class="pulse-dot"></span>
        <span class="status-text">ONLINE</span>
      </div>
    </nav>

    <!-- 3. 主视窗 (路由出口) -->
    <main class="main-viewport">
      <!-- 路由切换动画 -->
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// 菜单配置
const menuItems = [
  { path: '/dashboard', label: '📊 驾驶舱' },
  { path: '/routes', label: '🔗 路由矩阵' },
  { path: '/sentinel', label: '🛡️ 流量防卫' },
  { path: '/logs', label: '📜 审计日志' }
]

// 获取当前激活的路由路径
const currentPath = computed(() => route.path)

// 页面跳转
const handleNav = (path) => {
  router.push(path)
}
</script>

<style scoped>
/* 全局容器 */
.cockpit-container {
  width: 100vw;
  height: 100vh;
  background-color: #0f172a;
  color: #e2e8f0;
  font-family: 'Inter', system-ui, sans-serif;
  overflow: hidden;
  position: relative;
}

/* 背景网格 */
.background-grid {
  position: absolute;
  top: 0; left: 0; width: 100%; height: 100%;
  background-image:
      linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
  pointer-events: none;
  z-index: 0;
  /* 加一点暗角，让视线聚焦中心 */
  background: radial-gradient(circle at center, transparent 0%, #0f172a 90%);
}

.command-deck {
  position: absolute;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;

  /* 使用 fit-content 让宽度自适应内容，但给个最小值防止太挤 */
  width: fit-content;
  min-width: 600px;
  max-width: 90vw; /* 防止手机端溢出 */

  display: flex;
  align-items: center;
  justify-content: space-between; /* 左右分散，中间居中 */
  gap: 40px; /* 元素之间的间距 */

  padding: 12px 40px; /* 增加内边距 */
  background: rgba(30, 41, 59, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);

  white-space: nowrap; /* ⚠️ 核心修复：强制不换行 */
}

.logo {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 3px;
  color: #fff;
  text-shadow: 0 0 10px rgba(56, 189, 248, 0.5);
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 按钮区域：保持之前的胶囊风格 */
.nav-items {
  display: flex;
  gap: 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  color: #94a3b8;
  font-weight: 500;
}

.nav-item:hover {
  color: #e2e8f0;
}

/* 选中状态：文字发光 + 底部光条 */
.nav-item.active {
  background: rgba(56, 189, 248, 0.2);
  color: #38bdf8;
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.2);
}

@keyframes slideUp {
  from { transform: scaleX(0); }
  to { transform: scaleX(1); }
}

/* 状态指示器 */
.status-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  background: rgba(74, 222, 128, 0.1);
  border-radius: 20px;
  border: 1px solid rgba(74, 222, 128, 0.2);
}

.pulse-dot {
  width: 6px;
  height: 6px;
  background-color: #4ade80;
  border-radius: 50%;
  box-shadow: 0 0 8px #4ade80;
  animation: pulse 2s infinite;
}

.status-text {
  font-size: 12px;
  color: #4ade80;
  font-weight: 700;
  letter-spacing: 1px;
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.main-viewport {
  position: relative;
  z-index: 1;
  padding-top: 110px;
  height: 100vh;
  box-sizing: border-box;
  overflow-y: auto;
}

/* 路由切换动画：淡入淡出 + 轻微缩放 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.98);
}
</style>