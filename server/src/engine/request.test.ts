import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { executeRequest } from './request.js'

let server: http.Server
let baseUrl: string

beforeAll(async () => {
  server = http.createServer((req, res) => {
    let body = ''
    req.on('data', (chunk) => (body += chunk))
    req.on('end', () => {
      if (req.url?.startsWith('/status/404')) {
        res.statusCode = 404
        res.end('not found')
        return
      }
      res.setHeader('Content-Type', 'application/json')
      res.setHeader('X-Custom', 'yes')
      res.end(
        JSON.stringify({
          method: req.method,
          url: req.url,
          body,
          token: req.headers['x-token'],
        }),
      )
    })
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  const addr = server.address() as AddressInfo
  baseUrl = `http://127.0.0.1:${addr.port}`
})

afterAll(async () => {
  await new Promise<void>((resolve) => server.close(() => resolve()))
})

describe('executeRequest', () => {
  it('GET 请求并解析 JSON 响应', async () => {
    const res = await executeRequest({ method: 'GET', url: `${baseUrl}/echo` })
    expect(res.status).toBe(200)
    expect((res.body as { method: string }).method).toBe('GET')
    expect(res.headers['x-custom']).toBe('yes')
  })

  it('POST 传递 body 与 header', async () => {
    const res = await executeRequest({
      method: 'POST',
      url: `${baseUrl}/echo`,
      headers: { 'X-Token': 'abc' },
      body: { hello: 'world' },
    })
    const body = res.body as { token: string; body: string }
    expect(body.token).toBe('abc')
    expect(JSON.parse(body.body)).toEqual({ hello: 'world' })
  })

  it('query 参数拼接到 URL', async () => {
    const res = await executeRequest({
      method: 'GET',
      url: `${baseUrl}/echo`,
      query: { a: '1', b: '2' },
    })
    const body = res.body as { url: string }
    expect(body.url).toContain('a=1')
    expect(body.url).toContain('b=2')
  })

  it('404 状态码不抛错，返回 status', async () => {
    const res = await executeRequest({ method: 'GET', url: `${baseUrl}/status/404` })
    expect(res.status).toBe(404)
    expect(res.rawBody).toBe('not found')
  })
})
