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
import PageContainer from './components/PageContainer'
import { ProjectProvider } from './context/ProjectContext'
import Workbench from './components/workbench/Workbench'
import LoginPage from './pages/LoginPage'
import ProjectList from './pages/ProjectList'
import UserManagement from './pages/UserManagement'
import UiTestList from './pages/UiTestList'
import UiTestEditor from './pages/UiTestEditor'
import UiReportList from './pages/UiReportList'
import UiExecutePage from './pages/UiExecutePage'
import EnvironmentConfigPage from './pages/EnvironmentConfigPage'
import TestExecutionPage from './pages/TestExecutionPage'
import TestReportPage from './pages/TestReportPage'
import AuditLogPage from './pages/AuditLogPage'

/** 应用根组件：定义路由表 */
export default function App() {
  return (
    <Routes>
      {/* 登录页（公开，不套用业务布局） */}
      <Route path="/login" element={<LoginPage />} />

      {/* 受保护路由：需登录，并共享 AppLayout 布局与全局项目上下文 */}
      <Route
        element={
          <RequireAuth>
            <ProjectProvider>
              <AppLayout />
            </ProjectProvider>
          </RequireAuth>
        }
      >
        {/* 根路径重定向到接口自动化工作台 */}
        <Route path="/" element={<Navigate to="/workbench" replace />} />
        {/* 接口自动化工作台（全屏，不经过 PageContainer） */}
        <Route path="/workbench" element={<Workbench />} />

        {/* 其余业务页面统一套用内边距 + 滚动容器 */}
        <Route element={<PageContainer />}>
          {/* 接口自动化：测试执行 / 测试报告 */}
          <Route path="/test-execution" element={<TestExecutionPage />} />
          <Route path="/test-reports" element={<TestReportPage />} />
          {/* UI 自动化 */}
          <Route path="/ui-tests" element={<UiTestList />} />
          <Route path="/ui-tests/:testId" element={<UiTestEditor />} />
          <Route path="/ui-scenarios" element={<UiExecutePage />} />
          <Route path="/ui-reports" element={<UiReportList />} />
          {/* 环境配置 */}
          <Route path="/environments" element={<EnvironmentConfigPage />} />
          {/* 用户管理 */}
          <Route path="/users" element={<UserManagement />} />
          {/* 审计日志 */}
          <Route path="/audit-logs" element={<AuditLogPage />} />
          {/* 项目管理 */}
          <Route path="/projects" element={<ProjectList />} />
        </Route>

        {/* 未匹配路径统一重定向到工作台 */}
        <Route path="*" element={<Navigate to="/workbench" replace />} />
      </Route>
    </Routes>
  )
}
