import { describe, it, expect } from 'vitest'
import { spawnSync } from 'node:child_process'
import { runCustomScript } from './script-runner.js'

function hasBin(bins: string[]): boolean {
  for (const b of bins) {
    const r = spawnSync(b, ['--version'], { stdio: 'ignore' })
    if (!r.error) return true
  }
  return false
}

const hasPython = hasBin(['python3', 'python', 'py'])
const hasJava = (() => {
  const jc = spawnSync('javac', ['-version'], { stdio: 'ignore' })
  const j = spawnSync('java', ['-version'], { stdio: 'ignore' })
  return !jc.error && !j.error
})()

describe('script-runner 自定义代码', () => {
  it('JavaScript：提取变量 + 返回判断结果', async () => {
    const ctx: Record<string, string> = { a: '1' }
    const r = await runCustomScript("context.set('b', context.get('a') + '2'); context.set('__condition__', 'true')", 'javascript', ctx)
    expect(r.status).toBe('PASS')
    expect(r.extracted.b).toBe('12')
    expect(r.condition).toBe(true)
    // 预留变量 __condition__ 不应泄漏到上下文
    expect(ctx.__condition__).toBeUndefined()
  })

  it.skipIf(!hasPython)('Python：读取/写入变量并提取参数', async () => {
    const ctx: Record<string, string> = { token: 'abc' }
    const r = await runCustomScript(
      "token = context.get('token')\ncontext.set('upper', token.upper())\ncontext.set('__condition__', 'true')",
      'python',
      ctx,
    )
    expect(r.status).toBe('PASS')
    expect(r.extracted.upper).toBe('ABC')
    expect(r.condition).toBe(true)
    expect(r.extracted.__condition__).toBeUndefined()
  })

  it.skipIf(!hasJava)('Java：读取/写入变量并提取参数', async () => {
    const ctx: Record<string, string> = { token: 'abc' }
    const r = await runCustomScript(
      'String t = context.get("token");\ncontext.set("upper", t.toUpperCase());\ncontext.set("__condition__", "true");',
      'java',
      ctx,
    )
    expect(r.status).toBe('PASS')
    expect(r.extracted.upper).toBe('ABC')
    expect(r.condition).toBe(true)
    expect(r.extracted.__condition__).toBeUndefined()
  })
})
