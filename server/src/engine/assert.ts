/**
 * 断言模块。
 * 从响应中取出实际值（状态码 / JSONPath / 响应头 / 正则），
 * 按操作符与期望值比较，生成单条及批量断言结果。
 */
import { JSONPath } from 'jsonpath-plus'
import type { Assertion, AssertionOperator, AssertionResult, ResponseData } from './types.js'

/** 把任意值字符串化：字符串原样返回，其余 JSON 序列化 */
function stringify(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

/** 根据断言类型取得实际值（字符串化） */
function actualValue(res: ResponseData, type: Assertion['type'], expression: string): string {
  switch (type) {
    case 'statusCode':
      return String(res.status) // 状态码断言：直接取状态码
    case 'header':
      return res.headers[expression.toLowerCase()] ?? '' // 响应头断言：小写键查找，缺失返回空串
    case 'jsonPath': {
      // JSONPath 断言：取值后字符串化，未命中返回空串
      const raw = JSONPath({ path: expression, json: res.body as object, wrap: false })
      if (raw === undefined || raw === null) return ''
      if (Array.isArray(raw)) return raw.length === 0 ? '' : stringify(raw[0])
      return stringify(raw)
    }
    case 'regex': {
      // 正则断言：在原始响应体匹配，优先取第一个捕获组
      const match = res.rawBody.match(new RegExp(expression))
      return match ? (match[1] ?? match[0]) : ''
    }
  }
}

/** 按操作符比较实际值与期望值 */
function compare(actual: string, expected: string, operator: AssertionOperator): boolean {
  switch (operator) {
    case 'eq':
      return actual === expected // 等于
    case 'ne':
      return actual !== expected // 不等于
    case 'contains':
      return actual.includes(expected) // 包含
    case 'notContains':
      return !actual.includes(expected) // 不包含
    case 'regex':
      return new RegExp(expected).test(actual) // 正则匹配
    case 'gt':
      return Number(actual) > Number(expected) // 大于（数值比较）
    case 'lt':
      return Number(actual) < Number(expected) // 小于（数值比较）
  }
}

/** 评估单条断言，返回判定结果 */
export function evaluateAssertion(res: ResponseData, assertion: Assertion): AssertionResult {
  const actual = actualValue(res, assertion.type, assertion.expression) // 取实际值
  const operator = assertion.operator ?? 'eq' // 缺省操作符为等于
  const passed = compare(actual, assertion.expected, operator) // 执行比较

  return {
    passed,
    type: assertion.type,
    expression: assertion.expression,
    expected: assertion.expected,
    actual,
    message: passed
      ? `断言通过：${assertion.type} ${operator} ${assertion.expected}`
      : `断言失败：${assertion.type} 期望 ${operator} "${assertion.expected}"，实际为 "${actual}"`,
  }
}

/** 批量断言，全部通过才返回 true */
export function evaluateAssertions(
  res: ResponseData,
  assertions: Assertion[],
): { passed: boolean; results: AssertionResult[] } {
  const results = assertions.map((a) => evaluateAssertion(res, a)) // 逐条评估
  return { passed: results.every((r) => r.passed), results } // 全部通过才算通过
}
