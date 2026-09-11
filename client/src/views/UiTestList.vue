<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">UI 用例</div>
      <a-space>
        <a-button v-can-write type="primary" @click="openCreate">
          <plus-outlined />新建用例
        </a-button>
      </a-space>
    </div>

    <!-- 用例列表 -->
    <div class="panel">
      <a-table
        :data-source="tests"
        :columns="columns"
        row-key="id"
        :loading="loading"
        @row-click="(r: any) => router.push({ name: 'ui-test-editor', params: { id: r.id } })"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'" @click.stop>
            <a-space>
              <a-button v-can-write size="small" type="link" @click="run(record)" :loading="runningId === record.id">
                <play-circle-outlined />运行
              </a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button v-can-write size="small" type="link" danger @click.stop>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { UiApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { UiTestCase } from '@/types'

const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const tests = ref<UiTestCase[]>([])
const loading = ref(false)
const runningId = ref('')

const modal = ref(false)
const form = reactive<Partial<UiTestCase>>({ name: '', description: '', baseUrl: '' })

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: 'BaseURL', dataIndex: 'baseUrl', ellipsis: true },
  { title: '步骤数', key: 'stepCount', width: 100, customRender: ({ record }: any) => record.steps?.length || 0 },
  { title: '操作', key: 'action', width: 160 }
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

async function remove(record: UiTestCase) {
  await UiApi.deleteTest(record.id)
  await reload()
}

watch(projectId, () => reload(), { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 用例列表行点击进编辑器，光标反馈 */
:deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
</style>
