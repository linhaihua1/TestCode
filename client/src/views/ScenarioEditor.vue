<template>
  <a-card :bordered="false" v-if="scenario">
    <a-page-header :title="scenario.name" @back="() => router.push({ name: 'scenarios' })">
      <template #extra>
        <a-space>
          <a-button @click="execute" :loading="running">
            <play-circle-outlined />执行
          </a-button>
          <a-popconfirm title="确认删除？" @confirm="remove">
            <a-button danger><delete-outlined /></a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-page-header>

    <a-form layout="vertical">
      <a-form-item label="名称">
        <a-input v-model:value="scenario.name" />
      </a-form-item>
      <a-form-item label="描述">
        <a-textarea v-model:value="scenario.description" :auto-size="{ minRows: 1, maxRows: 4 }" />
      </a-form-item>
    </a-form>

    <a-divider>场景步骤</a-divider>

    <VueDraggable v-model="steps" :animation="180" handle=".step-handle">
      <a-card
        v-for="(step, idx) in steps"
        :key="idx"
        size="small"
        style="margin-bottom: 8px"
      >
        <a-row :gutter="8" align="middle">
          <a-col flex="32px">
            <drag-outlined class="step-handle" style="cursor: grab; color: #aaa" />
          </a-col>
          <a-col flex="200px">
            <a-input v-model:value="step.name" placeholder="步骤名称" />
          </a-col>
          <a-col flex="auto">
            <a-input
              v-model:value="step.apiCaseId"
              placeholder="关联用例 ID（可选）"
            />
          </a-col>
          <a-col flex="80px">
            <a-button size="small" type="text" danger @click="steps.splice(idx, 1)">
              删除
            </a-button>
          </a-col>
        </a-row>
        <a-tabs size="small" style="margin-top: 8px">
          <a-tab-pane key="assertions" tab="断言">
            <AssertionEditor v-model="step.assertions" />
          </a-tab-pane>
          <a-tab-pane key="extracts" tab="提取">
            <ExtractEditor v-model="step.extracts" />
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </VueDraggable>

    <a-button block type="dashed" @click="addStep">
      <plus-outlined />新增步骤
    </a-button>

    <a-divider />
    <a-button type="primary" @click="save" :loading="saving">保存场景</a-button>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlayCircleOutlined, DeleteOutlined, DragOutlined, PlusOutlined
} from '@ant-design/icons-vue'
import { VueDraggable } from 'vue-draggable-plus'
import { ScenarioApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { Scenario, AssertionResult, ExtractResult } from '@/types'
import AssertionEditor from '@/components/AssertionEditor.vue'
import ExtractEditor from '@/components/ExtractEditor.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

interface Step {
  sortOrder: number
  name?: string
  apiCaseId?: string
  assertions: AssertionResult[]
  extracts: ExtractResult[]
}

const scenario = ref<Scenario | null>(null)
const steps = ref<Step[]>([])
const saving = ref(false)
const running = ref(false)

async function load() {
  const id = route.params.id as string
  const data = await ScenarioApi.get(id)
  scenario.value = data.scenario
  steps.value = (data.steps || []) as Step[]
}

function addStep() {
  steps.value.push({
    sortOrder: steps.value.length,
    name: `步骤 ${steps.value.length + 1}`,
    apiCaseId: undefined,
    assertions: [],
    extracts: []
  })
}

async function save() {
  saving.value = true
  try {
    await ScenarioApi.update(scenario.value!.id, {
      name: scenario.value!.name,
      description: scenario.value!.description,
      steps: steps.value
    })
    message.success('已保存')
  } finally {
    saving.value = false
  }
}

async function execute() {
  running.value = true
  try {
    const r = await ScenarioApi.execute(scenario.value!.id)
    message.success(`执行完成：${r.status}`)
  } finally {
    running.value = false
  }
}

async function remove() {
  await ScenarioApi.remove(scenario.value!.id)
  router.push({ name: 'scenarios' })
}

onMounted(load)
</script>