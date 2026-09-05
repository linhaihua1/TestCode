import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'
import { hashPassword } from '../auth.js'
import { recordAudit } from '../audit.js'

interface UserBody {
  username?: string
  password?: string
  role?: string
}

/** 去除密码哈希，返回安全的用户信息 */
function toSafeUser(user: { passwordHash: string } & Record<string, unknown>) {
  const { passwordHash: _omit, ...rest } = user
  return rest
}

/**
 * 用户管理路由：列表 / 新增 / 编辑 / 删除 / 重置密码。
 * 均需登录（受鉴权中间件保护）。
 */
export async function userRoutes(app: FastifyInstance) {
  // 用户列表（不含密码哈希）
  app.get('/api/users', async () => {
    const users = await prisma.user.findMany({ orderBy: { createdAt: 'asc' } })
    return users.map(toSafeUser)
  })

  // 新增用户
  app.post('/api/users', async (req, reply) => {
    const body = req.body as UserBody
    if (!body?.username || !body?.password) {
      return reply.code(400).send({ error: '用户名和密码必填' })
    }
    const exists = await prisma.user.findUnique({ where: { username: body.username } })
    if (exists) return reply.code(409).send({ error: '用户名已存在' })

    const user = await prisma.user.create({
      data: {
        username: body.username,
        passwordHash: hashPassword(body.password),
        role: body.role ?? 'admin',
      },
    })
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'create', entityType: 'user', entityId: user.id, after: { username: user.username, role: user.role } })
    return toSafeUser(user)
  })

  // 编辑用户（用户名 / 角色）
  app.put('/api/users/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { username?: string; role?: string }

    const user = await prisma.user.findUnique({ where: { id } })
    if (!user) return reply.code(404).send({ error: '用户不存在' })

    // 若修改用户名，校验唯一性
    if (body.username && body.username !== user.username) {
      const exists = await prisma.user.findUnique({ where: { username: body.username } })
      if (exists) return reply.code(409).send({ error: '用户名已存在' })
    }

    const updated = await prisma.user.update({
      where: { id },
      data: { username: body.username, role: body.role },
    })
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'update', entityType: 'user', entityId: id, after: { username: updated.username, role: updated.role } })
    return toSafeUser(updated)
  })

  // 删除用户
  app.delete('/api/users/:id', async (req, reply) => {
    const { id } = req.params as { id: string }
    const user = await prisma.user.findUnique({ where: { id } })
    if (!user) return reply.code(404).send({ error: '用户不存在' })
    await prisma.user.delete({ where: { id } })
    await recordAudit({ user: (req as unknown as { user: { userId: string } }).user, action: 'delete', entityType: 'user', entityId: id, before: { username: user.username } })
    return { ok: true }
  })

  // 重置密码（管理员操作，直接设置新密码）
  app.put('/api/users/:id/password', async (req, reply) => {
    const { id } = req.params as { id: string }
    const body = req.body as { newPassword?: string }

    const user = await prisma.user.findUnique({ where: { id } })
    if (!user) return reply.code(404).send({ error: '用户不存在' })
    if (!body?.newPassword) return reply.code(400).send({ error: '新密码必填' })

    await prisma.user.update({
      where: { id },
      data: { passwordHash: hashPassword(body.newPassword) },
    })
    return { ok: true }
  })
}
