<!--
  接口管理面板（工作台右栏，需求文档 §5）。

  功能：
  - 按模块创建接口目录（树）
  - 手动新增接口请求
  - Excel 批量导入（格式：请求方式、URL）
  - Swagger JSON 导入
-->
<template>
  <div class="api-manager">
    <!-- 搜索 -->
    <a-input-search
      v-model:value="keyword"
      placeholder="搜索接口名"
      size="small"
      @search="reload"
      @press-enter="reload"
    />

    <!-- 接口目录（模块树） -->
    <div class="module-block">
      <div class="module-head">
        <span class="module-title">接口目录</span>
        <a-tooltip title="新建目录">
          <a-button size="small" type="text" @click="createModule">
            <folder-add-outlined />
          </a-button>
        </a-tooltip>
      </div>
      <VirtualTree
        v-if="moduleTree.length"
        :tree="moduleTree"
        :selected-key="selectedModuleId"
        :height="170"
        @select="onModuleSelect"
      />
      <a-empty
        v-else
        description="暂无目录，点上方新建"
        :image-style="{ height: '30px' }"
      />
    </div>

    <!-- 方法过滤 -->
    <a-select
      v-model:value="methodFilter"
      :options="methodFilters"
      size="small"
      class="method-filter"
      @change="reload"
    />

    <!-- 接口列表 -->
    <a-list
      :data-source="apis"
      :loading="loading"
      size="small"
      class="api-list"
    >
      <template #renderItem="{ item }">
        <a-list-item class="api-item" @click="openEdit(item)">
          <a-list-item-meta>
            <template #title>
              <a-space size="small">
                <a-tag :color="methodColor(item.method)">{{ item.method }}</a-tag>
                <span class="ellipsis">{{ item.name }}</span>
              </a-space>
            </template>
            <template #description>
              <small class="api-path ellipsis">{{ item.path }}</small>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>

    <!-- 底部操作 -->
    <div class="footer-actions">
      <a-button size="small" type="primary" @click="openCreate">
        <plus-outlined />新建接口
      </a-button>
      <a-upload
        :before-upload="handleExcel"
        :show-upload-list="false"
        accept=".xlsx,.xls"
      >
        <a-button size="small">
          <file-excel-outlined />Excel 导入
        </a-button>
      </a-upload>
      <a-popconfirm title="批量导入 Swagger JSON？" @confirm="importSwagger">
        <a-button size="small">
          <import-outlined />Swagger
        </a-button>
      </a-popconfirm>
    </div>

    <!-- 新建 / 编辑接口弹窗 -->
    <a-modal
      v-model:open="createOpen"
      :title="form.id ? '编辑接口' : '新建接口'"
      :confirm-loading="saving"
      width="640"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-row :gutter="8">
          <a-col :span="5">
            <a-form-item label="方法" required>
              <a-select v-model:value="form.method" :options="methodOptions" />
            </a-form-item>
          </a-col>
          <a-col :span="19">
            <a-form-item label="路径" required>
              <a-input v-model:value="form.path" placeholder="/api/users/{id}" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="所属目录">
          <a-select
            v-model:value="form.moduleId"
            :options="moduleOptions"
            allow-clear
            placeholder="选择目录（可选）"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :auto-size="{ minRows: 1, maxRows: 3 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined, ImportOutlined, FolderAddOutlined, FileExcelOutlined
} from '@ant-design/icons-vue'
import * as XLSX from 'xlsx'
import { ApiApi, ModuleApi } from '@/api'
import type { ApiDefinition, ModuleNode } from '@/types'
import VirtualTree from '@/components/VirtualTree.vue'

const props = defineProps<{ projectId: string }>()

const apis = ref<ApiDefinition[]>([])
const loading = ref(false)
const keyword = ref('')
const methodFilter = ref<string | undefined>()

const moduleTree = ref<ModuleNode[]>([])
const selectedModuleId = ref<string>('')

const createOpen = ref(false)
const saving = ref(false)
const form = reactive<Partial<ApiDefinition>>({
  method: 'GET',
  name: '',
  path: '',
  headers: [],
  query: [],
  tags: []
})

const methodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map((v) => ({
  value: v,
  label: v
}))

const methodFilters = [
  { value: undefined, label: '全部方法' },
  ...methodOptions
]

const moduleOptions = computed(() => {
  const flat: { value: string; label: string }[] = []
  const walk = (nodes: ModuleNode[], depth = 0) => {
    nodes.forEach((n) => {
      flat.push({ value: n.key, label: '　'.repeat(depth) + n.title })
      if (n.children?.length) walk(n.children, depth + 1)
    })
  }
  walk(moduleTree.value)
  return flat
})

function methodColor(m: string) {
  return m === 'GET' ? 'blue' : m === 'POST' ? 'green' : m === 'PUT' ? 'orange' : 'red'
}

async function reload() {
  if (!props.projectId) return
  loading.value = true
  try {
    apis.value = await ApiApi.list({
      projectId: props.projectId,
      moduleId: selectedModuleId.value || undefined,
      keyword: keyword.value,
      method: methodFilter.value
    })
  } finally {
    loading.value = false
  }
}

async function loadModules() {
  if (!props.projectId) return
  moduleTree.value = await ModuleApi.tree(props.projectId, 'api')
}

function onModuleSelect(key: string) {
  selectedModuleId.value = key
  reload()
}

async function createModule() {
  const name = window.prompt('目录名称')
  if (!name) return
  await ModuleApi.create({
    projectId: props.projectId,
    parentId: null,
    name,
    type: 'api',
    sortOrder: 0
  })
  await loadModules()
  message.success('目录已创建')
}

function openCreate() {
  Object.assign(form, {
    id: undefined,
    method: 'GET',
    name: '',
    path: '',
    moduleId: selectedModuleId.value || undefined,
    headers: [],
    query: [],
    tags: []
  })
  createOpen.value = true
}

function openEdit(item: ApiDefinition) {
  Object.assign(form, item)
  createOpen.value = true
}

async function save() {
  if (!form.name || !form.path) {
    message.warning('请填写名称与路径')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await ApiApi.update(form.id, form)
    } else {
      await ApiApi.create({ ...form, projectId: props.projectId })
    }
    message.success('已保存')
    createOpen.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

/** Excel 导入：格式为「请求方式 | URL」两列 */
async function handleExcel(file: File): Promise<boolean> {
  try {
    const data = await file.arrayBuffer()
    const workbook = XLSX.read(data, { type: 'array' })
    const sheet = workbook.Sheets[workbook.SheetNames[0]]
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1 }) as any[][]

    const imports: { method: string; path: string }[] = []
    for (let i = 1; i < rows.length; i++) {
      const method = String(rows[i]?.[0] || '').trim().toUpperCase()
      const path = String(rows[i]?.[1] || '').trim()
      if (method && path && ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
        imports.push({ method, path })
      }
    }
    if (!imports.length) {
      message.warning('未解析到有效接口，请检查 Excel 格式（第一列请求方式、第二列 URL）')
      return false
    }
    for (const imp of imports) {
      await ApiApi.create({
        projectId: props.projectId,
        moduleId: selectedModuleId.value || undefined,
        method: imp.method,
        path: imp.path,
        name: deriveName(imp.path),
        headers: [],
        query: [],
        tags: []
      } as Partial<ApiDefinition>)
    }
    message.success(`导入 ${imports.length} 个接口`)
    await reload()
  } catch (e: any) {
    message.error('Excel 解析失败：' + e.message)
  }
  return false
}

function deriveName(url: string) {
  const path = url.split('?')[0]
  const parts = path.split('/').filter(Boolean)
  return parts.length ? parts[parts.length - 1] : url
}

async function importSwagger() {
  const json = window.prompt('粘贴 Swagger JSON')
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

watch(() => props.projectId, () => {
  selectedModuleId.value = ''
  loadModules()
  reload()
})

onMounted(() => {
  loadModules()
  reload()
})
</script>

<style scoped>
.api-manager {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: var(--sp-2);
  padding: var(--sp-2);
}

.module-block {
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  padding: var(--sp-1) var(--sp-2);
}

.module-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--bd-subtle);
  margin-bottom: var(--sp-1);
}

.module-title {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--tx-2);
}

.method-filter {
  width: 100%;
}

.api-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.api-item {
  cursor: pointer;
  border-radius: var(--rd-sm);
}

.api-item:hover {
  background: var(--bg-hover);
}

.api-path {
  color: var(--tx-4);
}

.footer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
  padding-top: var(--sp-1);
  border-top: 1px solid var(--bd-subtle);
}
</style>
