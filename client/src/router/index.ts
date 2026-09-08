/**
 * Vue Router 配置。
 *
 * <h3>路由结构</h3>
 * <ul>
 *   <li>/login —— 登录页（公开）</li>
 *   <li>/ —— 根布局（AppLayout），含侧边栏 + 顶部导航</li>
 *   <li>  ├── /projects 项目列表
 *   <li>  ├── /environments / /environment-config 环境与全局变量
 *   <li>  ├── /workbench 三栏工作台（核心用例编辑）
 *   <li>  ├── /apis 接口定义管理
 *   <li>  ├── /scenarios /scenarios/:id 场景列表 / 编辑器
 *   <li>  ├── /tasks 测试任务
 *   <li>  ├── /reports /reports/:id 报告列表 / 详情
 *   <li>  ├── /perf /perf/reports/:id 性能用例 / 报告
 *   <li>  ├── /ui /ui/:id /ui/reports UI 用例列表 / 编辑器 / 报告
 *   <li>  ├── /users 用户管理（管理员）
 *   <li>  └── /audit-logs 审计日志（管理员）
 * </ul>
 *
 * <h3>路由守卫</h3>
 * <ul>
 *   <li>meta.public = true：不需登录（只有 /login）</li>
 *   <li>默认：未登录跳 /login 并保留 redirect 参数</li>
 *   <li>meta.adminOnly = true：非管理员跳 /projects</li>
 * </ul>
 *
 * <h3>Hash 模式</h3>
 * 使用 {@link createWebHashHistory}（路径以 # 开头），便于部署到任意路径（包括子目录）。
 * 生产环境如用 Nginx 反向代理，HTML 路由无需 server 配置。
 */
import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/** 路由表：path / name / component / meta */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginPage.vue'),
    meta: { public: true }                       // 不需登录
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/projects',                       // 进入根路径自动跳到项目列表
    children: [
      { path: 'projects', name: 'projects', component: () => import('@/views/ProjectList.vue') },
      { path: 'environments', name: 'environments', component: () => import('@/views/EnvironmentPage.vue') },
      { path: 'environment-config', name: 'environment-config', component: () => import('@/views/EnvironmentConfigPage.vue') },
      { path: 'workbench', name: 'workbench', component: () => import('@/views/Workbench.vue') },
      { path: 'recycle-bin', name: 'recycle-bin', component: () => import('@/views/RecycleBinPage.vue') },
      { path: 'global-variables', name: 'global-variables', component: () => import('@/views/GlobalVariablePage.vue') },
      { path: 'debug-records', name: 'debug-records', component: () => import('@/views/AuditLogPage.vue') },
      { path: 'apis', name: 'apis', component: () => import('@/views/ApiList.vue') },
      { path: 'scenarios', name: 'scenarios', component: () => import('@/views/ScenarioList.vue') },
      { path: 'scenarios/:id', name: 'scenario-editor', component: () => import('@/views/ScenarioEditor.vue') },
      { path: 'tasks', name: 'tasks', component: () => import('@/views/TestExecutionPage.vue') },
      { path: 'reports', name: 'reports', component: () => import('@/views/ReportList.vue') },
      { path: 'reports/:id', name: 'report-detail', component: () => import('@/views/TestReportPage.vue') },
      { path: 'trend', name: 'trend', component: () => import('@/views/TrendPage.vue') },
      { path: 'perf', name: 'perf', component: () => import('@/views/PerfCasePage.vue') },
      { path: 'perf/reports/:id', name: 'perf-report-detail', component: () => import('@/views/PerfReportPage.vue') },
      { path: 'ui', name: 'ui-tests', component: () => import('@/views/UiTestList.vue') },
      { path: 'ui/:id', name: 'ui-test-editor', component: () => import('@/views/UiTestEditor.vue') },
      { path: 'ui/reports', name: 'ui-reports', component: () => import('@/views/UiReportList.vue') },
      { path: 'users', name: 'users', component: () => import('@/views/UserManagement.vue'), meta: { adminOnly: true } },
      { path: 'audit-logs', name: 'audit-logs', component: () => import('@/views/AuditLogPage.vue'), meta: { adminOnly: true } }
    ]
  },
  // 兜底：未匹配路径跳回首页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 全局前置守卫：登录态 + 管理员权限校验。
 */
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  // 公开页面直接放行
  if (to.meta.public) {
    return next()
  }
  // 未登录跳登录页（带 redirect 用于登录后跳回）
  if (!auth.token) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }
  // 管理员专属页面：非管理员跳项目列表
  if (to.meta.adminOnly && !auth.isAdmin()) {
    return next({ name: 'projects' })
  }
  return next()
})

export default router
