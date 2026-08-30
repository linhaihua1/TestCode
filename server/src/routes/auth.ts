import type { FastifyInstance } from 'fastify'
import { prisma } from '../db.js'
import { signToken, verifyPassword } from '../auth.js'

/**
 * 认证相关路由：登录、获取当前用户信息。
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

    const token = signToken({ userId: user.id, username: user.username })
    return { token, user: { id: user.id, username: user.username, role: user.role } }
  })

  // 获取当前登录用户信息（token 由鉴权中间件解析后挂到 req.user）
  app.get('/api/auth/me', async (req) => {
    const payload = (req as unknown as { user: { userId: string } }).user
    const user = await prisma.user.findUnique({ where: { id: payload.userId } })
    if (!user) return { id: '', username: '', role: '' }
    return { id: user.id, username: user.username, role: user.role }
  })
}
