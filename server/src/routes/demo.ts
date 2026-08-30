import type { FastifyInstance } from 'fastify'

/**
 * 内置演示接口，用于验收/演示：
 * 模拟一个简单的业务系统（登录 + 查询用户），
 * 让场景执行能完整展示「提取 token → 变量传递 → 断言」。
 */
export async function demoRoutes(app: FastifyInstance) {
  // 模拟登录：返回 token 和 userId
  app.post('/demo/login', async (req) => {
    const body = (req.body ?? {}) as { username?: string } // 请求体可能为空，兜底为 {}
    const username = body.username || '张三' // 未传用户名时使用默认值
    return { token: 'demo-token-123456', userId: '1001', username }
  })

  // 模拟查询用户：校验 Authorization 头
  app.get('/demo/users/:id', async (req, reply) => {
    const auth = req.headers.authorization
    if (auth !== 'Bearer demo-token-123456') {
      return reply.code(401).send({ error: '未授权：token 错误' }) // token 不匹配返回 401
    }
    const { id } = req.params as { id: string }
    return {
      id,
      name: '张三',
      email: 'zhangsan@example.com',
      role: 'admin',
    }
  })

  // 模拟一个会返回错误码的接口（演示断言失败效果）
  app.get('/demo/error', async (_req, reply) => {
    return reply.code(500).send({ code: 500, message: '服务器内部错误' }) // 固定返回 500 错误
  })

  // 演示页面（供 UI 自动化测试使用）
  app.get('/demo/page', async (_req, reply) => {
    reply.type('text/html; charset=utf-8').send(`<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Demo Page</title></head>
<body>
  <h1 id="title">Hello UI Test</h1>
  <button id="btn" onclick="document.getElementById('result').innerText='已点击'">Click Me</button>
  <div id="result"></div>
  <input id="name" placeholder="输入名字" />
</body>
</html>`)
  })
}
