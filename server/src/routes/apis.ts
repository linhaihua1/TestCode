import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'

interface ApiBody {
  name: string
  method: string
  path: string
  headers?: Prisma.InputJsonValue
  query?: Prisma.InputJsonValue
  body?: string | null
  description?: string
}

interface CaseBody {
  name: string
  assertions?: Prisma.InputJsonValue
  extracts?: Prisma.InputJsonValue
}

export async function apiRoutes(app: FastifyInstance) {
  // ---------- ApiDefinition ----------
  app.get('/api/projects/:projectId/apis', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.apiDefinition.findMany({
      where: { projectId },
      orderBy: { createdAt: 'asc' },
    })
  })

  app.post('/api/projects/:projectId/apis', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as ApiBody
    if (!body?.name || !body?.method || !body?.path) {
      return reply.code(400).send({ error: 'name/method/path 必填' })
    }
    return prisma.apiDefinition.create({
      data: {
        projectId,
        name: body.name,
        method: body.method,
        path: body.path,
        headers: body.headers ?? [],
        query: body.query ?? [],
        body: body.body ?? null,
        description: body.description,
      },
    })
  })

  app.get('/api/apis/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const api = await prisma.apiDefinition.findUnique({ where: { id }, include: { cases: true } })
    if (!api) return reply.code(404).send({ error: '接口不存在' })
    return api
  })

  app.put('/api/apis/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ApiBody
    const api = await prisma.apiDefinition.findUnique({ where: { id } })
    if (!api) return reply.code(404).send({ error: '接口不存在' })
    return prisma.apiDefinition.update({ where: { id }, data: body })
  })

  app.delete('/api/apis/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const api = await prisma.apiDefinition.findUnique({ where: { id } })
    if (!api) return reply.code(404).send({ error: '接口不存在' })
    await prisma.apiDefinition.delete({ where: { id } })
    return { ok: true }
  })

  // ---------- ApiCase ----------
  app.get('/api/apis/:apiId/cases', async (req) => {
    const { apiId } = req.params as { apiId: string }
    return prisma.apiCase.findMany({ where: { apiId }, orderBy: { createdAt: 'asc' } })
  })

  app.post('/api/apis/:apiId/cases', async (req, reply) => {
    const { apiId } = req.params as { apiId: string }
    const body = req.body as CaseBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.apiCase.create({
      data: {
        apiId,
        name: body.name,
        assertions: body.assertions ?? [],
        extracts: body.extracts ?? [],
      },
    })
  })

  app.put('/api/cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as CaseBody
    const c = await prisma.apiCase.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    return prisma.apiCase.update({ where: { id }, data: body })
  })

  app.delete('/api/cases/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const c = await prisma.apiCase.findUnique({ where: { id } })
    if (!c) return reply.code(404).send({ error: '用例不存在' })
    await prisma.apiCase.delete({ where: { id } })
    return { ok: true }
  })
}
