import { existsSync } from 'node:fs'
import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { describe, it, expect } from 'vitest'
import { locateJmeter, runPerfTest } from './runner.js'
import type { PerfCaseModel } from './types.js'

const jmeter = locateJmeter()

function baseModel(url: string, overrides: Partial<PerfCaseModel> = {}): PerfCaseModel {
  return {
    name: '冒烟压测',
    threads: 1,
    rampUp: 1,
    loops: 1,
    duration: 0,
    thinkTime: 0,
    onSampleError: 'continue',
    variables: [],
    steps: [{ id: 's1', name: '健康检查', method: 'GET', url, assertions: [], enabled: true }],
    ...overrides,
  }
}

/** 启动本地测试服务：/api/ping 返回 200，/api/fail 返回 500 */
async function startServer(): Promise<{ url: (p: string) => string; close: () => void }> {
  const server = createServer((req, res) => {
    const fail = (req.url ?? '').includes('/fail')
    res.writeHead(fail ? 500 : 200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ ok: !fail, path: req.url }))
  })
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r))
  const port = (server.address() as AddressInfo).port
  return {
    url: (p: string) => `http://127.0.0.1:${port}${p}`,
    close: () => server.close(),
  }
}

describe('jmeter runner 运行时定位', () => {
  it('能找到可用的 JMeter（优先工程内置）', () => {
    expect(jmeter).toBeDefined()
    expect(existsSync(jmeter!.jar)).toBe(true)
    expect(jmeter!.jar.replace(/\\/g, '/')).toContain('ApacheJMeter.jar')
  })

  it('优先使用工程内置的 server/jmeter', () => {
    const here = new URL('../../../jmeter/bin/ApacheJMeter.jar', import.meta.url)
    if (existsSync(here)) {
      expect(jmeter!.source).toBe('embedded')
      expect(jmeter!.jar.replace(/\\/g, '/')).toContain('server/jmeter/bin/ApacheJMeter.jar')
    }
  })

  it('无启用步骤时给出明确错误且不执行', async () => {
    const r = await runPerfTest(baseModel('http://127.0.0.1:1/x', { steps: [] }))
    expect(r.status).toBe('error')
    expect(r.message).toContain('没有任何启用的请求步骤')
    expect(r.jmx).toContain('<jmeterTestPlan')
  })
})

describe('jmeter runner 真实执行', () => {
  it.skipIf(!jmeter)(
    '成功执行压测并产出完整报告数据',
    async () => {
      const srv = await startServer()
      try {
        const r = await runPerfTest(baseModel(srv.url('/api/ping'), { threads: 2, loops: 3 }), { timeoutMs: 120000 })
        expect(r.status, r.message).toBe('success')
        expect(r.summary.samples).toBe(6) // 2 线程 × 3 循环
        expect(r.summary.errors).toBe(0)
        expect(r.summary.errorRate).toBe(0)
        expect(r.summary.throughput).toBeGreaterThan(0)
        expect(r.labels.map((l) => l.label)).toContain('健康检查')
        expect(r.series.length).toBeGreaterThan(0)
        expect(r.maxThreads).toBeGreaterThan(0)
        expect(r.durationMs).toBeGreaterThan(0)
      } finally {
        srv.close()
      }
    },
    240000,
  )

  it.skipIf(!jmeter)(
    '请求失败时在报告中体现错误率与错误 TOP',
    async () => {
      const srv = await startServer()
      try {
        const r = await runPerfTest(
          baseModel(srv.url('/api/fail'), {
            threads: 1,
            loops: 2,
            steps: [
              {
                id: 's1',
                name: '必失败请求',
                method: 'GET',
                url: srv.url('/api/fail'),
                assertions: [{ type: 'responseCode', operator: 'equals', expected: '200' }],
                enabled: true,
              },
            ],
          }),
          { timeoutMs: 120000 },
        )
        expect(r.status).toBe('failed')
        expect(r.summary.samples).toBe(2)
        expect(r.summary.errors).toBe(2)
        expect(r.summary.errorRate).toBe(100)
        expect(r.errors.length).toBeGreaterThan(0)
        expect(r.message).toContain('失败')
      } finally {
        srv.close()
      }
    },
    240000,
  )

  it.skipIf(!jmeter)(
    '自定义变量在 JMeter 中生效（${baseUrl} 被替换）',
    async () => {
      const srv = await startServer()
      try {
        const r = await runPerfTest(
          baseModel('${baseUrl}/api/ping', {
            variables: [{ key: 'baseUrl', value: srv.url('/').replace(/\/$/, ''), enabled: true }],
          }),
          { timeoutMs: 120000 },
        )
        expect(r.status, r.message).toBe('success')
        expect(r.summary.samples).toBe(1)
      } finally {
        srv.close()
      }
    },
    240000,
  )
})
