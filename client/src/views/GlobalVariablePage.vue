<!--
  全局变量管理页（需求文档 §2.1）。

  <h3>支持的变量类型</h3>
  <ul>
    <li>STRING / NUMBER / BOOLEAN / TIMESTAMP / RANDOM / SECRET</li>
  </ul>

  <h3>操作</h3>
  <ul>
    <li>新增 / 编辑 / 删除 / 批量删除</li>
    <li>SECRET 类型值以 ****** 脱敏展示，点击眼睛图标临时查看（需二次确认）</li>
  </ul>
-->
<template>
  <a-card title="全局变量管理" :bordered="false">
    <template #extra>
      <a-space>
        <a-button type="primary" @click="openCreate">
          <plus-outlined />新增变量
        </a-button>
        <a-popconfirm title="确认批量删除选中的变量？" @confirm="batchDelete">
          <a-button danger :disabled="!selectedRowKeys.length">
            <delete-outlined />批量删除
          </a-button>
        </a-popconfirm>
      </a-space>
    </template>

    <a-table
      :data-source="variables"
      :columns="columns"
      :loading="loading"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      :pagination="{ pageSize: 20 }"
      row-key="id"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'value'">
          <span v-if="record.type === 'SECRET' && !revealed[record.id]">******</span>
          <span v-else>{{ record.value }}</span>
          <a-button
            v-if="record.type === 'SECRET'"
            size="small"
            type="text"
            @click="toggleReveal(record.id)"
          >
            {{ revealed[record.id] ? '🙈' : '👁' }}
          </a-button>
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-space>
            <a-button size="small" @click="openEdit(record)">编辑</a-button>
            <a-popconfirm title="确认删除？" @confirm="remove(record.id)">
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalOpen"
      :title="editing.id ? '编辑变量' : '新增变量'"
      @ok="save"
      width="520"
    >
      <a-form layout="vertical">
        <a-form-item label="变量名" required>
          <a-input
            v-model:value="editing.name"
            placeholder="字母/数字/下划线，不能以数字开头"
            :disabled="!!editing.id"
          />
        </a-form-item>
        <a-form-item label="类型" required>
          <a-select v-model:value="editing.type" :options="typeOptions" />
        </a-form-item>
        <a-form-item label="值" required>
          <a-input
            v-model:value="editing.value"
            :type="editing.type === 'SECRET' && !revealed[editing.id || ''] ? 'password' : 'text'"
            placeholder="SECRET 类型请使用密码框"
          />
        </a-form-item>
        <a-form-item label="描述">
          <a-input v-model:value="editing.description" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { GlobalVariableApi } from '@/api'
import type { GlobalVariable } from '@/types'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const variables = ref<GlobalVariable[]>([])
const loading = ref(false)
const selectedRowKeys = ref<string[]>([])
const revealed = reactive<Record<string, boolean>>({})

const modalOpen = ref(false)
const editing = reactive<Partial<GlobalVariable>>({
  id: '',
  name: '',
  type: 'STRING',
  value: '',
  description: ''
})

const typeOptions = [
  { label: '字符串', value: 'STRING' },
  { label: '数字', value: 'NUMBER' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: '时间戳', value: 'TIMESTAMP' },
  { label: '随机', value: 'RANDOM' },
  { label: '密钥', value: 'SECRET' }
]

const columns = [
  { title: '变量名', dataIndex: 'name', key: 'name' },
  { title: '类型', dataIndex: 'type', key: 'type', width: 100 },
  { title: '值', dataIndex: 'value', key: 'value' },
  { title: '描述', dataIndex: 'description', key: 'description' },
  { title: '操作', key: 'actions', width: 150 }
]

function onSelectChange(keys: string[]) {
  selectedRowKeys.value = keys
}

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
  editing.id = ''
  editing.name = ''
  editing.type = 'STRING'
  editing.value = ''
  editing.description = ''
  modalOpen.value = true
}

function openEdit(record: GlobalVariable) {
  Object.assign(editing, record)
  modalOpen.value = true
}

async function save() {
  if (!editing.name?.trim()) {
    message.warning('变量名不能为空')
    return
  }
  if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(editing.name)) {
    message.warning('变量名必须以字母/下划线开头，仅含字母数字下划线')
    return
  }
  if (editing.id) {
    await GlobalVariableApi.update(editing.id, editing)
    message.success('已更新')
  } else {
    await GlobalVariableApi.create({ ...editing, projectId: projectId.value } as any)
    message.success('已新增')
  }
  modalOpen.value = false
  await reload()
}

async function remove(id: string) {
  await GlobalVariableApi.remove(id)
  message.success('已删除')
  await reload()
}

async function batchDelete() {
  for (const id of selectedRowKeys.value) {
    await GlobalVariableApi.remove(id)
  }
  selectedRowKeys.value = []
  await reload()
  message.success('批量删除完成')
}

function toggleReveal(id: string) {
  if (!revealed[id]) {
    // 二次确认
    if (!confirm('确认查看密钥明文？')) return
  }
  revealed[id] = !revealed[id]
}

onMounted(reload)
</script>