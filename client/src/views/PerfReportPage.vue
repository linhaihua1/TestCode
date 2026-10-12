<template>
  <div class="page report-page">
    <!-- 加载中：避免请求未返回时整页空白 -->
    <div v-if="loading" class="report-status">
      <a-spin size="large" />
      <p class="report-status__text">报告加载中…</p>
    </div>

    <!-- 加载失败 / 报告不存在：给出明确提示，而不是空白页 -->
    <a-result
      v-else-if="!report"
      status="warning"
      title="报告无法显示"
      :sub-title="errorMsg || '报告不存在或已被删除'"
    >
      <template #extra>
        <a-button type="primary" @click="() => router.back()">返回</a-button>
      </template>
    </a-result>

    <template v-else>
      <!-- 页头 -->
      <header class="page-header">
        <div class="page-title">
          <line-chart-outlined class="page-title__icon" />
          <span class="ellipsis">{{ report.name }}</span>
          <a-tag class="sub" :color="statusColor(report.status)">
            {{ report.status }}
          </a-tag>
        </div>
        <a-space>
          <a-button @click="() => router.back()"> <arrow-left-outlined />返回 </a-button>
        </a-space>
      </header>

      <!-- Allure 风格概览：成功样本 vs 错误样本 -->
      <AllureSummary :stats="perfStats" />

      <!-- 关键指标 -->
      <section class="stat-grid">
        <div class="stat-card">
          <div class="label">样本数</div>
          <div class="value tabular">{{ summary.sampleCount || 0 }}</div>
        </div>
        <div class="stat-card">
          <div class="label">错误数</div>
          <div class="value tabular" :class="{ error: (summary.errorCount || 0) > 0 }">
            {{ summary.errorCount || 0 }}
          </div>
        </div>
        <div class="stat-card">
          <div class="label">平均响应</div>
          <div class="value tabular primary">{{ summary.avg || 0 }}<span class="unit"> ms</span></div>
        </div>
        <div class="stat-card">
          <div class="label">TPS</div>
          <div class="value tabular">{{ tpsDisplay }}</div>
        </div>
      </section>

      <!-- 百分位指标 -->
      <section class="stat-grid">
        <div class="stat-card">
          <div class="label">P90</div>
          <div class="value tabular">{{ summary.p90 || 0 }}<span class="unit"> ms</span></div>
        </div>
        <div class="stat-card">
          <div class="label">P95</div>
          <div class="value tabular">{{ summary.p95 || 0 }}<span class="unit"> ms</span></div>
        </div>
        <div class="stat-card">
          <div class="label">P99</div>
          <div class="value tabular">{{ summary.p99 || 0 }}<span class="unit"> ms</span></div>
        </div>
        <div class="stat-card">
          <div class="label">MAX</div>
          <div class="value tabular">{{ summary.max || 0 }}<span class="unit"> ms</span></div>
        </div>
      </section>

      <!-- 执行失败原因 -->
      <a-alert
        v-if="report.status !== 'success' && report.message"
        class="report-alert"
        type="error"
        show-icon
        :message="report.message"
      />

      <!-- 响应时间分布图 -->
      <section class="panel report-panel">
        <div class="panel__head">响应时间分布</div>
        <div v-if="!series.length" class="report-chart report-chart--empty">暂无时序数据</div>
        <div v-else ref="chartEl" class="report-chart"></div>
      </section>

      <!-- 分接口统计 -->
      <section class="panel report-panel">
        <div class="panel__head">分接口统计</div>
        <a-table
          :data-source="labels"
          :columns="labelColumns"
          size="small"
          :pagination="false"
          :locale="{ emptyText: '暂无数据' }"
        />
      </section>

      <!-- 错误 TOP -->
      <section v-if="errors.length" class="panel report-panel">
        <div class="panel__head danger">错误 TOP</div>
        <a-list :data-source="errors" size="small">
          <template #renderItem="{ item }">
            <a-list-item class="error-item">
              <a-tag color="red" class="error-count">{{ item?.count ?? 0 }}</a-tag>
              <span class="ellipsis">{{ item?.message || '-' }}</span>
            </a-list-item>
          </template>
        </a-list>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 性能测试报告详情页：展示汇总指标、响应时间趋势、分接口统计与错误 TOP。
 *
 * <p>接口返回的 summary/series/labels/errors 由后端解析为结构化对象（Map/List）。
 * 此处再做一层「非预期类型 → 安全默认值」归一化，避免历史脏数据或字段形态变更时渲染中断。</p>
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { LineChartOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue';
import * as echarts from 'echarts';
import { PerfApi } from '@/api';
import { statusColor } from '@/constants';
import type { PerfReport } from '@/types';
import AllureSummary, { type StatusStat } from '@/components/AllureSummary.vue';

const route = useRoute();
const router = useRouter();

const report = ref<PerfReport | null>(null);
const loading = ref(true);
const errorMsg = ref('');
const chartEl = ref<HTMLElement | null>(null);

let chart: echarts.ECharts | null = null;

/** 把任意值归一化为数组：非数组一律退化为空数组 */
function toArray(v: unknown): any[] {
  return Array.isArray(v) ? v : [];
}

/** 汇总指标：非对象（null / 数组 / 字符串）一律退化为空对象 */
const summary = computed<Record<string, any>>(() => {
  const s = report.value?.summary;
  return s && typeof s === 'object' && !Array.isArray(s) ? (s as Record<string, any>) : {};
});

/** 时序数据点（过滤掉非对象元素，避免取字段时抛错） */
const series = computed(() =>
  toArray(report.value?.series).filter((p) => p && typeof p === 'object'),
);

/** 分接口统计 */
const labels = computed(() => toArray(report.value?.labels));

/** 错误 TOP */
const errors = computed(() => toArray(report.value?.errors));

/** Allure 风格状态统计：成功样本 / 错误样本 */
const perfStats = computed<StatusStat[]>(() => {
  const sampleCount = Number(summary.value.sampleCount) || 0;
  const errorCount = Number(summary.value.errorCount) || 0;
  return [
    { key: 'passed', label: '成功样本', value: Math.max(sampleCount - errorCount, 0) },
    { key: 'broken', label: '错误样本', value: errorCount },
    { key: 'failed', label: '失败', value: 0 },
    { key: 'skipped', label: '跳过', value: 0 },
  ];
});

/** TPS 展示值：数值型保留两位小数，非数值型原样输出 */
const tpsDisplay = computed(() => {
  const tps = summary.value.tps;
  return typeof tps === 'number' ? tps.toFixed(2) : (tps ?? 0);
});

const labelColumns = [
  { title: '接口', dataIndex: 'label' },
  { title: '样本数', dataIndex: 'count', width: 100 },
  {
    title: 'Avg',
    // 必须声明 dataIndex，否则 customRender 拿到的 text 为 undefined
    dataIndex: 'avg',
    width: 100,
    customRender: ({ text }: any) =>
      typeof text === 'number' ? `${text.toFixed(1)} ms` : '-',
  },
  { title: 'P90', dataIndex: 'p90', width: 100 },
  { title: 'P95', dataIndex: 'p95', width: 100 },
  { title: 'P99', dataIndex: 'p99', width: 100 },
];

function renderChart() {
  if (!chartEl.value || !series.value.length) return;
  try {
    chart = echarts.init(chartEl.value);
    chart.setOption({
      title: { text: '响应时间 vs 时间 (s)' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'value', name: '时间 (s)' },
      yAxis: { type: 'value', name: '耗时 (ms)' },
      series: [
        {
          type: 'scatter',
          data: series.value.map((s: any) => [s.ts, s.elapsed]),
        },
      ],
    });
  } catch (e) {
    // 图表渲染失败不应阻断整页展示
    console.warn('响应时间分布图渲染失败', e);
  }
}

function handleResize() {
  chart?.resize();
}

onMounted(async () => {
  try {
    report.value = (await PerfApi.getReport(route.params.id as string)) || null;
    if (!report.value) errorMsg.value = '报告不存在或已被删除';
  } catch (e: any) {
    report.value = null;
    errorMsg.value = e?.message || '报告加载失败';
  } finally {
    loading.value = false;
  }
  await nextTick();
  renderChart();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
/* 复用全局 .page 容器，但 AppLayout 已在外层加了 padding，这里避免重复 */
.report-page {
  padding: 0;
}

/* 加载/空态容器 */
.report-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  min-height: 320px;
}

.report-status__text {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--tx-3);
}

.report-alert {
  margin-bottom: var(--sp-5);
}

.page-title__icon {
  font-size: var(--fs-lg);
  color: var(--c-primary);
  flex-shrink: 0;
}

/* 自定义统计卡片单位后缀 */
.stat-card .unit {
  font-size: var(--fs-sm);
  font-weight: 400;
  color: var(--tx-3);
  margin-left: 2px;
}

/* 面板容器（复用 .panel，再加点自定义 head） */
.report-panel {
  padding: 0;
  margin-bottom: var(--sp-5);
  overflow: hidden;
}

.report-panel :deep(.ant-table-wrapper) {
  padding: var(--sp-2) var(--sp-5) var(--sp-5);
}

.panel__head {
  padding: var(--sp-3) var(--sp-5);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--tx-1);
  border-bottom: 1px solid var(--bd-subtle);
  background: var(--bg-subtle);
}

.panel__head.danger {
  color: var(--c-error);
}

.report-chart {
  width: 100%;
  height: 360px;
  padding: var(--sp-4);
}

.report-chart--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 160px;
  font-size: var(--fs-sm);
  color: var(--tx-3);
}

/* 错误条目 */
.error-item {
  font-size: var(--fs-sm);
  color: var(--tx-2);
}

.error-count {
  flex-shrink: 0;
  margin-right: var(--sp-3);
}
</style>
