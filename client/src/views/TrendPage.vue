<template>
  <a-card title="测试趋势统计" :bordered="false">
    <template #extra>
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
    </template>

    <!-- 摘要卡片 -->
    <a-row :gutter="16" v-if="summary" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-statistic title="报告数" :value="summary.reportCount" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="用例总数" :value="summary.totalCases" />
      </a-col>
      <a-col :span="6">
        <a-statistic
          title="通过率"
          :value="summary.passRate"
          :precision="2"
          suffix="%"
          :value-style="{ color: summary.passRate >= 80 ? '#52c41a' : summary.passRate >= 60 ? '#faad14' : '#ff4d4f' }"
        />
      </a-col>
      <a-col :span="6">
        <a-statistic title="失败用例" :value="summary.failedCases + summary.errorCases">
          <template #suffix>
            <span style="font-size: 12px; color: #888">
              失败 {{ summary.failedCases }} / 异常 {{ summary.errorCases }}
            </span>
          </template>
        </a-statistic>
      </a-col>
    </a-row>

    <!-- ECharts 图表 -->
    <div ref="chartRef" class="trend-chart" v-show="!loading"></div>
    <a-empty v-if="!loading && trend.length === 0" description="所选时间范围暂无报告" />
  </a-card>
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