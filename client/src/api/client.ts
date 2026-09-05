/**
 * API 封装层
 *
 * 职责：集中封装所有对后端的 HTTP 调用。
 * 基于 axios 实例统一配置 baseURL，并以 api 对象暴露各业务域的方法，
 * 页面组件通过 import { api } 调用，避免在组件中散落请求细节。
 */
import axios from 'axios'
import { clearAuth, getToken } from './auth'
import type {
  ApiCase,
  ApiDefinition,
  CaseInfo,
  DebugRecord,
  Environment,
  GlobalVariable,
  Module,
  Project,
  Report,
  Scenario,
  ScenarioStep,
  UiReport,
  UiScenario,
  UiScenarioStep,
  UiTestCase,
  User,
} from './types'

// 创建共享的 axios 实例，所有请求统一以 /api 为前缀
const http = axios.create({ baseURL: '/api' })

// 请求拦截器：自动携带登录 token
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截器：登录失效（401）时清除登录态并跳转登录页
http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err?.response?.status === 401) {
      clearAuth()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  },
)

// 统一错误提示：从各类错误对象中提取用户可读的错误信息
export function getErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    // 优先读取后端返回的 error 字段，否则使用 axios 自带错误信息
    const data = err.response?.data as { error?: string } | undefined
    return data?.error ?? err.message
  }
  // 非 axios 错误：Error 取 message，其余转字符串
  return err instanceof Error ? err.message : String(err)
}

// 后端调用集合，按业务域分组
export const api = {
  // ---------- 项目（Project） ----------
  listProjects: () => http.get<Project[]>('/projects').then((r) => r.data),
  createProject: (data: { name: string; description?: string }) =>
    http.post<Project>('/projects', data).then((r) => r.data),
  updateProject: (id: string, data: { name?: string; description?: string }) =>
    http.put<Project>(`/projects/${id}`, data).then((r) => r.data),
  deleteProject: (id: string) => http.delete(`/projects/${id}`).then((r) => r.data),

  // ---------- 环境（Environment） ----------
  listEnvironments: (projectId: string) =>
    http.get<Environment[]>(`/projects/${projectId}/environments`).then((r) => r.data),
  createEnvironment: (projectId: string, data: Partial<Environment>) =>
    http.post<Environment>(`/projects/${projectId}/environments`, data).then((r) => r.data),
  updateEnvironment: (id: string, data: Partial<Environment>) =>
    http.put<Environment>(`/environments/${id}`, data).then((r) => r.data),
  deleteEnvironment: (id: string) => http.delete(`/environments/${id}`).then((r) => r.data),

  // ---------- 接口定义（ApiDefinition） ----------
  listApis: (projectId: string) =>
    http.get<ApiDefinition[]>(`/projects/${projectId}/apis`).then((r) => r.data),
  createApi: (projectId: string, data: Partial<ApiDefinition>) =>
    http.post<ApiDefinition>(`/projects/${projectId}/apis`, data).then((r) => r.data),
  getApi: (id: string) => http.get<ApiDefinition & { cases: ApiCase[] }>(`/apis/${id}`).then((r) => r.data),
  updateApi: (id: string, data: Partial<ApiDefinition>) =>
    http.put<ApiDefinition>(`/apis/${id}`, data).then((r) => r.data),
  deleteApi: (id: string) => http.delete(`/apis/${id}`).then((r) => r.data),
  importSwagger: (projectId: string, content: Record<string, unknown>) =>
    http
      .post<{ created: number; skipped: number }>(`/projects/${projectId}/apis/import-swagger`, { content })
      .then((r) => r.data),

  // ---------- 接口用例（ApiCase） ----------
  listCases: (apiId: string) => http.get<ApiCase[]>(`/apis/${apiId}/cases`).then((r) => r.data),
  createApiCase: (apiId: string, data: Partial<ApiCase>) =>
    http.post<ApiCase>(`/apis/${apiId}/cases`, data).then((r) => r.data),
  updateApiCase: (id: string, data: Partial<ApiCase>) =>
    http.put<ApiCase>(`/cases/${id}`, data).then((r) => r.data),
  deleteApiCase: (id: string) => http.delete(`/cases/${id}`).then((r) => r.data),
  debugCase: (id: string, environmentId?: string) =>
    http
      .post<{
        request: { method: string; url: string; headers: Record<string, string>; body: unknown }
        response: { status: number; headers: Record<string, string>; body: unknown; rawBody: string; duration: number }
        extracted: Record<string, string>
        assertions: Array<{ passed: boolean; type: string; expression: string; expected: string; actual: string; message: string }>
        passed: boolean
      }>(`/cases/${id}/debug`, { environmentId })
      .then((r) => r.data),
  runCase: (id: string, environmentId?: string) =>
    http.post<Report>(`/cases/${id}/run`, { environmentId }).then((r) => r.data),

  // ---------- 场景（Scenario） ----------
  listScenarios: (projectId: string) =>
    http.get<Scenario[]>(`/projects/${projectId}/scenarios`).then((r) => r.data),
  createScenario: (projectId: string, data: { name: string; description?: string }) =>
    http.post<Scenario>(`/projects/${projectId}/scenarios`, data).then((r) => r.data),
  getScenario: (id: string) =>
    http
      .get<Scenario & { steps: ScenarioStep[] }>(`/scenarios/${id}`)
      .then((r) => r.data),
  updateScenario: (id: string, data: { name?: string; description?: string }) =>
    http.put<Scenario>(`/scenarios/${id}`, data).then((r) => r.data),
  deleteScenario: (id: string) => http.delete(`/scenarios/${id}`).then((r) => r.data),
  updateScenarioSteps: (
    id: string,
    steps: Array<{
      order: number
      apiCaseId?: string | null
      name?: string | null
      assertions?: unknown
      extracts?: unknown
    }>,
  ) => http.put(`/scenarios/${id}/steps`, steps).then((r) => r.data),
  // 在指定环境下执行整个场景，返回本次执行报告
  runScenario: (id: string, environmentId: string) =>
    http.post<Report>(`/scenarios/${id}/run`, { environmentId }).then((r) => r.data),

  // ---------- 报告（Report） ----------
  listReports: (projectId: string) =>
    http.get<Report[]>(`/projects/${projectId}/reports`).then((r) => r.data),
  getReport: (id: string) => http.get<Report>(`/reports/${id}`).then((r) => r.data),

  // ---------- 认证（Auth） ----------
  login: (username: string, password: string) =>
    http
      .post<{ token: string; user: { id: string; username: string; role: string } }>('/auth/login', {
        username,
        password,
      })
      .then((r) => r.data),
  getMe: () => http.get<{ id: string; username: string; role: string }>('/auth/me').then((r) => r.data),
  changePassword: (oldPassword: string, newPassword: string) =>
    http.post('/auth/change-password', { oldPassword, newPassword }).then((r) => r.data),

  // ---------- 用户管理（User） ----------
  listUsers: () => http.get<User[]>('/users').then((r) => r.data),
  createUser: (data: { username: string; password: string; role?: string }) =>
    http.post<User>('/users', data).then((r) => r.data),
  updateUser: (id: string, data: { username?: string; role?: string }) =>
    http.put<User>(`/users/${id}`, data).then((r) => r.data),
  deleteUser: (id: string) => http.delete(`/users/${id}`).then((r) => r.data),
  resetUserPassword: (id: string, newPassword: string) =>
    http.put(`/users/${id}/password`, { newPassword }).then((r) => r.data),

  // ---------- UI 自动化测试（UiTestCase） ----------
  listUiTests: (projectId: string) =>
    http.get<UiTestCase[]>(`/projects/${projectId}/ui-tests`).then((r) => r.data),
  createUiTest: (projectId: string, data: Partial<UiTestCase>) =>
    http.post<UiTestCase>(`/projects/${projectId}/ui-tests`, data).then((r) => r.data),
  getUiTest: (id: string) => http.get<UiTestCase>(`/ui-tests/${id}`).then((r) => r.data),
  updateUiTest: (id: string, data: Partial<UiTestCase>) =>
    http.put<UiTestCase>(`/ui-tests/${id}`, data).then((r) => r.data),
  deleteUiTest: (id: string) => http.delete(`/ui-tests/${id}`).then((r) => r.data),
  runUiTest: (id: string) => http.post<UiReport>(`/ui-tests/${id}/run`).then((r) => r.data),
  listUiReports: (projectId: string) =>
    http.get<UiReport[]>(`/projects/${projectId}/ui-reports`).then((r) => r.data),
  getUiReport: (id: string) => http.get<UiReport>(`/ui-reports/${id}`).then((r) => r.data),

  // ---------- UI 执行场景（UiScenario） ----------
  listUiScenarios: (projectId: string) =>
    http.get<UiScenario[]>(`/projects/${projectId}/ui-scenarios`).then((r) => r.data),
  createUiScenario: (projectId: string, data: { name: string; description?: string }) =>
    http.post<UiScenario>(`/projects/${projectId}/ui-scenarios`, data).then((r) => r.data),
  getUiScenario: (id: string) =>
    http
      .get<UiScenario & { steps: UiScenarioStep[] }>(`/ui-scenarios/${id}`)
      .then((r) => r.data),
  updateUiScenario: (id: string, data: { name?: string; description?: string }) =>
    http.put<UiScenario>(`/ui-scenarios/${id}`, data).then((r) => r.data),
  deleteUiScenario: (id: string) => http.delete(`/ui-scenarios/${id}`).then((r) => r.data),
  updateUiScenarioSteps: (id: string, steps: Array<{ order: number; uiTestCaseId?: string | null }>) =>
    http.put(`/ui-scenarios/${id}/steps`, steps).then((r) => r.data),
  runUiScenario: (id: string) => http.post<UiReport>(`/ui-scenarios/${id}/run`).then((r) => r.data),
  executeUiCases: (projectId: string, testCaseIds: string[]) =>
    http.post<UiReport>(`/projects/${projectId}/ui-execute`, { testCaseIds }).then((r) => r.data),

  // ---------- 全局变量 ----------
  listGlobalVariables: (projectId: string) =>
    http.get<GlobalVariable[]>(`/projects/${projectId}/global-variables`).then((r) => r.data),
  createGlobalVariable: (projectId: string, data: Partial<GlobalVariable>) =>
    http.post<GlobalVariable>(`/projects/${projectId}/global-variables`, data).then((r) => r.data),
  updateGlobalVariable: (id: string, data: Partial<GlobalVariable>) =>
    http.put<GlobalVariable>(`/global-variables/${id}`, data).then((r) => r.data),
  deleteGlobalVariable: (id: string) => http.delete(`/global-variables/${id}`).then((r) => r.data),
  deleteGlobalVariablesBatch: (ids: string[]) =>
    http.delete('/global-variables', { data: { ids } }).then((r) => r.data),

  // ---------- 目录模块 ----------
  listModules: (projectId: string) =>
    http.get<Module[]>(`/projects/${projectId}/modules`).then((r) => r.data),
  createModule: (projectId: string, data: Partial<Module>) =>
    http.post<Module>(`/projects/${projectId}/modules`, data).then((r) => r.data),
  updateModule: (id: string, data: Partial<Module>) =>
    http.put<Module>(`/modules/${id}`, data).then((r) => r.data),
  deleteModule: (id: string) => http.delete(`/modules/${id}`).then((r) => r.data),
  getModuleDetail: (id: string) => http.get<Module>(`/modules/${id}`).then((r) => r.data),
  listModulesForSelect: (projectId: string) =>
    http.get<Module[]>(`/projects/${projectId}/modules-for-select`).then((r) => r.data),

  // ---------- 用例管理 ----------
  listCaseLibraryCases: (projectId: string, params?: Record<string, string>) => {
    const qs = new URLSearchParams(params).toString()
    return http.get<CaseInfo[]>(`/projects/${projectId}/cases?${qs}`).then((r) => r.data)
  },
  createCase: (projectId: string, data: Partial<CaseInfo>) =>
    http.post<CaseInfo>(`/projects/${projectId}/cases`, data).then((r) => r.data),
  getCase: (id: string) => http.get<CaseInfo>(`/case-info/${id}`).then((r) => r.data),
  updateCase: (id: string, data: Partial<CaseInfo>) =>
    http.put<CaseInfo>(`/case-info/${id}`, data).then((r) => r.data),
  deleteCase: (id: string) => http.delete(`/case-info/${id}`).then((r) => r.data),
  rollbackCase: (id: string, versionId: string) =>
    http.post(`/case-info/${id}/rollback/${versionId}`).then((r) => r.data),
  debugCaseInfo: (id: string, data?: { environmentId?: string; debugVars?: Record<string, string> }) =>
    http
      .post<{
        status: string
        duration: number
        results: Array<{
          id: string
          name: string
          type: string
          status: string
          message: string
          request?: { method: string; url: string }
          response?: { status: number; body: unknown; duration: number }
          assertions?: Array<{ passed: boolean; message: string }>
          extracted?: Record<string, string>
          children?: Array<unknown>
        }>
        variables: Record<string, string>
      }>(`/case-info/${id}/debug`, data ?? {})
      .then((r) => r.data),
  listRecycleCases: (projectId: string) =>
    http.get<CaseInfo[]>(`/projects/${projectId}/cases/recycle`).then((r) => r.data),
  restoreCase: (id: string) => http.post(`/case-info/${id}/restore`).then((r) => r.data),
  permanentDeleteCase: (id: string) => http.delete(`/case-info/${id}/permanent`).then((r) => r.data),

  // ---------- 调试记录 ----------
  listDebugRecords: (projectId: string, params?: Record<string, string>) => {
    const qs = new URLSearchParams(params).toString()
    return http.get<DebugRecord[]>(`/projects/${projectId}/debug-records?${qs}`).then((r) => r.data)
  },
  getDebugRecord: (id: string) => http.get<DebugRecord>(`/debug-records/${id}`).then((r) => r.data),
  deleteDebugRecord: (id: string) => http.delete(`/debug-records/${id}`).then((r) => r.data),
  cleanDebugRecords: (days?: number) =>
    http.delete('/debug-records/clean', { data: { days } }).then((r) => r.data),
}
