// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { nextTick } from 'vue';
import Antd from 'ant-design-vue';
import PerfReportPage from './PerfReportPage.vue';
import { PerfApi } from '@/api';

// jsdom 未实现 matchMedia / ResizeObserver，ant-design-vue 的响应式断点与表格依赖它们
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

// vue-router：页面只用到 params.id 与 router.back()
const { back } = vi.hoisted(() => ({ back: vi.fn() }));
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'r1' } }),
  useRouter: () => ({ back }),
}));

// echarts 依赖 canvas，jsdom 下不可用，替身只记录调用
const { initMock } = vi.hoisted(() => ({ initMock: vi.fn() }));
vi.mock('echarts', () => ({
  init: initMock,
}));

vi.mock('@/api', () => ({
  PerfApi: { getReport: vi.fn() },
}));

const getReport = vi.mocked(PerfApi.getReport);

/** 挂载并等待 onMounted 内的异步请求与后续渲染完成 */
async function mountPage() {
  const wrapper = mount(PerfReportPage, { global: { plugins: [Antd] } });
  await flushPromises();
  await nextTick();
  return wrapper;
}

describe('PerfReportPage 性能测试报告详情页', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    initMock.mockReturnValue({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() });
  });

  it('结构化报告数据正常渲染出全部区块（回归：曾因 series 为字符串调用 .map 而整页白屏）', async () => {
    getReport.mockResolvedValue({
      id: 'r1',
      projectId: 'p1',
      name: 'E2E压测用例',
      status: 'success',
      duration: 9270,
      startedAt: '2026-10-12T14:50:28Z',
      summary: { sampleCount: 10, errorCount: 2, avg: 1579, p90: 2918, p95: 3845, p99: 3845, max: 3845, min: 652, tps: 0.9258 },
      series: [
        { ts: 0, elapsed: 1145 },
        { ts: 1.5, elapsed: 1380 },
      ],
      labels: [{ label: 'HTTP', count: 10, avg: 1579, p90: 2918, p95: 3845, p99: 3845 }],
      errors: [{ count: 10, message: 'HTTP 404' }],
    } as any);

    const wrapper = await mountPage();
    const text = wrapper.text();

    // 关键指标区块
    expect(text).toContain('E2E压测用例');
    expect(text).toContain('样本数');
    expect(text).toContain('1579'); // summary.avg
    expect(text).toContain('0.93'); // tps 保留两位小数
    // 分接口统计表格（Avg 列必须有 dataIndex，否则单元格渲染会抛错）
    expect(text).toContain('分接口统计');
    expect(text).toContain('HTTP');
    expect(text).toContain('1579.0 ms');
    // 错误 TOP
    expect(text).toContain('错误 TOP');
    expect(text).toContain('HTTP 404');
    // 成功后不再显示加载中
    expect(text).not.toContain('报告加载中');
    // 有 series 时初始化图表
    expect(initMock).toHaveBeenCalledTimes(1);
  });

  it('summary/series/labels/errors 为 JSON 字符串（历史脏数据）时不崩溃，主体仍渲染', async () => {
    getReport.mockResolvedValue({
      id: 'r1',
      projectId: 'p1',
      name: '旧格式报告',
      status: 'failed',
      duration: 100,
      startedAt: '2026-10-12T14:50:28Z',
      summary: '{"sampleCount":10,"errorCount":3}',
      series: '[]',
      labels: '[]',
      errors: '[]',
    } as any);

    const wrapper = await mountPage();
    const text = wrapper.text();

    // 页面骨架必须可见，绝不能是空白页
    expect(text).toContain('旧格式报告');
    expect(text).toContain('分接口统计');
    expect(text).toContain('暂无时序数据');
    expect(text).not.toContain('报告加载中');
  });

  it('报告为空（接口返回 null）时给出明确提示而非白屏', async () => {
    getReport.mockResolvedValue(null as any);

    const wrapper = await mountPage();
    const text = wrapper.text();

    expect(text).toContain('报告无法显示');
    expect(text).toContain('报告不存在或已被删除');
  });

  it('接口报错时展示失败原因而非白屏', async () => {
    getReport.mockRejectedValue(new Error('报告不存在'));

    const wrapper = await mountPage();
    const text = wrapper.text();

    expect(text).toContain('报告无法显示');
    expect(text).toContain('报告不存在');
  });

  it('series 为空时不初始化图表，展示占位文案', async () => {
    getReport.mockResolvedValue({
      id: 'r1',
      projectId: 'p1',
      name: '空报告',
      status: 'success',
      duration: 0,
      startedAt: '2026-10-12T14:50:28Z',
      summary: {},
      series: [],
      labels: [],
      errors: [],
    } as any);

    const wrapper = await mountPage();

    expect(wrapper.text()).toContain('暂无时序数据');
    expect(initMock).not.toHaveBeenCalled();
  });
});
