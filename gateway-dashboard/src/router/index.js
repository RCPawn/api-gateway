import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    {
        path: '/',
        redirect: '/dashboard' // 默认跳去仪表盘
    },
    {
        path: '/dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '驾驶舱' }
    },
    {
        path: '/routes',
        name: 'routes',
        component: () => import('@/views/routes/index.vue'),
        meta: { title: '路由矩阵' }
    },
    {
        path: '/sentinel',
        name: 'sentinel',
        component: () => import('@/views/sentinel/index.vue'),
        meta: { title: '流量防卫' }
    },
    {
        path: '/logs',
        name: 'logs',
        component: () => import('@/views/logs/index.vue'),
        meta: { title: '审计日志' }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router