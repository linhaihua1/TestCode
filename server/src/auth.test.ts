import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import type { FastifyInstance } from 'fastify'
import { buildApp } from './app.js'
import { prisma } from './db.js'
import { hashPassword } from './auth.js'

let app: FastifyInstance

beforeAll(async () => {
  app = buildApp()
  await app.ready()
  // 确保默认管理员存在
  await prisma.user.upsert({
    where: { username: 'admin' },
    update: {},
    create: { username: 'admin', passwordHash: hashPassword('admin@123'), role: 'admin' },
  })
})

afterAll(async () => {
  await app.close()
})

async function login(username: string, password: string) {
  return app.inject({
    method: 'POST',
    url: '/api/auth/login',
    payload: { username, password },
  })
}

describe('登录认证', () => {
  it('正确密码登录成功，返回 token', async () => {
    const res = await login('admin', 'admin@123')
    expect(res.statusCode).toBe(200)
    expect(res.json().token).toBeTruthy()
    expect(res.json().user.username).toBe('admin')
  })

  it('错误密码登录失败返回 401', async () => {
    const res = await login('admin', 'wrong-password')
    expect(res.statusCode).toBe(401)
  })

  it('未登录访问业务接口返回 401', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/projects' })
    expect(res.statusCode).toBe(401)
  })

  it('携带有效 token 访问业务接口成功', async () => {
    const loginRes = await login('admin', 'admin@123')
    const token = loginRes.json().token
    const res = await app.inject({
      method: 'GET',
      url: '/api/projects',
      headers: { authorization: `Bearer ${token}` },
    })
    expect(res.statusCode).toBe(200)
  })

  it('无效 token 访问业务接口返回 401', async () => {
    const res = await app.inject({
      method: 'GET',
      url: '/api/projects',
      headers: { authorization: 'Bearer invalid-token' },
    })
    expect(res.statusCode).toBe(401)
  })
})

describe('用户管理', () => {
  it('新增 → 列表 → 编辑 → 改密码 → 删除 完整流程', async () => {
    const loginRes = await login('admin', 'admin@123')
    const token = loginRes.json().token
    const auth = { authorization: `Bearer ${token}` }

    // 新增
    const createRes = await app.inject({
      method: 'POST',
      url: '/api/users',
      headers: auth,
      payload: { username: 'test1', password: 'test123' },
    })
    expect(createRes.statusCode).toBe(200)
    const userId = createRes.json().id

    // 列表（不含密码哈希）
    const listRes = await app.inject({ method: 'GET', url: '/api/users', headers: auth })
    expect(listRes.statusCode).toBe(200)
    expect(listRes.json().some((u: { username: string }) => u.username === 'test1')).toBe(true)
    expect(listRes.json()[0].passwordHash).toBeUndefined()

    // 编辑角色
    const editRes = await app.inject({
      method: 'PUT',
      url: `/api/users/${userId}`,
      headers: auth,
      payload: { role: 'viewer' },
    })
    expect(editRes.json().role).toBe('viewer')

    // 改密码
    const pwdRes = await app.inject({
      method: 'PUT',
      url: `/api/users/${userId}/password`,
      headers: auth,
      payload: { newPassword: 'newpass123' },
    })
    expect(pwdRes.statusCode).toBe(200)

    // 用新密码登录
    const reLogin = await login('test1', 'newpass123')
    expect(reLogin.statusCode).toBe(200)

    // 删除
    const delRes = await app.inject({
      method: 'DELETE',
      url: `/api/users/${userId}`,
      headers: auth,
    })
    expect(delRes.statusCode).toBe(200)
  })

  it('重复用户名新增返回 409', async () => {
    const loginRes = await login('admin', 'admin@123')
    const token = loginRes.json().token
    const res = await app.inject({
      method: 'POST',
      url: '/api/users',
      headers: { authorization: `Bearer ${token}` },
      payload: { username: 'admin', password: 'x' },
    })
    expect(res.statusCode).toBe(409)
  })
})
