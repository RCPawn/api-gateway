import { ref, nextTick } from 'vue'

const isDark = ref(false)

export function useTheme() {

    // 1. 纯逻辑切换
    function toggleThemeLogic() {
        isDark.value = !isDark.value
        if (isDark.value) {
            document.documentElement.classList.add('dark')
            localStorage.setItem('app-theme', 'dark')
        } else {
            document.documentElement.classList.remove('dark')
            localStorage.setItem('app-theme', 'light')
        }
    }

    // 2. 带弹性扩散动画的切换
    const toggleTheme = (event) => {
        // 获取点击坐标，默认中心
        const x = event?.clientX ?? window.innerWidth / 2
        const y = event?.clientY ?? window.innerHeight / 2

        // 如果浏览器不支持 API，直接走逻辑
        if (!document.startViewTransition) {
            toggleThemeLogic()
            return
        }

        // 计算半径：确保覆盖屏幕最远角落
        const endRadius = Math.hypot(
            Math.max(x, window.innerWidth - x),
            Math.max(y, window.innerHeight - y)
        )

        // 执行转换
        const transition = document.startViewTransition(async () => {
            toggleThemeLogic()
            await nextTick() // 等待 Vue 响应式更新完成
        })

        // 动画执行
        transition.ready.then(() => {
            document.documentElement.animate(
                {
                    // 从点扩散到超大圆（1.1倍半径增加弹性感）
                    clipPath: [
                        `circle(0px at ${x}px ${y}px)`,
                        `circle(${endRadius}px at ${x}px ${y}px)`
                    ]
                },
                {
                    duration: 600, // 增加到 600ms 以展示弹性细节
                    // 精心调校的弹性曲线 (Back Out 效果)
                    easing: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
                    pseudoElement: '::view-transition-new(root)'
                }
            )
        })
    }

    // 初始化状态
    const savedTheme = localStorage.getItem('app-theme')
    if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
        isDark.value = true
        document.documentElement.classList.add('dark')
    }

    return { isDark, toggleTheme }
}