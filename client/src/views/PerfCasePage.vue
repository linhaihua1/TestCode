<template>
  <a-card title="性能测试" :bordered="false">
    <template #extra>
      <a-button type="primary" @click="openCreate">
        <plus-outlined />新建用例
      </a-button>
    </template>
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="cases" tab="用例">
        <a-table
          :data-source="cases"
          :columns="caseColumns"
          row-key="id"
          :loading="loading"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'threads'">
              {{ record.threads }} 线程 / {{ record.rampUp }}s ramp-up
              <a-tag v-if="durationFor(record)" color="purple">{{ record.duration }}s</a-tag>
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
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="reports" tab="报告">
        <a-table
          :data-source="reports"
          :columns="reportColumns"
          row-key="id"
          :loading="loadingReports"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <router-link :to="{ name: 'perf-report-detail', params: { id: record.id } }">
                <a-button size="small" type="link">查看</a-button>
              </router-link>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="modal"
      :title="form.id ? '编辑用例' : '新建用例'"
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
        <a-row :gutter="8">
          <a-col :span="6">
            <a-form-item label="线程数">
              <a-input-number v-model:value="form.threads" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="Ramp-Up (s)">
              <a-input-number v-model:value="form.rampUp" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="循环次数">
              <a-input-number v-model:value="form.loops" :min="1" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="6">
            <a-form-item label="持续时间 (s)">
              <a-input-number v-model:value="form.duration" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="8">
          <a-col :span="12">
            <a-form-item label="思考时间 (ms)">
              <a-input-number v-model:value="form.thinkTime" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="加压方式">
              <a-select
                v-model:value="profileForm.loadProfile"
                :options="profileOptions"
              />
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { PerfApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { PerfCase, PerfReport } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const activeTab = ref<'cases' | 'reports'>('cases')
const cases = ref<PerfCase[]>([])
const reports = ref<PerfReport[]>([])
const loading = ref(false)
const loadingReports = ref(false)
const runningId = ref('')

const modal = ref(false)
const form = reactive<Partial<PerfCase>>({
  name: '',
  description: '',
  threads: 10,
  rampUp: 5,
  loops: 1,
  duration: 0,
  thinkTime: 0,
  onSampleError: 'continue',
  variables: [],
  steps: [],
  profile: { loadProfile: 'fixed' }
})
const profileForm = reactive({ loadProfile: 'fixed' })

const caseColumns = [
  { title: '名称', dataIndex: 'name' },
  { title: '压测参数', key: 'threads' },
  { title: '操作', key: 'action', width: 220 }
]

const reportColumns = [
  { title: '名称', dataIndex: 'name' },
  { title: '状态', key: 'status', width: 100 },
  { title: '开始时间', dataIndex: 'startedAt', width: 180 },
  { title: '操作', key: 'action', width: 100 }
]

const profileOptions = [
  { value: 'fixed', label: '固定并发' },
  { value: 'stepping', label: '阶梯加压' }
]

function durationFor(c: PerfCase) {
  return c.duration > 0
}

function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'orange' : 'red'
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    cases.value = await PerfApi.listCases(projectId.value)
  } finally {
    loading.value = false
  }
}

async function reloadReports() {
  if (!projectId.value) return
  loadingReports.value = true
  try {
    reports.value = await PerfApi.listReports(projectId.value)
  } finally {
    loadingReports.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    threads: 10,
    rampUp: 5,
    loops: 1,
    duration: 0,
    thinkTime: 0,
    variables: [],
    steps: []
  })
  profileForm.loadProfile = 'fixed'
  modal.value = true
}

function openEdit(record: PerfCase) {
  Object.assign(form, record)
  profileForm.loadProfile = record.profile?.loadProfile ?? 'fixed'
  modal.value = true
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  form.profile = { loadProfile: profileForm.loadProfile as any }
  if (form.id) {
    await PerfApi.updateCase(form.id, form)
  } else {
    await PerfApi.createCase({ ...form, projectId: projectId.value })
  }
  message.success('已保存')
  modal.value = false
  await reload()
}

async function run(record: PerfCase) {
  runningId.value = record.id
  try {
    await PerfApi.run(record.id)
    message.success('已投递到 RabbitMQ，等待执行机消费')
    activeTab.value = 'reports'
    await reloadReports()
  } finally {
    runningId.value = ''
  }
}

async function remove(record: PerfCase) {
  await PerfApi.deleteCase(record.id)
  await reload()
}

watch(projectId, () => {
  reload()
  reloadReports()
}, { immediate: false })
onMounted(() => {
  reload()
  reloadReports()
})
</script>