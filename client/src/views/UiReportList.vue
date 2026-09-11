<template>
  <div class="page">
    <!-- 页头：标题 -->
    <div class="page-header">
      <div class="page-title">UI 自动化报告</div>
      <a-space>
        <a-button @click="reload" :loading="loading">
          <reload-outlined />刷新
        </a-button>
      </a-space>
    </div>

    <!-- Allure 风格汇总 -->
    <AllureSummary :stats="statusStats" />

    <!-- 报告表格 -->
    <div class="panel">
      <a-table
        :data-source="reports"
        :columns="columns"
        row-key="id"
        :loading="loading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)" class="status-pill">
              <span class="status-dot" :style="{ background: statusDotColor(record.status) }" />
              {{ statusText(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'duration'">
            {{ record.duration }} ms
          </template>
          <template v-else-if="column.key === 'startedAt'">
            {{ formatDate(record.startedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button size="small" type="link" @click="showDetail(record)">查看</a-button>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 报告详情弹窗 -->
    <a-modal
      v-model:open="detailOpen"
      :title="currentReport?.name || '报告明细'"
      width="920"
      :footer="null"
    >
      <!-- 弹窗内概览 -->
      <AllureSummary v-if="currentReport" :stats="detailStats" />

      <a-tabs v-model:active-key="detailTab">
        <a-tab-pane key="steps" tab="步骤明细">
          <a-timeline v-if="stepDetails.length">
            <a-timeline-item
              v-for="(item, idx) in stepDetails"
              :key="idx"
              :color="timelineColor(item.status)"
            >
              <div class="step-line">
                <a-tag :color="statusColor(item.status)" class="step-line__tag">
                  {{ statusText(item.status) }}
                </a-tag>
                <strong class="step-line__name">{{ item.segment }} / {{ item.step?.name || '步骤' }}</strong>
                <span class="step-line__dur">{{ item.durationMs }} ms</span>
              </div>
              <a-alert
                v-if="item.message"
                type="error"
                :message="item.message"
                class="step-line__error"
                show-icon
              />
              <a-image
                v-if="item.screenshot"
                :width="320"
                :src="`data:image/png;base64,${item.screenshot}`"
                class="step-line__shot"
              />
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无步骤明细" />
        </a-tab-pane>
        <a-tab-pane key="raw" tab="原始数据">
          <pre class="raw-pre">{{ JSON.stringify(currentReport, null, 2) }}</pre>
        </a-tab-pane>
      </a-tabs>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { UiApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { UiReport } from '@/types'
import AllureSummary, { type StatusStat } from '@/components/AllureSummary.vue'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const reports = ref<UiReport[]>([])
const loading = ref(false)
const detailOpen = ref(false)
const detailTab = ref('steps')
const currentReport = ref<UiReport | null>(null)

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '状态', key: 'status', width: 110 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '开始时间', key: 'startedAt', width: 180 },
  { title: '操作', key: 'action', width: 100 }
]

/** 报告列表整体状态统计 */
const statusStats = computed<StatusStat[]>(() => {
  const count = (s: string) => reports.value.filter((r) => r.status === s).length
  return [
    { key: 'passed', label: '通过', value: count('success') },
    { key: 'failed', label: '失败', value: count('failed') },
    { key: 'broken', label: '异常', value: count('error') },
    { key: 'skipped', label: '跳过', value: count('skipped') }
  ]
})

/** 当前报告详情步骤 */
const stepDetails = computed<any[]>(() => (currentReport.value?.details as any[]) || [])

/** 详情弹窗内的状态统计（从步骤推断） */
const detailStats = computed<StatusStat[]>(() => {
  const count = (s: string) => stepDetails.value.filter((d) => d.status === s).length
  return [
    { key: 'passed', label: '通过', value: count('success') },
    { key: 'failed', label: '失败', value: count('failed') },
    { key: 'broken', label: '异常', value: count('error') },
    { key: 'skipped', label: '跳过', value: count('skipped') }
  ]
})

function formatDate(iso?: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}

function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'red' : s === 'error' ? 'orange' : 'default'
}

function statusDotColor(s: string) {
  return s === 'success' ? '#16a34a' : s === 'failed' ? '#dc2626' : s === 'error' ? '#ea580c' : '#94a3b8'
}

function statusText(s: string) {
  return s === 'success' ? '通过' : s === 'failed' ? '失败' : s === 'error' ? '异常' : s === 'skipped' ? '跳过' : (s || '未知')
}

function timelineColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'red' : s === 'error' ? 'orange' : 'gray'
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    reports.value = await UiApi.listReports(projectId.value)
  } finally {
    loading.value = false
  }
}

async function showDetail(record: UiReport) {
  currentReport.value = await UiApi.getReport(record.id)
  detailOpen.value = true
  detailTab.value = 'steps'
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 状态标签（带圆点） */
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* 步骤时间线 */
.step-line {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.step-line__tag {
  margin: 0;
}

.step-line__name {
  color: var(--tx-1);
}

.step-line__dur {
  margin-left: auto;
  color: var(--tx-3);
  font-variant-numeric: tabular-nums;
}

.step-line__error {
  margin-top: var(--sp-2);
}

.step-line__shot {
  margin-top: var(--sp-2);
  border-radius: var(--rd-md);
}

.raw-pre {
  max-height: 480px;
  overflow: auto;
  margin: 0;
  padding: var(--sp-3);
  background: var(--bg-subtle);
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
}
</style>
