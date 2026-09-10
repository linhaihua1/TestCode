<template>
  <div class="page editor-page" v-if="testCase">
    <!-- 页头 -->
    <header class="page-header">
      <div class="page-title">
        <a-button type="text" class="page-title__back" @click="goBack" title="返回列表">
          <arrow-left-outlined />
        </a-button>
        <robot-outlined class="page-title__icon" />
        <span class="ellipsis">{{ testCase.name }}</span>
      </div>
      <a-space>
        <a-button @click="save" :loading="saving">
          <save-outlined />保存
        </a-button>
        <a-button type="primary" @click="run" :loading="running">
          <play-circle-outlined />运行
        </a-button>
      </a-space>
    </header>

    <!-- 基本信息 -->
    <section class="panel editor-panel">
      <a-form layout="vertical" class="editor-form">
        <a-row :gutter="16">
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
          <a-textarea
            v-model:value="testCase.description"
            :auto-size="{ minRows: 1, maxRows: 3 }"
          />
        </a-form-item>
      </a-form>
    </section>

    <!-- 步骤编辑 Tab -->
    <section class="panel editor-panel">
      <a-tabs v-model:active-key="activeTab" class="editor-tabs">
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
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  SaveOutlined, PlayCircleOutlined, RobotOutlined, ArrowLeftOutlined
} from '@ant-design/icons-vue'
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

/** 返回 UI 用例列表（不刷新页面，保留草稿编辑状态由列表页自行决定） */
function goBack() {
  router.push({ name: 'ui-tests' })
}

onMounted(load)
</script>

<style scoped>
/* AppLayout 外层已提供页面 padding，避免重复 */
.editor-page {
  padding: 0;
}

.page-title__icon {
  font-size: var(--fs-lg);
  color: var(--c-primary);
  flex-shrink: 0;
}

/* 面板通用：白色卡片容器 */
.editor-panel {
  padding: var(--sp-5);
  margin-bottom: var(--sp-5);
}

.editor-panel:last-child {
  margin-bottom: 0;
}

/* 表单：去掉容器内多余的内边距（在面板里已经留了 padding） */
.editor-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}

/* Tab 容器 */
.editor-tabs {
  /* 取消 tabs 外层负 margin，让其紧贴面板内边距 */
  margin: calc(-1 * var(--sp-5));
}

.editor-tabs :deep(.ant-tabs-content-holder) {
  padding: var(--sp-4) var(--sp-5) var(--sp-2);
}
</style>
