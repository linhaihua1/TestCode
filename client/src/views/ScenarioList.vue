<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">接口场景</div>
      <a-space>
        <a-button type="primary" @click="openCreate">
          <plus-outlined />新建场景
        </a-button>
      </a-space>
    </div>

    <!-- 表格卡片 -->
    <div class="panel">
      <a-table
        :data-source="scenarios"
        :columns="columns"
        row-key="id"
        :loading="loading"
        @row-click="(r: any) => router.push({ name: 'scenario-editor', params: { id: r.id } })"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space @click.stop>
              <a-button size="small" type="link" @click="execute(record)">
                <play-circle-outlined />执行
              </a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="modal"
      title="新建场景"
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
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { ScenarioApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { Scenario } from '@/types'

const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const scenarios = ref<Scenario[]>([])
const loading = ref(false)
const modal = ref(false)
const form = reactive<Partial<Scenario>>({ name: '', description: '' })

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '更新时间', dataIndex: 'updatedAt' },
  { title: '操作', key: 'action', width: 140 }
]

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    scenarios.value = await ScenarioApi.list(projectId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', description: '' })
  modal.value = true
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  const created = await ScenarioApi.create({
    ...form,
    projectId: projectId.value,
    steps: []
  })
  modal.value = false
  router.push({ name: 'scenario-editor', params: { id: created.id } })
}

async function execute(record: Scenario) {
  const r = await ScenarioApi.execute(record.id)
  message.success(`执行完成：${r.status}`)
}

async function remove(record: Scenario) {
  await ScenarioApi.remove(record.id)
  await reload()
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 行点击进入编辑器，光标反馈 */
:deep(.ant-table-tbody > tr) {
  cursor: pointer;
}
</style>