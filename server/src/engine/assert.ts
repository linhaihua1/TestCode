import { JSONPath } from 'jsonpath-plus'
import type { Assertion, AssertionOperator, AssertionResult, ResponseData } from './types.js'

function stringify(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

/** 根据断言类型取得实际值（字符串化） */
function actualValue(res: ResponseData, type: Assertion['type'], expression: string): string {
  switch (type) {
    case 'statusCode':
      return String(res.status)
    case 'header':
      return res.headers[expression.toLowerCase()] ?? ''
    case 'jsonPath': {
      const raw = JSONPath({ path: expression, json: res.body, wrap: false })
      if (raw === undefined || raw === null) return ''
      if (Array.isArray(raw)) return raw.length === 0 ? '' : stringify(raw[0])
      return stringify(raw)
    }
    case 'regex': {
      const match = res.rawBody.match(new RegExp(expression))
      return match ? (match[1] ?? match[0]) : ''
    }
  }
}

function compare(actual: string, expected: string, operator: AssertionOperator): boolean {
  switch (operator) {
    case 'eq':
      return actual === expected
    case 'ne':
      return actual !== expected
    case 'contains':
      return actual.includes(expected)
    case 'notContains':
      return !actual.includes(expected)
    case 'regex':
      return new RegExp(expected).test(actual)
    case 'gt':
      return Number(actual) > Number(expected)
    case 'lt':
      return Number(actual) < Number(expected)
  }
}

export function evaluateAssertion(res: ResponseData, assertion: Assertion): AssertionResult {
  const actual = actualValue(res, assertion.type, assertion.expression)
  const operator = assertion.operator ?? 'eq'
  const passed = compare(actual, assertion.expected, operator)

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
  const results = assertions.map((a) => evaluateAssertion(res, a))
  return { passed: results.every((r) => r.passed), results }
}
