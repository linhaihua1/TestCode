import { prisma } from '../src/db.js'
import { hashPassword } from '../src/auth.js'

/**
 * 构造验收用的演示数据：
 * 默认管理员 + 项目 → 环境 → 接口(登录/查询用户) → 用例(断言+提取) → 场景(两步编排)
 * 执行该场景可完整跑通「提取 token → 变量传递 → 断言」。
 */
async function main() {
  // 确保默认管理员存在（admin / admin@123）
  const admin = await prisma.user.upsert({
    where: { username: 'admin' },
    update: {},
    create: { username: 'admin', passwordHash: hashPassword('admin@123'), role: 'admin' },
  })
  console.log('默认管理员已就绪：', admin.username, '/ admin@123')

  // 清理旧的演示项目（级联删除其下所有数据）
  const existing = await prisma.project.findFirst({ where: { name: '演示项目' } })
  if (existing) {
    await prisma.project.delete({ where: { id: existing.id } })
    console.log('已清理旧的演示项目')
  }

  // 1. 项目
  const project = await prisma.project.create({
    data: { name: '演示项目', description: '验收演示用的示例数据（可直接执行）' },
  })

  // 2. 环境（baseUrl 指向本机后端，演示接口内置在后端）
  const env = await prisma.environment.create({
    data: {
      projectId: project.id,
      name: '演示环境',
      baseUrl: 'http://127.0.0.1:4000',
      variables: [],
      headers: [],
    },
  })

  // 3. 接口定义
  const loginApi = await prisma.apiDefinition.create({
    data: {
      projectId: project.id,
      name: '登录',
      method: 'POST',
      path: '/demo/login',
      headers: [{ key: 'Content-Type', value: 'application/json' }],
      query: [],
      body: '{"username": "张三"}',
      description: '模拟登录，返回 token 和 userId',
    },
  })
  const userApi = await prisma.apiDefinition.create({
    data: {
      projectId: project.id,
      name: '查询用户',
      method: 'GET',
      path: '/demo/users/${userId}',
      headers: [{ key: 'Authorization', value: 'Bearer ${token}' }],
      query: [],
      body: null,
      description: '携带 token 查询用户，路径使用上一步提取的 userId',
    },
  })

  // 4. 用例（断言 + 提取）
  const loginCase = await prisma.apiCase.create({
    data: {
      apiId: loginApi.id,
      name: '登录成功',
      assertions: [{ type: 'statusCode', expression: '', expected: '200', operator: 'eq' }],
      extracts: [
        { name: 'token', type: 'jsonPath', expression: '$.token' },
        { name: 'userId', type: 'jsonPath', expression: '$.userId' },
      ],
    },
  })
  const userCase = await prisma.apiCase.create({
    data: {
      apiId: userApi.id,
      name: '查询用户成功',
      assertions: [
        { type: 'statusCode', expression: '', expected: '200', operator: 'eq' },
        { type: 'jsonPath', expression: '$.name', expected: '张三', operator: 'eq' },
      ],
      extracts: [],
    },
  })

  // 5. 场景 + 步骤
  const scenario = await prisma.scenario.create({
    data: {
      projectId: project.id,
      name: '登录查询流程',
      description: '登录后携带 token 查询用户信息（演示提取传递 + 断言）',
    },
  })
  await prisma.scenarioStep.create({
    data: { scenarioId: scenario.id, order: 0, apiCaseId: loginCase.id, assertions: [], extracts: [] },
  })
  await prisma.scenarioStep.create({
    data: { scenarioId: scenario.id, order: 1, apiCaseId: userCase.id, assertions: [], extracts: [] },
  })

  console.log('✅ 演示数据创建成功：')
  console.log('  项目：', project.name)
  console.log('  环境：', env.name, '→', env.baseUrl)
  console.log('  场景：', scenario.name, '（2 步：登录 → 查询用户）')
  console.log('  执行方式：进入「场景自动化」→ 点「编排」→ 选环境 → 点「执行场景」')
}

main()
  .then(() => prisma.$disconnect())
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
