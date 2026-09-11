<template>
  <div class="page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="page-title">性能测试报告</div>
      <a-space>
        <a-button @click="reload">
          <reload-outlined />刷新
        </a-button>
      </a-space>
    </div>

    <!-- 报告列表 -->
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
          <template v-else-if="column.key === 'startedAt'">
            {{ formatTime(record.startedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <router-link :to="{ name: 'perf-report-detail', params: { id: record.id } }">
              <a-button size="small" type="link">查看</a-button>
            </router-link>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { PerfApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { PerfReport } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const reports = ref<PerfReport[]>([])
const loading = ref(false)

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '状态', key: 'status', width: 100 },
  { title: '开始时间', key: 'startedAt', width: 200 },
  { title: '操作', key: 'action', width: 100 }
]

function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'orange' : 'red'
}

function formatTime(iso?: string) {
  if (!iso) return '-'
  const d = new Date(iso)
  return isNaN(d.getTime()) ? iso : d.toLocaleString()
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    reports.value = await PerfApi.listReports(projectId.value)
  } finally {
    loading.value = false
  }
}

watch(projectId, () => reload(), { immediate: false })
onMounted(reload)
</script>

<style scoped>
</style>
