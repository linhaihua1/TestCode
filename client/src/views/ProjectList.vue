<template>
  <div class="page">
    <!-- 页头：标题 + 工具栏（原 #extra 内容） -->
    <div class="page-header">
      <div class="page-title">项目列表</div>
      <a-space>
        <a-input-search
          v-model:value="keyword"
          placeholder="按名称搜索"
          style="width: 220px"
          @search="reload"
          @pressEnter="reload"
        />
        <a-button type="primary" @click="openModal()">
          <plus-outlined /> 新建项目
        </a-button>
      </a-space>
    </div>

    <!-- 表格卡片 -->
    <div class="panel">
      <a-table
        :data-source="projects"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :pagination="{ pageSize: 20 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'updatedAt'">
            {{ formatTime(record.updatedAt) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" type="link" @click="enterProject(record)">
                <enter-outlined />进入
              </a-button>
              <a-button size="small" type="link" @click="openModal(record)">
                编辑
              </a-button>
              <a-popconfirm
                title="确认删除该项目及其下属数据？"
                @confirm="remove(record)"
              >
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>
  </div>

  <a-modal
    v-model:open="modal"
    :title="editing.id ? '编辑项目' : '新建项目'"
    @ok="save"
    :confirm-loading="saving"
    width="520"
  >
    <a-form layout="vertical">
      <a-form-item label="项目名称" required>
        <a-input v-model:value="editing.name" placeholder="例如 电商主站" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea
          v-model:value="editing.description"
          :auto-size="{ minRows: 2, maxRows: 5 }"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PlusOutlined, EnterOutlined } from '@ant-design/icons-vue'
import dayjs from 'dayjs'
import { ProjectApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { Project } from '@/types'

const router = useRouter()
const projectStore = useProjectStore()

const keyword = ref('')
const projects = ref<Project[]>([])
const loading = ref(false)
const modal = ref(false)
const saving = ref(false)
const editing = reactive<Partial<Project>>({ name: '', description: '' })

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '更新时间', key: 'updatedAt', width: 180 },
  { title: '操作', key: 'action', width: 200 }
]

function formatTime(iso: string) {
  return dayjs.utc(iso).local().format('YYYY-MM-DD HH:mm')
}

async function reload() {
  loading.value = true
  try {
    projects.value = await ProjectApi.list(keyword.value)
  } finally {
    loading.value = false
  }
}

function openModal(record?: Project) {
  if (record) {
    Object.assign(editing, record)
  } else {
    Object.assign(editing, { id: undefined, name: '', description: '' })
  }
  modal.value = true
}

async function save() {
  if (!editing.name) {
    message.warning('请填写项目名称')
    return
  }
  saving.value = true
  try {
    if (editing.id) {
      await ProjectApi.update(editing.id!, editing)
      message.success('已保存')
    } else {
      await ProjectApi.create(editing)
      message.success('已创建')
    }
    modal.value = false
    await reload()
    await projectStore.fetchAll()
  } finally {
    saving.value = false
  }
}

async function remove(record: Project) {
  await ProjectApi.remove(record.id)
  message.success('已删除')
  await reload()
  await projectStore.fetchAll()
}

function enterProject(record: Project) {
  projectStore.switchProject(record.id)
  router.push({ name: 'workbench' })
}

onMounted(reload)
</script>

<style scoped>
/* 操作列按钮：收紧与单元格的留白 */
:deep(.ant-table-tbody > tr > td .ant-btn-link) {
  padding: 0 var(--sp-2);
}
</style>