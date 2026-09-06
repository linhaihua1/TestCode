<template>
  <a-card :bordered="false" v-if="testCase">
    <a-page-header :title="testCase.name" @back="() => router.push({ name: 'ui-tests' })">
      <template #extra>
        <a-space>
          <a-button @click="save" :loading="saving"><save-outlined />保存</a-button>
          <a-button type="primary" @click="run" :loading="running">
            <play-circle-outlined />运行
          </a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-form layout="vertical">
      <a-row :gutter="8">
        <a-col :span="16">
          <a-form-item label="名称" required>
            <a-input v-model:value="testCase.name" />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="BaseURL">
            <a-input v-model:value="testCase.baseUrl" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="描述">
        <a-textarea v-model:value="testCase.description" :auto-size="{ minRows: 1, maxRows: 3 }" />
      </a-form-item>
    </a-form>

    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="setup" tab="前置步骤">
        <StepEditor v-model="testCase.setupSteps" />
      </a-tab-pane>
      <a-tab-pane key="test" tab="测试步骤">
        <StepEditor v-model="testCase.steps" />
      </a-tab-pane>
      <a-tab-pane key="teardown" tab="后置步骤">
        <StepEditor v-model="testCase.teardownSteps" />
      </a-tab-pane>
    </a-tabs>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { SaveOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { UiApi } from '@/api'
import type { UiTestCase, UiStep } from '@/types'
import StepEditor from '@/components/UiStepEditor.vue'

const route = useRoute()
const router = useRouter()
const testCase = ref<UiTestCase | null>(null)
const activeTab = ref<'setup' | 'test' | 'teardown'>('test')
const saving = ref(false)
const running = ref(false)

async function load() {
  testCase.value = await UiApi.getTest(route.params.id as string)
  if (!testCase.value.setupSteps) testCase.value.setupSteps = []
  if (!testCase.value.steps) testCase.value.steps = []
  if (!testCase.value.teardownSteps) testCase.value.teardownSteps = []
}

async function save() {
  saving.value = true
  try {
    await UiApi.updateTest(testCase.value!.id, testCase.value!)
    message.success('已保存')
  } finally {
    saving.value = false
  }
}

async function run() {
  await save()
  running.value = true
  try {
    await UiApi.runTest(testCase.value!.id)
    message.success('已投递到 RabbitMQ')
    router.push({ name: 'ui-reports' })
  } finally {
    running.value = false
  }
}

onMounted(load)
</script>