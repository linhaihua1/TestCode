import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

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
      // 拦截器（client.ts 会注册 request/response 拦截器）
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    }),
    isAxiosError: () => false,
  },
}))

import { api, filenameFromDisposition } from './client'

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

  it('login → POST /auth/login', async () => {
    mocks.post.mockResolvedValue({ data: { token: 't', user: { id: '1', username: 'admin' } } })
    const result = await api.login('admin', 'admin@123')
    expect(mocks.post).toHaveBeenCalledWith('/auth/login', {
      username: 'admin',
      password: 'admin@123',
    })
    expect(result.token).toBe('t')
  })

  it('listUsers → GET /users', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    await api.listUsers()
    expect(mocks.get).toHaveBeenCalledWith('/users')
  })

  it('resetUserPassword → PUT /users/:id/password', async () => {
    mocks.put.mockResolvedValue({ data: { ok: true } })
    await api.resetUserPassword('u1', 'newpass')
    expect(mocks.put).toHaveBeenCalledWith('/users/u1/password', { newPassword: 'newpass' })
  })
})

describe('filenameFromDisposition', () => {
  it('优先解析 RFC 5987 filename*（支持中文）', () => {
    const header = `attachment; filename="case.jmx"; filename*=UTF-8''${encodeURIComponent('登录压测.jmx')}`
    expect(filenameFromDisposition(header, 'fb.jmx')).toBe('登录压测.jmx')
  })

  it('无 filename* 时回退 filename=', () => {
    expect(filenameFromDisposition('attachment; filename="plan.jmx"', 'fb')).toBe('plan.jmx')
    expect(filenameFromDisposition('attachment; filename=plan.jmx', 'fb')).toBe('plan.jmx')
  })

  it('缺失或非法时返回兜底名，兼容数组形式', () => {
    expect(filenameFromDisposition(undefined, 'fb.jmx')).toBe('fb.jmx')
    expect(filenameFromDisposition('', 'fb.jmx')).toBe('fb.jmx')
    expect(filenameFromDisposition(['attachment; filename="x.jmx"'], 'fb')).toBe('x.jmx')
    expect(filenameFromDisposition("filename*=UTF-8''%%bad%%", 'fb.jmx')).toBe('fb.jmx')
  })
})

describe('perf api（性能测试）', () => {
  it('getPerfEnv → GET /perf-env', async () => {
    mocks.get.mockResolvedValue({ data: { available: true, source: 'embedded', home: 'x', javaHome: null } })
    const r = await api.getPerfEnv()
    expect(mocks.get).toHaveBeenCalledWith('/perf-env')
    expect(r.available).toBe(true)
  })

  it('listPerfCases / createPerfCase / updatePerfCase / deletePerfCase', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    await api.listPerfCases('p1')
    expect(mocks.get).toHaveBeenCalledWith('/projects/p1/perf-cases')

    mocks.post.mockResolvedValue({ data: { id: 'c1' } })
    await api.createPerfCase('p1', { name: '压测', threads: 10 })
    expect(mocks.post).toHaveBeenCalledWith('/projects/p1/perf-cases', { name: '压测', threads: 10 })

    mocks.put.mockResolvedValue({ data: { id: 'c1' } })
    await api.updatePerfCase('c1', { threads: 20 })
    expect(mocks.put).toHaveBeenCalledWith('/perf-cases/c1', { threads: 20 })

    mocks.delete.mockResolvedValue({ data: { ok: true } })
    await api.deletePerfCase('c1')
    expect(mocks.delete).toHaveBeenCalledWith('/perf-cases/c1')
  })

  it('runPerfCase → POST /perf-cases/:id/run（不限时）', async () => {
    mocks.post.mockResolvedValue({ data: { id: 'r1', status: 'success' } })
    await api.runPerfCase('c1')
    expect(mocks.post).toHaveBeenCalledWith('/perf-cases/c1/run', {}, { timeout: 0 })
    await api.runPerfCase('c1', 60000)
    expect(mocks.post).toHaveBeenLastCalledWith('/perf-cases/c1/run', { timeoutMs: 60000 }, { timeout: 0 })
  })

  it('importJmeter → POST 多文件', async () => {
    mocks.post.mockResolvedValue({ data: { created: [{ id: 'c1', name: 'n' }], failed: [] } })
    const files = [{ filename: 'a.jmx', content: '<x/>' }]
    const r = await api.importJmeter('p1', files)
    expect(mocks.post).toHaveBeenCalledWith('/projects/p1/perf-cases/import', { files })
    expect(r.created).toHaveLength(1)
  })

  it('listPerfReports / getPerfReport / deletePerfReport', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    await api.listPerfReports('p1')
    expect(mocks.get).toHaveBeenCalledWith('/projects/p1/perf-reports')
    await api.getPerfReport('r1')
    expect(mocks.get).toHaveBeenLastCalledWith('/perf-reports/r1')
    mocks.delete.mockResolvedValue({ data: { ok: true } })
    await api.deletePerfReport('r1')
    expect(mocks.delete).toHaveBeenCalledWith('/perf-reports/r1')
  })
})

describe('perf 导出下载', () => {
  let lastDownload = ''

  const stubBrowser = () => {
    vi.stubGlobal('URL', { createObjectURL: () => 'blob:mock', revokeObjectURL: () => {} })
    vi.stubGlobal('document', {
      createElement: () => {
        const el = {
          href: '',
          download: '',
          click() {
            lastDownload = el.download
          },
          remove() {},
        }
        return el
      },
      body: { appendChild: () => {} },
    })
  }

  beforeEach(() => {
    lastDownload = ''
    stubBrowser()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('exportJmx 请求文本并按响应头文件名触发下载', async () => {
    mocks.get.mockResolvedValue({
      data: '<?xml version="1.0"?><jmeterTestPlan/>',
      headers: { 'content-disposition': `attachment; filename="x.jmx"; filename*=UTF-8''${encodeURIComponent('登录.jmx')}` },
    })
    await api.exportJmx('c1', '兜底')
    expect(mocks.get).toHaveBeenCalledWith('/perf-cases/c1/export', { responseType: 'text' })
    expect(lastDownload).toBe('登录.jmx')
  })

  it('exportJmxBundle → POST 选中 id 列表', async () => {
    mocks.post.mockResolvedValue({ data: '<jmeterTestPlan/>', headers: {} })
    await api.exportJmxBundle('p1', ['a', 'b'], '批量计划')
    expect(mocks.post).toHaveBeenCalledWith(
      '/projects/p1/perf-cases/export',
      { ids: ['a', 'b'], planName: '批量计划' },
      { responseType: 'text' },
    )
    expect(lastDownload).toBe('批量计划.jmx')
  })

  it('导出失败时解出后端文本错误体中的 message', async () => {
    mocks.get.mockRejectedValue(
      Object.assign(new Error('Request failed with status code 404'), {
        response: { status: 404, data: JSON.stringify({ error: '压测用例不存在' }) },
      }),
    )
    await expect(api.exportJmx('nope', 'x')).rejects.toThrow('压测用例不存在')
  })

  it('导出失败且错误体非 JSON 时回退原始错误信息', async () => {
    mocks.get.mockRejectedValue(
      Object.assign(new Error('Network Error'), { response: { status: 500, data: 'boom' } }),
    )
    await expect(api.exportJmx('c1', 'x')).rejects.toThrow('Network Error')
  })
})
