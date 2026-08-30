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
  // 登录获取 token
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

describe('UI 测试用例 CRUD', () => {
  it('项目 → 新建 UI 用例 → 列表 → 更新 → 删除', async () => {
    // 建项目
    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'ui-demo' },
    })
    const project = projRes.json()

    const steps = [
      { action: 'open', target: 'https://example.com' },
      { action: 'assertTitle', value: 'Example' },
    ]

    // 新建
    const createRes = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/ui-tests`,
      headers: auth,
      payload: { name: '打开首页', baseUrl: 'https://example.com', steps },
    })
    expect(createRes.statusCode).toBe(200)
    const testCase = createRes.json()
    expect(testCase.steps).toHaveLength(2)

    // 列表
    const listRes = await app.inject({
      method: 'GET',
      url: `/api/projects/${project.id}/ui-tests`,
      headers: auth,
    })
    expect(listRes.json()).toHaveLength(1)

    // 更新
    const updateRes = await app.inject({
      method: 'PUT',
      url: `/api/ui-tests/${testCase.id}`,
      headers: auth,
      payload: { name: '打开首页-改', steps: [...steps, { action: 'click', target: 'a' }] },
    })
    expect(updateRes.json().name).toBe('打开首页-改')
    expect(updateRes.json().steps).toHaveLength(3)

    // 删除
    const delRes = await app.inject({
      method: 'DELETE',
      url: `/api/ui-tests/${testCase.id}`,
      headers: auth,
    })
    expect(delRes.statusCode).toBe(200)

    // 清理项目
    await prisma.project.delete({ where: { id: project.id } })
  })

  it('缺少 name 返回 400', async () => {
    const projRes = await app.inject({
      method: 'POST',
      url: '/api/projects',
      headers: auth,
      payload: { name: 'ui-demo-2' },
    })
    const project = projRes.json()
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/ui-tests`,
      headers: auth,
      payload: { steps: [] },
    })
    expect(res.statusCode).toBe(400)
    await prisma.project.delete({ where: { id: project.id } })
  })
})
