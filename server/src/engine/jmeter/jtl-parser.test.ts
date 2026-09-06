import { describe, it, expect } from 'vitest'
import { parseCsvLine, parseJtl } from './jtl-parser.js'

const HEADER =
  'timeStamp,elapsed,label,responseCode,responseMessage,threadName,dataType,success,failureMessage,bytes,sentBytes,grpThreads,allThreads,URL,Latency,IdleTime,Connect'

interface RowSpec {
  ts: number
  elapsed: number
  label: string
  code?: string
  message?: string
  success?: boolean
  threads?: number
}

function jtl(rows: RowSpec[]): string {
  const lines = rows.map((r) => {
    const success = r.success !== false
    const cells = [
      String(r.ts),
      String(r.elapsed),
      r.label,
      r.code ?? (success ? '200' : '500'),
      success ? 'OK' : 'Server Error',
      'TG 1-1',
      'text',
      success ? 'true' : 'false',
      success ? '' : (r.message ?? 'Assertion failed'),
      '1024',
      '256',
      String(r.threads ?? 5),
      String(r.threads ?? 5),
      'http://x/y',
      String(r.elapsed),
      '0',
      '1',
    ]
    return cells.map((c) => (/[",\n]/.test(c) ? `"${c.replace(/"/g, '""')}"` : c)).join(',')
  })
  return [HEADER, ...lines].join('\n')
}

describe('parseCsvLine', () => {
  it('处理引号包裹、内部逗号与转义双引号', () => {
    expect(parseCsvLine('a,"b,c",d')).toEqual(['a', 'b,c', 'd'])
    expect(parseCsvLine('a,"say ""hi""",b')).toEqual(['a', 'say "hi"', 'b'])
    expect(parseCsvLine('a,,b')).toEqual(['a', '', 'b'])
  })
})

describe('parseJtl 汇总指标', () => {
  const rows: RowSpec[] = [
    { ts: 1000, elapsed: 10, label: 'A' },
    { ts: 1100, elapsed: 20, label: 'A' },
    { ts: 1200, elapsed: 30, label: 'B' },
    { ts: 1300, elapsed: 40, label: 'B', success: false, message: 'Connection reset, aborted' },
    { ts: 1400, elapsed: 50, label: 'A' },
    { ts: 1500, elapsed: 60, label: 'A' },
    { ts: 1600, elapsed: 70, label: 'B' },
    { ts: 1700, elapsed: 80, label: 'B' },
    { ts: 1800, elapsed: 90, label: 'A' },
    { ts: 1900, elapsed: 100, label: 'A' },
  ]
  const result = parseJtl(jtl(rows))

  it('计算总数、错误数与错误率', () => {
    expect(result.summary.samples).toBe(10)
    expect(result.summary.errors).toBe(1)
    expect(result.summary.errorRate).toBe(10)
  })

  it('计算平均值、最小值、最大值与分位数', () => {
    expect(result.summary.avg).toBe(55)
    expect(result.summary.min).toBe(10)
    expect(result.summary.max).toBe(100)
    expect(result.summary.median).toBe(50)
    expect(result.summary.p90).toBe(90)
    expect(result.summary.p95).toBe(100)
    expect(result.summary.p99).toBe(100)
  })

  it('按实际时间窗口计算吞吐量', () => {
    // 窗口 = (1900+100) - 1000 = 1000ms → 10 请求/秒
    expect(result.summary.throughput).toBe(10)
    expect(result.startTime).toBe(1000)
    expect(result.endTime).toBe(2000)
  })

  it('分接口统计按 label 聚合', () => {
    expect(result.labels).toHaveLength(2)
    const a = result.labels.find((l) => l.label === 'A')!
    const b = result.labels.find((l) => l.label === 'B')!
    expect(a.samples).toBe(6)
    expect(a.errors).toBe(0)
    expect(a.avg).toBe(55)
    expect(b.samples).toBe(4)
    expect(b.errors).toBe(1)
    expect(b.errorRate).toBe(25)
  })

  it('错误 TOP 保留带逗号的原始信息', () => {
    expect(result.errors).toHaveLength(1)
    expect(result.errors[0].count).toBe(1)
    expect(result.errors[0].message).toBe('Connection reset, aborted')
    expect(result.errors[0].code).toBe('500')
  })

  it('时间序列与最大并发线程', () => {
    expect(result.series.length).toBeGreaterThan(0)
    expect(result.series.reduce((s, p) => s + p.samples, 0)).toBe(10)
    expect(result.maxThreads).toBe(5)
  })
})

describe('parseJtl 边界情况', () => {
  it('空内容返回零值结果', () => {
    const r = parseJtl('')
    expect(r.summary.samples).toBe(0)
    expect(r.summary.throughput).toBe(0)
    expect(r.series).toEqual([])
  })

  it('仅表头无数据', () => {
    const r = parseJtl(HEADER)
    expect(r.summary.samples).toBe(0)
  })

  it('XML 格式给出明确提示', () => {
    expect(() => parseJtl('<?xml version="1.0"?><testResults>')).toThrow(/CSV/)
  })

  it('长时压测自动放大时间桶，控制序列点数', () => {
    const rows: RowSpec[] = []
    for (let i = 0; i < 200; i++) rows.push({ ts: 1000 + i * 5000, elapsed: 20, label: 'A' })
    const r = parseJtl(jtl(rows))
    expect(r.summary.samples).toBe(200)
    expect(r.series.length).toBeLessThanOrEqual(300)
    expect(r.series.reduce((s, p) => s + p.samples, 0)).toBe(200)
  })
})
