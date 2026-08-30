import { describe, it, expect } from 'vitest'
import { evaluateAssertion, evaluateAssertions } from './assert.js'
import type { ResponseData } from './types.js'

function makeRes(): ResponseData {
  return {
    status: 200,
    headers: { 'content-type': 'application/json', 'x-request-id': 'abc-123' },
    body: { code: 0, data: { name: 'hello', price: 99.5 } },
    rawBody: JSON.stringify({ code: 0, data: { name: 'hello', price: 99.5 } }),
    duration: 12,
  }
}

describe('evaluateAssertion - statusCode', () => {
  it('状态码相等通过', () => {
    const r = evaluateAssertion(makeRes(), { type: 'statusCode', expression: '', expected: '200' })
    expect(r.passed).toBe(true)
  })

  it('状态码不等失败', () => {
    const r = evaluateAssertion(makeRes(), { type: 'statusCode', expression: '', expected: '201' })
    expect(r.passed).toBe(false)
  })
})

describe('evaluateAssertion - jsonPath', () => {
  it('jsonPath eq', () => {
    const r = evaluateAssertion(makeRes(), {
      type: 'jsonPath',
      expression: '$.code',
      expected: '0',
    })
    expect(r.passed).toBe(true)
  })

  it('jsonPath contains', () => {
    const r = evaluateAssertion(makeRes(), {
      type: 'jsonPath',
      expression: '$.data.name',
      expected: 'ell',
      operator: 'contains',
    })
    expect(r.passed).toBe(true)
  })

  it('jsonPath 未命中失败', () => {
    const r = evaluateAssertion(makeRes(), {
      type: 'jsonPath',
      expression: '$.nope',
      expected: '0',
    })
    expect(r.passed).toBe(false)
  })
})

describe('evaluateAssertion - header / regex / 数值', () => {
  it('响应头断言', () => {
    const r = evaluateAssertion(makeRes(), {
      type: 'header',
      expression: 'X-Request-Id',
      expected: 'abc-123',
    })
    expect(r.passed).toBe(true)
  })

  it('正则断言', () => {
    const r = evaluateAssertion(makeRes(), {
      type: 'regex',
      expression: '"price":(\\d+\\.\\d+)',
      expected: '99.5',
    })
    expect(r.passed).toBe(true)
  })

  it('数值 gt 比较', () => {
    const r = evaluateAssertion(makeRes(), {
      type: 'jsonPath',
      expression: '$.data.price',
      expected: '90',
      operator: 'gt',
    })
    expect(r.passed).toBe(true)
  })
})

describe('evaluateAssertions', () => {
  it('全部通过返回 true', () => {
    const { passed } = evaluateAssertions(makeRes(), [
      { type: 'statusCode', expression: '', expected: '200' },
      { type: 'jsonPath', expression: '$.code', expected: '0' },
    ])
    expect(passed).toBe(true)
  })

  it('存在失败返回 false', () => {
    const { passed, results } = evaluateAssertions(makeRes(), [
      { type: 'statusCode', expression: '', expected: '200' },
      { type: 'statusCode', expression: '', expected: '500' },
    ])
    expect(passed).toBe(false)
    expect(results[1].passed).toBe(false)
  })
})
