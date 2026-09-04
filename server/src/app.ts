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

function isPublicPath(url: string): boolean {
  return (
    url === '/api/health' ||
    url === '/api/auth/login' ||
    url.startsWith('/demo/')
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
      return reply.code(401).send({ error: '未登录' })
    }
    const token = authHeader.slice('Bearer '.length)
    try {
      const payload = verifyToken(token)
      ;(req as unknown as { user: unknown }).user = payload
    } catch {
      return reply.code(401).send({ error: '登录已过期，请重新登录' })
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

  return app
}
