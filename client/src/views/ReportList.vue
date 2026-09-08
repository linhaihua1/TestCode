<template>
  <a-card title="测试报告" :bordered="false">
    <template #extra>
      <a-space>
        <router-link to="/trend">
          <a-button type="link"><line-chart-outlined />趋势统计</a-button>
        </router-link>
        <a-button @click="reload" :loading="loading">
          <reload-outlined />刷新
        </a-button>
      </a-space>
    </template>

    <!-- 筛选 + 汇总 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-statistic title="报告总数" :value="reports.length" />
      </a-col>
      <a-col :span="6">
        <a-statistic
          title="近 7 天通过率"
          :value="recent7PassRate"
          :precision="2"
          suffix="%"
          :value-style="{ color: recent7PassRate >= 80 ? '#52c41a' : '#faad14' }"
        />
      </a-col>
      <a-col :span="6">
        <a-statistic title="总用例数" :value="totalCases" />
      </a-col>
      <a-col :span="6">
        <a-statistic title="总失败数" :value="totalFailed">
          <template #suffix>
            <span style="font-size: 12px; color: #888">含异常</span>
          </template>
        </a-statistic>
      </a-col>
    </a-row>

    <a-table
      :data-source="reports"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :pagination="{ pageSize: 20 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'triggerType'">
          <a-tag color="blue">{{ triggerLabel(record.triggerType) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'startedAt'">
          {{ formatTime(record.startedAt) }}
        </template>
        <template v-else-if="column.key === 'cases'">
          <span v-if="record.totalCases">
            <a-tag color="green">{{ record.passedCases || 0 }}</a-tag> /
            <a-tag color="red">{{ record.failedCases || 0 }}</a-tag> /
            <a-tag>{{ record.totalCases }}</a-tag>
          </span>
          <span v-else>-</span>
        </template>
        <template v-else-if="column.key === 'response'">
          {{ record.avgResponseTime || 0 }} ms
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <router-link :to="{ name: 'report-detail', params: { id: record.id } }">
              <a-button size="small" type="link">查看</a-button>
            </router-link>
            <a-dropdown>
              <a-button size="small" type="link">
                导出 <down-outlined />
              </a-button>
              <template #overlay>
                <a-menu @click="(e: any) => exportReport(record, e.key)">
                  <a-menu-item key="html">HTML</a-menu-item>
                  <a-menu-item key="pdf">PDF</a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
            <router-link :to="{ name: 'report-detail', params: { id: record.id }, hash: '#shares' }">
              <a-button size="small" type="link">分享</a-button>
            </router-link>
            <a-popconfirm title="确认删除？" @confirm="remove(record)">
              <a-button size="small" type="link" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  ReloadOutlined,
  LineChartOutlined,
  DownOutlined
} from '@ant-design/icons-vue'
import { ReportApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { Report } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const reports = ref<Report[]>([])
const loading = ref(false)

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '状态', key: 'status', width: 100 },
  { title: '触发', key: 'triggerType', width: 100 },
  { title: '通过/失败/总数', key: 'cases', width: 180 },
  { title: '平均响应', key: 'response', width: 120 },
  { title: '耗时 (ms)', key: 'duration', width: 110 },
  { title: '开始时间', key: 'startedAt', width: 180 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' }
]

const totalCases = computed(() =>
  reports.value.reduce((acc, r) => acc + (r.totalCases || 0), 0)
)
const totalFailed = computed(() =>
  reports.value.reduce(
    (acc, r) => acc + (r.failedCases || 0) + (r.errorCases || 0),
    0
  )
)

const recent7PassRate = computed(() => {
  const cutoff = Date.now() - 7 * 86400_000
  const recent = reports.value.filter(
    (r) => new Date(r.startedAt).getTime() >= cutoff
  )
  const total = recent.reduce((acc, r) => acc + (r.totalCases || 0), 0)
  const passed = recent.reduce((acc, r) => acc + (r.passedCases || 0), 0)
  return total === 0 ? 0 : (passed * 100) / total
})

function statusColor(s: string) {
  return s === 'passed' || s === 'success'
    ? 'green'
    : s === 'failed' ? 'red'
    : s === 'error' ? 'volcano'
    : s === 'running' ? 'blue'
    : 'default'
}

function triggerLabel(t?: string) {
  switch (t) {
    case 'manual': return '手动'
    case 'schedule': return '定时'
    case 'webhook': return 'Webhook'
    case 'api': return 'API'
    default: return '手动'
  }
}

function formatTime(iso: string) {
  return dayjs.utc(iso).local().format('YYYY-MM-DD HH:mm:ss')
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    reports.value = await ReportApi.list({ projectId: projectId.value })
  } finally {
    loading.value = false
  }
}

function exportReport(record: Report, format: 'html' | 'pdf') {
  const url = format === 'html'
    ? ReportApi.exportHtmlUrl(record.id)
    : ReportApi.exportPdfUrl(record.id)
  window.open(url, '_blank')
  message.success(`已导出 ${format.toUpperCase()}`)
}

async function remove(record: Report) {
  await ReportApi.remove(record.id)
  message.success('已删除')
  await reload()
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>