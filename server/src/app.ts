/**
 * Fastify 应用组装。
 * 创建应用实例、注册跨域中间件与健康检查接口，并挂载各业务路由。
 */
import Fastify, { type FastifyInstance } from 'fastify'
import cors from '@fastify/cors'
import { projectRoutes } from './routes/projects.js'
import { apiRoutes } from './routes/apis.js'
import { scenarioRoutes } from './routes/scenarios.js'
import { reportRoutes } from './routes/reports.js'
import { demoRoutes } from './routes/demo.js'

/** 构建并返回配置完整的 Fastify 应用实例 */
export function buildApp(): FastifyInstance {
  // 关闭内置日志，避免干扰运行输出
  const app = Fastify({ logger: false })

  // 允许任意来源跨域，便于前端本地开发访问
  app.register(cors, { origin: true })

  // 健康检查接口，供探活/负载均衡使用
  app.get('/api/health', async () => ({ status: 'ok', service: 'api-web-server' }))

  // 注册各业务模块路由
  app.register(projectRoutes) // 项目
  app.register(apiRoutes) // 接口用例
  app.register(scenarioRoutes) // 测试场景
  app.register(reportRoutes) // 执行报告
  app.register(demoRoutes) // 演示/示例

  return app
}
