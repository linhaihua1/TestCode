import Fastify, { type FastifyInstance } from 'fastify'
import cors from '@fastify/cors'
import { projectRoutes } from './routes/projects.js'
import { apiRoutes } from './routes/apis.js'
import { scenarioRoutes } from './routes/scenarios.js'
import { reportRoutes } from './routes/reports.js'

export function buildApp(): FastifyInstance {
  const app = Fastify({ logger: false })

  app.register(cors, { origin: true })

  app.get('/api/health', async () => ({ status: 'ok', service: 'api-web-server' }))

  app.register(projectRoutes)
  app.register(apiRoutes)
  app.register(scenarioRoutes)
  app.register(reportRoutes)

  return app
}
