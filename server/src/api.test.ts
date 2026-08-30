import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import type { FastifyInstance } from 'fastify'
import { buildApp } from './app.js'
import { prisma } from './db.js'

let app: FastifyInstance
let server: http.Server
let baseUrl: string

beforeAll(async () => {
  server = http.createServer((req, res) => {
    if (req.method === 'POST' && req.url === '/login') {
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ token: 'tok123' }))
      return
    }
    res.statusCode = 404
    res.end('not found')
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
  app = buildApp()
  await app.ready()
})

afterAll(async () => {
  await app.close()
  await new Promise<void>((resolve) => server.close(() => resolve()))
})

async function clean() {
  await prisma.reportDetail.deleteMany()
  await prisma.report.deleteMany()
  await prisma.scenarioStep.deleteMany()
  await prisma.scenario.deleteMany()
  await prisma.apiCase.deleteMany()
  await prisma.apiDefinition.deleteMany()
  await prisma.environment.deleteMany()
  await prisma.project.deleteMany()
}

describe('HTTP API 完整闭环', () => {
  it('项目→环境→接口→用例→场景→步骤→执行→报告', async () => {
    await clean()

    // 1. 创建项目
    const projRes = await app.inject({ method: 'POST', url: '/api/projects', payload: { name: 'demo' } })
    expect(projRes.statusCode).toBe(200)
    const project = projRes.json()

    // 2. 创建环境
    const envRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/environments`,
      payload: { name: 'test', baseUrl },
    })
    expect(envRes.statusCode).toBe(200)
    const env = envRes.json()

    // 3. 创建接口
    const apiRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis`,
      payload: { name: 'login', method: 'POST', path: '/login' },
    })
    expect(apiRes.statusCode).toBe(200)
    const api = apiRes.json()

    // 4. 创建用例（断言 + 提取）
    const caseRes = await app.inject({
      method: 'POST',
      url: `/api/apis/${api.id}/cases`,
      payload: {
        name: 'login ok',
        assertions: [{ type: 'statusCode', expression: '', expected: '200' }],
        extracts: [{ name: 'token', type: 'jsonPath', expression: '$.token' }],
      },
    })
    expect(caseRes.statusCode).toBe(200)
    const apiCase = caseRes.json()

    // 5. 创建场景
    const scnRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/scenarios`,
      payload: { name: 'flow' },
    })
    expect(scnRes.statusCode).toBe(200)
    const scenario = scnRes.json()

    // 6. 设置步骤
    const stepRes = await app.inject({
      method: 'PUT',
      url: `/api/scenarios/${scenario.id}/steps`,
      payload: [{ order: 0, apiCaseId: apiCase.id }],
    })
    expect(stepRes.statusCode).toBe(200)

    // 7. 执行
    const runRes = await app.inject({
      method: 'POST',
      url: `/api/scenarios/${scenario.id}/run`,
      payload: { environmentId: env.id },
    })
    expect(runRes.statusCode).toBe(200)
    const report = runRes.json()
    expect(report.status).toBe('PASS')
    expect(report.details).toHaveLength(1)
    expect(report.details[0].status).toBe('PASS')

    // 8. 查报告列表
    const reportsRes = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/reports` })
    expect(reportsRes.statusCode).toBe(200)
    expect(reportsRes.json()).toHaveLength(1)
  })

  it('缺少 environmentId 时返回 400', async () => {
    const runRes = await app.inject({
      method: 'POST',
      url: '/api/scenarios/any-id/run',
      payload: {},
    })
    expect(runRes.statusCode).toBe(400)
    expect(runRes.json().error).toContain('environmentId')
  })
})
