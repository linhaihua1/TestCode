<template>
  <a-card title="用户管理" :bordered="false">
    <template #extra>
      <a-button type="primary" @click="openCreate"><plus-outlined />新建用户</a-button>
    </template>
    <a-table :data-source="users" :columns="columns" row-key="id" :loading="loading">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'role'">
          <a-tag :color="record.role === 'admin' ? 'red' : 'blue'">{{ record.role }}</a-tag>
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

    <a-modal v-model:open="modal" :title="form.id ? '编辑用户' : '新建用户'" @ok="save" width="480">
      <a-form layout="vertical">
        <a-form-item label="用户名" required>
          <a-input v-model:value="form.username" :disabled="!!form.id" />
        </a-form-item>
        <a-form-item v-if="!form.id" label="初始密码" required>
          <a-input-password v-model:value="form.password" placeholder="至少 6 位" />
        </a-form-item>
        <a-form-item v-else label="重置密码（留空保持不变）">
          <a-input-password v-model:value="form.password" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="form.role" :options="roleOptions" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { UserApi } from '@/api'
import type { User } from '@/types'

const users = ref<User[]>([])
const loading = ref(false)
const modal = ref(false)
const form = reactive<{ id?: string; username: string; password: string; role: string }>({
  username: '',
  password: '',
  role: 'member'
})

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '角色', key: 'role', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt' },
  { title: '操作', key: 'action', width: 140 }
]

const roleOptions = [
  { value: 'admin', label: '管理员' },
  { value: 'member', label: '成员' },
  { value: 'viewer', label: '查看者' }
]

async function reload() {
  loading.value = true
  try {
    users.value = await UserApi.list()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, username: '', password: '', role: 'member' })
  modal.value = true
}

function openEdit(record: User) {
  Object.assign(form, { id: record.id, username: record.username, password: '', role: record.role })
  modal.value = true
}

async function save() {
  if (!form.username) {
    message.warning('请填写用户名')
    return
  }
  if (!form.id && form.password.length < 6) {
    message.warning('初始密码至少 6 位')
    return
  }
  if (form.id) {
    await UserApi.update(form.id, { role: form.role, password: form.password || undefined })
  } else {
    await UserApi.create({ username: form.username, password: form.password, role: form.role })
  }
  message.success('已保存')
  modal.value = false
  await reload()
}

async function remove(record: User) {
  await UserApi.remove(record.id)
  await reload()
}

onMounted(reload)
</script>