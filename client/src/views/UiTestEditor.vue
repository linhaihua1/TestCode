<template>
  <div v-if="testCase" class="page editor-page">
    <!-- 页头 -->
    <header class="page-header">
      <div class="page-title">
        <a-button type="text" class="page-title__back" title="返回列表" @click="goBack">
          <arrow-left-outlined />
        </a-button>
        <robot-outlined class="page-title__icon" />
        <span class="ellipsis">{{ testCase.name }}</span>
      </div>
      <a-space>
        <a-button v-can-write :loading="saving" @click="save"> <save-outlined />保存 </a-button>
        <a-button v-can-write type="primary" :loading="running" @click="run">
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
        <a-tab-pane :key="UI_SEGMENTS.SETUP" :tab="UI_SEGMENT_LABELS[UI_SEGMENTS.SETUP]">
          <StepEditor v-model="testCase.setupSteps" />
        </a-tab-pane>
        <a-tab-pane :key="UI_SEGMENTS.TEST" :tab="UI_SEGMENT_LABELS[UI_SEGMENTS.TEST]">
          <StepEditor v-model="testCase.steps" />
        </a-tab-pane>
        <a-tab-pane :key="UI_SEGMENTS.TEARDOWN" :tab="UI_SEGMENT_LABELS[UI_SEGMENTS.TEARDOWN]">
          <StepEditor v-model="testCase.teardownSteps" />
        </a-tab-pane>
      </a-tabs>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * UI 用例编辑器：编辑一个 UI 用例的三段式步骤（setup / test / teardown）。
 *
 * <p>执行前会先自动保存，避免跑的是未保存的草稿；
 * 执行为异步投递，成功后跳转到 UI 报告列表查看结果。</p>
 */
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  SaveOutlined,
  PlayCircleOutlined,
  RobotOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons-vue';
import { UiApi } from '@/api';
import { UI_SEGMENTS, UI_SEGMENT_LABELS, type UiSegmentCode } from '@/constants';
import type { UiTestCase, UiStep } from '@/types';
import StepEditor from '@/components/UiStepEditor.vue';

const route = useRoute();
const router = useRouter();
const testCase = ref<UiTestCase | null>(null);
const activeTab = ref<UiSegmentCode>(UI_SEGMENTS.TEST);
const saving = ref(false);
const running = ref(false);

/** 加载用例：三段步骤缺失时补空数组，兼容历史数据 */
async function load() {
  testCase.value = await UiApi.getTest(route.params.id as string);
  if (!testCase.value.setupSteps) testCase.value.setupSteps = [];
  if (!testCase.value.steps) testCase.value.steps = [];
  if (!testCase.value.teardownSteps) testCase.value.teardownSteps = [];
}

async function save() {
  saving.value = true;
  try {
    await UiApi.updateTest(testCase.value!.id, testCase.value!);
    message.success('已保存');
  } finally {
    saving.value = false;
  }
}

/** 保存后执行：先落库再投递，避免执行的是未保存的草稿 */
async function run() {
  await save();
  running.value = true;
  try {
    await UiApi.runTest(testCase.value!.id);
    message.success('已提交执行');
    router.push({ name: 'ui-reports' });
  } finally {
    running.value = false;
  }
}

/** 返回 UI 用例列表（不刷新页面，保留草稿编辑状态由列表页自行决定） */
function goBack() {
  router.push({ name: 'ui-tests' });
}

onMounted(load);
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
