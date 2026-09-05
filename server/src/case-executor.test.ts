import { describe, it, expect } from 'vitest'
import { executeCaseSteps, type CaseStepDef } from './engine/case-executor.js'

function step(partial: Partial<CaseStepDef> & { id: string; type: CaseStepDef['type']; phase: CaseStepDef['phase'] }): CaseStepDef {
  return { name: partial.id, enabled: true, ...partial }
}

describe('case-executor 边界行为（第 7 部分）', () => {
  it('变量表达式赋值：${a} + 2 计算为 3', async () => {
    const steps = [
      step({ id: 'v1', type: 'variable', phase: 'setup', varName: 'a', varValue: '1' }),
      step({ id: 'v2', type: 'variable', phase: 'setup', varName: 'b', varValue: '${a} + 2', varMode: 'expression' }),
    ]
    const { context } = await executeCaseSteps(steps, {})
    expect(context.b).toBe('3')
  })

  it('IF 条件满足走 THEN 分支，不满足走 ELSE 分支', async () => {
    const mk = (cond: string) => [
      step({ id: 'x', type: 'variable', phase: 'setup', varName: 'x', varValue: 'ok' }),
      step({
        id: 'if', type: 'controller', phase: 'test', controllerType: 'if', condition: cond,
        children: [step({ id: 'then', type: 'variable', phase: 'test', varName: 'r', varValue: 'THEN' })],
        elseChildren: [step({ id: 'else', type: 'variable', phase: 'test', varName: 'r', varValue: 'ELSE' })],
      }),
    ]
    const ok = await executeCaseSteps(mk('${x} == "ok"'), {})
    expect(ok.context.r).toBe('THEN')
    const no = await executeCaseSteps(mk('${x} == "no"'), {})
    expect(no.context.r).toBe('ELSE')
  })

  it('WHILE 未配置最大循环次数 → ERROR(2006)', async () => {
    const steps = [step({ id: 'w', type: 'controller', phase: 'test', controllerType: 'while', condition: 'true' })]
    const { results } = await executeCaseSteps(steps, {})
    expect(results[0].status).toBe('ERROR')
    expect(results[0].message).toContain('2006')
  })

  it('JS 脚本：context.get/set 操作变量', async () => {
    const steps = [
      step({ id: 'v', type: 'variable', phase: 'setup', varName: 'a', varValue: '1' }),
      step({ id: 's', type: 'script', phase: 'setup', script: "context.set('b', context.get('a') + '2'); context.set('c', 5)" }),
    ]
    const { context } = await executeCaseSteps(steps, {})
    expect(context.b).toBe('12')
    expect(context.c).toBe('5')
  })

  it('提取失败使用默认值', async () => {
    // 用 jsonPath 提取一个不存在的字段，应回退到 defaultValue（依赖 applyExtracts 逻辑）
    const { applyExtracts } = await import('./engine/extract.js')
    const ctx: Record<string, string> = {}
    applyExtracts(
      { status: 200, headers: {}, body: { a: 1 }, rawBody: '{"a":1}', duration: 0 },
      [{ name: 'missing', type: 'jsonPath', expression: '$.nope', defaultValue: 'DEFAULT' }],
      ctx,
    )
    expect(ctx.missing).toBe('DEFAULT')
  })
})
