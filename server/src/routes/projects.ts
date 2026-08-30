import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'

/**
 * 项目与环境相关的 REST 路由：
 * - 项目（Project）的增删改查（列表 / 新建 / 详情 / 更新 / 删除）
 * - 环境（Environment）的增删改查（列表 / 新建 / 更新 / 删除）
 */
interface ProjectBody {
  name: string // 项目名称
  description?: string // 项目描述（可选）
}

interface EnvironmentBody {
  name: string // 环境名称
  baseUrl?: string // 环境基础地址（可选）
  variables?: Prisma.InputJsonValue // 环境变量（可选，JSON）
  headers?: Prisma.InputJsonValue // 环境请求头（可选，JSON）
}

export async function projectRoutes(app: FastifyInstance) {
  // ---------- Project ----------
  // 获取项目列表
  app.get('/api/projects', async () => {
    return prisma.project.findMany({ orderBy: { createdAt: 'desc' } })
  })

  // 新建项目
  app.post('/api/projects', async (req, reply) => {
    const body = req.body as ProjectBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' }) // 项目名称必填校验
    return prisma.project.create({ data: { name: body.name, description: body.description } })
  })

  // 获取项目详情
  app.get('/api/projects/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const project = await prisma.project.findUnique({ where: { id } })
    if (!project) return reply.code(404).send({ error: '项目不存在' }) // 项目不存在返回 404
    return project
  })

  // 更新项目
  app.put('/api/projects/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ProjectBody
    const project = await prisma.project.findUnique({ where: { id } })
    if (!project) return reply.code(404).send({ error: '项目不存在' }) // 项目不存在返回 404
    return prisma.project.update({ where: { id }, data: body })
  })

  // 删除项目
  app.delete('/api/projects/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const project = await prisma.project.findUnique({ where: { id } })
    if (!project) return reply.code(404).send({ error: '项目不存在' }) // 项目不存在返回 404
    await prisma.project.delete({ where: { id } })
    return { ok: true }
  })

  // ---------- Environment ----------
  // 获取项目下的环境列表
  app.get('/api/projects/:projectId/environments', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.environment.findMany({
      where: { projectId },
      orderBy: { createdAt: 'asc' },
    })
  })

  // 在项目下新建环境
  app.post('/api/projects/:projectId/environments', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as EnvironmentBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' }) // 环境名称必填校验
    return prisma.environment.create({
      data: {
        projectId,
        name: body.name,
        baseUrl: body.baseUrl,
        variables: body.variables ?? [], // 变量缺省为空数组
        headers: body.headers ?? [], // 请求头缺省为空数组
      },
    })
  })

  // 更新环境
  app.put('/api/environments/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as EnvironmentBody
    const env = await prisma.environment.findUnique({ where: { id } })
    if (!env) return reply.code(404).send({ error: '环境不存在' }) // 环境不存在返回 404
    return prisma.environment.update({ where: { id }, data: body })
  })

  // 删除环境
  app.delete('/api/environments/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const env = await prisma.environment.findUnique({ where: { id } })
    if (!env) return reply.code(404).send({ error: '环境不存在' }) // 环境不存在返回 404
    await prisma.environment.delete({ where: { id } })
    return { ok: true }
  })
}
