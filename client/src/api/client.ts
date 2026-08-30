import axios from 'axios'
import type {
  ApiCase,
  ApiDefinition,
  Environment,
  Project,
  Report,
  Scenario,
  ScenarioStep,
} from './types'

const http = axios.create({ baseURL: '/api' })

// 统一错误提示
export function getErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { error?: string } | undefined
    return data?.error ?? err.message
  }
  return err instanceof Error ? err.message : String(err)
}

export const api = {
  // ---------- Project ----------
  listProjects: () => http.get<Project[]>('/projects').then((r) => r.data),
  createProject: (data: { name: string; description?: string }) =>
    http.post<Project>('/projects', data).then((r) => r.data),
  updateProject: (id: string, data: { name?: string; description?: string }) =>
    http.put<Project>(`/projects/${id}`, data).then((r) => r.data),
  deleteProject: (id: string) => http.delete(`/projects/${id}`).then((r) => r.data),

  // ---------- Environment ----------
  listEnvironments: (projectId: string) =>
    http.get<Environment[]>(`/projects/${projectId}/environments`).then((r) => r.data),
  createEnvironment: (projectId: string, data: Partial<Environment>) =>
    http.post<Environment>(`/projects/${projectId}/environments`, data).then((r) => r.data),
  updateEnvironment: (id: string, data: Partial<Environment>) =>
    http.put<Environment>(`/environments/${id}`, data).then((r) => r.data),
  deleteEnvironment: (id: string) => http.delete(`/environments/${id}`).then((r) => r.data),

  // ---------- ApiDefinition ----------
  listApis: (projectId: string) =>
    http.get<ApiDefinition[]>(`/projects/${projectId}/apis`).then((r) => r.data),
  createApi: (projectId: string, data: Partial<ApiDefinition>) =>
    http.post<ApiDefinition>(`/projects/${projectId}/apis`, data).then((r) => r.data),
  getApi: (id: string) => http.get<ApiDefinition & { cases: ApiCase[] }>(`/apis/${id}`).then((r) => r.data),
  updateApi: (id: string, data: Partial<ApiDefinition>) =>
    http.put<ApiDefinition>(`/apis/${id}`, data).then((r) => r.data),
  deleteApi: (id: string) => http.delete(`/apis/${id}`).then((r) => r.data),

  // ---------- ApiCase ----------
  listCases: (apiId: string) => http.get<ApiCase[]>(`/apis/${apiId}/cases`).then((r) => r.data),
  createCase: (apiId: string, data: Partial<ApiCase>) =>
    http.post<ApiCase>(`/apis/${apiId}/cases`, data).then((r) => r.data),
  updateCase: (id: string, data: Partial<ApiCase>) =>
    http.put<ApiCase>(`/cases/${id}`, data).then((r) => r.data),
  deleteCase: (id: string) => http.delete(`/cases/${id}`).then((r) => r.data),

  // ---------- Scenario ----------
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
  runScenario: (id: string, environmentId: string) =>
    http.post<Report>(`/scenarios/${id}/run`, { environmentId }).then((r) => r.data),

  // ---------- Report ----------
  listReports: (projectId: string) =>
    http.get<Report[]>(`/projects/${projectId}/reports`).then((r) => r.data),
  getReport: (id: string) => http.get<Report>(`/reports/${id}`).then((r) => r.data),
}
