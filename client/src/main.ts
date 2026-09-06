import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import * as Icons from '@ant-design/icons-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)

// 全局注册图标
for (const [name, icon] of Object.entries(Icons)) {
  app.component(name, icon)
}

app.use(createPinia())
app.use(router)
app.use(Antd)

// 启动时从 localStorage 恢复登录态
const auth = useAuthStore()
auth.restore()

app.mount('#app')