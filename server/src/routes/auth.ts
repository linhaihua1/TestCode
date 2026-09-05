import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'
import { hashPassword, signToken, verifyPassword } from '../auth.js'

/**
 * 认证相关路由：登录、获取当前用户信息、修改自己的密码。
 */
export async function authRoutes(app: FastifyInstance) {
  // 登录：校验用户名密码，返回 token 和用户信息
  app.post('/api/auth/login', async (req, reply) => {
    const body = req.body as { username?: string; password?: string }
    if (!body?.username || !body?.password) {
      return reply.code(400).send({ error: '用户名和密码必填' })
    }

    const user = await prisma.user.findUnique({ where: { username: body.username } })
    if (!user || !verifyPassword(body.password, user.passwordHash)) {
      return reply.code(401).send({ error: '用户名或密码错误' })
    }

    const token = signToken({ userId: user.id, username: user.username, role: user.role })
    return { token, user: { id: user.id, username: user.username, role: user.role } }
  })

  // 获取当前登录用户信息（token 由鉴权中间件解析后挂到 req.user）
  app.get('/api/auth/me', async (req) => {
    const payload = (req as unknown as { user: { userId: string } }).user
    const user = await prisma.user.findUnique({ where: { id: payload.userId } })
    if (!user) return { id: '', username: '', role: '' }
    return { id: user.id, username: user.username, role: user.role }
  })

  // 修改自己的密码（需校验旧密码）
  app.post('/api/auth/change-password', async (req, reply) => {
    const payload = (req as unknown as { user: { userId: string } }).user
    const body = req.body as { oldPassword?: string; newPassword?: string }
    if (!body?.oldPassword || !body?.newPassword) {
      return reply.code(400).send({ error: '旧密码和新密码必填' })
    }

    const user = await prisma.user.findUnique({ where: { id: payload.userId } })
    if (!user) return reply.code(404).send({ error: '用户不存在' })
    if (!verifyPassword(body.oldPassword, user.passwordHash)) {
      return reply.code(400).send({ error: '旧密码错误' })
    }

    await prisma.user.update({
      where: { id: user.id },
      data: { passwordHash: hashPassword(body.newPassword) },
    })
    return { ok: true }
  })
}
