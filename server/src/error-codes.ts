import type { FastifyReply } from 'fastify'

/**
 * 统一错误码定义（PRD 第六部分）。
 * 响应体统一为 { code, error, message }，其中 error 与 message 为可读文案（保持向前兼容）。
 */
export const ERROR_CODES = {
  PARAM_INVALID: { code: 1001, status: 400 },
  UNAUTHORIZED: { code: 1002, status: 401 },
  FORBIDDEN: { code: 1003, status: 403 },
  NOT_FOUND: { code: 1004, status: 404 },
  CONFLICT: { code: 1005, status: 409 },

  VAR_NAME_INVALID: { code: 2001, status: 400 },
  VAR_REFERENCED: { code: 2002, status: 400 },
  CASE_NAME_EMPTY: { code: 2003, status: 400 },
  TASK_NAME_EMPTY: { code: 2004, status: 400 },
  TASK_NO_CASES: { code: 2005, status: 400 },
  LOOP_NO_MAX: { code: 2006, status: 400 },

  SWAGGER_PARSE_FAIL: { code: 3001, status: 400 },
  EXCEL_IMPORT_INVALID: { code: 3002, status: 400 },
  METHOD_INVALID: { code: 3003, status: 400 },
  URL_INVALID: { code: 3004, status: 400 },

  DEBUG_ERROR: { code: 4001, status: 500 },
  TASK_RUN_ERROR: { code: 4002, status: 500 },
  MOCK_ERROR: { code: 4003, status: 500 },
  REPORT_GEN_FAIL: { code: 4004, status: 500 },
} as const

export type ErrorCodeKey = keyof typeof ERROR_CODES

const DEFAULT_MESSAGE: Record<ErrorCodeKey, string> = {
  PARAM_INVALID: '参数校验失败',
  UNAUTHORIZED: '未登录或 Token 过期',
  FORBIDDEN: '无权限操作',
  NOT_FOUND: '资源不存在',
  CONFLICT: '资源名称冲突（同名已存在）',
  VAR_NAME_INVALID: '变量名不合法',
  VAR_REFERENCED: '变量被引用，无法删除',
  CASE_NAME_EMPTY: '用例名称为空',
  TASK_NAME_EMPTY: '任务名称为空',
  TASK_NO_CASES: '任务至少需要 1 条用例才能运行',
  LOOP_NO_MAX: '循环控制器未配置最大循环次数',
  SWAGGER_PARSE_FAIL: 'Swagger 文档解析失败',
  EXCEL_IMPORT_INVALID: 'Excel 导入失败，存在非法行',
  METHOD_INVALID: '请求方式不合法',
  URL_INVALID: 'URL 格式不合法',
  DEBUG_ERROR: '调试执行异常',
  TASK_RUN_ERROR: '任务执行异常',
  MOCK_ERROR: 'Mock 服务异常',
  REPORT_GEN_FAIL: '报告生成失败',
}

/** 返回统一错误响应 */
export function fail(reply: FastifyReply, key: ErrorCodeKey, message?: string, extra?: Record<string, unknown>) {
  const e = ERROR_CODES[key]
  const msg = message ?? DEFAULT_MESSAGE[key]
  return reply.code(e.status).send({ code: e.code, error: msg, message: msg, ...extra })
}
