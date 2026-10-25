/**
 * 全部后端接口的统一封装层：页面/组件只通过这里的对象调接口，不直接写 axios 路径。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>底层 axios 实例见 {@link ./client}，已统一加上 /api/v1 前缀、JWT 请求头，
 *       并在响应拦截器中解包 {@code {code, message, data}}，因此这里的返回值就是 data 本身；</li>
 *   <li>后端以 JSON 字符串存储的字段（如环境的 variables/headers）在本层做数组与字符串的双向转换，
 *       页面拿到的都是结构化对象；</li>
 *   <li>新增接口时请同步补充对应的类型定义（见 @/types）。</li>
 * </ul>
 */
import axios from './client';
import type {
  User,
  Project,
  Environment,
  GlobalVariable,
  ModuleNode,
  CaseInfo,
  ApiDefinition,
  Scenario,
  TestTask,
  TestTaskRun,
  Report,
  ReportDetail,
  ReportShare,
  PageResult,
  TrendBucket,
  TrendSummary,
  PerfCase,
  PerfReport,
  UiTestCase,
  UiScenario,
  UiReport,
  AuditLog,
  ExecutorNode,
  CaseVersion,
  CaseReview,
  DebugRecord,
  KeyValueItem,
  CiPipeline,
  CiPipelineRun,
  CiTarget,
  JenkinsServer,
  PipelineTestTypeCode,
} from '@/types';

/** 认证相关：登录、获取当前用户、修改密码 */
export const AuthApi = {
  login: (username: string, password: string) =>
    axios.post<{ token: string; user: User }>('/auth/login', { username, password }),
  me: () => axios.get<User>('/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    axios.post('/auth/change-password', { oldPassword, newPassword }),
};

/** 用户管理（仅管理员可用） */
export const UserApi = {
  list: () => axios.get<User[]>('/users'),
  create: (data: { username: string; password: string; role: string }) =>
    axios.post<User>('/users', data),
  update: (id: string, data: any) => axios.put(`/users/${id}`, data),
  remove: (id: string) => axios.delete(`/users/${id}`),
};

/** 项目：所有数据的顶层隔离单元 */
export const ProjectApi = {
  list: (keyword?: string) => axios.get<Project[]>('/projects', { params: { keyword } }),
  get: (id: string) => axios.get<Project>(`/projects/${id}`),
  create: (data: Partial<Project>) => axios.post<Project>('/projects', data),
  update: (id: string, data: Partial<Project>) => axios.put(`/projects/${id}`, data),
  remove: (id: string) => axios.delete(`/projects/${id}`),
};

/**
 * 环境变量/公共 Header 前后端类型适配：
 * 后端 EnvironmentEntity.variables / headers 为 JSON 字符串（"[{key,value}]"），
 * 前端统一以 KeyValueItem[] 数组形式使用。此处做双向转换。
 */
function parseKvJson(v: unknown): KeyValueItem[] {
  if (Array.isArray(v)) return v as KeyValueItem[];
  if (typeof v === 'string' && v.trim()) {
    try {
      const arr = JSON.parse(v);
      return Array.isArray(arr) ? arr : [];
    } catch {
      return [];
    }
  }
  return [];
}

function parseEnv<T extends Partial<Environment>>(env: T): T {
  if (!env) return env;
  return {
    ...env,
    variables: parseKvJson(env.variables),
    headers: parseKvJson(env.headers),
  };
}

function serializeEnv<T extends Partial<Environment>>(env: T): T {
  if (!env) return env;
  return {
    ...env,
    variables: JSON.stringify(env.variables ?? []) as unknown as T['variables'],
    headers: JSON.stringify(env.headers ?? []) as unknown as T['headers'],
  };
}

/**
 * 环境管理。
 * <p>list 会把后端返回的 JSON 字符串解析成数组，create/update 反向序列化，
 * 这样页面里始终按 KeyValueItem[] 处理。</p>
 */
export const EnvironmentApi = {
  list: async (projectId: string) => {
    const list = await axios.get<Environment[]>('/environments', { params: { projectId } });
    return (list ?? []).map(parseEnv);
  },
  create: (data: Partial<Environment>) =>
    axios.post<Environment>('/environments', serializeEnv(data)),
  update: (id: string, data: Partial<Environment>) =>
    axios.put(`/environments/${id}`, serializeEnv(data)),
  remove: (id: string) => axios.delete(`/environments/${id}`),
};

/** 项目级全局变量 */
export const GlobalVariableApi = {
  list: (projectId: string) =>
    axios.get<GlobalVariable[]>('/global-variables', { params: { projectId } }),
  create: (data: Partial<GlobalVariable>) => axios.post<GlobalVariable>('/global-variables', data),
  update: (id: string, data: Partial<GlobalVariable>) => axios.put(`/global-variables/${id}`, data),
  remove: (id: string) => axios.delete(`/global-variables/${id}`),
};

/** 模块目录树：tree 返回已组装好的树形结构（含 children） */
export const ModuleApi = {
  tree: (projectId: string, type = 'case') =>
    axios.get<ModuleNode[]>('/modules/tree', { params: { projectId, type } }),
  create: (data: any) => axios.post('/modules', data),
  update: (id: string, data: any) => axios.put(`/modules/${id}`, data),
  remove: (id: string) => axios.delete(`/modules/${id}`),
};

/** 接口定义（API 资产库）：支持 Swagger/OpenAPI 的文本与文件导入 */
export const ApiApi = {
  list: (params: { projectId: string; moduleId?: string; keyword?: string; method?: string }) =>
    axios.get<ApiDefinition[]>('/apis', { params }),
  get: (id: string) => axios.get<ApiDefinition>(`/apis/${id}`),
  create: (data: Partial<ApiDefinition>) => axios.post<ApiDefinition>('/apis', data),
  update: (id: string, data: Partial<ApiDefinition>) => axios.put(`/apis/${id}`, data),
  remove: (id: string) => axios.delete(`/apis/${id}`),
  importSwagger: (projectId: string, swagger: any) =>
    axios.post<number>('/apis/import-swagger', swagger, { params: { projectId } }),
  importSwaggerFile: (projectId: string, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return axios.post('/apis/import-file', fd, {
      params: { projectId },
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

/**
 * 接口用例：CRUD + 调试执行 + 调试记录 + 版本快照/回滚 + 评审流 + 回收站。
 */
export const CaseApi = {
  list: (params: {
    projectId: string;
    moduleId?: string;
    status?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }) => axios.get<CaseInfo[]>('/cases', { params }),
  get: (id: string) => axios.get<CaseInfo>(`/cases/${id}`),
  create: (data: Partial<CaseInfo>) => axios.post<CaseInfo>('/cases', data),
  update: (id: string, data: Partial<CaseInfo>) => axios.put(`/cases/${id}`, data),
  remove: (id: string) => axios.delete(`/cases/${id}`),
  debug: (id: string, environmentId?: string) =>
    axios.post<DebugRecord>(`/cases/${id}/debug`, { environmentId }),
  debugRecords: (id: string) => axios.get<DebugRecord[]>(`/cases/${id}/debug-records`),
  allDebugRecords: (params: { projectId: string; page?: number; size?: number }) =>
    axios.get<DebugRecord[]>('/cases/debug-records', { params }),
  versions: (id: string) => axios.get<CaseVersion[]>(`/cases/${id}/versions`),
  restoreVersion: (id: string, version: number) =>
    axios.post(`/cases/${id}/versions/${version}/restore`),
  review: (id: string, action: 'submit' | 'approve' | 'reject', comment?: string) =>
    axios.post(`/cases/${id}/review`, { action, comment }),
  reviews: (id: string) => axios.get<CaseReview[]>(`/cases/${id}/reviews`),
  recycleBin: (projectId: string) =>
    axios.get<CaseInfo[]>('/cases/recycle-bin', { params: { projectId } }),
  restore: (id: string) => axios.post(`/cases/${id}/restore`),
  purge: (id: string) => axios.delete(`/cases/${id}/purge`),
};

/** 场景：多个接口用例的有序组合，execute 会同步返回一份报告 */
export const ScenarioApi = {
  list: (projectId: string) => axios.get<Scenario[]>('/scenarios', { params: { projectId } }),
  get: (id: string) => axios.get<{ scenario: Scenario; steps: any[] }>(`/scenarios/${id}`),
  create: (data: any) => axios.post<Scenario>('/scenarios', data),
  update: (id: string, data: any) => axios.put(`/scenarios/${id}`, data),
  remove: (id: string) => axios.delete(`/scenarios/${id}`),
  execute: (id: string, environmentId?: string) =>
    axios.post<Report>(`/scenarios/${id}/execute`, { environmentId }),
};

/**
 * 测试任务：定时/手动/CI 触发的执行单元。
 * <p>run 走 MQ 异步投递（立即返回），runSync 在服务端同步跑完再返回，
 * 页面可根据任务规模选择。</p>
 */
export const TestTaskApi = {
  list: (projectId: string) => axios.get<TestTask[]>('/test-tasks', { params: { projectId } }),
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
  disableWebhook: (id: string) => axios.post(`/test-tasks/${id}/webhook/disable`),
};

/** 测试报告：列表/明细/导出（HTML、PDF）/趋势统计/分享链接管理 */
export const ReportApi = {
  list: (params: {
    projectId?: string;
    scenarioId?: string;
    taskId?: string;
    page?: number;
    size?: number;
  }) => axios.get<PageResult<Report>>('/reports', { params }),
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
  listShares: (reportId: string) => axios.get<ReportShare[]>(`/reports/${reportId}/shares`),
  /** 撤销分享 */
  revokeShare: (reportId: string, shareId: string) =>
    axios.post(`/reports/${reportId}/share/${shareId}/revoke`),
  remove: (id: string) => axios.delete(`/reports/${id}`),
  /** 公开分享内容（免登录，凭 token + 可选密码） */
  publicContent: (token: string, password?: string) =>
    axios.get<{ report: Report; details: ReportDetail[] }>(`/public/reports/${token}/content`, {
      params: { password },
    }),
};

/**
 * CI/CD Webhook 触发（公开 API,无需登录）。
 */
export const WebhookApi = {
  /** 触发任务（一般由 CI 系统调用,query 传 token） */
  trigger: (token: string, triggerBy?: string) =>
    axios.post<{ taskId: string; taskName: string; execId: string; runIds: string[] }>(
      '/webhook/test-tasks/trigger',
      null,
      { params: { token, triggerBy } },
    ),
};

/** 性能测试：用例管理 + JMX 导入导出 + 执行 + 报告查询 */
export const PerfApi = {
  listCases: (projectId: string) => axios.get<PerfCase[]>('/perf/cases', { params: { projectId } }),
  getCase: (id: string) => axios.get<PerfCase>(`/perf/cases/${id}`),
  createCase: (data: Partial<PerfCase>) => axios.post<PerfCase>('/perf/cases', data),
  updateCase: (id: string, data: Partial<PerfCase>) => axios.put(`/perf/cases/${id}`, data),
  deleteCase: (id: string) => axios.delete(`/perf/cases/${id}`),
  run: (id: string) => axios.post<PerfReport>(`/perf/cases/${id}/run`),
  exportJmx: (id: string) => axios.get(`/perf/cases/${id}/export-jmx`, { responseType: 'blob' }),
  exportBatch: (ids: string[]) =>
    axios.post('/perf/cases/export-batch', { ids }, { responseType: 'blob' }),
  importJmx: (jmx: string) => axios.post<PerfCase>('/perf/cases/import-jmx', { jmx }),
  listReports: (projectId: string) =>
    axios.get<PerfReport[]>('/perf/reports', { params: { projectId } }),
  getReport: (id: string) => axios.get<PerfReport>(`/perf/reports/${id}`),
  deleteReport: (id: string) => axios.delete(`/perf/reports/${id}`),
};

/** UI 自动化：用例（三段式步骤）+ 场景（多用例编排）+ 执行与报告 */
export const UiApi = {
  listTests: (projectId: string) => axios.get<UiTestCase[]>('/ui/tests', { params: { projectId } }),
  getTest: (id: string) => axios.get<UiTestCase>(`/ui/tests/${id}`),
  createTest: (data: Partial<UiTestCase>) => axios.post<UiTestCase>('/ui/tests', data),
  updateTest: (id: string, data: Partial<UiTestCase>) => axios.put(`/ui/tests/${id}`, data),
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
  deleteReport: (id: string) => axios.delete(`/ui/reports/${id}`),
};

/** 审计日志查询（分页） */
export const AuditApi = {
  list: (params: { username?: string; entityType?: string; page?: number; size?: number }) =>
    axios.get<{ records: AuditLog[]; total: number }>('/audit-logs', { params }),
};

/** 执行机资源池：查看节点、上报心跳、释放节点 */
export const ExecutorApi = {
  pool: () => axios.get<ExecutorNode[]>('/executors/pool'),
  heartbeat: (data: { nodeId?: string; name?: string; capabilities: string[] }) =>
    axios.post('/executors/heartbeat', data),
  release: (nodeId: string) => axios.post(`/executors/${nodeId}/release`),
};

/**
 * 回收站统一管理 API（需求文档 §2.3）。
 *
 * <p>type 取值：{@code MODULE|API|CASE|TASK}
 */
export interface RecycleBinItem {
  id: string;
  type: 'MODULE' | 'API' | 'CASE' | 'TASK';
  name: string;
  extra?: string;
  deletedAt?: string;
}

export interface RecycleBinRestoreResult {
  restoredCount: number;
  renamedIds?: string[];
  message?: string;
}

export interface RecycleBinConfig {
  id?: string;
  projectId: string;
  /** 0 = 不自动清理 */
  cleanupDays: number;
}

/** 回收站：按类型查看已删除项、恢复、彻底删除、按天数清理、保留策略配置 */
export const RecycleBinApi = {
  list: (projectId: string, type: RecycleBinItem['type'] = 'CASE') =>
    axios.get<RecycleBinItem[]>('/recycle-bin', { params: { projectId, type } }),
  restore: (ids: string[], type: RecycleBinItem['type']) =>
    axios.post<RecycleBinRestoreResult>('/recycle-bin/restore', { ids, type }),
  permanent: (ids: string[], type: RecycleBinItem['type']) =>
    axios.delete<void>('/recycle-bin/permanent', { data: { ids, type } }),
  purgeOld: (projectId: string, type: RecycleBinItem['type'], olderThanDays = 0) =>
    axios.delete<number>('/recycle-bin/purge', {
      params: { projectId, type, olderThanDays },
    }),
  getConfig: (projectId: string) =>
    axios.get<RecycleBinConfig>('/recycle-bin/config', { params: { projectId } }),
  updateConfig: (cfg: RecycleBinConfig) => axios.put<void>('/recycle-bin/config', cfg),
};
/* ============================================================
 * CI/CD 流水线
 * ============================================================ */

/**
 * 流水线与执行记录接口。
 *
 * <p>qualityGate / jenkinsParams 等字段在传输层是 JSON 字符串，
 * 前端表单中用对象数组编辑，保存前自行 JSON.stringify。</p>
 */
export const CiApi = {
  /** 项目下的流水线列表 */
  list: (projectId: string) => axios.get<CiPipeline[]>('/ci/pipelines', { params: { projectId } }),
  /** 流水线详情 */
  get: (id: string) => axios.get<CiPipeline>(`/ci/pipelines/${id}`),
  /** 某测试类型下可选的执行目标 */
  targets: (projectId: string, testType: PipelineTestTypeCode) =>
    axios.get<CiTarget[]>('/ci/pipelines/targets', { params: { projectId, testType } }),
  /** 新建流水线 */
  create: (data: CiPipeline) => axios.post<CiPipeline>('/ci/pipelines', data),
  /** 更新流水线（未传字段保持原值） */
  update: (id: string, data: Partial<CiPipeline>) =>
    axios.put<CiPipeline>(`/ci/pipelines/${id}`, data),
  /** 删除流水线（软删除） */
  remove: (id: string) => axios.delete<void>(`/ci/pipelines/${id}`),
  /** 启用 / 停用 */
  toggle: (id: string) => axios.post<CiPipeline>(`/ci/pipelines/${id}/toggle`),
  /** 触发执行；wait=true 阻塞等待结果，false 立即返回 runId 供轮询 */
  trigger: (id: string, payload?: { wait?: boolean; triggerBy?: string }) =>
    axios.post<CiPipelineRun>(`/ci/pipelines/${id}/trigger`, payload),
  /** 流水线的执行记录列表 */
  runs: (id: string, limit?: number) =>
    axios.get<CiPipelineRun[]>(`/ci/pipelines/${id}/runs`, { params: { limit } }),
  /** 单条执行记录详情 */
  run: (runId: string) => axios.get<CiPipelineRun>(`/ci/pipelines/runs/${runId}`),
  /** 生成 / 重置 CI 触发 Token，返回触发地址与 curl 示例 */
  rotateWebhook: (id: string) =>
    axios.post<{ token: string; url: string; curl: string }>(`/ci/pipelines/${id}/webhook/rotate`),
  /** 生成 Jenkins Pipeline 脚本 */
  jenkinsfile: (id: string) =>
    axios.get<{ content: string; curl: string; pipelineId: string }>(
      `/ci/pipelines/${id}/jenkinsfile`,
    ),
};

/** Jenkins 服务器配置接口（管理员） */
export const JenkinsApi = {
  /** 服务器列表（不含 API Token） */
  list: () => axios.get<JenkinsServer[]>('/ci/jenkins-servers'),
  get: (id: string) => axios.get<JenkinsServer>(`/ci/jenkins-servers/${id}`),
  create: (data: JenkinsServer) => axios.post<JenkinsServer>('/ci/jenkins-servers', data),
  update: (id: string, data: Partial<JenkinsServer>) =>
    axios.put<void>(`/ci/jenkins-servers/${id}`, data),
  remove: (id: string) => axios.delete<void>(`/ci/jenkins-servers/${id}`),
  /** 连通性测试：返回是否成功与 Jenkins 版本号 */
  test: (id: string) =>
    axios.post<{ success: boolean; version: string | null; message: string }>(
      `/ci/jenkins-servers/${id}/test`,
    ),
  /** 拉取该 Jenkins 上的 Job 名称列表 */
  jobs: (id: string) => axios.get<string[]>(`/ci/jenkins-servers/${id}/jobs`),
};
