import type { FastifyInstance } from 'fastify'
import { Prisma } from '@prisma/client'
import { prisma } from '../db.js'
import { debugCase, runCase } from '../engine/runner.js'

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
  mockEnabled?: boolean // 是否启用 Mock
  mockResponse?: string | null // Mock 响应内容
  moduleId?: string | null // 所属模块
  tags?: Prisma.InputJsonValue // 标签
}

interface CaseBody {
  name: string // 用例名称
  assertions?: Prisma.InputJsonValue // 断言配置（可选，JSON）
  extracts?: Prisma.InputJsonValue // 变量提取配置（可选，JSON）
  stepDefs?: Prisma.InputJsonValue // 多步骤定义（可选，JSON）
}

/** 批量导入的单个接口条目（Swagger 导入与 Excel 导入共用） */
interface ImportItem {
  name: string
  method: string
  path: string
  headers?: Prisma.InputJsonValue
  query?: Prisma.InputJsonValue
  body?: string | null
  description?: string
  module?: string // 模块/标签名
  tags?: string[]
  mockEnabled?: boolean
  mockResponse?: string | null
}

/** 按名称查找或创建 api 类型模块，返回模块 ID */
async function findOrCreateApiModule(projectId: string, tagName: string, cache: Map<string, string>): Promise<string | null> {
  if (!tagName) return null
  if (cache.has(tagName)) return cache.get(tagName)!
  const mod = await prisma.module.findFirst({ where: { projectId, name: tagName, type: 'api' } })
  if (mod) {
    cache.set(tagName, mod.id)
    return mod.id
  }
  const newMod = await prisma.module.create({ data: { projectId, name: tagName, type: 'api' } })
  cache.set(tagName, newMod.id)
  return newMod.id
}

/** 批量导入接口定义，按 (method, path) 去重，返回新增与跳过数量 */
async function importApiItems(projectId: string, items: ImportItem[]): Promise<{ created: number; skipped: number }> {
  const moduleCache = new Map<string, string>()
  let created = 0
  let skipped = 0
  for (const it of items) {
    const name = String(it?.name ?? '').trim()
    const method = String(it?.method ?? '').trim().toUpperCase()
    const path = String(it?.path ?? '').trim()
    if (!name || !method || !path) {
      skipped++
      continue
    }
    const moduleName = String(it.module ?? '')
    const moduleId = await findOrCreateApiModule(projectId, moduleName, moduleCache)
    const tags = (it.tags ?? (moduleName ? [moduleName] : [])) as unknown as Prisma.InputJsonValue

    const exists = await prisma.apiDefinition.findFirst({ where: { projectId, method, path } })
    if (exists) {
      skipped++
      continue
    }
    await prisma.apiDefinition.create({
      data: {
        projectId,
        name,
        method,
        path,
        moduleId,
        tags,
        description: it.description ?? '',
        headers: it.headers ?? [],
        query: it.query ?? [],
        body: it.body ?? null,
        mockEnabled: it.mockEnabled ?? false,
        mockResponse: it.mockResponse ?? null,
      },
    })
    created++
  }
  return { created, skipped }
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
        mockEnabled: body.mockEnabled ?? false,
        mockResponse: body.mockResponse ?? null,
        moduleId: body.moduleId ?? null,
        tags: body.tags ?? [],
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
        stepDefs: body.stepDefs ?? [], // 多步骤定义缺省为空数组
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

  // 调试单个用例：发送请求并返回响应、提取结果、断言结果
  app.post('/api/cases/:id/debug', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { environmentId?: string }
    try {
      const result = await debugCase(id, body?.environmentId)
      return result
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(404).send({ error: message })
    }
  })

  // 独立运行单个用例（支持多步骤），生成报告
  app.post('/api/cases/:id/run', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { environmentId?: string }
    try {
      const { report } = await runCase(id, body?.environmentId)
      return report
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err)
      return reply.code(404).send({ error: message })
    }
  })

  // ---------- Swagger/OpenAPI 导入 ----------
  app.post('/api/projects/:projectId/apis/import-swagger', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as { content?: Record<string, unknown> }
    const doc = body?.content
    if (!doc || typeof doc !== 'object') return reply.code(400).send({ error: 'OpenAPI 文档内容无效' })

    const paths = (doc.paths ?? {}) as Record<string, Record<string, unknown>>
    const methods = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options']
    const items: ImportItem[] = []

    for (const [path, pathItem] of Object.entries(paths)) {
      if (!pathItem || typeof pathItem !== 'object') continue
      for (const m of methods) {
        const op = pathItem[m]
        if (!op || typeof op !== 'object') continue
        const opObj = op as Record<string, unknown>
        const tags = (opObj.tags as string[]) ?? []
        items.push({
          name: String(opObj.summary ?? opObj.operationId ?? `${m.toUpperCase()} ${path}`),
          method: m.toUpperCase(),
          path,
          tags,
          module: tags[0] ?? '',
          description: String(opObj.description ?? ''),
        })
      }
    }

    return importApiItems(projectId, items)
  })

  // ---------- 批量导入（Excel 模板导入） ----------
  app.post('/api/projects/:projectId/apis/import-batch', async (req, reply) => {
    const { projectId } = req.params as { projectId: string }
    const body = req.body as { items?: ImportItem[] }
    const items = body?.items
    if (!Array.isArray(items) || items.length === 0) {
      return reply.code(400).send({ error: '未提供有效的导入数据' })
    }
    return importApiItems(projectId, items)
  })

  // ---------- Mock ----------
  // 请求 Mock 地址，返回接口配置的模拟响应
  app.get('/mock/:apiId', async (req, reply) => {
    const { apiId } = req.params as { apiId: string }
    const api = await prisma.apiDefinition.findUnique({ where: { id: apiId } })
    if (!api) return reply.code(404).send({ error: '接口不存在' })
    if (!api.mockEnabled) return reply.code(404).send({ error: '该接口未启用 Mock' })
    try {
      const data = api.mockResponse ? JSON.parse(api.mockResponse) : {}
      return reply.type('application/json').send(data)
    } catch {
      return reply.type('text/plain').send(api.mockResponse ?? '')
    }
  })
}
