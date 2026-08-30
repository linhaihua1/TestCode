/**
 * 应用路由表定义
 *
 * 职责：集中声明前端所有页面的路由映射。
 * 登录页独立于布局之外；其余业务页面都嵌套在 RequireAuth（登录守卫）+ AppLayout 布局路由下，
 * 未登录会自动跳转登录页，登录后共享统一的左侧菜单与内容区框架。
 */
import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import RequireAuth from './components/RequireAuth'
import LoginPage from './pages/LoginPage'
import ProjectList from './pages/ProjectList'
import ApiList from './pages/ApiList'
import ScenarioList from './pages/ScenarioList'
import ScenarioEditor from './pages/ScenarioEditor'
import EnvironmentPage from './pages/EnvironmentPage'
import ReportList from './pages/ReportList'
import UserManagement from './pages/UserManagement'
import UiTestList from './pages/UiTestList'
import UiTestEditor from './pages/UiTestEditor'
import UiReportList from './pages/UiReportList'

/** 应用根组件：定义路由表 */
export default function App() {
  return (
    <Routes>
      {/* 登录页（公开，不套用业务布局） */}
      <Route path="/login" element={<LoginPage />} />

      {/* 受保护路由：需登录（RequireAuth），并共享 AppLayout 布局 */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
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
        {/* UI 自动化测试列表页 */}
        <Route path="/projects/:projectId/ui-tests" element={<UiTestList />} />
        {/* UI 测试编排与执行页 */}
        <Route path="/projects/:projectId/ui-tests/:testId" element={<UiTestEditor />} />
        {/* UI 测试报告页 */}
        <Route path="/projects/:projectId/ui-reports" element={<UiReportList />} />
        {/* 用户管理页 */}
        <Route path="/users" element={<UserManagement />} />
        {/* 未匹配路径统一重定向到项目列表 */}
        <Route path="*" element={<Navigate to="/projects" replace />} />
      </Route>
    </Routes>
  )
}
