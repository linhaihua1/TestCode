<template>
  <a-card size="small" title="用例列表" :bordered="false">
    <template #extra>
      <a-space>
        <a-select
          v-model:value="statusFilter"
          :options="statusOptions"
          size="small"
          style="width: 110px"
          @change="reload"
        />
        <a-button size="small" type="primary" @click="openCreate">
          <plus-outlined />
        </a-button>
      </a-space>
    </template>
    <a-input-search
      v-model:value="keyword"
      placeholder="搜索用例"
      size="small"
      style="margin-bottom: 8px"
      @search="reload"
    />
    <a-list
      :data-source="cases"
      :loading="loading"
      size="small"
      style="max-height: 480px; overflow: auto"
    >
      <template #renderItem="{ item }">
        <a-list-item @click="emit('open', item)" style="cursor: pointer">
          <a-list-item-meta>
            <template #title>
              <a-space>
                <span>{{ item.name }}</span>
                <a-tag color="blue" size="small">v{{ item.version }}</a-tag>
                <a-tag size="small" :color="caseStatusColor(item.status)">{{ CASE_STATUS_LABELS[item.status] || item.status }}</a-tag>
                <a-tag size="small">{{ item.priority }}</a-tag>
              </a-space>
            </template>
            <template #description>
              <small>{{ item.description || '—' }}</small>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>

    <a-modal
      v-model:open="createOpen"
      title="新建用例"
      @ok="create"
      width="480"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-select v-model:value="form.priority" :options="priorityOptions" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { CaseApi } from '@/api'
import { CASE_STATUS_LABELS, CASE_STATUS_OPTIONS, PRIORITY_OPTIONS, caseStatusColor } from '@/constants'
import type { CaseInfo } from '@/types'

const props = defineProps<{
  projectId: string
  moduleId?: string
}>()

const emit = defineEmits<{
  open: [c: CaseInfo]
}>()

const cases = ref<CaseInfo[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<string | undefined>()

const createOpen = ref(false)
const form = reactive<Partial<CaseInfo>>({ name: '', priority: 'P2', description: '' })

const statusOptions = [{ value: undefined, label: '全部' }, ...CASE_STATUS_OPTIONS]

const priorityOptions = PRIORITY_OPTIONS

async function reload() {
  if (!props.projectId) return
  loading.value = true
  try {
    cases.value = await CaseApi.list({
      projectId: props.projectId,
      moduleId: props.moduleId,
      status: statusFilter.value,
      keyword: keyword.value
    })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { name: '', priority: 'P2', description: '' })
  createOpen.value = true
}

async function create() {
  if (!form.name) {
    message.warning('请填写名称')
    return
  }
  const c = await CaseApi.create({
    ...form,
    projectId: props.projectId,
    moduleId: props.moduleId || undefined,
    status: 'draft',
    tags: [],
    steps: []
  })
  createOpen.value = false
  await reload()
  emit('open', c)
}

watch(() => [props.projectId, props.moduleId], reload, { immediate: false })
onMounted(reload)
</script>