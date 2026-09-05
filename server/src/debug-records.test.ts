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
  server = http.createServer((_req, res) => {
    res.setHeader('Content-Type', 'application/json')
    res.end(JSON.stringify({ token: 'tok123' }))
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
  const login = await app.inject({ method: 'POST', url: '/api/auth/login', payload: { username: 'admin', password: 'admin@123' } })
  auth = { authorization: `Bearer ${login.json().token}` }
})

afterAll(async () => {
  await app.close()
  await new Promise<void>((resolve) => server.close(() => resolve()))
})

async function clean() {
  await prisma.debugRecord.deleteMany()
  await prisma.caseReview.deleteMany()
  await prisma.caseVersion.deleteMany()
  await prisma.caseInfo.deleteMany()
  await prisma.module.deleteMany()
  await prisma.environment.deleteMany()
  await prisma.apiDefinition.deleteMany()
  await prisma.project.deleteMany()
}

describe('调试记录：一键重放 + 对比（模块 7）', () => {
  it('重放生成新记录，对比返回步骤/变量 diff', async () => {
    await clean()
    const project = await prisma.project.create({ data: { name: 'dbg' } })
    const env = await prisma.environment.create({
      data: { projectId: project.id, name: 'test', baseUrl, variables: [], headers: [] },
    })
    const step = {
      id: 's1', type: 'request', phase: 'test', name: '登录', enabled: true,
      method: 'GET', url: '/login', headers: [], query: [],
      assertions: [{ type: 'statusCode', expression: '', expected: '200', operator: 'eq' }],
      extracts: [{ name: 'token', type: 'jsonPath', expression: '$.token' }],
    }
    const c = await prisma.caseInfo.create({
      data: { projectId: project.id, name: '登录', steps: [step] as unknown as Prisma.InputJsonValue },
    })

    // 两次调试
    const d1 = await app.inject({ method: 'POST', url: `/api/case-info/${c.id}/debug`, headers: auth, payload: { environmentId: env.id } })
    const d2 = await app.inject({ method: 'POST', url: `/api/case-info/${c.id}/debug`, headers: auth, payload: { environmentId: env.id } })
    expect(d1.json().status).toBe('success')
    expect(d2.json().status).toBe('success')

    // 列表应有 2 条
    const list = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/debug-records`, headers: auth })
    expect(list.json()).toHaveLength(2)
    const [r1, r2] = list.json()

    // 重放第一条 → 生成新记录
    const replay = await app.inject({ method: 'POST', url: `/api/debug-records/${r1.id}/replay`, headers: auth })
    expect(replay.statusCode).toBe(200)
    expect(replay.json().status).toBe('success')
    const listAfter = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/debug-records`, headers: auth })
    expect(listAfter.json()).toHaveLength(3)

    // 对比两条记录
    const cmp = await app.inject({ method: 'GET', url: `/api/debug-records/compare?a=${r1.id}&b=${r2.id}`, headers: auth })
    expect(cmp.statusCode).toBe(200)
    const body = cmp.json()
    expect(body.diff.stepDiffs).toHaveLength(1)
    expect(body.diff.stepDiffs[0].changed).toBe(false)
  })
})
