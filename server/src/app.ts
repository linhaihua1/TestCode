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
import { uiScenarioRoutes } from './routes/ui-scenarios.js'
import { globalVariableRoutes } from './routes/global-variables.js'
import { moduleRoutes } from './routes/modules.js'
import { caseRoutes } from './routes/cases.js'
import { debugRecordRoutes } from './routes/debug-records.js'
import { auditLogRoutes } from './routes/audit-logs.js'
import { testTaskRoutes } from './routes/test-tasks.js'
import { perfRoutes } from './routes/perf-tests.js'
import { fail } from './error-codes.js'
import { prisma } from './db.js'

function isPublicPath(url: string): boolean {
  return (
    url === '/api/health' ||
    url === '/api/auth/login' ||
    url.startsWith('/demo/') ||
    url.startsWith('/mock/')
  )
}

export function buildApp(): FastifyInstance {
  const app = Fastify({ logger: false })
  app.register(cors, { origin: true })
  app.get('/api/health', async () => ({ status: 'ok', service: 'api-web-server' }))

  app.addHook('preHandler', async (req, reply) => {
    if (isPublicPath(req.url)) return
    const authHeader = req.headers.authorization
    if (!authHeader?.startsWith('Bearer ')) {
      return fail(reply, 'UNAUTHORIZED')
    }
    const token = authHeader.slice('Bearer '.length)
    let payload: { userId: string; username: string; role?: string }
    try {
      payload = verifyToken(token)
      // 从 DB 读取当前用户与角色：修复旧 token 无 role 导致的误判，角色变更即时生效，并拒绝已删除用户
      const dbUser = await prisma.user.findUnique({
        where: { id: payload.userId },
        select: { username: true, role: true },
      })
      if (!dbUser) return fail(reply, 'UNAUTHORIZED')
      const currentRole = dbUser.role ?? 'viewer'
      ;(req as unknown as { user: unknown }).user = { userId: payload.userId, username: dbUser.username, role: currentRole }
      payload = { userId: payload.userId, username: dbUser.username, role: currentRole }
    } catch {
      return fail(reply, 'UNAUTHORIZED')
    }

    // ---------- 权限（RBAC） ----------
    // 角色：admin（管理员，全部权限）/ member（成员，接口+UI 自动化全部操作）/ viewer（查看者，只读）
    const role = payload.role ?? 'viewer'
    const readOnly = ['GET', 'HEAD', 'OPTIONS'].includes(req.method)
    const path = req.url.split('?')[0]

    // 系统管理（用户管理 / 审计日志）仅管理员可访问（含查看）
    if ((path.startsWith('/api/users') || path.startsWith('/api/audit-logs')) && role !== 'admin') {
      return fail(reply, 'FORBIDDEN')
    }
    // 项目管理（新建/编辑/删除项目）仅管理员；环境配置属业务，成员可操作
    if (/^\/api\/projects(\/[^/]+)?$/.test(path) && !readOnly && role !== 'admin') {
      return fail(reply, 'FORBIDDEN')
    }
    // 查看者只读：禁止业务写操作（自服务改密码除外）
    if (role === 'viewer' && !readOnly && !path.startsWith('/api/auth/')) {
      return fail(reply, 'FORBIDDEN')
    }
  })

  app.register(authRoutes)
  app.register(demoRoutes)
  app.register(projectRoutes)
  app.register(apiRoutes)
  app.register(scenarioRoutes)
  app.register(reportRoutes)
  app.register(userRoutes)
  app.register(uiTestRoutes)
  app.register(uiScenarioRoutes)
  app.register(globalVariableRoutes)
  app.register(moduleRoutes)
  app.register(caseRoutes)
  app.register(debugRecordRoutes)
  app.register(auditLogRoutes)
  app.register(testTaskRoutes)
  app.register(perfRoutes)

  return app
}
