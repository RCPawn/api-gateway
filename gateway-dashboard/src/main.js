import { createApp } from 'vue'
import '@/styles/theme.css'
import './styles/style.css'
import App from './App.vue'
import router from './router' // 👈 1. 引入路由

// 1. 引入 Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

const app = createApp(App)

// 2. 使用 Element Plus
app.use(ElementPlus)
app.use(router) // 👈 2. 挂载路由
app.mount('#app')
