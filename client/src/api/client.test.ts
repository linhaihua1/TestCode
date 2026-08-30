import { describe, it, expect, vi, beforeEach } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: mocks.get,
      post: mocks.post,
      put: mocks.put,
      delete: mocks.delete,
    }),
    isAxiosError: () => false,
  },
}))

import { api } from './client'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('api client', () => {
  it('listProjects → GET /projects', async () => {
    mocks.get.mockResolvedValue({ data: [{ id: '1', name: 'p' }] })
    const result = await api.listProjects()
    expect(mocks.get).toHaveBeenCalledWith('/projects')
    expect(result).toEqual([{ id: '1', name: 'p' }])
  })

  it('createProject → POST /projects', async () => {
    mocks.post.mockResolvedValue({ data: { id: '2', name: 'x' } })
    await api.createProject({ name: 'x' })
    expect(mocks.post).toHaveBeenCalledWith('/projects', { name: 'x' })
  })

  it('listApis → GET /projects/:id/apis', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    await api.listApis('p1')
    expect(mocks.get).toHaveBeenCalledWith('/projects/p1/apis')
  })

  it('runScenario → POST /scenarios/:id/run with environmentId', async () => {
    mocks.post.mockResolvedValue({ data: { id: 'r1' } })
    await api.runScenario('s1', 'e1')
    expect(mocks.post).toHaveBeenCalledWith('/scenarios/s1/run', { environmentId: 'e1' })
  })

  it('updateScenarioSteps → PUT /scenarios/:id/steps', async () => {
    mocks.put.mockResolvedValue({ data: { ok: true } })
    const steps = [{ order: 0, apiCaseId: 'c1' }]
    await api.updateScenarioSteps('s1', steps)
    expect(mocks.put).toHaveBeenCalledWith('/scenarios/s1/steps', steps)
  })

  it('deleteProject → DELETE /projects/:id', async () => {
    mocks.delete.mockResolvedValue({ data: { ok: true } })
    await api.deleteProject('p1')
    expect(mocks.delete).toHaveBeenCalledWith('/projects/p1')
  })
})
