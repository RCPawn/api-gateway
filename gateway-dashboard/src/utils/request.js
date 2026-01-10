import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const service = axios.create({
    baseURL: 'http://localhost:9000', // 后端 Gateway 地址
    timeout: 5000
})

// 请求拦截器 (可以在这里加 Token)
service.interceptors.request.use(
    config => {
        // 示例：如果有 token，加到 header
        // config.headers['Authorization'] = getToken()
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 响应拦截器 (统一处理 Result 结构)
service.interceptors.response.use(
    response => {
        const res = response.data
        // 如果后端返回的 code 不是 200，说明业务逻辑出错
        if (res.code !== 200) {
            ElMessage.error(res.message || '系统错误')
            return Promise.reject(new Error(res.message || 'Error'))
        } else {
            // 剥离外层，只返回 data，让页面拿到的直接是数据
            return res.data
        }
    },
    error => {
        console.log('err' + error)
        ElMessage.error(error.message)
        return Promise.reject(error)
    }
)

export default service