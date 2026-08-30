/**
 * 前端入口文件
 *
 * 职责：
 * 1. 将 React 应用挂载到 index.html 中的 #root 容器；
 * 2. 通过 ConfigProvider 为 antd 注入中文本地化配置；
 * 3. 通过 BrowserRouter 启用基于 URL 的前端路由。
 */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import './index.css'
import App from './App.tsx'

// 创建 React 根节点并渲染应用
createRoot(document.getElementById('root')!).render(
  // StrictMode：开发期用于暴露潜在问题的严格模式
  <StrictMode>
    {/* ConfigProvider：全局配置 antd 组件语言为中文 */}
    <ConfigProvider locale={zhCN}>
      {/* BrowserRouter：为应用提供前端路由上下文 */}
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ConfigProvider>
  </StrictMode>,
)
