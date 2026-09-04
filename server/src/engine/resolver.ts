/**
 * 变量优先级解析器。
 * 遵循 MeterSphere 优先级规则：
 *   临时调试变量 > 任务执行变量 > 环境变量 > 全局变量
 * 同名变量高优先级自动覆盖低优先级，并在返回结果中记录来源。
 */
import type { VariableContext } from './types.js'

/** 变量来源标记 */
export interface VariableWithSource {
  value: string
  source: 'debug' | 'task' | 'env' | 'global'
}

/** 合并多层变量上下文，高优先级覆盖低优先级 */
export function mergeVariableContexts(
  debugVars: Record<string, string> = {},
  taskVars: Record<string, string> = {},
  envVars: Record<string, string> = {},
  globalVars: Record<string, string> = {},
): { vars: VariableContext; sources: Record<string, string> } {
  const sources: Record<string, string> = {}
  const vars: VariableContext = {}

  // 从低到高依次合并，高优先级覆盖低优先级
  for (const [k, v] of Object.entries(globalVars)) {
    vars[k] = v
    sources[k] = 'global'
  }
  for (const [k, v] of Object.entries(envVars)) {
    vars[k] = v
    sources[k] = 'env'
  }
  for (const [k, v] of Object.entries(taskVars)) {
    vars[k] = v
    sources[k] = 'task'
  }
  for (const [k, v] of Object.entries(debugVars)) {
    vars[k] = v
    sources[k] = 'debug'
  }

  return { vars, sources }
}

/** 从 DB 加载项目全局变量，返回键值对 */
export async function loadGlobalVariables(projectId: string): Promise<Record<string, string>> {
  const { prisma } = await import('../db.js')
  const vars = await prisma.globalVariable.findMany({ where: { projectId } })
  const result: Record<string, string> = {}
  for (const v of vars) {
    result[v.name] = v.value
  }
  return result
}

/**
 * 构建完整变量上下文并返回覆盖来源信息，
 * 供执行引擎记录日志。
 */
export interface MergedContext {
  vars: VariableContext
  sources: Record<string, string>
  /** 本次执行中被覆盖的变量名列表 */
  overridden: string[]
}

export function buildMergedContext(
  globalVars: Record<string, string>,
  envVars: Record<string, string>,
  taskVars: Record<string, string>,
  debugVars: Record<string, string>,
): MergedContext {
  const sources: Record<string, string> = {}
  const overridden: string[] = []

  const allKeys = new Set([...Object.keys(globalVars), ...Object.keys(envVars), ...Object.keys(taskVars), ...Object.keys(debugVars)])
  for (const k of allKeys) {
    sources[k] = 'global'
    if (envVars[k] !== undefined) { sources[k] = 'env'; if (globalVars[k] !== undefined) overridden.push(k) }
    if (taskVars[k] !== undefined) { sources[k] = 'task'; if (envVars[k] !== undefined || globalVars[k] !== undefined) { if (!overridden.includes(k)) overridden.push(k) } }
    if (debugVars[k] !== undefined) { sources[k] = 'debug'; if (taskVars[k] !== undefined || envVars[k] !== undefined || globalVars[k] !== undefined) { if (!overridden.includes(k)) overridden.push(k) } }
  }

  const { vars } = mergeVariableContexts(debugVars, taskVars, envVars, globalVars)
  return { vars, sources, overridden }
}
