/**
 * 性能测试模块端到端冒烟脚本（需要后端已启动）
 *
 * 覆盖：JMeter 环境探测 → 建用例 → 真实执行压测 → 报告查询 → .jmx 导出 → 再导入 → 批量导出 → 清理
 * 用法：node scripts/perf-e2e-smoke.mjs           # 默认打 http://127.0.0.1:4000/api
 *       BASE=http://host:port/api node scripts/perf-e2e-smoke.mjs
 */
import assert from 'node:assert/strict'

const BASE = process.env.BASE ?? 'http://127.0.0.1:4000/api'
const step = (n, msg) => console.log(`OK ${n}) ${msg}`)

async function req(method, path, { token, body, raw = false } = {}) {
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json; charset=utf-8' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (raw) {
    const text = await res.text()
    return { status: res.status, headers: res.headers, text }
  }
  const payload = await res.json().catch(() => null)
  if (!res.ok) {
    throw new Error(`${method} ${path} → ${res.status} ${JSON.stringify(payload)}`)
  }
  return payload
}

/** 等待后端就绪 */
async function waitReady(timeoutMs = 60000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const r = await fetch(`${BASE}/health`)
      if (r.ok) return
    } catch {
      /* 未启动，继续等 */
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error('后端未就绪：' + BASE)
}

// 1) 就绪 + 登录
await waitReady()
step(1, '后端就绪')
const login = await req('POST', '/auth/login', { body: { username: 'admin', password: 'admin@123' } })
const token = login.token
assert.ok(token, '未取得 token')
step(2, `登录成功（${login.user.username} / ${login.user.role}）`)

// 2) 项目
let projects = await req('GET', '/projects', { token })
if (!projects?.length) {
  projects = [await req('POST', '/projects', { token, body: { name: '性能演示项目' } })]
}
const projectId = projects[0].id
step(3, `使用项目「${projects[0].name}」`)

// 3) 未认证必须被拒
const anon = await fetch(`${BASE}/projects/${projectId}/perf-cases`)
assert.equal(anon.status, 401)
step(4, '未登录访问被拒绝（401）')

// 4) 创建用例（压平台自身的健康检查接口，公开无需鉴权）
const created = await req('POST', `/projects/${projectId}/perf-cases`, {
  token,
  body: {
    name: 'E2E冒烟-健康检查压测',
    description: '端到端验证：结构化用例 → .jmx → JMeter 执行 → 报告',
    threads: 2,
    rampUp: 1,
    loops: 3,
    duration: 0,
    thinkTime: 0,
    onSampleError: 'continue',
    variables: [{ key: 'baseUrl', value: new URL(BASE).origin, enabled: true }],
    steps: [
      {
        id: 's1',
        name: '健康检查',
        method: 'GET',
        url: '${baseUrl}/api/health',
        query: [{ key: 'from', value: 'jmeter', enabled: true }],
        assertions: [{ type: 'responseCode', operator: 'equals', expected: '200' }],
        enabled: true,
      },
    ],
  },
})
assert.equal(created.threads, 2)
step(5, `用例已创建（id=${created.id}，2 线程 × 3 循环）`)

// 5) JMeter 环境
const env = await req('GET', '/perf-env', { token })
assert.ok(env.available, `未找到 JMeter：${JSON.stringify(env)}`)
step(6, `JMeter 就绪 source=${env.source} home=${env.home}`)

// 6) 真实执行
const t0 = Date.now()
const report = await req('POST', `/perf-cases/${created.id}/run`, { token, body: {} })
const secs = Math.round((Date.now() - t0) / 1000)
assert.equal(report.status, 'success', `压测未通过：${report.message}`)
assert.equal(report.summary.samples, 6, '采样数应为 2 线程 × 3 循环 = 6')
assert.equal(report.summary.errors, 0)
assert.ok(report.summary.throughput > 0, 'TPS 应大于 0')
assert.ok(report.series.length > 0, '应有时序数据')
assert.equal(report.labels[0].label, '健康检查')
step(
  7,
  `真实压测完成：samples=${report.summary.samples} avg=${report.summary.avg}ms p95=${report.summary.p95}ms TPS=${report.summary.throughput} 时序点=${report.series.length}（耗时 ${secs}s）`,
)

// 7) 报告列表（不含时序）与详情（含时序）
const list = await req('GET', `/projects/${projectId}/perf-reports`, { token })
assert.ok(list.length >= 1)
assert.equal(list[0].series, undefined, '列表不应返回时序数据')
const detail = await req('GET', `/perf-reports/${report.id}`, { token })
assert.ok(detail.series.length > 0, '详情应返回时序数据')
step(8, `报告列表 ${list.length} 条；详情含时序 ${detail.series.length} 点、分接口 ${detail.labels.length} 项`)

// 8) 导出 .jmx
const exported = await req('GET', `/perf-cases/${created.id}/export`, { token, raw: true })
assert.equal(exported.status, 200)
const disposition = exported.headers.get('content-disposition') ?? ''
assert.match(disposition, /filename\*=UTF-8''/)
assert.match(exported.text, /<jmeterTestPlan/)
assert.match(exported.text, /<stringProp name="ThreadGroup\.num_threads">2<\/stringProp>/)
assert.match(exported.text, /\$\{baseUrl\}\/api\/health/)
step(9, `导出 .jmx 成功（${exported.text.length} 字节，文件名头：${disposition.slice(0, 46)}…）`)

// 9) 导出的文件可被 JMeter 真实执行（写入临时用例后直接跑）
const reimported = await req('POST', `/projects/${projectId}/perf-cases/import`, {
  token,
  body: { files: [{ filename: 'e2e-export.jmx', content: exported.text }] },
})
assert.equal(reimported.created.length, 1, `导入失败：${JSON.stringify(reimported)}`)
assert.equal(reimported.failed.length, 0)
const back = await req('GET', `/perf-cases/${reimported.created[0].id}`, { token })
assert.equal(back.name, 'E2E冒烟-健康检查压测')
assert.equal(back.threads, 2)
assert.equal(back.loops, 3)
assert.equal(back.steps[0].url, '${baseUrl}/api/health')
assert.equal(back.steps[0].assertions[0].expected, '200')
assert.equal(back.steps[0].query[0].key, 'from')
step(10, '导入回读一致（名称/线程/循环/URL/断言/Query 全部还原）')

// 10) 导入的文件真的能跑
const reRun = await req('POST', `/perf-cases/${back.id}/run`, { token, body: {} })
assert.equal(reRun.status, 'success', `导入后的用例执行失败：${reRun.message}`)
assert.equal(reRun.summary.samples, 6)
step(11, `导出的 .jmx 重新导入后同样可执行（samples=${reRun.summary.samples}）`)

// 11) 批量导出
const bundle = await req('POST', `/projects/${projectId}/perf-cases/export`, {
  token,
  body: { ids: [created.id, back.id], planName: 'E2E批量计划' },
  raw: true,
})
assert.equal((bundle.text.match(/<ThreadGroup /g) ?? []).length, 2, '批量导出应含 2 个线程组')
step(12, '批量导出为单计划多线程组（2 个线程组）')

// 12) 清理
await req('DELETE', `/perf-cases/${created.id}`, { token })
await req('DELETE', `/perf-cases/${back.id}`, { token })
await req('DELETE', `/perf-reports/${report.id}`, { token })
await req('DELETE', `/perf-reports/${reRun.id}`, { token })
step(13, '冒烟数据已清理')

console.log('\nE2E ALL PASSED ✅')
