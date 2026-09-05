import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { buildApp } from './app.js'
import { prisma } from './db.js'
import { hashPassword } from './auth.js'

let app: FastifyInstance
let server: http.Server
let baseUrl: string
let auth: { authorization: string }

beforeAll(async () => {
  server = http.createServer((req, res) => {
    res.setHeader('Content-Type', 'application/json')
    if (req.method === 'POST' && req.url === '/login') {
      res.end(JSON.stringify({ token: 'tok123', userId: '1001' }))
      return
    }
    res.statusCode = 404
    res.end('{"error":"not found"}')
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
  app = buildApp()
  await app.ready()

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
  await prisma.testTaskRun.deleteMany()
  await prisma.testTask.deleteMany()
  await prisma.caseVersion.deleteMany()
  await prisma.debugRecord.deleteMany()
  await prisma.caseInfo.deleteMany()
  await prisma.module.deleteMany()
  await prisma.reportDetail.deleteMany()
  await prisma.report.deleteMany()
  await prisma.scenarioStep.deleteMany()
  await prisma.scenario.deleteMany()
  await prisma.apiCase.deleteMany()
  await prisma.apiDefinition.deleteMany()
  await prisma.environment.deleteMany()
  await prisma.project.deleteMany()
}

/** 生成一个请求 /login 的用例步骤 */
function loginStep(name: string, expectedStatus: string) {
  return {
    id: name,
    type: 'request',
    phase: 'test',
    name,
    enabled: true,
    method: 'POST',
    url: '/login',
    headers: [{ key: 'Content-Type', value: 'application/json' }],
    query: [],
    body: '{"username":"admin"}',
    assertions: [{ type: 'statusCode', expression: '', expected: expectedStatus, operator: 'eq' }],
    extracts: [],
  }
}

describe('测试任务与报告（第 5 期）', () => {
  it('串行执行多用例，全部通过 → 报告 PASS', async () => {
    await clean()

    const project = await prisma.project.create({ data: { name: 'task-demo' } })
    const env = await prisma.environment.create({
      data: { projectId: project.id, name: 'test', baseUrl, variables: [], headers: [] },
    })
    const c1 = await prisma.caseInfo.create({
      data: { projectId: project.id, name: '登录A', steps: [loginStep('login-a', '200')] as unknown as Prisma.InputJsonValue },
    })
    const c2 = await prisma.caseInfo.create({
      data: { projectId: project.id, name: '登录B', steps: [loginStep('login-b', '200')] as unknown as Prisma.InputJsonValue },
    })

    // 创建任务
    const createRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/test-tasks`,
      headers: auth,
      payload: { name: '登录回归', caseIds: [c1.id, c2.id], environmentId: env.id, executeMode: 'sequential' },
    })
    expect(createRes.statusCode).toBe(200)
    const task = createRes.json()
    expect(task.caseIds).toEqual([c1.id, c2.id])

    // 执行
    const runRes = await app.inject({ method: 'POST', url: `/api/test-tasks/${task.id}/run`, headers: auth })
    expect(runRes.statusCode).toBe(200)
    const run = runRes.json()
    expect(run.result).toBe('PASS')
    expect(run.summary).toEqual({ total: 2, passed: 2, failed: 0, error: 0, skipped: 0, result: 'PASS' })
    expect(run.details).toHaveLength(2)

    // 报告列表与详情
    const runsRes = await app.inject({ method: 'GET', url: `/api/test-tasks/${task.id}/runs`, headers: auth })
    expect(runsRes.json()).toHaveLength(1)
    const detailRes = await app.inject({ method: 'GET', url: `/api/test-task-runs/${run.id}`, headers: auth })
    expect(detailRes.json().summary.result).toBe('PASS')
  })

  it('断言失败 → 报告 FAIL；空用例任务执行返回 400', async () => {
    await clean()

    const project = await prisma.project.create({ data: { name: 'task-fail' } })
    const env = await prisma.environment.create({
      data: { projectId: project.id, name: 'test', baseUrl, variables: [], headers: [] },
    })
    // 期望 500，实际 200 → 断言失败
    const badCase = await prisma.caseInfo.create({
      data: { projectId: project.id, name: '失败用例', steps: [loginStep('fail', '500')] as unknown as Prisma.InputJsonValue },
    })

    const task = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/test-tasks`,
        headers: auth,
        payload: { name: '失败回归', caseIds: [badCase.id], environmentId: env.id },
      })
    ).json()

    const runRes = await app.inject({ method: 'POST', url: `/api/test-tasks/${task.id}/run`, headers: auth })
    const run = runRes.json()
    expect(run.result).toBe('FAIL')
    expect(run.summary.failed).toBe(1)
    expect(run.details[0].status).toBe('FAIL')

    // 空用例任务
    const emptyTask = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/test-tasks`,
        headers: auth,
        payload: { name: '空任务', caseIds: [] },
      })
    ).json()
    const emptyRun = await app.inject({ method: 'POST', url: `/api/test-tasks/${emptyTask.id}/run`, headers: auth })
    expect(emptyRun.statusCode).toBe(400)
  })

  it('任务 CRUD：列表/更新/软删除', async () => {
    await clean()

    const project = await prisma.project.create({ data: { name: 'task-crud' } })
    const task = (
      await app.inject({
        method: 'POST',
        url: `/api/projects/${project.id}/test-tasks`,
        headers: auth,
        payload: { name: '待改名', caseIds: [] },
      })
    ).json()

    const listRes = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/test-tasks`, headers: auth })
    expect(listRes.json()).toHaveLength(1)

    const updRes = await app.inject({
      method: 'PUT',
      url: `/api/test-tasks/${task.id}`,
      headers: auth,
      payload: { name: '已改名', retryCount: 2 },
    })
    expect(updRes.json().name).toBe('已改名')
    expect(updRes.json().retryCount).toBe(2)

    await app.inject({ method: 'DELETE', url: `/api/test-tasks/${task.id}`, headers: auth })
    const after = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/test-tasks`, headers: auth })
    expect(after.json()).toHaveLength(0)
  })
})
