import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'

/**
 * 接口定义与接口用例相关的 REST 路由：
 * - 接口定义（ApiDefinition）的增删改查（列表 / 新建 / 详情 / 更新 / 删除）
 * - 接口用例（ApiCase）的增删改查（列表 / 新建 / 更新 / 删除）
 */
interface ApiBody {
  name: string // 接口名称
  method: string // HTTP 请求方法
  path: string // 接口路径
  headers?: Prisma.InputJsonValue // 请求头（可选，JSON）
  query?: Prisma.InputJsonValue // 查询参数（可选，JSON）
  body?: string | null // 请求体（可选，字符串）
  description?: string // 接口描述（可选）
}

interface CaseBody {
  name: string // 用例名称
  assertions?: Prisma.InputJsonValue // 断言配置（可选，JSON）
  extracts?: Prisma.InputJsonValue // 变量提取配置（可选，JSON）
}

export async function apiRoutes(app: FastifyInstance) {
  // ---------- ApiDefinition ----------
  // 获取项目下的接口定义列表
  app.get('/api/projects/:projectId/apis', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.apiDefinition.findMany({
      where: { projectId },
      orderBy: { createdAt: 'asc' },
    })
  })

  // 在项目下新建接口定义
  app.post('/api/projects/:projectId/apis', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as ApiBody
    if (!body?.name || !body?.method || !body?.path) {
      return reply.code(400).send({ error: 'name/method/path 必填' }) // 名称/方法/路径必填校验
    }
    return prisma.apiDefinition.create({
      data: {
        projectId,
        name: body.name,
        method: body.method,
        path: body.path,
        headers: body.headers ?? [], // 请求头缺省为空数组
        query: body.query ?? [], // 查询参数缺省为空数组
        body: body.body ?? null, // 请求体缺省为 null
        description: body.description,
      },
    })
  })

  // 获取接口定义详情（含其用例）
  app.get('/api/apis/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const api = await prisma.apiDefinition.findUnique({ where: { id }, include: { cases: true } })
    if (!api) return reply.code(404).send({ error: '接口不存在' }) // 接口不存在返回 404
    return api
  })

  // 更新接口定义
  app.put('/api/apis/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ApiBody
    const api = await prisma.apiDefinition.findUnique({ where: { id } })
    if (!api) return reply.code(404).send({ error: '接口不存在' }) // 接口不存在返回 404
    return prisma.apiDefinition.update({ where: { id }, data: body })
  })

  // 删除接口定义
  app.delete('/api/apis/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const api = await prisma.apiDefinition.findUnique({ where: { id } })
    if (!api) return reply.code(404).send({ error: '接口不存在' }) // 接口不存在返回 404
    await prisma.apiDefinition.delete({ where: { id } })
    return { ok: true }
  })

  // ---------- ApiCase ----------
  // 获取接口下的用例列表
  app.get('/api/apis/:apiId/cases', async (req) => {
    const { apiId } = req.params as { apiId: string }
    return prisma.apiCase.findMany({ where: { apiId }, orderBy: { createdAt: 'asc' } })
  })

  // 在接口下新建用例
  app.post('/api/apis/:apiId/cases', async (req, reply) => {
    const { apiId } = req.params as { apiId: string }
    const body = req.body as CaseBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' }) // 用例名称必填校验
    return prisma.apiCase.create({
      data: {
        apiId,
        name: body.name,
        assertions: body.assertions ?? [], // 断言缺省为空数组
        extracts: body.extracts ?? [], // 变量提取缺省为空数组
      },
    })
  })

  // 更新用例
  app.put('/api/cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as CaseBody
    const c = await prisma.apiCase.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' }) // 用例不存在返回 404
    return prisma.apiCase.update({ where: { id }, data: body })
  })

  // 删除用例
  app.delete('/api/cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.apiCase.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' }) // 用例不存在返回 404
    await prisma.apiCase.delete({ where: { id } })
    return { ok: true }
  })
}
