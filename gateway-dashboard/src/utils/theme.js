import { ref, nextTick } from 'vue'

const isDark = ref(false)

function resolveDarkPreference() {
    const saved = localStorage.getItem('app-theme')
    if (saved === 'dark') return true
    if (saved === 'light') return false
    return window.matchMedia('(prefers-color-scheme: dark)').matches
}

/** 与 index.html 内联脚本规则一致，在首帧前由 main 调用，减轻 FOUC */
export function initTheme() {
    try {
        const dark = resolveDarkPreference()
        isDark.value = dark
        document.documentElement.classList.toggle('dark', dark)
    } catch {
        /* localStorage / matchMedia 不可用 */
    }
}

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

function prefersReducedMotion() {
    return (
        typeof window.matchMedia === 'function' &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
    )
}

/**
 * 主题切换：优先 View Transition + 圆形揭示；降级为即时切换。
 * @param {MouseEvent} [event]
 */
function toggleTheme(event) {
    if (prefersReducedMotion()) {
        toggleThemeLogic()
        return
    }

    const x = event?.clientX ?? window.innerWidth / 2
    const y = event?.clientY ?? window.innerHeight / 2

    if (typeof document.startViewTransition !== 'function') {
        toggleThemeLogic()
        return
    }

    const root = document.documentElement
    root.classList.add('theme-switching')

    let transition
    try {
        transition = document.startViewTransition(async () => {
            toggleThemeLogic()
            await nextTick()
        })
    } catch {
        root.classList.remove('theme-switching')
        toggleThemeLogic()
        return
    }

    const clearSwitching = () => root.classList.remove('theme-switching')
    transition.finished.finally(clearSwitching)

    transition.ready
        .then(() => {
            const endRadius =
                Math.hypot(
                    Math.max(x, window.innerWidth - x),
                    Math.max(y, window.innerHeight - y)
                ) * 1.05
            return document.documentElement.animate(
                {
                    clipPath: [
                        `circle(0px at ${x}px ${y}px)`,
                        `circle(${endRadius}px at ${x}px ${y}px)`
                    ]
                },
                {
                    duration: 520,
                    easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
                    pseudoElement: '::view-transition-new(root)'
                }
            ).finished
        })
        .catch(() => {})
}

export function useTheme() {
    return { isDark, toggleTheme }
}
