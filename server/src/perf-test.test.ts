import { createServer } from 'node:http'
import type { AddressInfo } from 'node:net'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { FastifyInstance } from 'fastify'
import { buildApp } from './app.js'
import { prisma } from './db.js'
import { hashPassword } from './auth.js'
import { locateJmeter } from './engine/jmeter/runner.js'
import { parseJmx } from './engine/jmeter/jmx-parser.js'

let app: FastifyInstance
let auth: { authorization: string }
const jmeter = locateJmeter()

async function login(username: string, password: string, role: string) {
  await prisma.user.upsert({
    where: { username },
    update: { role },
    create: { username, passwordHash: hashPassword(password), role },
  })
  const res = await app.inject({ method: 'POST', url: '/api/auth/login', payload: { username, password } })
  return { authorization: `Bearer ${res.json().token}` }
}

async function newProject(name: string) {
  const res = await app.inject({ method: 'POST', url: '/api/projects', headers: auth, payload: { name } })
  return res.json() as { id: string }
}

const casePayload = (name: string, url: string) => ({
  name,
  description: '压测描述',
  threads: 10,
  rampUp: 3,
  loops: 2,
  duration: 0,
  thinkTime: 100,
  onSampleError: 'continue',
  variables: [{ key: 'baseUrl', value: url, enabled: true }],
  steps: [
    {
      id: 's1',
      name: '健康检查',
      method: 'GET',
      url: '${baseUrl}/api/ping',
      headers: [{ key: 'X-Tenant', value: 'demo', enabled: true }],
      assertions: [{ type: 'responseCode', operator: 'equals', expected: '200' }],
      enabled: true,
    },
  ],
})

beforeAll(async () => {
  app = buildApp()
  await app.ready()
  auth = await login('admin', 'admin@123', 'admin')
})

afterAll(async () => {
  await prisma.project.deleteMany({ where: { name: { startsWith: 'perf-' } } })
  await app.close()
})

describe('性能测试用例 CRUD', () => {
  it('新建 → 列表 → 详情 → 更新 → 删除', async () => {
    const project = await newProject('perf-crud')
    const createRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases`,
      headers: auth,
      payload: casePayload('登录压测', 'http://127.0.0.1:4000'),
    })
    expect(createRes.statusCode).toBe(200)
    const created = createRes.json()
    expect(created.threads).toBe(10)
    expect(created.steps).toHaveLength(1)
    expect(created.variables[0].key).toBe('baseUrl')

    const listRes = await app.inject({
      method: 'GET',
      url: `/api/projects/${project.id}/perf-cases`,
      headers: auth,
    })
    expect(listRes.json()).toHaveLength(1)

    const detailRes = await app.inject({ method: 'GET', url: `/api/perf-cases/${created.id}`, headers: auth })
    expect(detailRes.json().name).toBe('登录压测')

    const updateRes = await app.inject({
      method: 'PUT',
      url: `/api/perf-cases/${created.id}`,
      headers: auth,
      payload: { ...casePayload('登录压测', 'http://127.0.0.1:4000'), threads: 50, name: '登录压测V2' },
    })
    expect(updateRes.json().threads).toBe(50)
    expect(updateRes.json().name).toBe('登录压测V2')

    const delRes = await app.inject({ method: 'DELETE', url: `/api/perf-cases/${created.id}`, headers: auth })
    expect(delRes.json().ok).toBe(true)
    const afterDelete = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/perf-cases`, headers: auth })
    expect(afterDelete.json()).toHaveLength(0)
    const goneDetail = await app.inject({ method: 'GET', url: `/api/perf-cases/${created.id}`, headers: auth })
    expect(goneDetail.statusCode).toBe(404)
  })

  it('参数校验：缺 name 报 400，越界参数被夹取，非法步骤被丢弃', async () => {
    const project = await newProject('perf-valid')
    const noName = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases`,
      headers: auth,
      payload: { threads: 5 },
    })
    expect(noName.statusCode).toBe(400)
    expect(noName.json().error).toContain('name')

    const extreme = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases`,
      headers: auth,
      payload: {
        name: '边界',
        threads: 999999,
        rampUp: -5,
        loops: 'abc',
        duration: 0,
        thinkTime: 99999999,
        steps: [
          { name: 'ok', method: 'GET', url: 'http://x/a' },
          { name: 'no url', method: 'GET', url: '  ' },
          { name: 'bad method', method: 'HACK', url: 'http://x/b' },
        ],
      },
    })
    expect(extreme.statusCode).toBe(200)
    const row = extreme.json()
    expect(row.threads).toBe(2000) // 上限夹取
    expect(row.rampUp).toBe(1) // 下限夹取
    expect(row.loops).toBe(1) // 非数字回退默认
    expect(row.thinkTime).toBe(600000) // 上限夹取
    expect(row.steps).toHaveLength(2) // 无 url 的步骤被丢弃
    expect(row.steps[1].method).toBe('GET') // 非法方法回退 GET
  })

  it('查看者只读：不能创建压测用例', async () => {
    const project = await newProject('perf-rbac')
    const viewerAuth = await login('perf_viewer', 'view@12345', 'viewer')
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases`,
      headers: viewerAuth,
      payload: casePayload('不允许', 'http://x'),
    })
    expect(res.statusCode).toBe(403)
    const listRes = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/perf-cases`, headers: viewerAuth })
    expect(listRes.statusCode).toBe(200)
    await prisma.user.deleteMany({ where: { username: 'perf_viewer' } })
  })
})

describe('JMeter 格式导入与导出', () => {
  it('导出 .jmx → 导入 → 结构化字段保持一致', async () => {
    const project = await newProject('perf-io')
    const created = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/perf-cases`,
        headers: auth,
        payload: casePayload('导入导出闭环', 'http://127.0.0.1:4000'),
      })
    ).json()

    const exportRes = await app.inject({ method: 'GET', url: `/api/perf-cases/${created.id}/export`, headers: auth })
    expect(exportRes.statusCode).toBe(200)
    expect(exportRes.headers['content-disposition']).toContain('.jmx')
    expect(exportRes.headers['content-disposition']).toContain('filename*=UTF-8')
    const xml = exportRes.body
    expect(xml).toContain('<jmeterTestPlan')
    expect(xml).toContain('<stringProp name="ThreadGroup.num_threads">10</stringProp>')

    // 导出的文件必须是可解析的合法 JMeter 计划
    const parsed = parseJmx(xml)
    expect(parsed.threads).toBe(10)
    expect(parsed.steps[0].url).toBe('${baseUrl}/api/ping')

    const importRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases/import`,
      headers: auth,
      payload: { filename: '导出的用例.jmx', content: xml },
    })
    expect(importRes.statusCode).toBe(200)
    const { created: imported } = importRes.json()
    expect(imported).toHaveLength(1)

    const detail = (await app.inject({ method: 'GET', url: `/api/perf-cases/${imported[0].id}`, headers: auth })).json()
    expect(detail.name).toBe('导入导出闭环')
    expect(detail.threads).toBe(10)
    expect(detail.rampUp).toBe(3)
    expect(detail.loops).toBe(2)
    expect(detail.thinkTime).toBe(100)
    expect(detail.steps[0].headers[0].key).toBe('X-Tenant')
    expect(detail.steps[0].assertions[0].expected).toBe('200')
    expect(detail.variables[0].value).toBe('http://127.0.0.1:4000')
  })

  it('支持一次导入多个 .jmx 文件', async () => {
    const project = await newProject('perf-io-multi')
    const one = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/perf-cases`,
        headers: auth,
        payload: casePayload('用例甲', 'http://a'),
      })
    ).json()
    const two = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/perf-cases`,
        headers: auth,
        payload: casePayload('用例乙', 'http://b'),
      })
    ).json()
    const x1 = (await app.inject({ method: 'GET', url: `/api/perf-cases/${one.id}/export`, headers: auth })).body
    const x2 = (await app.inject({ method: 'GET', url: `/api/perf-cases/${two.id}/export`, headers: auth })).body

    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases/import`,
      headers: auth,
      payload: {
        files: [
          { filename: 'a.jmx', content: x1 },
          { filename: '坏文件.jmx', content: '<html>not jmeter</html>' },
          { filename: 'b.jmx', content: x2 },
        ],
      },
    })
    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.created).toHaveLength(2)
    expect(body.failed).toHaveLength(1)
    expect(body.failed[0].filename).toBe('坏文件.jmx')
    expect(body.failed[0].reason).toContain('不是有效的 JMeter 测试计划')
  })

  it('全部导入失败时返回 400 与原因', async () => {
    const project = await newProject('perf-io-bad')
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases/import`,
      headers: auth,
      payload: { filename: 'empty.jmx', content: '   ' },
    })
    expect(res.statusCode).toBe(400)
    expect(res.json().error).toBeTruthy()
  })

  it('批量导出为单计划多线程组', async () => {
    const project = await newProject('perf-io-batch')
    const ids: string[] = []
    for (const n of ['批量甲', '批量乙']) {
      const r = (
        await app.inject({
          method: 'POST',
          url: `/api/projects/${project.id}/perf-cases`,
          headers: auth,
          payload: casePayload(n, 'http://127.0.0.1:4000'),
        })
      ).json()
      ids.push(r.id)
    }
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases/export`,
      headers: auth,
      payload: { ids, planName: '批量导出计划' },
    })
    expect(res.statusCode).toBe(200)
    expect(res.body).toContain('testname="批量导出计划"')
    expect(res.body.split('<ThreadGroup ').length - 1).toBe(2)
    expect(res.body).toContain('testname="批量甲"')
    expect(res.body).toContain('testname="批量乙"')
    // 合并文件仍可被解析（取第一个线程组）
    expect(parseJmx(res.body).steps).toHaveLength(1)

    const empty = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/perf-cases/export`,
      headers: auth,
      payload: { ids: [] },
    })
    expect(empty.statusCode).toBe(400)
  })

  it('导出他人项目的用例返回 404', async () => {
    const mine = await newProject('perf-io-mine')
    const other = await newProject('perf-io-other')
    const created = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${mine.id}/perf-cases`,
        headers: auth,
        payload: casePayload('只属于我', 'http://x'),
      })
    ).json()
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${other.id}/perf-cases/export`,
      headers: auth,
      payload: { ids: [created.id] },
    })
    expect(res.statusCode).toBe(404)
  })
})

describe('性能测试执行与报告', () => {
  it('/api/perf-env 报告 JMeter 就绪状态', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/perf-env', headers: auth })
    expect(res.statusCode).toBe(200)
    const body = res.json()
    expect(body.available).toBe(Boolean(jmeter))
    if (jmeter) {
      expect(['embedded', 'env', 'path']).toContain(body.source)
      expect(body.home).toBeTruthy()
    } else {
      expect(body.source).toBeNull()
    }
  })

  it.skipIf(!jmeter)(
    '执行压测 → 生成报告 → 列表与详情 → 删除报告',
    async () => {
      const server = createServer((_req, res) => {
        res.writeHead(200, { 'Content-Type': 'application/json' })
        res.end(JSON.stringify({ ok: true }))
      })
      await new Promise<void>((r) => server.listen(0, '127.0.0.1', r))
      const port = (server.address() as AddressInfo).port

      try {
        const project = await newProject('perf-run')
        const created = (
          await app.inject({
            method: 'POST',
            url: `/api/projects/${project.id}/perf-cases`,
            headers: auth,
            payload: {
              ...casePayload('真实压测', `http://127.0.0.1:${port}`),
              threads: 2,
              loops: 2,
              thinkTime: 0,
            },
          })
        ).json()

        const runRes = await app.inject({
          method: 'POST',
          url: `/api/perf-cases/${created.id}/run`,
          headers: auth,
          payload: { timeoutMs: 60000 },
        })
        expect(runRes.statusCode).toBe(200)
        const report = runRes.json()
        expect(report.status).toBe('success')
        expect(report.summary.samples).toBe(4)
        expect(report.summary.errors).toBe(0)
        expect(report.summary.throughput).toBeGreaterThan(0)
        expect(report.series.length).toBeGreaterThan(0)
        expect(report.labels[0].label).toBe('健康检查')

        const listRes = await app.inject({
          method: 'GET',
          url: `/api/projects/${project.id}/perf-reports`,
          headers: auth,
        })
        const list = listRes.json()
        expect(list).toHaveLength(1)
        expect(list[0].name).toBe('真实压测')
        expect(list[0].perfCase.name).toBe('真实压测')
        expect(list[0].series).toBeUndefined() // 列表不返回时序数据

        const detailRes = await app.inject({ method: 'GET', url: `/api/perf-reports/${report.id}`, headers: auth })
        expect(detailRes.json().series.length).toBeGreaterThan(0)

        const delRes = await app.inject({ method: 'DELETE', url: `/api/perf-reports/${report.id}`, headers: auth })
        expect(delRes.json().ok).toBe(true)
      } finally {
        server.close()
      }
    },
    240000,
  )

  it('执行不存在的用例返回 404', async () => {
    const res = await app.inject({ method: 'POST', url: '/api/perf-cases/not-exist/run', headers: auth })
    expect(res.statusCode).toBe(404)
  })

  it('无请求步骤的用例执行后生成 error 报告且错误可见', async () => {
    const project = await newProject('perf-empty-steps')
    const created = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/perf-cases`,
        headers: auth,
        payload: { name: '空步骤', threads: 1, steps: [] },
      })
    ).json()
    const runRes = await app.inject({ method: 'POST', url: `/api/perf-cases/${created.id}/run`, headers: auth })
    expect(runRes.statusCode).toBe(200)
    const report = runRes.json()
    expect(report.status).toBe('error')
    expect(report.message).toBeTruthy()
    expect(report.summary.samples).toBe(0)
    expect(report.series).toEqual([])
  })

  it('删除用例后历史报告仍可查看', async () => {
    const project = await newProject('perf-keep-report')
    const created = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/perf-cases`,
        headers: auth,
        payload: casePayload('将被删除', 'http://x'),
      })
    ).json()
    const report = await prisma.perfReport.create({
      data: {
        projectId: project.id,
        caseId: created.id,
        name: created.name,
        status: 'success',
        duration: 1000,
        summary: { samples: 10 },
      },
    })
    await app.inject({ method: 'DELETE', url: `/api/perf-cases/${created.id}`, headers: auth })

    const detail = await app.inject({ method: 'GET', url: `/api/perf-reports/${report.id}`, headers: auth })
    expect(detail.statusCode).toBe(200)
    expect(detail.json().summary.samples).toBe(10)
  })
})
