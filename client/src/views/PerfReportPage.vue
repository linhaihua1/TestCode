<template>
  <a-card :bordered="false" v-if="report">
    <a-page-header :title="report.name" @back="() => router.back()">
      <template #extra>
        <a-tag :color="statusColor(report.status)">{{ report.status }}</a-tag>
      </template>
    </a-page-header>

    <a-row :gutter="12">
      <a-col :span="6">
        <a-statistic title="样本数" :value="summary.sampleCount || 0" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="错误数" :value="summary.errorCount || 0" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="平均响应" :value="summary.avg || 0" suffix="ms" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="TPS" :value="summary.tps || 0" :precision="2" />
      </a-col>
    </a-row>

    <a-row :gutter="12" style="margin-top: 12px">
      <a-col :span="6">
        <a-statistic title="P90" :value="summary.p90 || 0" suffix="ms" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="P95" :value="summary.p95 || 0" suffix="ms" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="P99" :value="summary.p99 || 0" suffix="ms" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="MAX" :value="summary.max || 0" suffix="ms" />
      </a-col>
    </a-row>

    <a-divider>响应时间分布</a-divider>
    <div ref="chartEl" style="width: 100%; height: 360px"></div>

    <a-divider>分接口统计</a-divider>
    <a-table :data-source="report.labels || []" :columns="labelColumns" size="small" />

    <a-divider v-if="report.errors?.length">错误 TOP</a-divider>
    <a-list v-if="report.errors?.length" :data-source="report.errors" size="small">
      <template #renderItem="{ item }">
        <a-list-item>
          <a-space>
            <a-tag color="red">{{ item.count }}</a-tag>
            <span>{{ item.message }}</span>
          </a-space>
        </a-list-item>
      </template>
    </a-list>
  </a-card>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { PerfApi } from '@/api'
import type { PerfReport } from '@/types'

const route = useRoute()
const router = useRouter()

const report = ref<PerfReport | null>(null)
const chartEl = ref<HTMLElement | null>(null)

const summary = computed(() => (report.value?.summary as any) || {})

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