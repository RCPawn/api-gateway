// @/assets/techIcons.js
// 仅配置SVG文件路径，无需手写/压缩任何SVG代码
export const techIcons = {
    mysql: () => import('@/assets/icons/mysql.svg?raw'), // ?raw 告诉Vite以字符串形式导入SVG
    redis: () => import('@/assets/icons/redis.svg?raw'),
    gateway: () => import('@/assets/icons/gateway.svg?raw'),
    users: () => import('@/assets/icons/users.svg?raw'),
    cloud: () => import('@/assets/icons/cloud.svg?raw'),
    tomcat: () => import('@/assets/icons/tomcat.svg?raw'),
};