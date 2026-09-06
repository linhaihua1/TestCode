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
 *
 * 执行以**非阻塞**方式提供（startPerfRun 返回句柄）：压测可能持续数十分钟，
 * 必须能边跑边看进度、能中途停止，而不是把一个 HTTP 请求挂到压测结束。
 * runPerfTest 保留为同步语义的便捷封装（等待句柄结束），供内部与测试复用。
 */
import { spawn, type ChildProcess } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildJmx } from './jmx-builder.js'
import { parseJtl, type PerfCodeStat, type PerfErrorItem, type PerfLabelStat, type PerfMetrics, type PerfSeriesPoint } from './jtl-parser.js'
import type { PerfCaseModel } from './types.js'

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

const EMPTY_METRICS: PerfMetrics = {
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
}

/** 压测结束后的最终结果 */
export interface PerfRunResult {
  status: 'success' | 'failed' | 'error' | 'stopped'
  message?: string
  durationMs: number
  summary: PerfMetrics
  series: PerfSeriesPoint[]
  labels: PerfLabelStat[]
  errors: PerfErrorItem[]
  maxThreads: number
  jmx: string
  jmeterVersion?: string
  /** 响应码分布（由 JTL 汇总，等价于 Response Codes per Second 监听器） */
  codes: PerfCodeStat[]
  /** 原始 JTL 文件路径（仅当调用方指定持久 workDir 时保留） */
  jtlPath?: string
  /** JMeter 官方 HTML 报告目录（启用 dashboard 且生成成功时给出） */
  dashboardDir?: string
}

/** 运行中的实时快照（由已写入的 JTL 前缀增量解析得到） */
export interface PerfLive {
  summary: PerfMetrics
  series: PerfSeriesPoint[]
  labels: PerfLabelStat[]
  errors: PerfErrorItem[]
  codes: PerfCodeStat[]
  maxThreads: number
  elapsedMs: number
  /** 时长模式下的目标总时长（毫秒）；0 表示按循环次数、无法预估 */
  expectedDurationMs: number
}

export interface RunPerfOptions {
  /** 最长执行时间（毫秒），默认取环境变量 PERF_TIMEOUT_MS，再默认 1 小时 */
  timeoutMs?: number
  /**
   * 工作目录：写入 plan.jmx / results.jtl / jmeter.log（以及 dashboard 输出）。
   * 留空则使用系统临时目录并在结束后删除；传入持久目录时保留文件，
   * 以便报告页继续读取原始 JTL 与 HTML 报告。
   */
  workDir?: string
  /** 是否额外生成 JMeter 官方 HTML 报告（-e -o）：线程数曲线、百分位、每秒响应码、APDEX 等 */
  dashboard?: boolean
}

export interface PerfRunHandle {
  readonly jmx: string
  readonly startedAt: number
  /** 进程是否仍在运行 */
  running(): boolean
  /** 当前已采集到的指标（可随时轮询调用） */
  live(): PerfLive
  /** 请求停止；返回是否确实在运行并被要求终止 */
  stop(): boolean
  /** 等待压测结束并拿到最终结果 */
  readonly finished: Promise<PerfRunResult>
}

/** 单次压测最长执行时间 */
export function defaultTimeoutMs(): number {
  const v = Number(process.env.PERF_TIMEOUT_MS ?? 60 * 60 * 1000)
  return Number.isFinite(v) && v > 0 ? v : 60 * 60 * 1000
}

/**
 * 启动前置校验：返回不可执行的原因，undefined 表示可以执行。
 * 路由层据此在创建报告之前就拒掉，避免留下垃圾报告。
 */
export function preflightPerf(model: PerfCaseModel): string | undefined {
  if (!locateJmeter()) {
    return '未找到 JMeter 运行时：请将 JMeter 解压到工程 server/jmeter/ 目录，或设置环境变量 JMETER_HOME（并把 jmeter 加入 PATH）'
  }
  if (!(model.steps ?? []).filter((s) => s.enabled !== false).length) {
    return '压测用例没有任何启用的请求步骤'
  }
  return undefined
}

/** 读 JTL 的「完整行前缀」：末行可能正在被 JMeter 写入，截到最后一个换行符为止 */
function readJtlPrefix(path: string): string {
  let text: string
  try {
    if (!existsSync(path)) return ''
    text = readFileSync(path, 'utf8')
  } catch {
    return ''
  }
  const idx = text.lastIndexOf('\n')
  return idx < 0 ? '' : text.slice(0, idx + 1)
}

function readTail(path: string, lines = 12): string {
  try {
    if (!existsSync(path)) return ''
    return readFileSync(path, 'utf8').split(/\r?\n/).slice(-lines).join('\n')
  } catch {
    return ''
  }
}

function tryParseJtl(text: string): ReturnType<typeof parseJtl> | undefined {
  if (!text.trim()) return undefined
  try {
    return parseJtl(text)
  } catch {
    return undefined
  }
}

/**
 * 以非阻塞方式启动一次压测。
 *
 * 调用方通过 handle.live() 轮询进度、handle.stop() 中止、handle.finished 拿最终结果。
 * 前置条件不满足（缺运行时 / 无启用步骤）时不启动进程，finished 立即以 error 结束。
 */
export function startPerfRun(model: PerfCaseModel, options: RunPerfOptions = {}): PerfRunHandle {
  const jmx = buildJmx(model)
  const startedAt = Date.now()
  const timeoutMs = options.timeoutMs ?? defaultTimeoutMs()
  const expectedDurationMs = Math.max(0, Number(model.duration) || 0) * 1000

  const failFast = (message: string): PerfRunHandle => ({
    jmx,
    startedAt,
    running: () => false,
    live: () => ({
      summary: EMPTY_METRICS,
      series: [],
      labels: [],
      errors: [],
      codes: [],
      maxThreads: 0,
      elapsedMs: Date.now() - startedAt,
      expectedDurationMs,
    }),
    stop: () => false,
    finished: Promise.resolve({
      status: 'error',
      message,
      durationMs: 0,
      summary: EMPTY_METRICS,
      series: [],
      labels: [],
      errors: [],
      codes: [],
      maxThreads: 0,
      jmx,
    }),
  })

  const jmeter = locateJmeter()
  if (!jmeter) {
    return failFast('未找到 JMeter 运行时：请将 JMeter 解压到工程 server/jmeter/ 目录，或设置环境变量 JMETER_HOME（并把 jmeter 加入 PATH）')
  }
  if (!(model.steps ?? []).filter((s) => s.enabled !== false).length) {
    return failFast('压测用例没有任何启用的请求步骤')
  }

  let dir: string
  let ownsDir = true
  try {
    if (options.workDir) {
      dir = options.workDir
      ownsDir = false
      mkdirSync(dir, { recursive: true })
    } else {
      dir = mkdtempSync(join(tmpdir(), 'apiweb-perf-'))
    }
  } catch (err) {
    return failFast(`无法创建压测工作目录：${err instanceof Error ? err.message : String(err)}`)
  }
  const planPath = join(dir, 'plan.jmx')
  const jtlPath = join(dir, 'results.jtl')
  const logPath = join(dir, 'jmeter.log')
  const reportPath = join(dir, 'report')
  const wantDashboard = options.dashboard === true
  // JMeter 要求 -o 目录不存在（否则报 "file exists"），重跑同一报告目录时先清掉
  try {
    if (existsSync(reportPath)) rmSync(reportPath, { recursive: true, force: true })
  } catch {
    /* ignore */
  }

  let child: ChildProcess | undefined
  let spawnError: string | undefined
  try {
    writeFileSync(planPath, jmx, 'utf8')
    const args = ['-jar', jmeter.jar, '-n', '-t', planPath, '-l', jtlPath, '-j', logPath]
    if (wantDashboard) args.push('-e', '-o', reportPath)
    child = spawn(javaBin(), args, {
      cwd: jmeter.home,
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch (err) {
    spawnError = err instanceof Error ? err.message : String(err)
  }

  let closed = Boolean(spawnError)
  let exitCode = -1
  let killedBy: 'timeout' | 'stop' | undefined
  let output = ''
  const append = (chunk: unknown) => {
    output = (output + String(chunk)).slice(-4000)
  }

  const timer = child
    ? setTimeout(() => {
        killedBy = 'timeout'
        try {
          child?.kill('SIGKILL')
        } catch {
          /* ignore */
        }
      }, timeoutMs)
    : undefined

  if (child) {
    child.on('error', (err) => {
      spawnError = err.message
    })
    child.stdout?.on('data', append)
    child.stderr?.on('data', append)
    child.on('close', (code) => {
      exitCode = code ?? -1
      closed = true
    })
  }

  const live = (): PerfLive => {
    const parsed = tryParseJtl(readJtlPrefix(jtlPath))
    return {
      summary: parsed?.summary ?? EMPTY_METRICS,
      series: parsed?.series ?? [],
      labels: parsed?.labels ?? [],
      errors: parsed?.errors ?? [],
      codes: parsed?.codes ?? [],
      maxThreads: parsed?.maxThreads ?? 0,
      elapsedMs: Date.now() - startedAt,
      expectedDurationMs,
    }
  }

  const finished: Promise<PerfRunResult> = new Promise((resolvePromise) => {
    const settle = () => {
      if (timer) clearTimeout(timer)
      const durationMs = Date.now() - startedAt
      const parsed = tryParseJtl(readJtlPrefix(jtlPath))
      const samples = parsed?.summary.samples ?? 0
      const cleanup = () => {
        if (ownsDir) rmSync(dir, { recursive: true, force: true })
      }
      const dashboardOk = !ownsDir && wantDashboard && existsSync(join(reportPath, 'index.html'))
      const base = {
        durationMs,
        jmx,
        summary: parsed?.summary ?? EMPTY_METRICS,
        series: parsed?.series ?? [],
        labels: parsed?.labels ?? [],
        errors: parsed?.errors ?? [],
        codes: parsed?.codes ?? [],
        maxThreads: parsed?.maxThreads ?? 0,
        jmeterVersion: jmeter.source,
        jtlPath: ownsDir ? undefined : jtlPath,
        dashboardDir: dashboardOk ? reportPath : undefined,
      }
      const done = (patch: Partial<PerfRunResult>) => {
        cleanup()
        resolvePromise({ ...base, status: 'error', ...patch } as PerfRunResult)
      }

      if (spawnError) return done({ status: 'error', message: `无法启动 JMeter 进程：${spawnError}` })

      if (killedBy === 'timeout') {
        return done({
          status: samples > 0 ? 'failed' : 'error',
          message: `压测执行超时（${Math.round(timeoutMs / 1000)}s），已强制终止${samples > 0 ? '，以下为已采集到的部分结果' : ''}`,
        })
      }
      if (killedBy === 'stop') {
        return done({
          status: 'stopped',
          message: `压测已被手动停止${samples > 0 ? `，停止前共采集 ${samples} 个请求` : '（停止前未采集到请求）'}`,
        })
      }
      if (!parsed || samples === 0) {
        const logTail = readTail(logPath)
        const reason = logTail ? `JMeter 日志：${logTail}` : output.trim() || `JMeter 退出码 ${exitCode}`
        return done({ status: 'error', message: `压测未产生任何结果：${reason}`.slice(0, 2000) })
      }
      cleanup()
      const ok = parsed.summary.errors === 0 && exitCode === 0
      resolvePromise({
        ...base,
        status: ok ? 'success' : 'failed',
        message: ok
          ? '压测完成'
          : parsed.summary.errors > 0
            ? `压测完成，存在 ${parsed.summary.errors} 个失败请求（错误率 ${parsed.summary.errorRate}%）`
            : `JMeter 退出码 ${exitCode}`,
      })
    }

    if (closed) {
      settle()
      return
    }
    child!.once('close', settle)
    // spawn 直接失败时不会有 close，这里兜一层微任务保证被看到
    if (spawnError) settle()
  })

  return {
    jmx,
    startedAt,
    running: () => !closed,
    live,
    stop: () => {
      if (closed) return false
      killedBy = 'stop'
      try {
        child?.kill('SIGKILL')
      } catch {
        /* ignore */
      }
      return true
    },
    finished,
  }
}

/** 同步语义便捷封装：启动并等待结束（内部/测试复用；对外接口走异步） */
export async function runPerfTest(model: PerfCaseModel, options: RunPerfOptions = {}): Promise<PerfRunResult> {
  return startPerfRun(model, options).finished
}
