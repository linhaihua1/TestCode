/**
 * 后端启动入口。
 * 构建 Fastify 应用实例，读取端口/监听地址环境变量后启动 HTTP 服务。
 * 启动失败时记录错误日志并以非零码退出。
 */
import { buildApp } from './app.js'

// 构建应用实例（路由、中间件等均在 app.ts 中装配）
const app = buildApp()

// 监听端口与地址，可通过环境变量覆盖
const port = Number(process.env.PORT ?? 4000)
const host = process.env.HOST ?? '0.0.0.0'

try {
  // 启动 HTTP 服务
  await app.listen({ port, host })
} catch (err) {
  // 启动失败：记录日志并退出进程，交由进程管理器重启
  app.log.error(err)
  process.exit(1)
}
