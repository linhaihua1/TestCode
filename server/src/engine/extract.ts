/**
 * 提取模块。
 * 按 JSONPath / 响应头 / 正则规则从响应中取值，并把提取结果写入变量上下文，
 * 供后续步骤通过 `${var}` 复用。
 */
import { JSONPath } from 'jsonpath-plus'
import type { ExtractRule, ResponseData, VariableContext } from './types.js'

/** 把任意值字符串化：字符串原样返回，其余 JSON 序列化 */
function stringifyValue(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

/** 从响应中按规则提取单个值，未命中返回 undefined */
export function extractValue(res: ResponseData, rule: ExtractRule): string | undefined {
  let raw: unknown

  switch (rule.type) {
    case 'jsonPath': {
      // 用 JSONPath 在解析后的响应体上取值，wrap: false 返回单个值而非数组
      raw = JSONPath({ path: rule.expression, json: res.body as object, wrap: false })
      break
    }
    case 'header': {
      // 响应头按小写键查找（HTTP 头大小写不敏感）
      raw = res.headers[rule.expression.toLowerCase()]
      break
    }
    case 'regex': {
      // 在原始响应体上做正则匹配，优先取第一个捕获组，否则取整个匹配
      const match = res.rawBody.match(new RegExp(rule.expression))
      raw = match ? (match[1] ?? match[0]) : undefined
      break
    }
  }

  if (raw === undefined || raw === null) return undefined // 未命中返回 undefined
  if (Array.isArray(raw)) return raw.length === 0 ? undefined : stringifyValue(raw[0]) // 数组取首元素
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
      // 提取成功才写入上下文，避免覆盖已有变量为 undefined
      context[rule.name] = value
      extracted[rule.name] = value
    }
  }
  return extracted
}
