import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { prisma } from '../db.js'
import { runScenario } from './runner.js'
import type { Assertion } from './types.js'

let server: http.Server
let baseUrl: string

beforeAll(async () => {
  server = http.createServer((req, res) => {
    if (req.method === 'POST' && req.url === '/login') {
      res.setHeader('Content-Type', 'application/json')
      res.end(JSON.stringify({ token: 'tok123', userId: '42' }))
      return
    }
    if (req.method === 'GET' && req.url === '/users/42') {
      if (req.headers['authorization'] === 'Bearer tok123') {
        res.setHeader('Content-Type', 'application/json')
        res.end(JSON.stringify({ name: 'alice' }))
      } else {
        res.statusCode = 401
        res.end('unauthorized')
      }
      return
    }
    res.statusCode = 404
    res.end('not found')
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseUrl = `http://127.0.0.1:${(server.address() as AddressInfo).port}`
})

afterAll(async () => {
  await new Promise<void>((resolve) => server.close(() => resolve()))
  await prisma.$disconnect()
})

async function createScenario(loginAssertions: Assertion[], usersAssertions: Assertion[]) {
  // 清理旧数据
  await prisma.reportDetail.deleteMany()
  await prisma.report.deleteMany()
  await prisma.scenarioStep.deleteMany()
  await prisma.scenario.deleteMany()
  await prisma.apiCase.deleteMany()
  await prisma.apiDefinition.deleteMany()
  await prisma.environment.deleteMany()
  await prisma.project.deleteMany()

  const project = await prisma.project.create({ data: { name: 'demo' } })
  const env = await prisma.environment.create({
    data: { projectId: project.id, name: 'test', baseUrl, variables: [], headers: [] },
  })
  const loginApi = await prisma.apiDefinition.create({
    data: {
      projectId: project.id,
      name: 'login',
      method: 'POST',
      path: '/login',
      headers: [],
      query: [],
      body: null,
    },
  })
  const usersApi = await prisma.apiDefinition.create({
    data: {
      projectId: project.id,
      name: 'getUser',
      method: 'GET',
      path: '/users/${userId}',
      headers: [{ key: 'Authorization', value: 'Bearer ${token}' }],
      query: [],
      body: null,
    },
  })
  const loginCase = await prisma.apiCase.create({
    data: {
      apiId: loginApi.id,
      name: 'login case',
      assertions: loginAssertions,
      extracts: [
        { name: 'token', type: 'jsonPath', expression: '$.token' },
        { name: 'userId', type: 'jsonPath', expression: '$.userId' },
      ],
    },
  })
  const usersCase = await prisma.apiCase.create({
    data: { apiId: usersApi.id, name: 'user case', assertions: usersAssertions, extracts: [] },
  })
  const scenario = await prisma.scenario.create({ data: { projectId: project.id, name: 'flow' } })
  await prisma.scenarioStep.create({
    data: { scenarioId: scenario.id, order: 0, apiCaseId: loginCase.id, assertions: [], extracts: [] },
  })
  await prisma.scenarioStep.create({
    data: { scenarioId: scenario.id, order: 1, apiCaseId: usersCase.id, assertions: [], extracts: [] },
  })
  return { scenarioId: scenario.id, envId: env.id }
}

describe('runScenario', () => {
  it('完整流程：变量替换 + 提取传递 + 断言全部通过', async () => {
    const { scenarioId, envId } = await createScenario(
      [{ type: 'statusCode', expression: '', expected: '200' }],
      [
        { type: 'statusCode', expression: '', expected: '200' },
        { type: 'jsonPath', expression: '$.name', expected: 'alice' },
      ],
    )

    const { report, context } = await runScenario({ scenarioId, environmentId: envId })

    expect(report.status).toBe('PASS')
    expect(report.details).toHaveLength(2)
    expect(report.details.every((d) => d.status === 'PASS')).toBe(true)
    // 提取的变量已进入上下文
    expect(context.token).toBe('tok123')
    expect(context.userId).toBe('42')
  })

  it('断言失败 → report.status FAIL', async () => {
    const { scenarioId, envId } = await createScenario(
      [{ type: 'statusCode', expression: '', expected: '200' }],
      [{ type: 'jsonPath', expression: '$.name', expected: 'bob' }],
    )

    const { report } = await runScenario({ scenarioId, environmentId: envId })

    expect(report.status).toBe('FAIL')
    expect(report.details[1].status).toBe('FAIL')
    expect(report.details[1].assertions[0].passed).toBe(false)
  })
})
