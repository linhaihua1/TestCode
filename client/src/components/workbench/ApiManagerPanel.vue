<template>
  <a-card size="small" title="接口管理" :bordered="false">
    <template #extra>
      <a-space>
        <a-button size="small" type="primary" @click="openCreate">
          <plus-outlined />
        </a-button>
        <a-popconfirm title="批量导入 Swagger JSON？" @confirm="importSwagger">
          <a-button size="small">
            <import-outlined />
          </a-button>
        </a-popconfirm>
      </a-space>
    </template>
    <a-input-search
      v-model:value="keyword"
      placeholder="搜索接口名"
      size="small"
      style="margin-bottom: 8px"
      @search="reload"
    />
    <a-select
      v-model:value="methodFilter"
      :options="methodFilters"
      size="small"
      style="margin-bottom: 8px; width: 100%"
      @change="reload"
    />
    <a-list
      :data-source="apis"
      :loading="loading"
      size="small"
      style="max-height: 480px; overflow: auto"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta>
            <template #title>
              <a-space>
                <a-tag :color="methodColor(item.method)">{{ item.method }}</a-tag>
                <span>{{ item.name }}</span>
              </a-space>
            </template>
            <template #description>
              <small>{{ item.path }}</small>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>

    <a-modal
      v-model:open="createOpen"
      :title="form.id ? '编辑接口' : '新建接口'"
      @ok="save"
      width="640"
    >
      <a-form layout="vertical">
        <a-row :gutter="8">
          <a-col :span="4">
            <a-form-item label="方法" required>
              <a-select v-model:value="form.method" :options="methodFilters" />
            </a-form-item>
          </a-col>
          <a-col :span="20">
            <a-form-item label="路径" required>
              <a-input v-model:value="form.path" placeholder="/api/users/{id}" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :auto-size="{ minRows: 1, maxRows: 3 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, ImportOutlined } from '@ant-design/icons-vue'
import { ApiApi } from '@/api'
import type { ApiDefinition } from '@/types'

const props = defineProps<{ projectId: string }>()

const apis = ref<ApiDefinition[]>([])
const loading = ref(false)
const keyword = ref('')
const methodFilter = ref<string | undefined>()

const createOpen = ref(false)
const form = reactive<Partial<ApiDefinition>>({
  method: 'GET',
  name: '',
  path: '',
  headers: [],
  query: [],
  tags: []
})

const methodFilters = [
  { value: undefined, label: '全部方法' },
  { value: 'GET', label: 'GET' },
  { value: 'POST', label: 'POST' },
  { value: 'PUT', label: 'PUT' },
  { value: 'DELETE', label: 'DELETE' },
  { value: 'PATCH', label: 'PATCH' }
]

function methodColor(m: string) {
  return m === 'GET' ? 'blue' : m === 'POST' ? 'green' : m === 'PUT' ? 'orange' : 'red'
}

async function reload() {
  if (!props.projectId) return
  loading.value = true
  try {
    apis.value = await ApiApi.list({
      projectId: props.projectId,
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
    tags: []
  })
  createOpen.value = true
}

async function save() {
  if (!form.name || !form.path) {
    message.warning('请填写名称与路径')
    return
  }
  if (form.id) {
    await ApiApi.update(form.id, form)
  } else {
    await ApiApi.create({ ...form, projectId: props.projectId })
  }
  message.success('已保存')
  createOpen.value = false
  await reload()
}

async function importSwagger() {
  const json = prompt('粘贴 Swagger JSON')
  if (!json) return
  try {
    const obj = JSON.parse(json)
    const count = await ApiApi.importSwagger(props.projectId, obj)
    message.success(`导入 ${count} 个接口`)
    await reload()
  } catch (e: any) {
    message.error('解析失败：' + e.message)
  }
}

watch(() => props.projectId, reload, { immediate: false })
onMounted(reload)
</script>