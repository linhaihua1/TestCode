/**
 * Fastify 应用组装。
 * 创建应用实例、注册跨域中间件、鉴权中间件与健康检查接口，并挂载各业务路由。
 */
import Fastify, { type FastifyInstance } from 'fastify'
import cors from '@fastify/cors'
import { verifyToken } from './auth.js'
import { projectRoutes } from './routes/projects.js'
import { apiRoutes } from './routes/apis.js'
import { scenarioRoutes } from './routes/scenarios.js'
import { reportRoutes } from './routes/reports.js'
import { demoRoutes } from './routes/demo.js'
import { authRoutes } from './routes/auth.js'
import { userRoutes } from './routes/users.js'
import { uiTestRoutes } from './routes/ui-tests.js'

/** 无需登录即可访问的路径（登录、健康检查、演示接口） */
function isPublicPath(url: string): boolean {
  return (
    url === '/api/health' ||
    url === '/api/auth/login' ||
    url.startsWith('/demo/')
  )
}

/** 构建并返回配置完整的 Fastify 应用实例 */
export function buildApp(): FastifyInstance {
  // 关闭内置日志，避免干扰运行输出
  const app = Fastify({ logger: false })

  // 允许任意来源跨域，便于前端本地开发访问
  app.register(cors, { origin: true })

  // 健康检查接口，供探活/负载均衡使用
  app.get('/api/health', async () => ({ status: 'ok', service: 'api-web-server' }))

  // 鉴权中间件：除公开路径外，其余请求需携带有效的 Bearer token
  // 需在所有受保护路由之前注册；公开路径通过 isPublicPath 放行
  app.addHook('preHandler', async (req, reply) => {
    if (isPublicPath(req.url)) return

    const authHeader = req.headers.authorization
    if (!authHeader?.startsWith('Bearer ')) {
      return reply.code(401).send({ error: '未登录' })
    }
    const token = authHeader.slice('Bearer '.length)
    try {
      const payload = verifyToken(token)
      // 将解析出的用户信息挂到请求上，供后续 handler 使用
      ;(req as unknown as { user: unknown }).user = payload
    } catch {
      return reply.code(401).send({ error: '登录已过期，请重新登录' })
    }
  })

  // 认证路由（login 为公开路径，me/change-password 受鉴权保护）
  app.register(authRoutes)
  // 演示/示例接口（公开，被场景执行当作“被测接口”调用）
  app.register(demoRoutes)

  // 注册各业务模块路由（均受鉴权保护）
  app.register(projectRoutes) // 项目
  app.register(apiRoutes) // 接口用例
  app.register(scenarioRoutes) // 测试场景
  app.register(reportRoutes) // 执行报告
  app.register(userRoutes) // 用户管理
  app.register(uiTestRoutes) // UI 自动化测试

  return app
}
