import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { fail } from '../error-codes.js'

interface VariableBody {
  name?: string
  type?: string
  value?: string
  encrypted?: boolean
  description?: string
}

/** 查找引用该变量的位置（用例 / 接口 / 环境） */
async function findVariableReferences(projectId: string, varName: string): Promise<string[]> {
  const token = `\${${varName}}`
  const refs: string[] = []

  const cases = await prisma.caseInfo.findMany({ where: { projectId, deletedAt: null } })
  for (const c of cases) {
    const text = c.name + JSON.stringify(c.steps ?? []) + JSON.stringify(c.tags ?? [])
    if (text.includes(token)) refs.push(`用例「${c.name}」`)
  }

  const apis = await prisma.apiDefinition.findMany({ where: { projectId } })
  for (const a of apis) {
    const text = a.path + (a.body ?? '') + JSON.stringify(a.headers ?? []) + JSON.stringify(a.query ?? [])
    if (text.includes(token)) refs.push(`接口「${a.name}」`)
  }

  const envs = await prisma.environment.findMany({ where: { projectId } })
  for (const e of envs) {
    if (JSON.stringify(e.variables ?? []).includes(token)) refs.push(`环境「${e.name}」`)
  }

  return refs
}

export async function globalVariableRoutes(app: FastifyInstance) {
  // 获取项目下的全局变量列表
  app.get('/api/projects/:projectId/global-variables', async (req) => {
    const { projectId } = req.params as { projectId: string }
    return prisma.globalVariable.findMany({
      where: { projectId },
      orderBy: { name: 'asc' },
    })
  })

  // 新建全局变量
  app.post('/api/projects/:projectId/global-variables', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as VariableBody
    if (!body?.name) return reply.code(400).send({ error: 'name 必填' })
    // 变量名校验：字母/数字/下划线，不能以数字开头
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(body.name)) {
      return fail(reply, 'VAR_NAME_INVALID')
    }
    // 同名检查
    const exists = await prisma.globalVariable.findFirst({ where: { projectId, name: body.name } })
    if (exists) return reply.code(409).send({ error: '变量名已存在' })
    return prisma.globalVariable.create({
      data: {
        projectId,
        name: body.name,
        type: body.type ?? 'string',
        value: body.value ?? '',
        encrypted: body.encrypted ?? false,
        description: body.description,
      },
    })
  })

  // 更新全局变量
  app.put('/api/global-variables/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as VariableBody
    const v = await prisma.globalVariable.findUnique({ where: { id } })
    if (!v) return reply.code(404).send({ error: '变量不存在' })
    return prisma.globalVariable.update({ where: { id }, data: { value: body.value, description: body.description } })
  })

  // 删除全局变量
  app.delete('/api/global-variables/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const v = await prisma.globalVariable.findUnique({ where: { id } })
    if (!v) return reply.code(404).send({ error: '变量不存在' })
    // 被引用时禁止删除（2002），返回引用位置
    const refs = await findVariableReferences(v.projectId, v.name)
    if (refs.length > 0) {
      return fail(reply, 'VAR_REFERENCED', `变量「${v.name}」被引用，无法删除：${refs.join('、')}`)
    }
    await prisma.globalVariable.delete({ where: { id } })
    return { ok: true }
  })

  // 批量删除
  app.delete('/api/global-variables', async (req, reply) => {
    const body = req.body as { ids: string[] }
    if (!body?.ids?.length) return reply.code(400).send({ error: 'ids 必填' })
    await prisma.globalVariable.deleteMany({ where: { id: { in: body.ids } } })
    return { ok: true }
  })

  // 获取所有全局变量（跨项目，供变量解析器使用）
  app.get('/api/global-variables', async () => {
    return prisma.globalVariable.findMany({ orderBy: { name: 'asc' } })
  })
}
