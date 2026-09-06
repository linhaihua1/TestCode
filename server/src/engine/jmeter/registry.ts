/**
 * 正在运行中的压测注册表。
 *
 * 压测可能持续数十分钟，因此执行以异步方式进行：路由创建 status='running' 的报告后立即返回，
 * 由本模块持有 JMeter 子进程句柄，供轮询实时指标、手动停止、并发限流使用。
 *
 * 说明：注册表是**进程内**状态（与 JMeter 子进程同生命周期），因此服务重启会遗留
 * status='running' 的孤儿报告，由 reapOrphanPerfRuns 在启动时回收。
 */
import type { PerfRunHandle } from './runner.js'

interface ActiveRun {
  reportId: string
  caseId: string
  handle: PerfRunHandle
}

const active = new Map<string, ActiveRun>()

/** 同时允许的压测进程数（防止把本机 CPU/网络打满） */
export function maxConcurrency(): number {
  const v = Number(process.env.PERF_MAX_CONCURRENCY ?? 2)
  return Number.isFinite(v) && v > 0 ? v : 2
}

export function registerRun(reportId: string, caseId: string, handle: PerfRunHandle): void {
  active.set(reportId, { reportId, caseId, handle })
}

export function unregisterRun(reportId: string): void {
  active.delete(reportId)
}

export function getRun(reportId: string): ActiveRun | undefined {
  return active.get(reportId)
}

export function runningCaseIds(): Set<string> {
  const set = new Set<string>()
  for (const run of active.values()) if (run.handle.running()) set.add(run.caseId)
  return set
}

export function isCaseRunning(caseId: string): boolean {
  for (const run of active.values()) if (run.caseId === caseId && run.handle.running()) return true
  return false
}

export function activeCount(): number {
  let n = 0
  for (const run of active.values()) if (run.handle.running()) n++
  return n
}

/** 请求停止某个压测；返回是否确实处于运行中并被要求终止 */
export function stopRun(reportId: string): boolean {
  const run = active.get(reportId)
  if (!run) return false
  return run.handle.stop()
}

export function isRunActive(reportId: string): boolean {
  const run = active.get(reportId)
  return Boolean(run && run.handle.running())
}

/** 测试清理用：丢弃所有登记（不杀进程，由测试自身保证结束） */
export function clearRuns(): void {
  active.clear()
}
