import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { prisma } from './db.js'
import { runCase } from './engine/runner.js'

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

describe('runCase 多步骤用例', () => {
  it('多步骤用例：登录 → 查询用户（变量传递）', async () => {
    await clean()

    const project = await prisma.project.create({ data: { name: 'case-demo' } })
    const env = await prisma.environment.create({
      data: { projectId: project.id, name: 'test', baseUrl, variables: [], headers: [] },
    })
    const loginApi = await prisma.apiDefinition.create({
      data: { projectId: project.id, name: 'login', method: 'POST', path: '/login', headers: [], query: [], body: null },
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

    // 多步骤用例：登录 + 查询用户，变量在步骤间传递
    const apiCase = await prisma.apiCase.create({
      data: {
        apiId: loginApi.id,
        name: '登录并查用户',
        assertions: [],
        extracts: [],
        stepDefs: [
          {
            apiId: loginApi.id,
            name: '登录',
            assertions: [{ type: 'statusCode', expression: '', expected: '200' }],
            extracts: [
              { name: 'token', type: 'jsonPath', expression: '$.token' },
              { name: 'userId', type: 'jsonPath', expression: '$.userId' },
            ],
          },
          {
            apiId: usersApi.id,
            name: '查询用户',
            assertions: [
              { type: 'statusCode', expression: '', expected: '200' },
              { type: 'jsonPath', expression: '$.name', expected: 'alice' },
            ],
            extracts: [],
          },
        ],
      },
    })

    const { report, context } = await runCase(apiCase.id, env.id)

    expect(report.status).toBe('PASS')
    expect(report.details).toHaveLength(2)
    expect(report.details.every((d) => d.status === 'PASS')).toBe(true)
    // 变量已传递到上下文
    expect(context.token).toBe('tok123')
    expect(context.userId).toBe('42')
  })

  it('单接口用例（无 stepDefs）向后兼容', async () => {
    await clean()

    const project = await prisma.project.create({ data: { name: 'case-demo' } })
    const env = await prisma.environment.create({
      data: { projectId: project.id, name: 'test', baseUrl, variables: [], headers: [] },
    })
    const loginApi = await prisma.apiDefinition.create({
      data: { projectId: project.id, name: 'login', method: 'POST', path: '/login', headers: [], query: [], body: null },
    })
    const apiCase = await prisma.apiCase.create({
      data: {
        apiId: loginApi.id,
        name: '登录成功',
        assertions: [{ type: 'statusCode', expression: '', expected: '200' }],
        extracts: [{ name: 'token', type: 'jsonPath', expression: '$.token' }],
        stepDefs: [],
      },
    })

    const { report } = await runCase(apiCase.id, env.id)
    expect(report.status).toBe('PASS')
    expect(report.details).toHaveLength(1)
  })
})
