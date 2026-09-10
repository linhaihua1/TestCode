<!--
  调试弹窗（需求文档 §4）。

  选中接口用例后点「调试」弹出，支持：
  - 选择调试环境
  - 环境变量新增 / 修改 / 删除（保存到所选环境）
  - 新建 / 删除环境
  - 用例按所选环境变量执行
-->
<template>
  <a-modal
    :open="open"
    title="调试选项"
    width="680"
    :confirm-loading="running"
    ok-text="开始调试"
    cancel-text="取消"
    @ok="run"
    @cancel="close"
  >
    <a-form layout="vertical">
      <a-form-item label="调试环境" required>
        <a-select
          v-model:value="selectedEnvId"
          :options="envOptions"
          :loading="envLoading"
          placeholder="选择用例执行时使用的环境"
          not-found-content="暂无环境，点击下方「新建环境」创建"
        />
      </a-form-item>
    </a-form>

    <a-divider>环境变量（{{ currentVariables.length }}）</a-divider>
    <a-empty
      v-if="!currentVariables.length"
      description="该环境暂无变量"
      :image-style="{ height: '40px' }"
    />
    <div v-for="(v, idx) in currentVariables" :key="idx" class="env-var">
      <a-input v-model:value="v.key" placeholder="变量名" />
      <a-input v-model:value="v.value" placeholder="变量值" />
      <a-button type="text" danger @click="removeVar(idx)">
        <delete-outlined />
      </a-button>
    </div>
    <a-button block type="dashed" size="small" :disabled="!selectedEnvId" @click="addVar">
      <plus-outlined />新增变量
    </a-button>

    <a-divider>环境管理</a-divider>
    <a-space>
      <a-button @click="createEnv">
        <plus-outlined />新建环境
      </a-button>
      <a-popconfirm
        title="确认删除当前环境及其变量？"
        :disabled="!selectedEnvId"
        @confirm="deleteEnv"
      >
        <a-button danger :disabled="!selectedEnvId">
          <delete-outlined />删除环境
        </a-button>
      </a-popconfirm>
    </a-space>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { EnvironmentApi, CaseApi } from '@/api'
import type { Environment, KeyValueItem } from '@/types'

const props = defineProps<{ open: boolean; caseId: string; projectId: string }>()
const emit = defineEmits<{
  'update:open': [v: boolean]
  finished: [caseId: string]
}>()

const envs = ref<Environment[]>([])
const envLoading = ref(false)
const selectedEnvId = ref<string | undefined>()
const currentVariables = ref<KeyValueItem[]>([])
const running = ref(false)

const envOptions = computed(() => envs.value.map((e) => ({ value: e.id, label: e.name })))

async function loadEnvs() {
  if (!props.projectId) return
  envLoading.value = true
  try {
    envs.value = await EnvironmentApi.list(props.projectId)
  } finally {
    envLoading.value = false
  }
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      selectedEnvId.value = undefined
      currentVariables.value = []
      loadEnvs()
    }
  }
)

watch(selectedEnvId, (id) => {
  const env = envs.value.find((e) => e.id === id)
  currentVariables.value = env ? JSON.parse(JSON.stringify(env.variables || [])) : []
})

function addVar() {
  currentVariables.value.push({ key: '', value: '', enabled: true })
}

function removeVar(idx: number) {
  currentVariables.value.splice(idx, 1)
}

async function createEnv() {
  const name = window.prompt('请输入环境名称')
  if (!name) return
  await EnvironmentApi.create({
    projectId: props.projectId,
    name,
    variables: [],
    headers: []
  } as Partial<Environment>)
  await loadEnvs()
  message.success('环境已创建')
}

async function deleteEnv() {
  if (!selectedEnvId.value) return
  await EnvironmentApi.remove(selectedEnvId.value)
  selectedEnvId.value = undefined
  currentVariables.value = []
  await loadEnvs()
  message.success('环境已删除')
}

async function run() {
  // 先保存变量到所选环境
  if (selectedEnvId.value) {
    await EnvironmentApi.update(selectedEnvId.value, { variables: currentVariables.value })
  }
  running.value = true
  try {
    await CaseApi.debug(props.caseId, selectedEnvId.value)
    message.success('调试完成')
    close()
    emit('finished', props.caseId)
  } catch (e) {
    // 错误已由 axios 拦截器统一提示
  } finally {
    running.value = false
  }
}

function close() {
  emit('update:open', false)
}
</script>

<style scoped>
.env-var {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  margin-bottom: var(--sp-2);
}
</style>
