import request from '@/utils/request'

// 1. 获取聚合后的资源列表 (Dashboard 核心)
export function getSentinelResources() {
    return request({
        url: '/admin/sentinel/resources',
        method: 'get'
    })
}

// 2. 保存限流规则
export function saveFlowRule(data) {
    return request({
        url: '/admin/sentinel/flow',
        method: 'post',
        data
    })
}

// 3. 保存降级规则
export function saveDegradeRule(data) {
    return request({
        url: '/admin/sentinel/degrade',
        method: 'post',
        data
    })
}

// 4. 删除资源防护
export function deleteResource(resourceName) {
    return request({
        url: '/admin/sentinel/resource',
        method: 'delete',
        params: { name: resourceName }
    })
}