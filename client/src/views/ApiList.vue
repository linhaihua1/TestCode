<template>
  <div class="page">
    <!-- 页头：标题 -->
    <div class="page-header">
      <div class="page-title">接口管理</div>
    </div>

    <!-- 操作栏：新建接口 / Excel 导入模板 / Swagger 示例 / 导入 Swagger -->
    <div class="action-bar">
      <a-button v-can-write type="primary" @click="openCreate">
        <plus-outlined />新建接口
      </a-button>
      <a-button v-can-write @click="downloadExcelExample">
        <file-excel-outlined />Excel 导入模板
      </a-button>
      <a-button v-can-write @click="downloadSwaggerExample">
        <file-text-outlined />Swagger 示例
      </a-button>
      <a-upload
        :show-upload-list="false"
        accept=".json,.yaml,.yml"
        :before-upload="handleSwaggerFile"
      >
        <a-button v-can-write>
          <import-outlined />导入 Swagger
        </a-button>
      </a-upload>
    </div>

    <!-- 搜索 + 筛选 -->
    <div class="filter-bar">
      <a-input-search v-model:value="keyword" placeholder="搜索" style="width: 240px" @search="reload" />
      <a-select v-model:value="methodFilter" :options="methodOptions" style="width: 120px" @change="reload" />
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
import {
  PlusOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  ImportOutlined
} from '@ant-design/icons-vue'
import * as XLSX from 'xlsx'
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

/* ---------------- 示例下载（Excel 模板 / Swagger 示例） ---------------- */

/** 标准 Swagger/OpenAPI 3.0 示例，便于用户了解导入格式 */
const SWAGGER_EXAMPLE = {
  openapi: '3.0.0',
  info: { title: '示例 API', version: '1.0.0', description: 'Swagger 导入示例文档' },
  servers: [{ url: 'https://api.example.com' }],
  tags: [{ name: '用户管理', description: '用户相关接口' }],
  paths: {
    '/users': {
      get: {
        tags: ['用户管理'],
        summary: '获取用户列表',
        parameters: [
          { name: 'page', in: 'query', required: false, schema: { type: 'integer' }, description: '页码' }
        ],
        responses: {
          '200': {
            description: '成功',
            content: { 'application/json': { example: { code: 0, data: [] } } }
          }
        }
      },
      post: {
        tags: ['用户管理'],
        summary: '创建用户',
        requestBody: {
          required: true,
          content: {
            'application/json': {
              schema: { type: 'object' },
              example: { name: '张三', email: 'zhangsan@example.com' }
            }
          }
        },
        responses: { '200': { description: '成功' } }
      }
    },
    '/users/{id}': {
      get: {
        tags: ['用户管理'],
        summary: '获取用户详情',
        parameters: [
          { name: 'id', in: 'path', required: true, schema: { type: 'string' }, description: '用户 ID' }
        ],
        responses: { '200': { description: '成功' } }
      }
    }
  }
}

function downloadBlob(content: BlobPart, filename: string, mime: string) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/** 下载 Excel 导入模板（格式：请求方式 | URL，含示例行） */
function downloadExcelExample() {
  const rows: any[][] = [
    ['请求方式', 'URL', '名称（可选）'],
    ['GET', '/api/v1/users', '获取用户列表'],
    ['POST', '/api/v1/users', '创建用户'],
    ['PUT', '/api/v1/users/{id}', '更新用户'],
    ['DELETE', '/api/v1/users/{id}', '删除用户'],
    ['PATCH', '/api/v1/users/{id}', '部分更新用户']
  ]
  const ws = XLSX.utils.aoa_to_sheet(rows)
  // 设置列宽，提升可读性
  ws['!cols'] = [{ wch: 12 }, { wch: 32 }, { wch: 24 }]
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '接口导入模板')
  const out = XLSX.write(wb, { bookType: 'xlsx', type: 'array' })
  downloadBlob(out as BlobPart, '接口导入模板.xlsx',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')
  message.success('已下载 Excel 导入模板')
}

/** 下载 Swagger/OpenAPI 示例 */
function downloadSwaggerExample() {
  const json = JSON.stringify(SWAGGER_EXAMPLE, null, 2)
  downloadBlob(json, 'swagger-示例.json', 'application/json')
  message.success('已下载 Swagger 示例')
}

/* ---------------- Swagger 文件导入 ---------------- */

async function handleSwaggerFile(file: File) {
  if (!projectId.value) {
    message.warning('请先选择项目')
    return false
  }
  try {
    const res: any = await ApiApi.importSwaggerFile(projectId.value, file)
    const total = res?.total ?? 0
    const inserted = res?.inserted ?? 0
    message.success(`Swagger 导入完成：解析 ${total} 个接口，新增 ${inserted} 个`)
    await reload()
  } catch (e: any) {
    message.error(e?.response?.data?.message || 'Swagger 导入失败')
  }
  return false
}

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
/* 方法列：标签字体等宽以便对齐 */
:deep(.ant-table-tbody > tr > td .ant-tag) {
  font-family: var(--font-mono);
  min-width: 44px;
  text-align: center;
}
/* 顶部操作栏：新建接口 / Excel 导入模板 / Swagger 示例 / 导入 Swagger */
.action-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
}
/* 搜索 + 筛选行 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-bottom: var(--sp-4);
}
</style>