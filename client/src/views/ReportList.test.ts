// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import Antd from 'ant-design-vue';
import ReportList from './ReportList.vue';
import { ReportApi } from '@/api';

// jsdom 缺失的浏览器 API（ant-design-vue 依赖）
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}
if (typeof globalThis.ResizeObserver === 'undefined') {
  (globalThis as any).ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {} }),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}));

vi.mock('@/stores/project', () => ({
  useProjectStore: () => ({ currentProjectId: 'p1' }),
}));

vi.mock('@/api', () => ({
  ReportApi: {
    list: vi.fn(),
    remove: vi.fn(),
    exportHtmlUrl: (id: string) => `/api/v1/reports/${id}/export/html`,
    exportPdfUrl: (id: string) => `/api/v1/reports/${id}/export/pdf`,
  },
}));

const listMock = vi.mocked(ReportApi.list);

async function mountPage() {
  const wrapper = mount(ReportList, {
    global: {
      plugins: [Antd],
      stubs: { 'router-link': true },
    },
  });
  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('ReportList 测试报告列表页', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('后端返回 PageResult 时正确取 records 渲染（回归：直接赋值导致 reduce 崩溃白屏）', async () => {
    listMock.mockResolvedValue({
      records: [
        {
          id: 'r1',
          projectId: 'p1',
          name: '登录流程回归',
          status: 'success',
          totalCases: 10,
          passedCases: 9,
          failedCases: 1,
          errorCases: 0,
          startedAt: '2026-10-18T10:00:00Z',
          triggerType: 'manual',
        },
      ],
      total: 37,
      page: 1,
      size: 100,
    } as any);

    const wrapper = await mountPage();
    const text = wrapper.text();

    expect(text).toContain('登录流程回归');
    // 报告总数取后端 total，而非当前页条数
    expect(text).toContain('37');
    expect(text).not.toContain('报告加载');
  });

  it('后端返回空列表时不崩溃', async () => {
    listMock.mockResolvedValue({ records: [], total: 0, page: 1, size: 100 } as any);

    const wrapper = await mountPage();

    expect(wrapper.text()).toContain('测试报告');
  });

  it('请求分页参数，避免只拿到默认 20 条', async () => {
    listMock.mockResolvedValue({ records: [], total: 0, page: 1, size: 100 } as any);

    await mountPage();

    expect(listMock).toHaveBeenCalledWith(
      expect.objectContaining({ projectId: 'p1', page: 1, size: 100 }),
    );
  });
});
