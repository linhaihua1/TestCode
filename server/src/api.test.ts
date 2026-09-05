import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import type { FastifyInstance } from 'fastify'
import { buildApp } from './app.js'
import { prisma } from './db.js'
import { hashPassword } from './auth.js'

let app: FastifyInstance
let server: http.Server
let baseUrl: string
let auth: { authorization: string }

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

  // 登录获取 token，后续业务接口请求需携带
  await prisma.user.upsert({
    where: { username: 'admin' },
    update: {},
    create: { username: 'admin', passwordHash: hashPassword('admin@123'), role: 'admin' },
  })
  const loginRes = await app.inject({
    method: 'POST',
    url: '/api/auth/login',
    payload: { username: 'admin', password: 'admin@123' },
  })
  auth = { authorization: `Bearer ${loginRes.json().token}` }
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
    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'demo' },
    })
    expect(projRes.statusCode).toBe(200)
    const project = projRes.json()

    // 2. 创建环境
    const envRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/environments`,
      headers: auth,
      payload: { name: 'test', baseUrl },
    })
    expect(envRes.statusCode).toBe(200)
    const env = envRes.json()

    // 3. 创建接口
    const apiRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis`,
      headers: auth,
      payload: { name: 'login', method: 'POST', path: '/login' },
    })
    expect(apiRes.statusCode).toBe(200)
    const api = apiRes.json()

    // 4. 创建用例（断言 + 提取）
    const caseRes = await app.inject({
      method: 'POST',
      url: `/api/apis/${api.id}/cases`,
      headers: auth,
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
      headers: auth,
      payload: { name: 'flow' },
    })
    expect(scnRes.statusCode).toBe(200)
    const scenario = scnRes.json()

    // 6. 设置步骤
    const stepRes = await app.inject({
      method: 'PUT',
      url: `/api/scenarios/${scenario.id}/steps`,
      headers: auth,
      payload: [{ order: 0, apiCaseId: apiCase.id }],
    })
    expect(stepRes.statusCode).toBe(200)

    // 7. 执行
    const runRes = await app.inject({
      method: 'POST',
      url: `/api/scenarios/${scenario.id}/run`,
      headers: auth,
      payload: { environmentId: env.id },
    })
    expect(runRes.statusCode).toBe(200)
    const report = runRes.json()
    expect(report.status).toBe('PASS')
    expect(report.details).toHaveLength(1)
    expect(report.details[0].status).toBe('PASS')

    // 8. 查报告列表
    const reportsRes = await app.inject({
      method: 'GET',
      url: `/api/projects/${project.id}/reports`,
      headers: auth,
    })
    expect(reportsRes.statusCode).toBe(200)
    expect(reportsRes.json()).toHaveLength(1)
  })

  it('缺少 environmentId 时返回 400', async () => {
    const runRes = await app.inject({
      method: 'POST',
      url: '/api/scenarios/any-id/run',
      headers: auth,
      payload: {},
    })
    expect(runRes.statusCode).toBe(400)
    expect(runRes.json().error).toContain('environmentId')
  })

  it('PUT 场景更新时忽略多余字段不报错', async () => {
    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'put-test-project' },
    })
    expect(projRes.statusCode).toBe(200)
    const project = projRes.json()

    const scnRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/scenarios`,
      headers: auth,
      payload: { name: 'flow-update-test' },
    })
    expect(scnRes.statusCode).toBe(200)
    const scenario = scnRes.json()

    // 传入不存在的字段（如 steps、projectId），应被忽略而非报错
    const updateRes = await app.inject({
      method: 'PUT',
      url: `/api/scenarios/${scenario.id}`,
      headers: auth,
      payload: { name: 'updated name', steps: [], projectId: 'should-be-ignored' },
    })
    expect(updateRes.statusCode).toBe(200)
    expect(updateRes.json().name).toBe('updated name')

    await prisma.project.delete({ where: { id: project.id } })
  })
})

describe('接口管理（第 4 期）：Swagger 导入 + Mock', () => {
  it('Swagger 导入：按 tag 建模块、创建接口、重复导入去重', async () => {
    await clean()

    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'swagger-project' },
    })
    expect(projRes.statusCode).toBe(200)
    const project = projRes.json()

    const doc = {
      openapi: '3.0.0',
      info: { title: 't', version: '1' },
      paths: {
        '/order': {
          get: { summary: '查询订单', tags: ['订单'] },
          post: { summary: '创建订单', tags: ['订单'] },
        },
        '/product/{id}': {
          get: { summary: '查询商品', tags: ['商品'] },
        },
      },
    }

    const importRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis/import-swagger`,
      headers: auth,
      payload: { content: doc },
    })
    expect(importRes.statusCode).toBe(200)
    expect(importRes.json()).toEqual({ created: 3, skipped: 0 })

    // 接口应带上 tags 与 moduleId
    const apis = await app.inject({
      method: 'GET',
      url: `/api/projects/${project.id}/apis`,
      headers: auth,
    })
    const imported = apis.json().filter((a: { path: string }) => a.path.startsWith('/order') || a.path.startsWith('/product'))
    expect(imported).toHaveLength(3)
    for (const a of imported) {
      expect(Array.isArray(a.tags)).toBe(true)
      expect(a.tags.length).toBeGreaterThan(0)
      expect(a.moduleId).toBeTruthy()
    }

    // 按 tag 生成 api 类型的模块
    const mods = await app.inject({
      method: 'GET',
      url: `/api/projects/${project.id}/modules`,
      headers: auth,
    })
    const apiMods = mods.json().filter((m: { type: string }) => m.type === 'api')
    expect(apiMods.map((m: { name: string }) => m.name).sort()).toEqual(['商品', '订单'])

    // 重复导入应全部跳过
    const reimportRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis/import-swagger`,
      headers: auth,
      payload: { content: doc },
    })
    expect(reimportRes.json()).toEqual({ created: 0, skipped: 3 })
  })

  it('Mock：启用后返回模拟响应，未启用返回 404，公开访问无需鉴权', async () => {
    await clean()

    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'mock-project' },
    })
    const project = projRes.json()

    // 创建启用 Mock 的接口，验证 mockEnabled/mockResponse/tags 持久化
    const apiRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis`,
      headers: auth,
      payload: {
        name: 'mock api', method: 'GET', path: '/mock-me',
        mockEnabled: true, mockResponse: '{"code":0,"data":{"ok":true}}', tags: ['mock'],
      },
    })
    expect(apiRes.statusCode).toBe(200)
    const api = apiRes.json()
    expect(api.mockEnabled).toBe(true)
    expect(api.mockResponse).toBe('{"code":0,"data":{"ok":true}}')
    expect(api.tags).toEqual(['mock'])

    // 公开访问（不携带鉴权头）应返回模拟响应
    const mockRes = await app.inject({ method: 'GET', url: `/mock/${api.id}` })
    expect(mockRes.statusCode).toBe(200)
    expect(mockRes.json()).toEqual({ code: 0, data: { ok: true } })

    // 未启用 Mock 的接口返回 404
    const plainRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis`,
      headers: auth,
      payload: { name: 'plain api', method: 'GET', path: '/plain' },
    })
    const plain = plainRes.json()
    expect(plain.mockEnabled).toBe(false)
    const plainMockRes = await app.inject({ method: 'GET', url: `/mock/${plain.id}` })
    expect(plainMockRes.statusCode).toBe(404)
  })

  it('批量导入（Excel 模板）：建模块、持久化请求头/查询参数/Mock、去重', async () => {
    await clean()

    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'excel-project' },
    })
    const project = projRes.json()

    const items = [
      {
        name: '登录', method: 'POST', path: '/login',
        headers: [{ key: 'Content-Type', value: 'application/json' }],
        query: [], body: '{"username":"admin"}', description: '登录', module: '用户模块',
      },
      {
        name: '查询用户', method: 'GET', path: '/users/${userId}',
        headers: [], query: [{ key: 'page', value: '1' }],
        module: '用户模块', mockEnabled: true, mockResponse: '{"code":0}',
      },
    ]

    const importRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis/import-batch`,
      headers: auth,
      payload: { items },
    })
    expect(importRes.statusCode).toBe(200)
    expect(importRes.json()).toEqual({ created: 2, skipped: 0 })

    // 字段持久化
    const apis = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/apis`, headers: auth })
    const login = apis.json().find((a: { path: string }) => a.path === '/login')
    const user = apis.json().find((a: { path: string }) => a.path === '/users/${userId}')
    expect(login.headers).toEqual([{ key: 'Content-Type', value: 'application/json' }])
    expect(login.tags).toEqual(['用户模块'])
    expect(user.query).toEqual([{ key: 'page', value: '1' }])
    expect(user.mockEnabled).toBe(true)

    // 按模块名生成 api 类型模块
    const mods = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/modules`, headers: auth })
    expect(mods.json().filter((m: { type: string }) => m.type === 'api')).toHaveLength(1)

    // 重复导入去重
    const reimportRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/apis/import-batch`,
      headers: auth,
      payload: { items },
    })
    expect(reimportRes.json()).toEqual({ created: 0, skipped: 2 })
  })
})
