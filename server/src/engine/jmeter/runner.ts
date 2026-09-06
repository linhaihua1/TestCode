/**
 * JMeter 执行器。
 *
 * 运行时定位优先级（内置优先，做到开箱可用）：
 *   1. 工程内置：server/jmeter/bin/ApacheJMeter.jar（与 bin/chromedriver 同策略，不提交 git）
 *   2. 环境变量 JMETER_HOME
 *   3. PATH 中的 jmeter / jmeter.bat（反推其 home）
 * 统一通过 `java -jar ApacheJMeter.jar` 启动，避免 Windows 下 .bat 需 shell 的转义问题。
 *
 * 流程：用例模型 → .jmx → `jmeter -n -t plan.jmx -l results.jtl` → 解析 JTL → 结构化结果。
 */
import { spawn } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildJmx } from './jmx-builder.js'
import { parseJtl, type PerfErrorItem, type PerfLabelStat, type PerfMetrics, type PerfSeriesPoint } from './jtl-parser.js'
import type { PerfCaseModel } from './types.js'

export interface PerfRunResult {
  status: 'success' | 'failed' | 'error'
  message?: string
  durationMs: number
  summary: PerfMetrics
  series: PerfSeriesPoint[]
  labels: PerfLabelStat[]
  errors: PerfErrorItem[]
  maxThreads: number
  jmx: string
  jmeterVersion?: string
}

export interface JmeterLocation {
  home: string
  jar: string
  source: 'embedded' | 'env' | 'path'
}

const JAR_REL = join('bin', 'ApacheJMeter.jar')

/** 本模块所在目录（src/engine/jmeter 与 dist/engine/jmeter 深度一致，均可回溯到 server/） */
function moduleDir(): string {
  try {
    return dirname(fileURLToPath(import.meta.url))
  } catch {
    return process.cwd()
  }
}

/** 内置 JMeter 目录：server/jmeter */
function embeddedCandidates(): string[] {
  const here = moduleDir()
  return [
    resolve(here, '..', '..', '..', 'jmeter'), // server/{src,dist}/engine/jmeter → server/jmeter
    resolve(here, '..', '..', 'jmeter'),
    resolve(process.cwd(), 'jmeter'),
    resolve(process.cwd(), 'server', 'jmeter'),
  ]
}

/** 由 PATH 上的 jmeter 可执行文件反推 home */
function homeFromPath(): string | undefined {
  const exts = process.platform === 'win32' ? ['.bat', '.cmd', '', '.exe'] : ['']
  const bins = process.env.PATH?.split(process.platform === 'win32' ? ';' : ':') ?? []
  for (const dir of bins) {
    if (!dir) continue
    for (const name of ['jmeter', 'jmeter.bat']) {
      for (const ext of exts) {
        const candidate = join(dir, name + (ext && !name.endsWith(ext) ? ext : ''))
        if (existsSync(candidate)) {
          const home = resolve(dirname(candidate), '..')
          if (existsSync(join(home, JAR_REL))) return home
        }
      }
    }
  }
  return undefined
}

/** 定位可用的 JMeter 运行时 */
export function locateJmeter(): JmeterLocation | undefined {
  for (const home of embeddedCandidates()) {
    const jar = join(home, JAR_REL)
    if (existsSync(jar)) return { home, jar, source: 'embedded' }
  }
  const envHome = process.env.JMETER_HOME
  if (envHome) {
    const jar = join(envHome, JAR_REL)
    if (existsSync(jar)) return { home: envHome, jar, source: 'env' }
  }
  const home = homeFromPath()
  if (home) return { home, jar: join(home, JAR_REL), source: 'path' }
  return undefined
}

/** 定位 java 可执行文件 */
function javaBin(): string {
  const javaHome = process.env.JAVA_HOME
  if (javaHome) {
    const p = join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    if (existsSync(p)) return p
  }
  return 'java'
}

interface SpawnOutcome {
  code: number
  stdout: string
  stderr: string
  spawnError?: string
  timedOut?: boolean
}

function runProcess(cmd: string, args: string[], opts: { cwd?: string; timeoutMs: number }): Promise<SpawnOutcome> {
  return new Promise((resolvePromise) => {
    let child
    try {
      child = spawn(cmd, args, { cwd: opts.cwd, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
    } catch (err) {
      resolvePromise({ code: -1, stdout: '', stderr: '', spawnError: err instanceof Error ? err.message : String(err) })
      return
    }
    let stdout = ''
    let stderr = ''
    let settled = false
    const cap = (s: string) => (s.length > 4000 ? `\n...${s.slice(-4000)}` : s)
    const finish = (r: SpawnOutcome) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolvePromise({ ...r, stdout: cap(stdout), stderr: cap(stderr) })
    }
    const timer = setTimeout(() => {
      try {
        child.kill('SIGKILL')
      } catch {
        /* ignore */
      }
      finish({ code: -2, stdout, stderr, timedOut: true })
    }, opts.timeoutMs)
    child.on('error', (err) => finish({ code: -1, stdout, stderr, spawnError: err.message }))
    child.stdout?.on('data', (d) => {
      stdout += d
    })
    child.stderr?.on('data', (d) => {
      stderr += d
    })
    child.on('close', (code) => finish({ code: code ?? -1, stdout, stderr }))
  })
}

/** 读取 .jtl（不存在返回空串） */
function readIfExists(p: string): string {
  try {
    return existsSync(p) ? readFileSync(p, 'utf8') : ''
  } catch {
    return ''
  }
}

export interface RunPerfOptions {
  /** 最长执行时间（毫秒），默认取环境变量 PERF_TIMEOUT_MS，再默认 1 小时 */
  timeoutMs?: number
}

const EMPTY = {
  summary: {
    samples: 0,
    errors: 0,
    errorRate: 0,
    avg: 0,
    min: 0,
    max: 0,
    median: 0,
    p90: 0,
    p95: 0,
    p99: 0,
    throughput: 0,
  } as PerfMetrics,
  series: [] as PerfSeriesPoint[],
  labels: [] as PerfLabelStat[],
  errors: [] as PerfErrorItem[],
}

/** 执行一次压测：生成 .jmx、调用 JMeter、解析 JTL */
export async function runPerfTest(model: PerfCaseModel, options: RunPerfOptions = {}): Promise<PerfRunResult> {
  const jmx = buildJmx(model)
  const timeoutMs = options.timeoutMs ?? Number(process.env.PERF_TIMEOUT_MS ?? 60 * 60 * 1000)

  const jmeter = locateJmeter()
  if (!jmeter) {
    return {
      status: 'error',
      durationMs: 0,
      jmx,
      ...EMPTY,
      maxThreads: 0,
      message:
        '未找到 JMeter 运行时：请将 JMeter 解压到工程 server/jmeter/ 目录，或设置环境变量 JMETER_HOME（并把 jmeter 加入 PATH）',
    }
  }

  if (!(model.steps ?? []).filter((s) => s.enabled !== false).length) {
    return { status: 'error', durationMs: 0, jmx, ...EMPTY, maxThreads: 0, message: '压测用例没有任何启用的请求步骤' }
  }

  const dir = mkdtempSync(join(tmpdir(), 'apiweb-perf-'))
  const start = Date.now()
  try {
    const planPath = join(dir, 'plan.jmx')
    const jtlPath = join(dir, 'results.jtl')
    const logPath = join(dir, 'jmeter.log')
    writeFileSync(planPath, jmx, 'utf8')

    const args = ['-jar', jmeter.jar, '-n', '-t', planPath, '-l', jtlPath, '-j', logPath]
    const outcome = await runProcess(javaBin(), args, { cwd: jmeter.home, timeoutMs })
    const durationMs = Date.now() - start

    const jtl = readIfExists(jtlPath)
    let parsed = null as ReturnType<typeof parseJtl> | null
    if (jtl.trim()) {
      try {
        parsed = parseJtl(jtl)
      } catch (err) {
        return {
          status: 'error',
          durationMs,
          jmx,
          ...EMPTY,
          maxThreads: 0,
          message: `结果解析失败：${err instanceof Error ? err.message : String(err)}`,
        }
      }
    }

    if (outcome.timedOut) {
      const partial = parsed
      return {
        status: partial && partial.summary.samples > 0 ? 'failed' : 'error',
        durationMs,
        jmx,
        maxThreads: partial?.maxThreads ?? 0,
        summary: partial?.summary ?? EMPTY.summary,
        series: partial?.series ?? [],
        labels: partial?.labels ?? [],
        errors: partial?.errors ?? [],
        jmeterVersion: jmeter.source,
        message: `压测执行超时（${Math.round(timeoutMs / 1000)}s），已强制终止${partial && partial.summary.samples > 0 ? '，以下为已采集到的部分结果' : ''}`,
      }
    }

    if (!parsed || parsed.summary.samples === 0) {
      const logTail = readIfExists(logPath).split(/\r?\n/).slice(-12).join('\n')
      const reason =
        outcome.spawnError ??
        (logTail ? `JMeter 日志：${logTail}` : outcome.stderr.trim() || `JMeter 退出码 ${outcome.code}`)
      return {
        status: 'error',
        durationMs,
        jmx,
        ...EMPTY,
        maxThreads: 0,
        message: `压测未产生任何结果：${reason}`.slice(0, 2000),
      }
    }

    const ok = parsed.summary.errors === 0 && outcome.code === 0
    return {
      status: ok ? 'success' : 'failed',
      durationMs,
      jmx,
      summary: parsed.summary,
      series: parsed.series,
      labels: parsed.labels,
      errors: parsed.errors,
      maxThreads: parsed.maxThreads,
      jmeterVersion: jmeter.source,
      message: ok
        ? '压测完成'
        : parsed.summary.errors > 0
          ? `压测完成，存在 ${parsed.summary.errors} 个失败请求（错误率 ${parsed.summary.errorRate}%）`
          : `JMeter 退出码 ${outcome.code}`,
    }
  } catch (err) {
    return {
      status: 'error',
      durationMs: Date.now() - start,
      jmx,
      ...EMPTY,
      maxThreads: 0,
      message: `执行异常：${err instanceof Error ? err.message : String(err)}`,
    }
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}
