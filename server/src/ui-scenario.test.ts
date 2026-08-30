import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import type { FastifyInstance } from 'fastify'
import { buildApp } from './app.js'
import { prisma } from './db.js'
import { hashPassword } from './auth.js'

let app: FastifyInstance
let auth: { authorization: string }

beforeAll(async () => {
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
})

describe('UI 执行场景 CRUD', () => {
  it('项目 → 建 UI 用例 → 建场景 → 更新步骤 → 删除', async () => {
    // 建项目
    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'ui-scn-demo' },
    })
    const project = projRes.json()

    // 建 UI 用例
    const tcRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/ui-tests`,
      headers: auth,
      payload: {
        name: '打开首页',
        baseUrl: 'https://example.com',
        steps: [{ action: 'open', target: '/' }],
      },
    })
    const testCase = tcRes.json()

    // 建场景
    const scnRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/ui-scenarios`,
      headers: auth,
      payload: { name: 'UI 业务流程' },
    })
    expect(scnRes.statusCode).toBe(200)
    const scenario = scnRes.json()

    // 更新步骤（绑定 UI 用例）
    const stepRes = await app.inject({
      method: 'PUT',
      url: `/api/ui-scenarios/${scenario.id}/steps`,
      headers: auth,
      payload: [{ order: 0, uiTestCaseId: testCase.id }],
    })
    expect(stepRes.statusCode).toBe(200)

    // 场景详情含步骤与用例
    const detailRes = await app.inject({
      method: 'GET',
      url: `/api/ui-scenarios/${scenario.id}`,
      headers: auth,
    })
    const detail = detailRes.json()
    expect(detail.steps).toHaveLength(1)
    expect(detail.steps[0].uiTestCase.name).toBe('打开首页')

    // 清理
    await prisma.project.delete({ where: { id: project.id } })
  })

  it('缺少 name 返回 400', async () => {
    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'ui-scn-demo-2' },
    })
    const project = projRes.json()
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/ui-scenarios`,
      headers: auth,
      payload: {},
    })
    expect(res.statusCode).toBe(400)
    await prisma.project.delete({ where: { id: project.id } })
  })
})
