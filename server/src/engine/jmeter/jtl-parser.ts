/**
 * JMeter 结果文件（JTL, CSV 格式）解析器。
 *
 * 标准列：timeStamp,elapsed,label,responseCode,responseMessage,threadName,
 *        dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,
 *        URL,Latency,IdleTime,Connect
 * 产出：汇总指标（含 p50/p90/p95/p99、吞吐量、错误率）、时间序列、分接口统计、错误 TOP。
 */

export interface PerfMetrics {
  samples: number
  errors: number
  errorRate: number // 百分比，保留两位
  avg: number
  min: number
  max: number
  median: number
  p90: number
  p95: number
  p99: number
  throughput: number // 每秒请求数
}

export interface PerfSeriesPoint {
  t: number // 相对开始的时间（秒）
  samples: number
  errors: number
  avg: number
  max: number
}

export interface PerfLabelStat extends PerfMetrics {
  label: string
}

export interface PerfErrorItem {
  code: string
  message: string
  count: number
}

export interface PerfJtlResult {
  summary: PerfMetrics
  series: PerfSeriesPoint[]
  labels: PerfLabelStat[]
  errors: PerfErrorItem[]
  maxThreads: number
  startTime: number
  endTime: number
}

const EMPTY_METRICS = (): PerfMetrics => ({
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
})

function round(n: number, d = 2): number {
  const f = 10 ** d
  return Math.round(n * f) / f
}

/** 解析一行 CSV（支持双引号包裹与 "" 转义） */
export function parseCsvLine(line: string): string[] {
  const out: string[] = []
  let cur = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inQuotes) {
      if (c === '"') {
        if (line[i + 1] === '"') {
          cur += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        cur += c
      }
    } else if (c === '"') {
      inQuotes = true
    } else if (c === ',') {
      out.push(cur)
      cur = ''
    } else {
      cur += c
    }
  }
  out.push(cur)
  return out
}

/** 最近排名法分位数 */
function percentile(sorted: number[], p: number): number {
  if (sorted.length === 0) return 0
  const idx = Math.ceil((p / 100) * sorted.length) - 1
  return sorted[Math.min(sorted.length - 1, Math.max(0, idx))]
}

interface Row {
  ts: number
  elapsed: number
  label: string
  success: boolean
  code: string
  message: string
  threads: number
}

function computeMetrics(rows: Row[], windowMs: number): PerfMetrics {
  if (rows.length === 0) return EMPTY_METRICS()
  const times: number[] = []
  let errors = 0
  for (const r of rows) {
    times.push(r.elapsed)
    if (!r.success) errors++
  }
  times.sort((a, b) => a - b)
  const sum = times.reduce((s, v) => s + v, 0)
  const seconds = windowMs > 0 ? windowMs / 1000 : 1
  return {
    samples: rows.length,
    errors,
    errorRate: round((errors / rows.length) * 100),
    avg: round(sum / rows.length),
    min: times[0],
    max: times[times.length - 1],
    median: percentile(times, 50),
    p90: percentile(times, 90),
    p95: percentile(times, 95),
    p99: percentile(times, 99),
    throughput: round(rows.length / seconds),
  }
}

/** 解析 JTL CSV 文本 */
export function parseJtl(csv: string): PerfJtlResult {
  const text = (csv ?? '').replace(/^\uFEFF/, '').trim()
  if (!text) {
    return {
      summary: EMPTY_METRICS(),
      series: [],
      labels: [],
      errors: [],
      maxThreads: 0,
      startTime: 0,
      endTime: 0,
    }
  }
  if (text.startsWith('<')) {
    throw new Error('暂不支持 XML 格式的 JTL，请在 JMeter 中设置为 CSV 输出')
  }

  const lines = text.split(/\r?\n/).filter((l) => l.length > 0)
  const header = parseCsvLine(lines[0]).map((h) => h.trim())
  const col = (name: string) => header.indexOf(name)
  const iTs = col('timeStamp')
  const iElapsed = col('elapsed')
  const iLabel = col('label')
  const iSuccess = col('success')
  const iCode = col('responseCode')
  const iMsg = col('failureMessage') >= 0 ? col('failureMessage') : col('responseMessage')
  const iThreads = col('allThreads') >= 0 ? col('allThreads') : col('grpThreads')

  if (iTs < 0 || iElapsed < 0) throw new Error('JTL 文件缺少 timeStamp/elapsed 列，格式不正确')

  const rows: Row[] = []
  for (let i = 1; i < lines.length; i++) {
    const cells = parseCsvLine(lines[i])
    const ts = Number(cells[iTs])
    const elapsed = Number(cells[iElapsed])
    if (!Number.isFinite(ts) || !Number.isFinite(elapsed)) continue
    const successRaw = (iSuccess >= 0 ? cells[iSuccess] : 'true') ?? 'true'
    rows.push({
      ts,
      elapsed,
      label: (iLabel >= 0 ? cells[iLabel] : '') ?? '',
      success: successRaw.trim().toLowerCase() !== 'false',
      code: (iCode >= 0 ? cells[iCode] : '') ?? '',
      message: (iMsg >= 0 ? cells[iMsg] : '') ?? '',
      threads: iThreads >= 0 ? Number(cells[iThreads]) || 0 : 0,
    })
  }

  if (rows.length === 0) {
    return { summary: EMPTY_METRICS(), series: [], labels: [], errors: [], maxThreads: 0, startTime: 0, endTime: 0 }
  }

  const startTime = Math.min(...rows.map((r) => r.ts))
  const endTime = Math.max(...rows.map((r) => r.ts + r.elapsed))
  const windowMs = Math.max(1, endTime - startTime)

  const summary = computeMetrics(rows, windowMs)
  const maxThreads = rows.reduce((m, r) => Math.max(m, r.threads), 0)

  // 时间序列：尽量控制在 ~300 个点内
  const targetPoints = 300
  const bucketMs = Math.max(1000, Math.ceil(windowMs / targetPoints / 1000) * 1000)
  const buckets = new Map<number, Row[]>()
  for (const r of rows) {
    const b = Math.floor((r.ts - startTime) / bucketMs)
    const list = buckets.get(b)
    if (list) list.push(r)
    else buckets.set(b, [r])
  }
  const series: PerfSeriesPoint[] = [...buckets.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([b, list]) => {
      const m = computeMetrics(list, bucketMs)
      return { t: round(b * (bucketMs / 1000)), samples: m.samples, errors: m.errors, avg: m.avg, max: m.max }
    })

  // 分接口（按 label 聚合）
  const byLabel = new Map<string, Row[]>()
  for (const r of rows) {
    const list = byLabel.get(r.label)
    if (list) list.push(r)
    else byLabel.set(r.label, [r])
  }
  const labels: PerfLabelStat[] = [...byLabel.entries()]
    .map(([label, list]) => ({ label, ...computeMetrics(list, windowMs) }))
    .sort((a, b) => b.samples - a.samples)

  // 错误 TOP
  const errMap = new Map<string, PerfErrorItem>()
  for (const r of rows) {
    if (r.success) continue
    const message = (r.message || '').trim() || (r.code || '').trim() || '未知错误'
    const key = `${r.code}||${message}`
    const hit = errMap.get(key)
    if (hit) hit.count++
    else errMap.set(key, { code: r.code || '', message, count: 1 })
  }
  const errors = [...errMap.values()].sort((a, b) => b.count - a.count).slice(0, 10)

  return { summary, series, labels, errors, maxThreads, startTime, endTime }
}
