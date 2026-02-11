import request from '@/utils/request'

// 获取实时监控数据 (QPS, Latency, Top Routes)
export function getDashboardMetrics() {
    return request({
        url: '/dashboard/metrics/realtime',
        method: 'get'
    })
}

// 1. 刷新
export function refreshRoutes() {
    return request({ url: '/dashboard/ops/refresh-routes', method: 'post' })
}

// 2. WAF (开/关)
export function toggleWaf(enable) {
    return request({
        url: '/dashboard/ops/waf',
        method: 'post',
        params: { enable } // 传参
    })
}

// 3. 采样
export function startSampling() {
    return request({ url: '/dashboard/ops/sample', method: 'post' })
}

// 4. 清理
export function cleanMetrics() {
    return request({ url: '/dashboard/ops/clean', method: 'post' })
}

// 获取网络拓扑
export function getTopologyData() {
    return request({ url: '/dashboard/metrics/topology', method: 'get' })
}