import { JSONPath } from 'jsonpath-plus'
import type { ExtractRule, ResponseData, VariableContext } from './types.js'

function stringifyValue(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

/** 从响应中按规则提取单个值，未命中返回 undefined */
export function extractValue(res: ResponseData, rule: ExtractRule): string | undefined {
  let raw: unknown

  switch (rule.type) {
    case 'jsonPath': {
      raw = JSONPath({ path: rule.expression, json: res.body as object, wrap: false })
      break
    }
    case 'header': {
      raw = res.headers[rule.expression.toLowerCase()]
      break
    }
    case 'regex': {
      const match = res.rawBody.match(new RegExp(rule.expression))
      raw = match ? (match[1] ?? match[0]) : undefined
      break
    }
  }

  if (raw === undefined || raw === null) return undefined
  if (Array.isArray(raw)) return raw.length === 0 ? undefined : stringifyValue(raw[0])
  return stringifyValue(raw)
}

/**
 * 依次应用所有提取规则，把结果写入上下文。
 * 返回本次实际提取到的键值对。
 */
export function applyExtracts(
  res: ResponseData,
  rules: ExtractRule[],
  context: VariableContext,
): Record<string, string> {
  const extracted: Record<string, string> = {}
  for (const rule of rules) {
    const value = extractValue(res, rule)
    if (value !== undefined) {
      context[rule.name] = value
      extracted[rule.name] = value
    }
  }
  return extracted
}
