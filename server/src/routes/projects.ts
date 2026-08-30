import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'

interface ProjectBody {
  name: string
  description?: string
}

interface EnvironmentBody {
  name: string
  baseUrl?: string
  variables?: Prisma.InputJsonValue
  headers?: Prisma.InputJsonValue
}

export async function projectRoutes(app: FastifyInstance) {
  // ---------- Project ----------
  app.get('/api/projects', async () => {
    return prisma.project.findMany({ orderBy: { createdAt: 'desc' } })
  })

  app.post('/api/projects', async (req, reply) => {
    const body = req.body as ProjectBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.project.create({ data: { name: body.name, description: body.description } })
  })

  app.get('/api/projects/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const project = await prisma.project.findUnique({ where: { id } })
    if (!project) return reply.code(404).send({ error: '项目不存在' })
    return project
  })

  app.put('/api/projects/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as ProjectBody
    const project = await prisma.project.findUnique({ where: { id } })
    if (!project) return reply.code(404).send({ error: '项目不存在' })
    return prisma.project.update({ where: { id }, data: body })
  })

  app.delete('/api/projects/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const project = await prisma.project.findUnique({ where: { id } })
    if (!project) return reply.code(404).send({ error: '项目不存在' })
    await prisma.project.delete({ where: { id } })
    return { ok: true }
  })

  // ---------- Environment ----------
  app.get('/api/projects/:projectId/environments', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.environment.findMany({
      where: { projectId },
      orderBy: { createdAt: 'asc' },
    })
  })

  app.post('/api/projects/:projectId/environments', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as EnvironmentBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    return prisma.environment.create({
      data: {
        projectId,
        name: body.name,
        baseUrl: body.baseUrl,
        variables: body.variables ?? [],
        headers: body.headers ?? [],
      },
    })
  })

  app.put('/api/environments/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as EnvironmentBody
    const env = await prisma.environment.findUnique({ where: { id } })
    if (!env) return reply.code(404).send({ error: '环境不存在' })
    return prisma.environment.update({ where: { id }, data: body })
  })

  app.delete('/api/environments/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const env = await prisma.environment.findUnique({ where: { id } })
    if (!env) return reply.code(404).send({ error: '环境不存在' })
    await prisma.environment.delete({ where: { id } })
    return { ok: true }
  })
}
