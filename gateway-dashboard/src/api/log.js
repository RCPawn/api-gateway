import request from '@/utils/request'

// 获取日志列表
export function getLogList(params) {
    return request({
        url: '/log/logs', // 走网关的 /log 前缀
        method: 'get',
        params
    })
}

export function getRecentLogs() {
    return request({
        url: '/dashboard/metrics/logs',
        method: 'get'
    })
}