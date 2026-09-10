<template>
  <div class="page">
    <!-- 页头：标题（无额外操作） -->
    <div class="page-header">
      <div class="page-title">UI 自动化报告</div>
    </div>

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
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'duration'">
            {{ record.duration }} ms
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button size="small" type="link" @click="showDetail(record)">查看</a-button>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal v-model:open="detailOpen" title="报告明细" width="900" :footer="null">
      <a-tabs v-model:active-key="detailTab">
        <a-tab-pane key="steps" tab="步骤明细">
          <a-list :data-source="(currentReport?.details as any[]) || []">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-space direction="vertical" style="width: 100%">
                  <a-space>
                    <a-tag :color="statusColor(item.status)">{{ item.status }}</a-tag>
                    <strong>{{ item.segment }} / {{ item.step?.name || '步骤' }}</strong>
                    <span style="color: #999">{{ item.durationMs }} ms</span>
                  </a-space>
                  <a-alert v-if="item.message" type="error" :message="item.message" />
                  <a-image
                    v-if="item.screenshot"
                    :width="320"
                    :src="`data:image/png;base64,${item.screenshot}`"
                  />
                </a-space>
              </a-list-item>
            </template>
          </a-list>
        </a-tab-pane>
        <a-tab-pane key="raw" tab="原始数据">
          <pre style="max-height: 500px; overflow: auto">{{ JSON.stringify(currentReport, null, 2) }}</pre>
        </a-tab-pane>
      </a-tabs>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { UiApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { UiReport } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const reports = ref<UiReport[]>([])
const loading = ref(false)
const detailOpen = ref(false)
const detailTab = ref('steps')
const currentReport = ref<UiReport | null>(null)

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '状态', key: 'status', width: 100 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '开始时间', dataIndex: 'startedAt', width: 180 },
  { title: '操作', key: 'action', width: 100 }
]

function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'orange' : 'red'
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
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 耗时列：等宽数字 */
:deep(.ant-table-tbody > tr > td) {
  font-variant-numeric: tabular-nums;
}
</style>