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
  Environment,
  Project,
  Report,
  Scenario,
  ScenarioStep,
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

  // ---------- 接口用例（ApiCase） ----------
  listCases: (apiId: string) => http.get<ApiCase[]>(`/apis/${apiId}/cases`).then((r) => r.data),
  createCase: (apiId: string, data: Partial<ApiCase>) =>
    http.post<ApiCase>(`/apis/${apiId}/cases`, data).then((r) => r.data),
  updateCase: (id: string, data: Partial<ApiCase>) =>
    http.put<ApiCase>(`/cases/${id}`, data).then((r) => r.data),
  deleteCase: (id: string) => http.delete(`/cases/${id}`).then((r) => r.data),

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
}
