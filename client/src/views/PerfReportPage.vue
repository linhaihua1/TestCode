<template>
  <div class="page report-page" v-if="report">
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
        <a-button @click="() => router.back()">
          <arrow-left-outlined />返回
        </a-button>
      </a-space>
    </header>

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

    <!-- 响应时间分布图 -->
    <section class="panel report-panel">
      <div class="panel__head">响应时间分布</div>
      <div ref="chartEl" class="report-chart"></div>
    </section>

    <!-- 分接口统计 -->
    <section class="panel report-panel">
      <div class="panel__head">分接口统计</div>
      <a-table
        :data-source="report.labels || []"
        :columns="labelColumns"
        size="small"
        :pagination="false"
      />
    </section>

    <!-- 错误 TOP -->
    <section v-if="report.errors?.length" class="panel report-panel">
      <div class="panel__head danger">错误 TOP</div>
      <a-list :data-source="report.errors" size="small">
        <template #renderItem="{ item }">
          <a-list-item class="error-item">
            <a-tag color="red" class="error-count">{{ item.count }}</a-tag>
            <span class="ellipsis">{{ item.message }}</span>
          </a-list-item>
        </template>
      </a-list>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  LineChartOutlined, ArrowLeftOutlined
} from '@ant-design/icons-vue'
import * as echarts from 'echarts'
import { PerfApi } from '@/api'
import type { PerfReport } from '@/types'

const route = useRoute()
const router = useRouter()

const report = ref<PerfReport | null>(null)
const chartEl = ref<HTMLElement | null>(null)

const summary = computed(() => (report.value?.summary as any) || {})

/** TPS 展示值：数值型保留两位小数，非数值型原样输出 */
const tpsDisplay = computed(() => {
  const tps = summary.value.tps
  return typeof tps === 'number' ? tps.toFixed(2) : (tps ?? 0)
})

const labelColumns = [
  { title: '接口', dataIndex: 'label' },
  { title: '样本数', dataIndex: 'count', width: 100 },
  { title: 'Avg', key: 'avg', width: 100, customRender: ({ text }: any) => `${text.toFixed?.(1) || text} ms` },
  { title: 'P90', dataIndex: 'p90', width: 100 },
  { title: 'P95', dataIndex: 'p95', width: 100 },
  { title: 'P99', dataIndex: 'p99', width: 100 }
]

function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'orange' : 'red'
}

function renderChart() {
  if (!chartEl.value || !report.value?.series?.length) return
  const chart = echarts.init(chartEl.value)
  chart.setOption({
    title: { text: '响应时间 vs 时间 (s)' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value', name: '时间 (s)' },
    yAxis: { type: 'value', name: '耗时 (ms)' },
    series: [{
      type: 'scatter',
      data: (report.value.series as any[]).map((s: any) => [s.ts, s.elapsed])
    }]
  })
}

onMounted(async () => {
  report.value = await PerfApi.getReport(route.params.id as string)
  await nextTick()
  renderChart()
})
</script>

<style scoped>
/* 复用全局 .page 容器，但 AppLayout 已在外层加了 padding，这里避免重复 */
.report-page {
  padding: 0;
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
