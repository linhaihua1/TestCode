import type { VariableContext } from './types.js'

/**
 * 替换字符串中的 `${var}` 占位符。
 * 未在上下文中找到的变量保持原样。
 */
export function resolveString(input: string, context: VariableContext): string {
  return input.replace(/\$\{([^{}]+)\}/g, (match, key: string) => {
    const name = key.trim()
    const value = context[name]
    return value === undefined ? match : value
  })
}

/**
 * 递归替换对象/数组/字符串中的变量。
 */
export function resolveTemplate<T>(input: T, context: VariableContext): T {
  if (typeof input === 'string') {
    return resolveString(input, context) as unknown as T
  }
  if (Array.isArray(input)) {
    return input.map((item) => resolveTemplate(item, context)) as unknown as T
  }
  if (input !== null && typeof input === 'object') {
    const result: Record<string, unknown> = {}
    for (const [key, value] of Object.entries(input as Record<string, unknown>)) {
      result[key] = resolveTemplate(value, context)
    }
    return result as unknown as T
  }
  return input
}
