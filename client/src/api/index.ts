import axios from './client'
import type {
  User, Project, Environment, GlobalVariable, ModuleNode, CaseInfo,
  ApiDefinition, Scenario, TestTask, TestTaskRun, Report, ReportDetail,
  ReportShare, TrendBucket, TrendSummary,
  PerfCase, PerfReport, UiTestCase, UiScenario, UiReport, AuditLog, ExecutorNode,
  CaseVersion, CaseReview, DebugRecord
} from '@/types'

export const AuthApi = {
  login: (username: string, password: string) =>
    axios.post<{ token: string; user: User }>('/auth/login', { username, password }),
  me: () => axios.get<User>('/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    axios.post('/auth/change-password', { oldPassword, newPassword })
}

export const UserApi = {
  list: () => axios.get<User[]>('/users'),
  create: (data: { username: string; password: string; role: string }) =>
    axios.post<User>('/users', data),
  update: (id: string, data: any) => axios.put(`/users/${id}`, data),
  remove: (id: string) => axios.delete(`/users/${id}`)
}

export const ProjectApi = {
  list: (keyword?: string) => axios.get<Project[]>('/projects', { params: { keyword } }),
  get: (id: string) => axios.get<Project>(`/projects/${id}`),
  create: (data: Partial<Project>) => axios.post<Project>('/projects', data),
  update: (id: string, data: Partial<Project>) => axios.put(`/projects/${id}`, data),
  remove: (id: string) => axios.delete(`/projects/${id}`)
}

export const EnvironmentApi = {
  list: (projectId: string) =>
    axios.get<Environment[]>('/environments', { params: { projectId } }),
  create: (data: Partial<Environment>) => axios.post<Environment>('/environments', data),
  update: (id: string, data: Partial<Environment>) => axios.put(`/environments/${id}`, data),
  remove: (id: string) => axios.delete(`/environments/${id}`)
}

export const GlobalVariableApi = {
  list: (projectId: string) =>
    axios.get<GlobalVariable[]>('/global-variables', { params: { projectId } }),
  create: (data: Partial<GlobalVariable>) => axios.post<GlobalVariable>('/global-variables', data),
  update: (id: string, data: Partial<GlobalVariable>) =>
    axios.put(`/global-variables/${id}`, data),
  remove: (id: string) => axios.delete(`/global-variables/${id}`)
}

export const ModuleApi = {
  tree: (projectId: string, type = 'case') =>
    axios.get<ModuleNode[]>('/modules/tree', { params: { projectId, type } }),
  create: (data: any) => axios.post('/modules', data),
  update: (id: string, data: any) => axios.put(`/modules/${id}`, data),
  remove: (id: string) => axios.delete(`/modules/${id}`)
}

export const ApiApi = {
  list: (params: { projectId: string; moduleId?: string; keyword?: string; method?: string }) =>
    axios.get<ApiDefinition[]>('/apis', { params }),
  get: (id: string) => axios.get<ApiDefinition>(`/apis/${id}`),
  create: (data: Partial<ApiDefinition>) => axios.post<ApiDefinition>('/apis', data),
  update: (id: string, data: Partial<ApiDefinition>) => axios.put(`/apis/${id}`, data),
  remove: (id: string) => axios.delete(`/apis/${id}`),
  importSwagger: (projectId: string, swagger: any) =>
    axios.post<number>('/apis/import-swagger', swagger, { params: { projectId } })
}

export const CaseApi = {
  list: (params: {
    projectId: string
    moduleId?: string
    status?: string
    keyword?: string
    page?: number
    size?: number
  }) => axios.get<CaseInfo[]>('/cases', { params }),
  get: (id: string) => axios.get<CaseInfo>(`/cases/${id}`),
  create: (data: Partial<CaseInfo>) => axios.post<CaseInfo>('/cases', data),
  update: (id: string, data: Partial<CaseInfo>) => axios.put(`/cases/${id}`, data),
  remove: (id: string) => axios.delete(`/cases/${id}`),
  debug: (id: string, environmentId?: string) =>
    axios.post<DebugRecord>(`/cases/${id}/debug`, { environmentId }),
  debugRecords: (id: string) =>
    axios.get<DebugRecord[]>(`/cases/${id}/debug-records`),
  versions: (id: string) => axios.get<CaseVersion[]>(`/cases/${id}/versions`),
  restoreVersion: (id: string, version: number) =>
    axios.post(`/cases/${id}/versions/${version}/restore`),
  review: (id: string, action: 'submit' | 'approve' | 'reject', comment?: string) =>
    axios.post(`/cases/${id}/review`, { action, comment }),
  reviews: (id: string) => axios.get<CaseReview[]>(`/cases/${id}/reviews`),
  recycleBin: (projectId: string) =>
    axios.get<CaseInfo[]>('/cases/recycle-bin', { params: { projectId } }),
  restore: (id: string) => axios.post(`/cases/${id}/restore`),
  purge: (id: string) => axios.delete(`/cases/${id}/purge`)
}

export const ScenarioApi = {
  list: (projectId: string) => axios.get<Scenario[]>('/scenarios', { params: { projectId } }),
  get: (id: string) => axios.get<{ scenario: Scenario; steps: any[] }>(`/scenarios/${id}`),
  create: (data: any) => axios.post<Scenario>('/scenarios', data),
  update: (id: string, data: any) => axios.put(`/scenarios/${id}`, data),
  remove: (id: string) => axios.delete(`/scenarios/${id}`),
  execute: (id: string, environmentId?: string) =>
    axios.post<Report>(`/scenarios/${id}/execute`, { environmentId })
}

export const TestTaskApi = {
  list: (projectId: string) =>
    axios.get<TestTask[]>('/test-tasks', { params: { projectId } }),
  get: (id: string) => axios.get<TestTask>(`/test-tasks/${id}`),
  create: (data: Partial<TestTask>) => axios.post<TestTask>('/test-tasks', data),
  update: (id: string, data: Partial<TestTask>) => axios.put(`/test-tasks/${id}`, data),
  remove: (id: string) => axios.delete(`/test-tasks/${id}`),
  /** 异步执行(投递 RabbitMQ) */
  run: (id: string) => axios.post<TestTaskRun[]>(`/test-tasks/${id}/run`),
  /** 同步本地执行(走 TaskExecutorService,支持并行池) */
  runSync: (id: string) => axios.post<TestTaskRun[]>(`/test-tasks/${id}/run-sync`),
  runs: (id: string) => axios.get<TestTaskRun[]>(`/test-tasks/${id}/runs`),
  toggle: (id: string) => axios.post(`/test-tasks/${id}/toggle`),
  /** 生成/重置 webhook token,返回完整 URL 仅一次 */
  rotateWebhook: (id: string) =>
    axios.post<{ token: string; url: string }>(`/test-tasks/${id}/webhook/rotate`),
  /** 关闭 webhook */
  disableWebhook: (id: string) => axios.post(`/test-tasks/${id}/webhook/disable`)
}

export const ReportApi = {
  list: (params: {
    projectId?: string
    scenarioId?: string
    taskId?: string
    page?: number
    size?: number
  }) => axios.get<Report[]>('/reports', { params }),
  /** 报告基本信息 */
  get: (id: string) => axios.get<Report>(`/reports/${id}`),
  /** 报告步骤明细 */
  details: (id: string) => axios.get<ReportDetail[]>(`/reports/${id}/details`),
  /** HTML 导出(直接下载) */
  exportHtmlUrl: (id: string) => `/api/v1/reports/${id}/export/html`,
  /** PDF 导出(直接下载) */
  exportPdfUrl: (id: string) => `/api/v1/reports/${id}/export/pdf`,
  /** 趋势统计 */
  trend: (params: { projectId?: string; days?: number }) =>
    axios.get<TrendBucket[]>('/reports/trend', { params }),
  /** 整体汇总 */
  summary: (params: { projectId?: string; days?: number }) =>
    axios.get<TrendSummary>('/reports/summary', { params }),
  /** 创建分享链接 */
  createShare: (reportId: string, data: { expireDays: number; password?: string }) =>
    axios.post<ReportShare>(`/reports/${reportId}/share`, data),
  /** 报告的全部分享 */
  listShares: (reportId: string) =>
    axios.get<ReportShare[]>(`/reports/${reportId}/shares`),
  /** 撤销分享 */
  revokeShare: (shareId: string) =>
    axios.post(`/reports/shares/${shareId}/revoke`),
  remove: (id: string) => axios.delete(`/reports/${id}`)
}

/**
 * CI/CD Webhook 触发（公开 API,无需登录）。
 */
export const WebhookApi = {
  /** 触发任务（一般由 CI 系统调用,query 传 token） */
  trigger: (token: string, triggerBy?: string) =>
    axios.post<{ taskId: string; taskName: string; execId: string; runIds: string[] }>(
      '/webhook/test-tasks/trigger', null, { params: { token, triggerBy } })
}

export const PerfApi = {
  listCases: (projectId: string) =>
    axios.get<PerfCase[]>('/perf/cases', { params: { projectId } }),
  getCase: (id: string) => axios.get<PerfCase>(`/perf/cases/${id}`),
  createCase: (data: Partial<PerfCase>) => axios.post<PerfCase>('/perf/cases', data),
  updateCase: (id: string, data: Partial<PerfCase>) =>
    axios.put(`/perf/cases/${id}`, data),
  deleteCase: (id: string) => axios.delete(`/perf/cases/${id}`),
  run: (id: string) => axios.post<PerfReport>(`/perf/cases/${id}/run`),
  listReports: (projectId: string) =>
    axios.get<PerfReport[]>('/perf/reports', { params: { projectId } }),
  getReport: (id: string) => axios.get<PerfReport>(`/perf/reports/${id}`),
  deleteReport: (id: string) => axios.delete(`/perf/reports/${id}`)
}

export const UiApi = {
  listTests: (projectId: string) =>
    axios.get<UiTestCase[]>('/ui/tests', { params: { projectId } }),
  getTest: (id: string) => axios.get<UiTestCase>(`/ui/tests/${id}`),
  createTest: (data: Partial<UiTestCase>) => axios.post<UiTestCase>('/ui/tests', data),
  updateTest: (id: string, data: Partial<UiTestCase>) =>
    axios.put(`/ui/tests/${id}`, data),
  deleteTest: (id: string) => axios.delete(`/ui/tests/${id}`),
  runTest: (id: string) => axios.post<UiReport>(`/ui/tests/${id}/run`),
  listScenarios: (projectId: string) =>
    axios.get<UiScenario[]>('/ui/scenarios', { params: { projectId } }),
  getScenario: (id: string) =>
    axios.get<{ scenario: UiScenario; steps: any[] }>(`/ui/scenarios/${id}`),
  createScenario: (data: any) => axios.post<UiScenario>('/ui/scenarios', data),
  updateScenario: (id: string, data: any) => axios.put(`/ui/scenarios/${id}`, data),
  deleteScenario: (id: string) => axios.delete(`/ui/scenarios/${id}`),
  runScenario: (id: string) => axios.post<UiReport[]>(`/ui/scenarios/${id}/run`),
  listReports: (projectId: string) =>
    axios.get<UiReport[]>('/ui/reports', { params: { projectId } }),
  getReport: (id: string) => axios.get<UiReport>(`/ui/reports/${id}`),
  deleteReport: (id: string) => axios.delete(`/ui/reports/${id}`)
}

export const AuditApi = {
  list: (params: { username?: string; entityType?: string; page?: number; size?: number }) =>
    axios.get<{ records: AuditLog[]; total: number }>('/audit-logs', { params })
}

export const ExecutorApi = {
  pool: () => axios.get<ExecutorNode[]>('/executors/pool'),
  heartbeat: (data: { nodeId?: string; name?: string; capabilities: string[] }) =>
    axios.post('/executors/heartbeat', data),
  release: (nodeId: string) => axios.post(`/executors/${nodeId}/release`)
}

/**
 * 回收站统一管理 API（需求文档 §2.3）。
 *
 * <p>type 取值：{@code MODULE|API|CASE|TASK}
 */
export interface RecycleBinItem {
  id: string
  type: 'MODULE' | 'API' | 'CASE' | 'TASK'
  name: string
  extra?: string
  deletedAt?: string
}

export interface RecycleBinRestoreResult {
  restoredCount: number
  renamedIds?: string[]
  message?: string
}

export interface RecycleBinConfig {
  id?: string
  projectId: string
  /** 0 = 不自动清理 */
  cleanupDays: number
}

export const RecycleBinApi = {
  list: (projectId: string, type: RecycleBinItem['type'] = 'CASE') =>
    axios.get<RecycleBinItem[]>('/recycle-bin', { params: { projectId, type } }),
  restore: (ids: string[], type: RecycleBinItem['type']) =>
    axios.post<RecycleBinRestoreResult>('/recycle-bin/restore', { ids, type }),
  permanent: (ids: string[], type: RecycleBinItem['type']) =>
    axios.delete<void>('/recycle-bin/permanent', { data: { ids, type } }),
  purgeOld: (projectId: string, type: RecycleBinItem['type'], olderThanDays = 0) =>
    axios.delete<number>('/recycle-bin/purge', {
      params: { projectId, type, olderThanDays }
    }),
  getConfig: (projectId: string) =>
    axios.get<RecycleBinConfig>('/recycle-bin/config', { params: { projectId } }),
  updateConfig: (cfg: RecycleBinConfig) =>
    axios.put<void>('/recycle-bin/config', cfg)
}