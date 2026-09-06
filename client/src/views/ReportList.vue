<template>
  <a-card title="测试报告" :bordered="false">
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
          <a-space>
            <router-link :to="{ name: 'report-detail', params: { id: record.id } }">
              <a-button size="small" type="link">查看</a-button>
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
  { title: '耗时', key: 'duration', width: 100, customRender: ({ text }: any) => `${text} ms` },
  { title: '开始时间', key: 'startedAt', width: 180 },
  { title: '操作', key: 'action', width: 140 }
]

function statusColor(s: string) {
  return s === 'success' ? 'green' : 'volcano'
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

async function remove(record: Report) {
  await ReportApi.remove(record.id)
  message.success('已删除')
  await reload()
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>