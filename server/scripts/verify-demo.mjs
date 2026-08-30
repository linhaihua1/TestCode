const base = 'http://127.0.0.1:4000'

const projects = await (await fetch(base + '/api/projects')).json()
const demo = projects.find((p) => p.name === '演示项目')
console.log('项目：', demo.name)

const scenarios = await (await fetch(base + `/api/projects/${demo.id}/scenarios`)).json()
const envs = await (await fetch(base + `/api/projects/${demo.id}/environments`)).json()
console.log('场景：', scenarios.map((s) => s.name).join(', '))
console.log('环境：', envs.map((e) => `${e.name}(${e.baseUrl})`).join(', '))

const res = await fetch(base + `/api/scenarios/${scenarios[0].id}/run`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ environmentId: envs[0].id }),
})
const report = await res.json()
console.log('\n=== 执行结果 ===')
console.log('状态：', report.status, '| 耗时：', report.duration + 'ms')
for (const d of report.details) {
  console.log(`\n[${d.status}] ${d.stepName}`)
  for (const a of d.assertions) {
    console.log(`  ${a.passed ? '✓' : '✗'} ${a.message}`)
  }
}
