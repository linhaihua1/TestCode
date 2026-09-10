<template>
  <div class="page">
    <!-- 页头：标题 + 筛选/刷新 -->
    <div class="page-header">
      <div class="page-title">审计日志</div>
      <a-space>
        <a-input v-model:value="username" placeholder="按用户名筛选" style="width: 160px" @pressEnter="reload" />
        <a-select
          v-model:value="entityType"
          :options="entityOptions"
          style="width: 160px"
          @change="reload"
        />
        <a-button @click="reload"><reload-outlined />刷新</a-button>
      </a-space>
    </div>

    <!-- 审计日志表格 -->
    <div class="panel">
      <a-table
        :data-source="records"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :pagination="{ current: page, pageSize: size, total, onChange: onPageChange }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-tag color="blue">{{ record.action }}</a-tag>
          </template>
          <template v-else-if="column.key === 'beforeAfter'">
            <a-popover trigger="click" v-if="record.beforeJson">
              <a-button type="link" size="small">查看变更</a-button>
              <template #content>
                <pre style="max-width: 500px; max-height: 300px; overflow: auto">{{
                  JSON.stringify({ before: record.beforeJson, after: record.afterJson }, null, 2)
                }}</pre>
              </template>
            </a-popover>
          </template>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { AuditApi } from '@/api'
import type { AuditLog } from '@/types'

const records = ref<AuditLog[]>([])
const loading = ref(false)
const username = ref('')
const entityType = ref<string>()
const page = ref(1)
const size = ref(20)
const total = ref(0)

const columns = [
  { title: '时间', key: 'createdAt', width: 180 },
  { title: '用户', dataIndex: 'username', width: 120 },
  { title: '动作', key: 'action', width: 120 },
  { title: '实体', dataIndex: 'entityType', width: 120 },
  { title: '实体 ID', dataIndex: 'entityId', width: 200 },
  { title: 'IP', dataIndex: 'ip', width: 140 },
  { title: '前后值', key: 'beforeAfter', width: 100 }
]

const entityOptions = [
  { value: undefined, label: '全部实体' },
  { value: 'project', label: '项目' },
  { value: 'environment', label: '环境' },
  { value: 'global_variable', label: '全局变量' },
  { value: 'module', label: '模块' },
  { value: 'api', label: '接口' },
  { value: 'case', label: '用例' },
  { value: 'scenario', label: '场景' },
  { value: 'test_task', label: '测试任务' },
  { value: 'perf_case', label: '性能用例' },
  { value: 'ui_test', label: 'UI 用例' },
  { value: 'ui_scenario', label: 'UI 场景' },
  { value: 'user', label: '用户' }
]

function formatTime(iso: string) {
  return dayjs.utc(iso).local().format('YYYY-MM-DD HH:mm:ss')
}

async function reload() {
  loading.value = true
  try {
    const r = await AuditApi.list({
      username: username.value || undefined,
      entityType: entityType.value,
      page: page.value,
      size: size.value
    })
    records.value = r.records
    total.value = r.total
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  reload()
}

onMounted(reload)
</script>

<style scoped>
/* 审计动作标签：等宽字体便于区分 */
:deep(.ant-table-tbody > tr > td .ant-tag) {
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
</style>