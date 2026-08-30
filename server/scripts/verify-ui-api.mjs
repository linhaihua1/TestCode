const base = 'http://127.0.0.1:4000'

// 登录
const login = await (
  await fetch(base + '/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin@123' }),
  })
).json()
const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${login.token}` }

// 找演示项目
const projects = await (await fetch(base + '/api/projects', { headers: auth })).json()
const project = projects.find((p) => p.name === '演示项目')

// 创建 UI 测试用例
const steps = [
  { action: 'open', target: 'http://127.0.0.1:4000/demo/page' },
  { action: 'assertTitle', value: 'Demo Page' },
  { action: 'assertExists', locatorType: 'id', target: 'title' },
  { action: 'click', locatorType: 'id', target: 'btn' },
  { action: 'assertText', locatorType: 'id', target: 'result', value: '已点击' },
]
const tc = await (
  await fetch(base + `/api/projects/${project.id}/ui-tests`, {
    method: 'POST',
    headers: auth,
    body: JSON.stringify({ name: '演示 UI 测试', baseUrl: 'http://127.0.0.1:4000', steps }),
  })
).json()
console.log('已创建 UI 用例：', tc.name)

// 执行（run 接口无 body，不带 Content-Type）
console.log('执行中（启动 Chrome）...')
const report = await (
  await fetch(base + `/api/ui-tests/${tc.id}/run`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${login.token}` },
  })
).json()

console.log('\n=== 执行报告（原始） ===')
console.log(JSON.stringify(report, null, 2))
