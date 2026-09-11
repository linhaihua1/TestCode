/**
 * Vue 应用入口。
 *
 * <p>启动流程：
 * <ol>
 *   <li>创建 Vue 应用实例</li>
 *   <li>全局注册 Ant Design 图标（按需使用）</li>
 *   <li>安装 Pinia（状态管理）、vue-router（路由）、Antd（UI 组件库）</li>
 *   <li>从 localStorage 恢复登录态（用户 token + 信息）</li>
 *   <li>挂载到 #app 节点</li>
 * </ol>
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import * as Icons from '@ant-design/icons-vue'
import 'ant-design-vue/dist/reset.css'
// 设计系统：令牌 → 全局样式（顺序不可颠倒，global 依赖 tokens 的变量）
import './styles/tokens.css'
import './styles/global.css'
import App from './App.vue'
import router from './router'
import { canWrite } from './directives/canWrite'
import { useAuthStore } from './stores/auth'

// 创建 Vue 应用实例
const app = createApp(App)

// 注册权限指令：查看者自动禁用写操作按钮
app.directive('can-write', canWrite)

// 全局注册所有 Ant Design 图标（按需引用，例如 <HomeOutlined />）
// 注意：完整注册会增大打包体积，如对包大小敏感可改为按需 import
for (const [name, icon] of Object.entries(Icons)) {
  app.component(name, icon)
}

// 安装插件
app.use(createPinia())   // 状态管理
app.use(router)          // 路由
app.use(Antd)            // Ant Design Vue 组件库（全局注册所有组件）

// 启动时从 localStorage 恢复登录态（用户 token + 用户信息）
// 这一步必须在 router 安装之后、app.mount 之前，否则路由守卫拿不到 auth 状态
const auth = useAuthStore()
auth.restore()

// 挂载到 index.html 的 #app 节点
app.mount('#app')
