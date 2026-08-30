import { describe, it, expect } from 'vitest'
import { buildApp } from './app.js'

describe('GET /api/health', () => {
  it('returns ok status', async () => {
    const app = buildApp()
    const res = await app.inject({ method: 'GET', url: '/api/health' })

    expect(res.statusCode).toBe(200)
    expect(res.json()).toEqual({ status: 'ok', service: 'api-web-server' })

    await app.close()
  })
})
