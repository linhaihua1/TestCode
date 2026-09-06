import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginPage.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    redirect: '/projects',
    children: [
      { path: 'projects', name: 'projects', component: () => import('@/views/ProjectList.vue') },
      {
        path: 'environments',
        name: 'environments',
        component: () => import('@/views/EnvironmentPage.vue')
      },
      {
        path: 'environment-config',
        name: 'environment-config',
        component: () => import('@/views/EnvironmentConfigPage.vue')
      },
      {
        path: 'workbench',
        name: 'workbench',
        component: () => import('@/views/Workbench.vue')
      },
      { path: 'apis', name: 'apis', component: () => import('@/views/ApiList.vue') },
      {
        path: 'scenarios',
        name: 'scenarios',
        component: () => import('@/views/ScenarioList.vue')
      },
      {
        path: 'scenarios/:id',
        name: 'scenario-editor',
        component: () => import('@/views/ScenarioEditor.vue')
      },
      {
        path: 'tasks',
        name: 'tasks',
        component: () => import('@/views/TestExecutionPage.vue')
      },
      { path: 'reports', name: 'reports', component: () => import('@/views/ReportList.vue') },
      {
        path: 'reports/:id',
        name: 'report-detail',
        component: () => import('@/views/TestReportPage.vue')
      },
      { path: 'perf', name: 'perf', component: () => import('@/views/PerfCasePage.vue') },
      {
        path: 'perf/reports/:id',
        name: 'perf-report-detail',
        component: () => import('@/views/PerfReportPage.vue')
      },
      { path: 'ui', name: 'ui-tests', component: () => import('@/views/UiTestList.vue') },
      {
        path: 'ui/:id',
        name: 'ui-test-editor',
        component: () => import('@/views/UiTestEditor.vue')
      },
      {
        path: 'ui/reports',
        name: 'ui-reports',
        component: () => import('@/views/UiReportList.vue')
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UserManagement.vue'),
        meta: { adminOnly: true }
      },
      {
        path: 'audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/AuditLogPage.vue'),
        meta: { adminOnly: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (to.meta.public) {
    return next()
  }
  if (!auth.token) {
    return next({ name: 'login', query: { redirect: to.fullPath } })
  }
  if (to.meta.adminOnly && !auth.isAdmin()) {
    return next({ name: 'projects' })
  }
  return next()
})

export default router