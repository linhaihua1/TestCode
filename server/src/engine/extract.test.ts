import { describe, it, expect } from 'vitest'
import { extractValue, applyExtracts } from './extract.js'
import type { ResponseData } from './types.js'

function makeRes(overrides: Partial<ResponseData> = {}): ResponseData {
  return {
    status: 200,
    headers: { 'content-type': 'application/json', authorization: 'Bearer token123' },
    body: { data: { id: 42, name: 'foo', list: [{ a: 1 }, { a: 2 }] } },
    rawBody: JSON.stringify({ data: { id: 42, name: 'foo' } }),
    duration: 10,
    ...overrides,
  }
}

describe('extractValue - jsonPath', () => {
  it('提取对象字段', () => {
    const res = makeRes()
    expect(extractValue(res, { name: 'id', type: 'jsonPath', expression: '$.data.id' })).toBe('42')
  })

  it('提取字符串字段', () => {
    const res = makeRes()
    expect(extractValue(res, { name: 'n', type: 'jsonPath', expression: '$.data.name' })).toBe('foo')
  })

  it('提取数组首元素', () => {
    const res = makeRes()
    expect(extractValue(res, { name: 'a', type: 'jsonPath', expression: '$.data.list[0].a' })).toBe('1')
  })

  it('未命中返回 undefined', () => {
    const res = makeRes()
    expect(extractValue(res, { name: 'x', type: 'jsonPath', expression: '$.nope' })).toBeUndefined()
  })
})

describe('extractValue - header / regex', () => {
  it('提取响应头（大小写不敏感）', () => {
    const res = makeRes()
    expect(extractValue(res, { name: 't', type: 'header', expression: 'Authorization' })).toBe(
      'Bearer token123',
    )
  })

  it('正则提取捕获组', () => {
    const res = makeRes()
    expect(extractValue(res, { name: 'id', type: 'regex', expression: '"id":(\\d+)' })).toBe('42')
  })
})

describe('applyExtracts', () => {
  it('把提取结果写入上下文并返回', () => {
    const res = makeRes()
    const context: Record<string, string> = {}
    const extracted = applyExtracts(
      res,
      [
        { name: 'userId', type: 'jsonPath', expression: '$.data.id' },
        { name: 'missing', type: 'jsonPath', expression: '$.nope' },
      ],
      context,
    )
    expect(extracted).toEqual({ userId: '42' })
    expect(context.userId).toBe('42')
    expect(context.missing).toBeUndefined()
  })
})


describe('extractValue - invalid regex safety', () => {
  it('invalid regex expression does not crash', () => {
    const res = makeRes()
    expect(() => extractValue(res, { name: 'x', type: 'regex', expression: '[invalid(' })).not.toThrow()
    expect(extractValue(res, { name: 'x', type: 'regex', expression: '[invalid(' })).toBeUndefined()
  })
})
