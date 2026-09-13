<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">用户管理</div>
      <a-space>
        <a-button type="primary" @click="openCreate"><plus-outlined />新建用户</a-button>
      </a-space>
    </div>

    <!-- 用户表格 -->
    <div class="panel">
      <a-table :data-source="users" :columns="columns" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-tag :color="roleColor(record.role)">{{ roleLabel(record.role) }}</a-tag>
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
  </div>
</template>

<script setup lang="ts">
/**
 * 用户管理页（仅管理员）：用户的创建、角色调整、密码重置与删除。
 *
 * <p>编辑时密码留空表示不修改；新建时密码至少 6 位。
 * 角色决定写权限（viewer 只读），具体取值见 constants.ROLES。</p>
 */
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { UserApi } from '@/api'
import { ROLES, ROLE_OPTIONS, roleColor, roleLabel } from '@/constants'
import { formatDateTime } from '@/utils/format'
import type { User } from '@/types'

const users = ref<User[]>([])
const loading = ref(false)
const modal = ref(false)
const form = reactive<{ id?: string; username: string; password: string; role: string }>({
  username: '',
  password: '',
  role: ROLES.MEMBER
})

const columns = [
  { title: '用户名', dataIndex: 'username' },
  { title: '角色', key: 'role', width: 100 },
  { title: '创建时间', key: 'createdAt', customRender: ({ record }: any) => formatDateTime(record.createdAt) },
  { title: '操作', key: 'action', width: 140 }
]

const roleOptions = ROLE_OPTIONS

async function reload() {
  loading.value = true
  try {
    users.value = await UserApi.list()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: undefined, username: '', password: '', role: ROLES.MEMBER })
  modal.value = true
}

function openEdit(record: User) {
  Object.assign(form, { id: record.id, username: record.username, password: '', role: record.role })
  modal.value = true
}

/** 保存用户：编辑时密码留空表示不改；新建时要求密码至少 6 位 */
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

<style scoped>
/* 角色标签：用等宽字体便于阅读 */
:deep(.ant-table-tbody > tr > td .ant-tag) {
  font-family: var(--font-mono);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}
</style>