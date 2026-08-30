import { describe, it, expect } from 'vitest'
import { resolveString, resolveTemplate } from './variables.js'

describe('resolveString', () => {
  it('替换已定义变量', () => {
    expect(resolveString('hello ${name}', { name: 'world' })).toBe('hello world')
  })

  it('替换多个变量', () => {
    expect(resolveString('${a}-${b}', { a: '1', b: '2' })).toBe('1-2')
  })

  it('未定义变量保持原样', () => {
    expect(resolveString('${missing}', {})).toBe('${missing}')
  })

  it('支持变量名前后空格', () => {
    expect(resolveString('${ name }', { name: 'x' })).toBe('x')
  })
})

describe('resolveTemplate', () => {
  it('递归替换嵌套对象', () => {
    const obj = { url: '${base}/api', body: { token: '${token}', n: 1 } }
    const out = resolveTemplate(obj, { base: 'http://x', token: 'abc' })
    expect(out).toEqual({ url: 'http://x/api', body: { token: 'abc', n: 1 } })
  })

  it('替换数组元素', () => {
    expect(resolveTemplate(['${a}', 'b'], { a: 'A' })).toEqual(['A', 'b'])
  })

  it('非字符串值原样返回', () => {
    expect(resolveTemplate(42, {})).toBe(42)
  })
})
