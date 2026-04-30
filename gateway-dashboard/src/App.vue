<template>
  <!-- 最外层容器 -->
  <div class="cockpit-container">
    <!-- 1. 悬浮指挥台 (导航栏) -->
    <nav class="command-deck">
      <div class="logo-area">
        <span class="logo-icon">📍</span>
        <span class="logo-text">GATEWAY</span>
      </div>

      <div class="nav-items">
        <div
            v-for="item in menuItems"
            :key="item.path"
            :class="['nav-item', { active: currentPath === item.path }]"
            @click="handleNav(item.path)"
        >
          {{ item.label }}
        </div>
      </div>

      <div class="right-actions">
        <el-button circle text @click="toggleTheme" class="theme-btn">
          <el-icon :size="20">
            <component :is="isDark ? Moon : Sunny" />
          </el-icon>
        </el-button>

        <div class="status-badge">
          <span class="pulse-dot"></span>
          <span>ONLINE</span>
        </div>
      </div>
    </nav>

    <!-- 2. 主视窗 -->
    <main class="main-viewport">
      <router-view v-slot="{ Component }">
        <transition name="page-flip" mode="out-in">
          <div :key="route.path" class="view-wrapper">
            <component :is="Component" />
          </div>
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Sunny, Moon } from '@element-plus/icons-vue'
import { useTheme } from '@/utils/theme'

const { isDark, toggleTheme } = useTheme()
const router = useRouter()
const route = useRoute()

const menuItems = [
  { path: '/dashboard', label: '📊 驾驶舱' },
  { path: '/routes', label: '🔗 路由矩阵' },
  { path: '/sentinel', label: '🛡️ 流量防卫' },
  { path: '/logs', label: '📜 审计日志' }
]

const currentPath = computed(() => route.path)

const handleNav = (path) => {
  if (currentPath.value !== path) {
    router.push(path)
  }
}
</script>

<style scoped>
/* App.vue 现在只负责布局定位，颜色全靠 style.css */
.cockpit-container {
  width: 100%;
  max-width: 100vw;
  min-height: 100vh;
  min-height: 100dvh;
  height: 100vh;
  height: 100dvh;
  position: relative;
  background-color: var(--bg-body);
  box-sizing: border-box;
}

/* === 导航栏 === */
.command-deck {
  position: absolute;
  top: var(--cockpit-nav-top, 24px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;

  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  row-gap: 0.5rem;
  gap: clamp(8px, 2vw, 40px);

  padding: 0.65rem clamp(1rem, 2.8vw, 2rem);
  width: min(96vw, 1000px);
  max-width: calc(100vw - 24px);
  min-width: 0;
  box-sizing: border-box;

  background-color: var(--bg-header); /* 跟随主题 */
  backdrop-filter: blur(12px) saturate(180%); /* 磨砂玻璃 */

  border: 1px solid var(--border-color);
  border-radius: 99px;
  box-shadow: var(--card-shadow);

  transition: background-color 0.35s ease, border-color 0.35s ease,
    box-shadow 0.35s ease, backdrop-filter 0.35s ease;
}

/* Logo */
.logo-area {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--text-main);
  flex-shrink: 0;
}

.logo-text {
  color: var(--text-main);
  font-weight: 700; /* 加粗 */
}

html.dark .logo-text {
  color: var(--text-highlight);
  /* 暗黑模式下Logo蓝更醒目，可保留加粗 */
  font-weight: 600;
}

/* 菜单项 */
.nav-items {
  display: flex;
  gap: clamp(4px, 1vw, 8px);
  flex-wrap: wrap;
  justify-content: center;
  flex: 1 1 auto;
  min-width: 0;
}
.nav-item {
  padding: clamp(6px, 1.2vw, 8px) clamp(10px, 1.8vw, 16px);
  border-radius: 20px;
  cursor: pointer;
  font-size: clamp(12px, 1.15vw, 14px);
  font-weight: 500;
  color: var(--text-secondary);
  transition: background-color 0.25s ease, color 0.25s ease, box-shadow 0.25s ease;
  white-space: nowrap;
}
.nav-item:hover {
  background: var(--bg-glass);
  color: var(--text-main);
}
.nav-item.active {
  background: var(--text-main); /* 选中变为前景色 */
  color: var(--bg-body); /* 文字变为背景色 (反色效果) */
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 右侧按钮 */
.right-actions { display: flex; align-items: center; gap: 15px; flex-shrink: 0; }
.theme-btn { color: var(--text-secondary) !important; }
.theme-btn:hover { color: var(--text-highlight) !important; }

/* 在线状态 */
.status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: 12px;
  font-size: 12px;
  color: #10b981;
  font-weight: 700;
}
.pulse-dot {
  width: 6px;
  height: 6px;
  background: #10b981;
  border-radius: 50%;
  box-shadow: 0 0 8px #10b981;
  animation: pulse 2s infinite;
}

/* === 视窗与动画 === */
.main-viewport {
  position: relative;
  z-index: 1;
  padding-top: var(--cockpit-main-pt, 100px);
  padding-left: env(safe-area-inset-left, 0);
  padding-right: env(safe-area-inset-right, 0);
  padding-bottom: env(safe-area-inset-bottom, 0);
  min-height: 100vh;
  min-height: 100dvh;
  height: 100vh;
  height: 100dvh;
  box-sizing: border-box;
  overflow-y: auto;
  overflow-x: hidden;
}
.view-wrapper { width: 100%; height: 100%; }

/* 翻页动画 */
.page-flip-enter-active { transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1); }
.page-flip-leave-active { transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1); }
.page-flip-enter-from { opacity: 0; transform: translateY(30px) scale(0.95); filter: blur(10px); }
.page-flip-leave-to { opacity: 0; transform: translateY(-30px) scale(0.95); filter: blur(10px); }

@keyframes pulse {
  0% { opacity: 1; } 50% { opacity: 0.4; } 100% { opacity: 1; }
}
</style>