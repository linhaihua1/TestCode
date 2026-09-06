/**
 * 后端启动入口。
 * 构建 Fastify 应用实例，读取端口/监听地址环境变量后启动 HTTP 服务。
 * 启动失败时记录错误日志并以非零码退出。
 */
import { buildApp } from './app.js'
import { reapOrphanPerfRuns } from './routes/perf-tests.js'

// 构建应用实例（路由、中间件等均在 app.ts 中装配）
const app = buildApp()

// 回收上次进程残留的 running 压测报告（压测进程随服务一起消失，无机会善后）
reapOrphanPerfRuns()
  .then((n) => {
    if (n > 0) app.log.info(`已回收 ${n} 条中断的压测报告`)
  })
  .catch(() => {
    /* 启动期 DB 未就绪等情况忽略，不影响监听 */
  })

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
