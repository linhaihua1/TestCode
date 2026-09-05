import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import type { FastifyInstance } from 'fastify'
import { buildApp } from './app.js'
import { prisma } from './db.js'
import { hashPassword, signToken } from './auth.js'

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

async function clean() {
  await prisma.caseReview.deleteMany()
  await prisma.caseVersion.deleteMany()
  await prisma.debugRecord.deleteMany()
  await prisma.caseInfo.deleteMany()
  await prisma.module.deleteMany()
  await prisma.auditLog.deleteMany()
  await prisma.testTaskRun.deleteMany()
  await prisma.testTask.deleteMany()
  await prisma.apiDefinition.deleteMany()
  await prisma.environment.deleteMany()
  await prisma.project.deleteMany()
  await prisma.user.deleteMany({ where: { username: { not: 'admin' } } })
}

async function loginToken(username: string, password: string): Promise<string> {
  const res = await app.inject({ method: 'POST', url: '/api/auth/login', payload: { username, password } })
  return res.json().token
}

describe('第 6 期：评审 + 审计 + 权限', () => {
  it('评审流程：提交 → 通过/驳回，评审记录留存', async () => {
    await clean()
    const project = await prisma.project.create({ data: { name: 'review-demo' } })
    const c = await prisma.caseInfo.create({ data: { projectId: project.id, name: '待评审用例' } })

    // 提交评审 → pending
    const submit = await app.inject({
      method: 'POST', url: `/api/case-info/${c.id}/review`, headers: auth,
      payload: { action: 'submit', comment: '请评审' },
    })
    expect(submit.statusCode).toBe(200)
    expect((await prisma.caseInfo.findUnique({ where: { id: c.id } }))!.status).toBe('pending')

    // 通过 → passed
    const approve = await app.inject({
      method: 'POST', url: `/api/case-info/${c.id}/review`, headers: auth,
      payload: { action: 'approve', comment: '同意' },
    })
    expect(approve.statusCode).toBe(200)
    expect((await prisma.caseInfo.findUnique({ where: { id: c.id } }))!.status).toBe('passed')

    // 记录列表应有 2 条
    const reviewsRes = await app.inject({ method: 'GET', url: `/api/case-info/${c.id}/reviews`, headers: auth })
    expect(reviewsRes.json()).toHaveLength(2)

    // 驳回 → rejected
    await app.inject({ method: 'POST', url: `/api/case-info/${c.id}/review`, headers: auth, payload: { action: 'reject', comment: '需修改' } })
    expect((await prisma.caseInfo.findUnique({ where: { id: c.id } }))!.status).toBe('rejected')
  })

  it('审计日志：CRUD 操作记录可查询，含变更快照与用户', async () => {
    await clean()
    const project = await prisma.project.create({ data: { name: 'audit-demo' } })
    await app.inject({
      method: 'POST', url: `/api/projects/${project.id}/apis`, headers: auth,
      payload: { name: 'a', method: 'GET', path: '/a' },
    })

    const logsRes = await app.inject({ method: 'GET', url: '/api/audit-logs', headers: auth })
    const logs = logsRes.json().logs
    expect(logs.some((l: { entityType: string; action: string }) => l.entityType === 'api' && l.action === 'create')).toBe(true)

    const metaRes = await app.inject({ method: 'GET', url: '/api/audit-logs/meta', headers: auth })
    expect(metaRes.json().entityTypes).toContain('api')
  })

  it('权限：viewer 只读、member 可写、非管理员不能管理用户', async () => {
    await clean()
    await app.inject({ method: 'POST', url: '/api/users', headers: auth, payload: { username: 'viewer1', password: '123456', role: 'viewer' } })
    await app.inject({ method: 'POST', url: '/api/users', headers: auth, payload: { username: 'member1', password: '123456', role: 'member' } })

    const viewerAuth = { authorization: `Bearer ${await loginToken('viewer1', '123456')}` }
    const memberAuth = { authorization: `Bearer ${await loginToken('member1', '123456')}` }

    const project = await prisma.project.create({ data: { name: 'perm-demo' } })

    // viewer 读 OK
    const readRes = await app.inject({ method: 'GET', url: `/api/projects/${project.id}/cases`, headers: viewerAuth })
    expect(readRes.statusCode).toBe(200)
    // viewer 写 403
    const writeRes = await app.inject({ method: 'POST', url: `/api/projects/${project.id}/cases`, headers: viewerAuth, payload: { name: 'x' } })
    expect(writeRes.statusCode).toBe(403)
    // viewer 查看用户列表 403（系统管理仅管理员）
    const viewerUsers = await app.inject({ method: 'GET', url: '/api/users', headers: viewerAuth })
    expect(viewerUsers.statusCode).toBe(403)

    // member 写业务 OK
    const memberWrite = await app.inject({ method: 'POST', url: `/api/projects/${project.id}/cases`, headers: memberAuth, payload: { name: 'm' } })
    expect(memberWrite.statusCode).toBe(200)
    // member 创建环境 OK（环境配置属业务）
    const memberEnv = await app.inject({ method: 'POST', url: `/api/projects/${project.id}/environments`, headers: memberAuth, payload: { name: 'dev' } })
    expect(memberEnv.statusCode).toBe(200)

    // member 管理用户 403
    const memberUsers = await app.inject({ method: 'POST', url: '/api/users', headers: memberAuth, payload: { username: 'u2', password: '123456' } })
    expect(memberUsers.statusCode).toBe(403)
    // member 新建项目 403（项目管理仅管理员）
    const memberProject = await app.inject({ method: 'POST', url: '/api/projects', headers: memberAuth, payload: { name: 'p2' } })
    expect(memberProject.statusCode).toBe(403)
  })

  it('旧 token（无 role）的管理员仍能写操作（从 DB 回读角色）', async () => {
    await clean()
    const admin = await prisma.user.findUnique({ where: { username: 'admin' } })
    // 用不含 role 的旧版 token 模拟历史登录态
    const staleToken = signToken({ userId: admin!.id, username: admin!.username })
    const staleAuth = { authorization: `Bearer ${staleToken}` }

    const project = await prisma.project.create({ data: { name: 'stale-admin' } })
    const res = await app.inject({
      method: 'POST',
      url: `/api/projects/${project.id}/modules`,
      headers: staleAuth,
      payload: { name: '模块A' },
    })
    expect(res.statusCode).toBe(200)
  })
})
