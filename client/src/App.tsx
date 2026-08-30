/**
 * 应用路由表定义
 *
 * 职责：集中声明前端所有页面的路由映射。
 * 所有业务页面都嵌套在 AppLayout 布局路由下，
 * 从而共享统一的左侧菜单与内容区框架。
 */
import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import ProjectList from './pages/ProjectList'
import ApiList from './pages/ApiList'
import ScenarioList from './pages/ScenarioList'
import ScenarioEditor from './pages/ScenarioEditor'
import EnvironmentPage from './pages/EnvironmentPage'
import ReportList from './pages/ReportList'

/** 应用根组件：定义路由表 */
export default function App() {
  return (
    <Routes>
      {/* 所有子路由共享 AppLayout 布局（左侧菜单 + 内容区） */}
      <Route element={<AppLayout />}>
        {/* 根路径重定向到项目列表 */}
        <Route path="/" element={<Navigate to="/projects" replace />} />
        {/* 项目列表页 */}
        <Route path="/projects" element={<ProjectList />} />
        {/* 接口管理页（项目维度） */}
        <Route path="/projects/:projectId/apis" element={<ApiList />} />
        {/* 场景列表页（项目维度） */}
        <Route path="/projects/:projectId/scenarios" element={<ScenarioList />} />
        {/* 场景编排与执行页 */}
        <Route path="/projects/:projectId/scenarios/:scenarioId" element={<ScenarioEditor />} />
        {/* 环境配置页 */}
        <Route path="/projects/:projectId/environments" element={<EnvironmentPage />} />
        {/* 测试报告页 */}
        <Route path="/projects/:projectId/reports" element={<ReportList />} />
        {/* 未匹配路径统一重定向到项目列表 */}
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Route>
    </Routes>
  )
}
