<template>
  <div class="page">
    <!-- 页头：标题 + 工具栏 -->
    <div class="page-header">
      <div class="page-title">接口管理</div>
      <a-space>
        <a-input-search v-model:value="keyword" placeholder="搜索" style="width: 200px" @search="reload" />
        <a-select v-model:value="methodFilter" :options="methodOptions" style="width: 120px" @change="reload" />
        <a-button v-can-write type="primary" @click="openCreate">
          <plus-outlined />新建
        </a-button>
      </a-space>
    </div>

    <!-- 表格卡片 -->
    <div class="panel">
      <a-table
        :data-source="apis"
        :columns="columns"
        row-key="id"
        :loading="loading"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'method'">
            <a-tag :color="methodColor(record.method)">{{ record.method }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-can-write size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button v-can-write size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="modal"
      :title="form.id ? '编辑接口' : '新建接口'"
      @ok="save"
      width="800"
    >
      <a-form layout="vertical">
        <a-row :gutter="8">
          <a-col :span="4">
            <a-form-item label="方法" required>
              <a-select v-model:value="form.method" :options="methodOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="20">
            <a-form-item label="路径" required>
              <a-input v-model:value="form.path" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="请求头">
          <KeyValueEditor v-model="form.headers" />
        </a-form-item>
        <a-form-item label="Query 参数">
          <KeyValueEditor v-model="form.query" key-label="Param" />
        </a-form-item>
        <a-form-item label="Body (JSON)">
          <Monaco v-model="form.body" language="json" height="180px" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { ApiApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { ApiDefinition } from '@/types'
import KeyValueEditor from '@/components/KeyValueEditor.vue'
import Monaco from '@/components/Monaco.vue'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const apis = ref<ApiDefinition[]>([])
const loading = ref(false)
const keyword = ref('')
const methodFilter = ref<string>()

const modal = ref(false)
const form = reactive<Partial<ApiDefinition>>({
  method: 'GET',
  name: '',
  path: '',
  headers: [],
  query: [],
  body: '',
  tags: []
})

const methodOptions = [
  { value: undefined, label: '全部' },
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
  { value: 'PUT', label: 'PUT' },
  { value: 'DELETE', label: 'DELETE' },
  { value: 'PATCH', label: 'PATCH' }
]

const columns = [
  { title: '方法', key: 'method', width: 80 },
  { title: '路径', dataIndex: 'path' },
  { title: '名称', dataIndex: 'name' },
  { title: '标签', key: 'tags' },
  { title: '操作', key: 'action', width: 140 }
]

function methodColor(m: string) {
  return m === 'GET' ? 'blue' : m === 'POST' ? 'green' : m === 'PUT' ? 'orange' : 'red'
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    apis.value = await ApiApi.list({
      projectId: projectId.value,
      keyword: keyword.value,
      method: methodFilter.value
    })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    method: 'GET',
    name: '',
    path: '',
    headers: [],
    query: [],
    body: '',
    tags: []
  })
  modal.value = true
}

function openEdit(record: ApiDefinition) {
  Object.assign(form, record)
  modal.value = true
}

async function save() {
  if (!form.name || !form.path) {
    message.warning('请填写名称与路径')
    return
  }
  if (form.id) {
    await ApiApi.update(form.id, form)
  } else {
    await ApiApi.create({ ...form, projectId: projectId.value })
  }
  message.success('已保存')
  modal.value = false
  await reload()
}

async function remove(record: ApiDefinition) {
  await ApiApi.remove(record.id)
  await reload()
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 工具栏：搜索 + 下拉同行等高对齐 */
:deep(.page-header .ant-input-affix-wrapper),
:deep(.page-header .ant-select) {
  vertical-align: middle;
}
/* 方法列：标签字体等宽以便对齐 */
:deep(.ant-table-tbody > tr > td .ant-tag) {
  font-family: var(--font-mono);
  min-width: 44px;
  text-align: center;
}
</style>