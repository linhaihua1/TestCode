/**
 * 变量替换模块。
 * 将字符串/对象/数组中的 `${var}` 占位符替换为上下文中对应的变量值，
 * 用于在发起请求前把用例模板渲染成最终请求参数。
 */
import type { VariableContext } from './types.js'

/**
 * 替换字符串中的 `${var}` 占位符。
 * 未在上下文中找到的变量保持原样。
 */
export function resolveString(input: string, context: VariableContext): string {
  return input.replace(/\$\{([^{}]+)\}/g, (match, key: string) => {
    const name = key.trim() // 去掉变量名两侧空白
    const value = context[name]
    return value === undefined ? match : value // 未定义则保留原占位符
  })
}

/**
 * 递归替换对象/数组/字符串中的变量。
 */
export function resolveTemplate<T>(input: T, context: VariableContext): T {
  if (typeof input === 'string') {
    return resolveString(input, context) as unknown as T // 字符串直接替换
  }
  if (Array.isArray(input)) {
    return input.map((item) => resolveTemplate(item, context)) as unknown as T // 数组逐元素递归
  }
  if (input !== null && typeof input === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(input as Record<string, unknown>)) {
      result[key] = resolveTemplate(value, context) // 对象逐字段递归
    }
    return result as unknown as T
  }
  return input // 其他原始类型原样返回
}
