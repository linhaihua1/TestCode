/**
 * 全局常量与枚举映射（前端唯一来源）。
 *
 * <p>目的：消除散落在各页面 / 组件中的硬编码字面量（角色、状态、分页大小、分段名等），
 * 与后端 {@code com.apiweb.engine} 下的枚举编码保持一致。
 *
 * <p>注意：这里的 code 必须与后端枚举 / 数据库取值完全一致，改动需前后端同步。
 */

// ============================================================
// 业务错误码（与后端 com.apiweb.common.ErrorCode 一致）
// ============================================================

/**
 * 后端业务错误码。
 *
 * <p>前端拦截器依赖这些码做分流，**不要**在页面里再写裸数字。</p>
 */
export const API_CODE = {
  /** 成功 */
  SUCCESS: 0,
  /** 请求参数错误 */
  BAD_REQUEST: 1001,
  /** 未登录 / 登录态过期（需清理凭证并跳登录页） */
  UNAUTHORIZED: 1002,
  /** 权限不足 */
  FORBIDDEN: 1003,
  /** 资源不存在 */
  NOT_FOUND: 1004,
  /** 资源名称冲突 */
  CONFLICT: 1005,
  /** 登录失败：账号或密码错误（不清理凭证、不跳转） */
  LOGIN_FAILED: 1006,
  /** 登录被锁定：连续失败次数过多，需等待解锁（前端仅展示提示） */
  LOGIN_LOCKED: 1007,
} as const;

// ============================================================
// 路由
// ============================================================

/** 登录页路由路径（hash 路由，实际地址为 {@code #/login}） */
export const LOGIN_ROUTE_PATH = '/login';

// ============================================================
// 角色
// ============================================================

/** 角色编码（与后端 Role 枚举一致） */
export const ROLES = {
  ADMIN: 'admin',
  MEMBER: 'member',
  VIEWER: 'viewer',
} as const;

export type RoleCode = (typeof ROLES)[keyof typeof ROLES];

/** 角色中文名 */
export const ROLE_LABELS: Record<string, string> = {
  [ROLES.ADMIN]: '管理员',
  [ROLES.MEMBER]: '成员',
  [ROLES.VIEWER]: '查看者',
};

/** 角色下拉选项 */
export const ROLE_OPTIONS = [
  { value: ROLES.ADMIN, label: ROLE_LABELS[ROLES.ADMIN] },
  { value: ROLES.MEMBER, label: ROLE_LABELS[ROLES.MEMBER] },
  { value: ROLES.VIEWER, label: ROLE_LABELS[ROLES.VIEWER] },
];

/** 角色 → 标签颜色 */
const ROLE_COLORS: Record<string, string> = {
  [ROLES.ADMIN]: 'red',
  [ROLES.MEMBER]: 'blue',
  [ROLES.VIEWER]: 'default',
};

/** 取角色标签颜色 */
export function roleColor(role?: string | null): string {
  if (!role) return STATUS_COLOR_FALLBACK;
  return ROLE_COLORS[role] ?? STATUS_COLOR_FALLBACK;
}

/** 取角色中文名（未命中原样返回） */
export function roleLabel(role?: string | null): string {
  if (!role) return '';
  return ROLE_LABELS[role] ?? role;
}

// ============================================================
// 分页
// ============================================================

/** 列表默认每页条数 */
export const DEFAULT_PAGE_SIZE = 20;

/** 可选每页条数 */
export const PAGE_SIZE_OPTIONS = ['10', '20', '50', '100'];

// ============================================================
// 执行 / 报告状态
// ============================================================

/** 运行状态编码（与后端 RunStatus 一致） */
export const RUN_STATUS = {
  PENDING: 'pending',
  RUNNING: 'running',
  SUCCESS: 'success',
  FAILED: 'failed',
  ERROR: 'error',
  SKIPPED: 'skipped',
} as const;

/** 报告整体状态编码（与后端 ReportStatus 一致） */
export const REPORT_STATUS = {
  RUNNING: 'running',
  PASSED: 'passed',
  FAILED: 'failed',
  ERROR: 'error',
} as const;

/**
 * 状态 → Ant Design Tag 颜色。
 * 兼容后端历史取值（success / passed 均视为通过）。
 */
export const STATUS_COLORS: Record<string, string> = {
  success: 'green',
  passed: 'green',
  failed: 'red',
  error: 'volcano',
  broken: 'volcano',
  skipped: 'default',
  pending: 'default',
  running: 'blue',
};

/**
 * 状态 → 中文名。
 */
export const STATUS_LABELS: Record<string, string> = {
  success: '通过',
  passed: '通过',
  failed: '失败',
  error: '异常',
  broken: '异常',
  skipped: '跳过',
  pending: '待执行',
  running: '执行中',
};

/**
 * 状态 → 十六进制色值（用于自绘圆点 / 时间轴等无法使用 Tag 颜色的场景）。
 * 色值语义与 STATUS_COLORS 保持一致。
 */
export const STATUS_HEX_COLORS: Record<string, string> = {
  success: '#16a34a',
  passed: '#16a34a',
  failed: '#dc2626',
  error: '#ea580c',
  broken: '#ea580c',
  skipped: '#94a3b8',
  pending: '#94a3b8',
  running: '#2563eb',
};

/** 状态兜底色值 */
export const STATUS_HEX_FALLBACK = '#94a3b8';

/** 状态兜底颜色 */
export const STATUS_COLOR_FALLBACK = 'default';

// ----- Allure 风格统计（键与 AllureSummary 组件一致） -----

/** Allure 状态键，数组顺序即展示顺序（前端唯一来源） */
export const ALLURE_STATUS_KEYS = ['passed', 'failed', 'broken', 'skipped'] as const;

export type AllureStatusKey = (typeof ALLURE_STATUS_KEYS)[number];

/** Allure 状态键 → 中文名 */
export const ALLURE_STATUS_LABELS: Record<string, string> = {
  passed: '通过',
  failed: '失败',
  broken: '异常',
  skipped: '跳过',
};

/** Allure 状态键 → 标签颜色 */
export const ALLURE_STATUS_COLORS: Record<string, string> = {
  passed: 'green',
  failed: 'red',
  broken: 'orange',
  skipped: 'default',
};

/** Allure 状态键 → 后端运行状态编码 */
export const ALLURE_TO_RUN_STATUS: Record<string, string> = {
  passed: 'success',
  failed: 'failed',
  broken: 'error',
  skipped: 'skipped',
};

/**
 * 取状态对应的标签颜色（未知状态返回兜底色）。
 */
export function statusColor(status?: string | null): string {
  if (!status) return STATUS_COLOR_FALLBACK;
  return STATUS_COLORS[status] ?? STATUS_COLOR_FALLBACK;
}

/**
 * 取状态对应的中文名（未知状态原样返回）。
 */
export function statusLabel(status?: string | null): string {
  if (!status) return '';
  return STATUS_LABELS[status] ?? status;
}

/**
 * 取状态对应的十六进制色值。
 */
export function statusHexColor(status?: string | null): string {
  if (!status) return STATUS_HEX_FALLBACK;
  return STATUS_HEX_COLORS[status] ?? STATUS_HEX_FALLBACK;
}

// ============================================================
// 用例状态（评审流）
// ============================================================

/** 用例状态编码 */
export const CASE_STATUS = {
  DRAFT: 'draft',
  REVIEWING: 'reviewing',
  PASS: 'pass',
  FAIL: 'fail',
  TRASH: 'trash',
} as const;

/** 用例状态（评审流）下拉选项 */
export const CASE_STATUS_OPTIONS = [
  { value: CASE_STATUS.DRAFT, label: '草稿' },
  { value: CASE_STATUS.REVIEWING, label: '评审中' },
  { value: CASE_STATUS.PASS, label: '通过' },
  { value: CASE_STATUS.FAIL, label: '失败' },
];

/** 用例状态中文名（含已废弃） */
export const CASE_STATUS_LABELS: Record<string, string> = {
  [CASE_STATUS.DRAFT]: '草稿',
  [CASE_STATUS.REVIEWING]: '评审中',
  [CASE_STATUS.PASS]: '已通过',
  [CASE_STATUS.FAIL]: '驳回',
  [CASE_STATUS.TRASH]: '已废弃',
};

/** 用例状态筛选选项（含已废弃） */
export const CASE_STATUS_FILTER_OPTIONS = Object.entries(CASE_STATUS_LABELS).map(
  ([value, label]) => ({ value, label }),
);

/** 用例状态 → 标签颜色 */
const CASE_STATUS_COLORS: Record<string, string> = {
  [CASE_STATUS.PASS]: 'green',
  [CASE_STATUS.FAIL]: 'red',
  [CASE_STATUS.REVIEWING]: 'orange',
  [CASE_STATUS.TRASH]: 'default',
};

/** 取用例状态标签颜色（未命中回退蓝色） */
export function caseStatusColor(status?: string | null): string {
  if (!status) return 'blue';
  return CASE_STATUS_COLORS[status] ?? 'blue';
}

/** 取用例状态中文名（未命中原样返回） */
export function caseStatusLabel(status?: string | null): string {
  if (!status) return '-';
  return CASE_STATUS_LABELS[status] ?? status;
}

/** 用例优先级选项（P0-P3） */
export const PRIORITY_OPTIONS = ['P0', 'P1', 'P2', 'P3'].map((v) => ({ value: v, label: v }));

/** 用例优先级 → 标签颜色 */
const PRIORITY_COLORS: Record<string, string> = {
  P0: 'red',
  P1: 'orange',
  P2: 'blue',
};

/** 取优先级标签颜色 */
export function priorityColor(priority?: string | null): string {
  if (!priority) return STATUS_COLOR_FALLBACK;
  return PRIORITY_COLORS[priority] ?? STATUS_COLOR_FALLBACK;
}

// ============================================================
// 触发方式
// ============================================================

/** 触发方式编码（与后端 TriggerType 一致） */
export const TRIGGER_TYPES = {
  MANUAL: 'manual',
  SCHEDULE: 'schedule',
  WEBHOOK: 'webhook',
  API: 'api',
} as const;

/** 触发方式中文名 */
export const TRIGGER_LABELS: Record<string, string> = {
  [TRIGGER_TYPES.MANUAL]: '手动',
  [TRIGGER_TYPES.SCHEDULE]: '定时',
  [TRIGGER_TYPES.WEBHOOK]: 'Webhook',
  [TRIGGER_TYPES.API]: 'API',
};

// ============================================================
// UI 用例分段
// ============================================================

/** UI 用例步骤分段（与后端 UiSegment 一致） */
export const UI_SEGMENTS = {
  SETUP: 'setup',
  TEST: 'test',
  TEARDOWN: 'teardown',
} as const;

export type UiSegmentCode = (typeof UI_SEGMENTS)[keyof typeof UI_SEGMENTS];

/** 分段中文名 */
export const UI_SEGMENT_LABELS: Record<string, string> = {
  [UI_SEGMENTS.SETUP]: '前置步骤',
  [UI_SEGMENTS.TEST]: '测试步骤',
  [UI_SEGMENTS.TEARDOWN]: '后置步骤',
};

// ============================================================
// 任务执行模式 / 失败策略
// ============================================================

/** 执行模式（与后端 ExecuteMode 一致） */
export const EXECUTE_MODES = {
  SEQUENTIAL: 'sequential',
  PARALLEL: 'parallel',
} as const;

/** 失败策略（与后端 FailStrategy 一致） */
export const FAIL_STRATEGIES = {
  STOP_ON_FAIL: 'stop_on_fail',
  CONTINUE_ALL: 'continue_all',
  RETRY_THEN_STOP: 'retry_then_stop',
} as const;

/** 失败策略中文名 */
export const FAIL_STRATEGY_LABELS: Record<string, string> = {
  [FAIL_STRATEGIES.STOP_ON_FAIL]: '失败即停',
  [FAIL_STRATEGIES.CONTINUE_ALL]: '失败继续',
  [FAIL_STRATEGIES.RETRY_THEN_STOP]: '重试后停',
};

/** 失败策略下拉选项 */
export const FAIL_STRATEGY_OPTIONS = Object.entries(FAIL_STRATEGY_LABELS).map(([value, label]) => ({
  value,
  label,
}));

/** 执行模式中文名 */
export const EXECUTE_MODE_LABELS: Record<string, string> = {
  [EXECUTE_MODES.SEQUENTIAL]: '顺序执行',
  [EXECUTE_MODES.PARALLEL]: '并行执行',
};

/** 通知渠道中文名 */
export const NOTIFY_CHANNEL_LABELS: Record<string, string> = {
  email: '邮件',
  dingtalk: '钉钉',
  feishu: '飞书',
  webhook: '自定义 Webhook',
};

// ============================================================
// 通知渠道
// ============================================================

/** 通知渠道编码 */
export const NOTIFY_CHANNELS = {
  EMAIL: 'email',
  DINGTALK: 'dingtalk',
  FEISHU: 'feishu',
  WEBHOOK: 'webhook',
} as const;

export type NotifyChannelCode = (typeof NOTIFY_CHANNELS)[keyof typeof NOTIFY_CHANNELS];

/** 通知渠道下拉选项 */
export const NOTIFY_CHANNEL_OPTIONS = Object.entries(NOTIFY_CHANNEL_LABELS).map(
  ([value, label]) => ({ value, label }),
);

// ============================================================
// 变量提取方式（与后端 ExtractType 一致）
// ============================================================

/** 提取方式编码（后端大小写不敏感，此处统一用大写规范值） */
export const EXTRACT_TYPES = {
  JSONPATH: 'JSONPATH',
  REGEX: 'REGEX',
  HEADER: 'HEADER',
  COOKIE: 'COOKIE',
  XPATH: 'XPATH',
} as const;

export type ExtractTypeCode = (typeof EXTRACT_TYPES)[keyof typeof EXTRACT_TYPES];

/** 提取方式中文名 */
export const EXTRACT_TYPE_LABELS: Record<string, string> = {
  [EXTRACT_TYPES.JSONPATH]: 'JSONPath',
  [EXTRACT_TYPES.REGEX]: '正则表达式',
  [EXTRACT_TYPES.HEADER]: '响应头',
  [EXTRACT_TYPES.COOKIE]: 'Cookie',
  [EXTRACT_TYPES.XPATH]: 'XPath',
};

/** 提取方式下拉选项 */
export const EXTRACT_TYPE_OPTIONS = Object.entries(EXTRACT_TYPE_LABELS).map(([value, label]) => ({
  value,
  label,
}));

// ============================================================
// HTTP 方法
// ============================================================

/** 支持的 HTTP 方法（与后端 HttpStep.method 一致） */
export const HTTP_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'] as const;

export type HttpMethod = (typeof HTTP_METHODS)[number];

/** HTTP 方法下拉选项 */
export const HTTP_METHOD_OPTIONS = HTTP_METHODS.map((v) => ({ value: v, label: v }));

/** HTTP 方法筛选下拉选项（含"全部"） */
export const HTTP_METHOD_FILTER_OPTIONS = [
  { value: undefined, label: '全部' },
  ...HTTP_METHOD_OPTIONS,
];

/** HTTP 方法 → 标签颜色 */
const HTTP_METHOD_COLORS: Record<string, string> = {
  GET: 'blue',
  POST: 'green',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
  HEAD: 'cyan',
  OPTIONS: 'default',
};

/** 取 HTTP 方法标签颜色 */
export function httpMethodColor(method?: string | null): string {
  if (!method) return STATUS_COLOR_FALLBACK;
  return HTTP_METHOD_COLORS[method.toUpperCase()] ?? STATUS_COLOR_FALLBACK;
}

/* ============================================================
 * CI/CD 流水线
 * ============================================================ */

/** 流水线测试类型（与后端 PipelineTestType 枚举一致） */
export const PIPELINE_TEST_TYPES = {
  API: 'api',
  UI: 'ui',
  PERF: 'perf',
} as const;

/** 测试类型 → 中文名 */
export const PIPELINE_TEST_TYPE_LABELS: Record<string, string> = {
  api: '接口自动化',
  ui: 'UI 自动化',
  perf: '性能测试',
};

/** 测试类型下拉选项 */
export const PIPELINE_TEST_TYPE_OPTIONS = Object.entries(PIPELINE_TEST_TYPE_LABELS).map(
  ([value, label]) => ({ value, label }),
);

/** 流水线执行方式（与后端 PipelineExecMode 枚举一致） */
export const PIPELINE_EXEC_MODES = {
  LOCAL: 'local',
  JENKINS: 'jenkins',
} as const;

/** 执行方式 → 中文名 */
export const PIPELINE_EXEC_MODE_LABELS: Record<string, string> = {
  local: '平台执行',
  jenkins: 'Jenkins 执行',
};

/** 执行方式下拉选项 */
export const PIPELINE_EXEC_MODE_OPTIONS = Object.entries(PIPELINE_EXEC_MODE_LABELS).map(
  ([value, label]) => ({ value, label }),
);

/** 执行目标类型 → 中文名 */
export const PIPELINE_TARGET_TYPE_LABELS: Record<string, string> = {
  test_task: '测试任务',
  ui_scenario: 'UI 场景',
  ui_test: 'UI 用例',
  perf_case: '性能用例',
};

/** 流水线执行状态（与后端 PipelineRunStatus 枚举一致） */
export const PIPELINE_RUN_STATUS = {
  PENDING: 'pending',
  RUNNING: 'running',
  SUCCESS: 'success',
  FAILED: 'failed',
  ERROR: 'error',
} as const;

/** 执行状态 → 中文名 */
export const PIPELINE_STATUS_LABELS: Record<string, string> = {
  pending: '等待中',
  running: '执行中',
  success: '通过',
  failed: '未通过',
  error: '执行异常',
};

/** 执行状态 → 标签颜色 */
export const PIPELINE_STATUS_COLORS: Record<string, string> = {
  pending: 'default',
  running: 'processing',
  success: 'success',
  failed: 'error',
  error: 'error',
};

/** 取执行状态标签颜色 */
export function pipelineStatusColor(status?: string | null): string {
  if (!status) return STATUS_COLOR_FALLBACK;
  return PIPELINE_STATUS_COLORS[status] ?? STATUS_COLOR_FALLBACK;
}

/** 取执行状态中文名 */
export function pipelineStatusLabel(status?: string | null): string {
  if (!status) return '-';
  return PIPELINE_STATUS_LABELS[status] ?? status;
}

/** 是否为终态（CI 轮询到此即可结束） */
export function isPipelineTerminal(status?: string | null): boolean {
  return (
    status === PIPELINE_RUN_STATUS.SUCCESS ||
    status === PIPELINE_RUN_STATUS.FAILED ||
    status === PIPELINE_RUN_STATUS.ERROR
  );
}

/** 质量门禁指标（与后端 QualityGateMetric 枚举一致） */
export const QUALITY_GATE_METRICS = {
  PASS_RATE: 'pass_rate',
  FAIL_RATE: 'fail_rate',
  ERROR_RATE: 'error_rate',
  AVG_RESPONSE_TIME: 'avg_response_time',
  P95_RESPONSE_TIME: 'p95_response_time',
  ERROR_COUNT: 'error_count',
  TPS: 'tps',
} as const;

/** 门禁指标 → 中文名（含单位） */
export const QUALITY_GATE_METRIC_LABELS: Record<string, string> = {
  pass_rate: '通过率(%)',
  fail_rate: '失败率(%)',
  error_rate: '错误率(%)',
  avg_response_time: '平均响应时间(ms)',
  p95_response_time: 'P95 响应时间(ms)',
  error_count: '错误样本数(个)',
  tps: '吞吐量(tps)',
};

/** 门禁指标下拉选项 */
export const QUALITY_GATE_METRIC_OPTIONS = Object.entries(QUALITY_GATE_METRIC_LABELS).map(
  ([value, label]) => ({ value, label }),
);

/** 门禁比较操作符（与后端 QualityGateOperator 枚举一致；命中即违规） */
export const QUALITY_GATE_OPERATORS = {
  GT: 'gt',
  GTE: 'gte',
  LT: 'lt',
  LTE: 'lte',
  EQ: 'eq',
} as const;

/** 操作符 → 数学符号 */
export const QUALITY_GATE_OPERATOR_LABELS: Record<string, string> = {
  gt: '大于 >',
  gte: '大于等于 >=',
  lt: '小于 <',
  lte: '小于等于 <=',
  eq: '等于 =',
};

/** 操作符下拉选项 */
export const QUALITY_GATE_OPERATOR_OPTIONS = Object.entries(QUALITY_GATE_OPERATOR_LABELS).map(
  ([value, label]) => ({ value, label }),
);
