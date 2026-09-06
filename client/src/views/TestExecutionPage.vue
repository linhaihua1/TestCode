<template>
  <a-card title="测试任务" :bordered="false">
    <template #extra>
      <a-button type="primary" @click="openCreate">
        <plus-outlined />新建任务
      </a-button>
    </template>
    <a-table
      :data-source="tasks"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :expanded-row-keys="expandedKeys"
      @expand="onExpand"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-switch
            :checked="record.enabled"
            @change="toggle(record)"
          />
        </template>
        <template v-else-if="column.key === 'cronExpr'">
          <span v-if="record.cronExpr">{{ record.cronExpr }}</span>
          <a-tag v-else color="default">手动</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button size="small" type="link" @click="run(record)" :loading="runningId === record.id">
              <play-circle-outlined />运行
            </a-button>
            <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确认删除？" @confirm="remove(record)">
              <a-button size="small" type="link" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
      <template #expandedRowRender="{ record }">
        <a-table
          :data-source="runsByTask[record.id] || []"
          :columns="runColumns"
          row-key="id"
          size="small"
          :pagination="false"
        >
          <template #bodyCell="{ column, run }">
            <template v-if="column.key === 'result'">
              <a-tag :color="resultColor(run.result)">{{ run.result }}</a-tag>
            </template>
            <template v-else-if="column.key === 'duration'">
              {{ run.duration }} ms
            </template>
          </template>
        </a-table>
      </template>
    </a-table>

    <a-modal
      v-model:open="modal"
      :title="form.id ? '编辑任务' : '新建任务'"
      @ok="save"
      width="640"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :auto-size="{ minRows: 1, maxRows: 3 }" />
        </a-form-item>
        <a-form-item label="用例 ID（每行一个或英文逗号分隔）" required>
          <a-textarea
            v-model:value="caseIdsText"
            :auto-size="{ minRows: 2, maxRows: 6 }"
            placeholder="abc123, def456"
          />
        </a-form-item>
        <a-row :gutter="8">
          <a-col :span="12">
            <a-form-item label="超时 (ms)">
              <a-input-number v-model:value="form.timeoutMs" :min="1000" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="失败重试次数">
              <a-input-number v-model:value="form.retryCount" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="Cron 表达式（留空为手动）">
          <a-input v-model:value="form.cronExpr" placeholder="0 0 2 * * ? (Quartz Cron)" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
        <a-form-item label="回调通知 URL">
          <a-input v-model:value="form.notifyUrl" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { TestTaskApi, CaseApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { TestTask, TestTaskRun } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const tasks = ref<TestTask[]>([])
const runsByTask = ref<Record<string, TestTaskRun[]>>({})
const expandedKeys = ref<string[]>([])
const loading = ref(false)
const runningId = ref<string>('')

const modal = ref(false)
const form = reactive<Partial<TestTask>>({
  name: '',
  description: '',
  timeoutMs: 300000,
  retryCount: 0,
  cronExpr: '',
  enabled: true,
  notifyUrl: '',
  baseUrl: ''
})
const caseIdsText = ref('')

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '调度', key: 'cronExpr', width: 160 },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '操作', key: 'action', width: 220 }
]

const runColumns = [
  { title: '状态', key: 'result', width: 100 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '开始时间', dataIndex: 'startedAt' }
]

function resultColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'red' : s === 'error' ? 'volcano' : 'blue'
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    tasks.value = await TestTaskApi.list(projectId.value)
  } finally {
    loading.value = false
  }
}

async function onExpand(expanded: boolean, record: TestTask) {
  if (expanded) {
    runsByTask.value[record.id] = await TestTaskApi.runs(record.id)
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    timeoutMs: 300000,
    retryCount: 0,
    cronExpr: '',
    enabled: true,
    notifyUrl: '',
    baseUrl: ''
  })
  caseIdsText.value = ''
  modal.value = true
}

function openEdit(record: TestTask) {
  Object.assign(form, record)
  caseIdsText.value = (record.caseIds || []).join(', ')
  modal.value = true
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  const ids = caseIdsText.value.split(/[\n,]+/).map((s) => s.trim()).filter(Boolean)
  const payload = { ...form, caseIds: ids, projectId: projectId.value }
  if (form.id) {
    await TestTaskApi.update(form.id, payload)
  } else {
    await TestTaskApi.create(payload)
  }
  message.success('已保存')
  modal.value = false
  await reload()
}

async function run(record: TestTask) {
  runningId.value = record.id
  try {
    const runs = await TestTaskApi.run(record.id)
    message.success(`已投递 ${runs.length} 个用例到 RabbitMQ`)
    runsByTask.value[record.id] = runs
    expandedKeys.value = [record.id]
  } finally {
    runningId.value = ''
  }
}

async function toggle(record: TestTask) {
  await TestTaskApi.toggle(record.id)
  await reload()
}

async function remove(record: TestTask) {
  await TestTaskApi.remove(record.id)
  await reload()
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>