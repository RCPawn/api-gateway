import request from '@/utils/request'

// 获取路由列表
export function getRoutes() {
    return request({
        url: '/admin/routes',
        method: 'get'
    })
}

// 新增或更新路由
export function saveRoute(data) {
    return request({
        url: '/admin/routes',
        method: 'post',
        data
    })
}

// 删除路由
export function deleteRoute(id) {
    return request({
        url: `/admin/routes/${id}`,
        method: 'delete'
    })
}