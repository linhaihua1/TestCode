import { prisma } from '../src/db.js'

async function main() {
  // 删除所有项目（级联删除环境/接口/用例/场景/报告/UI用例/UI执行/UI报告）
  const projects = await prisma.project.deleteMany()
  console.log('已删除项目：', projects.count)

  // 剩余用户（保留，用于登录）
  const users = await prisma.user.findMany()
  console.log('保留用户：', users.map((u) => `${u.username}(${u.role})`).join(', '))

  // 统计剩余数据
  const envCount = await prisma.environment.count()
  const apiCount = await prisma.apiDefinition.count()
  const reportCount = await prisma.report.count()
  const uiTestCount = await prisma.uiTestCase.count()
  const uiReportCount = await prisma.uiReport.count()
  console.log('剩余：环境', envCount, '接口', apiCount, '报告', reportCount, 'UI用例', uiTestCount, 'UI报告', uiReportCount)
}

main()
  .then(() => prisma.$disconnect())
  .catch((e) => {
    console.error(e)
    process.exit(1)
  })
