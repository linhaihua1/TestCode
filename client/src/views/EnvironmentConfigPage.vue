<template>
  <div class="page">
    <!-- 页头：标题 + 操作 -->
    <div class="page-header">
      <div class="page-title">全局变量</div>
      <a-space>
        <a-button @click="openCreate">
          <plus-outlined />新增变量
        </a-button>
      </a-space>
    </div>

    <!-- 变量表格 -->
    <div class="panel">
      <a-table
        :data-source="variables"
        :columns="columns"
        row-key="id"
        :loading="loading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'value'">
            <span>{{ record.encrypted ? '••••••' : record.value }}</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
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
      :title="form.id ? '编辑变量' : '新增变量'"
      @ok="save"
      width="480"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select v-model:value="form.type" :options="typeOptions" />
        </a-form-item>
        <a-form-item label="值">
          <a-textarea v-model:value="form.value" :auto-size="{ minRows: 1, maxRows: 4 }" />
        </a-form-item>
        <a-form-item label="加密存储">
          <a-switch v-model:checked="form.encrypted" />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="form.description" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { GlobalVariableApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { GlobalVariable } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const variables = ref<GlobalVariable[]>([])
const loading = ref(false)
const modal = ref(false)
const form = reactive<Partial<GlobalVariable>>({
  name: '',
  type: 'string',
  value: '',
  encrypted: false
})

const typeOptions = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'json', label: 'JSON' }
]

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '类型', dataIndex: 'type', width: 100 },
  { title: '值', key: 'value' },
  { title: '加密', dataIndex: 'encrypted', width: 80 },
  { title: '描述', dataIndex: 'description' },
  { title: '操作', key: 'action', width: 140 }
]

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    variables.value = await GlobalVariableApi.list(projectId.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, name: '', type: 'string', value: '', encrypted: false })
  modal.value = true
}

function openEdit(record: GlobalVariable) {
  Object.assign(form, record)
  modal.value = true
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  const payload = { ...form, projectId: projectId.value }
  if (form.id) {
    await GlobalVariableApi.update(form.id, form)
  } else {
    await GlobalVariableApi.create(payload)
  }
  message.success('已保存')
  modal.value = false
  await reload()
}

async function remove(record: GlobalVariable) {
  await GlobalVariableApi.remove(record.id)
  await reload()
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 当加密值有长 JSON 时，文本可换行避免撑破表格 */
:deep(.ant-table-tbody > tr > td) {
  word-break: break-word;
}
</style>