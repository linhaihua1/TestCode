<template>
  <a-card title="UI 自动化" :bordered="false">
    <template #extra>
      <a-button type="primary" @click="openCreate">
        <plus-outlined />新建用例
      </a-button>
    </template>
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="tests" tab="用例">
        <a-table
          :data-source="tests"
          :columns="testColumns"
          row-key="id"
          :loading="loading"
          @row-click="(r: any) => router.push({ name: 'ui-test-editor', params: { id: r.id } })"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'" @click.stop>
              <a-space>
                <a-button size="small" type="link" @click="run(record)" :loading="runningId === record.id">
                  <play-circle-outlined />运行
                </a-button>
                <a-popconfirm title="确认删除？" @confirm="remove(record)">
                  <a-button size="small" type="link" danger @click.stop>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
      <a-tab-pane key="scenarios" tab="场景">
        <a-table :data-source="scenarios" :columns="scenarioColumns" row-key="id" :loading="loadingScenarios">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button size="small" type="link" @click="runScenario(record)" :loading="runningScenarioId === record.id">
                  <play-circle-outlined />运行
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <a-modal
      v-model:open="modal"
      title="新建用例"
      @ok="save"
      width="480"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :auto-size="{ minRows: 1, maxRows: 3 }" />
        </a-form-item>
        <a-form-item label="BaseURL">
          <a-input v-model:value="form.baseUrl" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { UiApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { UiTestCase, UiScenario } from '@/types'

const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const activeTab = ref<'tests' | 'scenarios'>('tests')
const tests = ref<UiTestCase[]>([])
const scenarios = ref<UiScenario[]>([])
const loading = ref(false)
const loadingScenarios = ref(false)
const runningId = ref('')
const runningScenarioId = ref('')

const modal = ref(false)
const form = reactive<Partial<UiTestCase>>({ name: '', description: '', baseUrl: '' })

const testColumns = [
  { title: '名称', dataIndex: 'name' },
  { title: 'BaseURL', dataIndex: 'baseUrl', ellipsis: true },
  { title: '步骤数', key: 'stepCount', width: 100, customRender: ({ record }: any) => record.steps?.length || 0 },
  { title: '操作', key: 'action', width: 160 }
]

const scenarioColumns = [
  { title: '名称', dataIndex: 'name' },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 100 }
]

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    tests.value = await UiApi.listTests(projectId.value)
  } finally {
    loading.value = false
  }
}

async function reloadScenarios() {
  if (!projectId.value) return
  loadingScenarios.value = true
  try {
    scenarios.value = await UiApi.listScenarios(projectId.value)
  } finally {
    loadingScenarios.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', description: '', baseUrl: '' })
  modal.value = true
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  const created = await UiApi.createTest({ ...form, projectId: projectId.value })
  message.success('已创建')
  modal.value = false
  await reload()
  router.push({ name: 'ui-test-editor', params: { id: created.id } })
}

async function run(record: UiTestCase) {
  runningId.value = record.id
  try {
    await UiApi.runTest(record.id)
    message.success('已投递到 RabbitMQ，等待执行机消费')
  } finally {
    runningId.value = ''
  }
}

async function runScenario(record: UiScenario) {
  runningScenarioId.value = record.id
  try {
    const list = await UiApi.runScenario(record.id)
    message.success(`已投递 ${list.length} 个用例`)
  } finally {
    runningScenarioId.value = ''
  }
}

async function remove(record: UiTestCase) {
  await UiApi.deleteTest(record.id)
  await reload()
}

watch(projectId, () => { reload(); reloadScenarios() }, { immediate: false })
onMounted(() => { reload(); reloadScenarios() })
</script>