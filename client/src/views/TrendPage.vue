<template>
  <div class="page">
    <!-- 页头：标题 + 时间维度切换 + 刷新 -->
    <div class="page-header">
      <div class="page-title">测试趋势统计</div>
      <a-space>
        <a-segmented
          v-model:value="days"
          :options="[7, 14, 30, 60, 90]"
          @change="reload"
        />
        <a-button @click="reload" :loading="loading">
          <reload-outlined />刷新
        </a-button>
      </a-space>
    </div>

    <!-- 摘要卡片：统一为 stat-grid -->
    <div v-if="summary" class="stat-grid">
      <div class="stat-card">
        <div class="label">报告数</div>
        <div class="value tabular">{{ summary.reportCount }}</div>
      </div>
      <div class="stat-card">
        <div class="label">用例总数</div>
        <div class="value tabular">{{ summary.totalCases }}</div>
      </div>
      <div class="stat-card">
        <div class="label">通过率</div>
        <div
          class="value tabular"
          :class="{
            success: summary.passRate >= 80,
            warning: summary.passRate >= 60 && summary.passRate < 80,
            error: summary.passRate < 60
          }"
        >
          {{ summary.passRate.toFixed(2) }}<span class="text-3" style="font-size: var(--fs-md); margin-left: 4px">%</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">失败用例</div>
        <div class="value error tabular">{{ summary.failedCases + summary.errorCases }}</div>
        <div class="text-3" style="font-size: var(--fs-xs); margin-top: var(--sp-1)">
          失败 {{ summary.failedCases }} / 异常 {{ summary.errorCases }}
        </div>
      </div>
    </div>

    <!-- ECharts 图表放在 panel 内，统一卡观感 -->
    <div class="panel" style="padding: var(--sp-5)">
      <div ref="chartRef" class="trend-chart" v-show="!loading"></div>
      <a-empty v-if="!loading && trend.length === 0" description="所选时间范围暂无报告" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
  ToolboxComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { ReportApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { TrendBucket, TrendSummary } from '@/types'

echarts.use([
  LineChart, BarChart,
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  DataZoomComponent, ToolboxComponent,
  CanvasRenderer
])

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const trend = ref<TrendBucket[]>([])
const summary = ref<TrendSummary | null>(null)
const days = ref<number>(30)
const loading = ref(false)
const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [t, s] = await Promise.all([
      ReportApi.trend({ projectId: projectId.value, days: days.value }),
      ReportApi.summary({ projectId: projectId.value, days: days.value })
    ])
    trend.value = t
    summary.value = s
    await nextTick()
    renderChart()
  } finally {
    loading.value = false
  }
}

function renderChart() {
  if (!chartRef.value || trend.value.length === 0) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value, undefined, { renderer: 'canvas' })
  }
  const dates = trend.value.map((b) => b.date)
  chartInstance.setOption({
    title: { text: `近 ${days.value} 天测试通过率趋势`, left: 'left' },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        const date = params[0].axisValue
        const lines = [`<b>${date}</b>`]
        params.forEach((p: any) => {
          lines.push(`${p.marker}${p.seriesName}: ${p.value}${p.seriesName === '通过率' ? '%' : ''}`)
        })
        return lines.join('<br/>')
      }
    },
    legend: { top: 30, data: ['通过率', '用例总数', '失败数', '平均响应时间'] },
    grid: { left: 60, right: 80, top: 80, bottom: 60 },
    toolbox: {
      feature: {
        saveAsImage: { title: '保存为图片' }
      }
    },
    xAxis: { type: 'category', data: dates, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '通过率(%)', max: 100, position: 'left' },
      { type: 'value', name: '数量', position: 'right' }
    ],
    dataZoom: [{ type: 'inside', start: 0, end: 100 }, { type: 'slider', height: 24 }],
    series: [
      {
        name: '通过率',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        data: trend.value.map((b) => Number(b.passRate.toFixed(2))),
        itemStyle: { color: '#52c41a' },
        areaStyle: { color: 'rgba(82, 196, 26, 0.1)' }
      },
      {
        name: '用例总数',
        type: 'bar',
        yAxisIndex: 1,
        data: trend.value.map((b) => b.totalCases),
        itemStyle: { color: '#1890ff' }
      },
      {
        name: '失败数',
        type: 'bar',
        yAxisIndex: 1,
        data: trend.value.map((b) => b.failedCases + b.errorCases),
        itemStyle: { color: '#ff4d4f' }
      },
      {
        name: '平均响应时间',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: trend.value.map((b) => b.avgResponseTime),
        itemStyle: { color: '#fa8c16' }
      }
    ]
  })
}

function handleResize() {
  chartInstance?.resize()
}

watch(projectId, reload)
watch(days, reload)

onMounted(() => {
  window.addEventListener('resize', handleResize)
  reload()
})
</script>

<style scoped>
.trend-chart {
  width: 100%;
  height: 60vh;
  min-height: 400px;
}
</style>