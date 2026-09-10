<template>
  <div class="page editor-page" v-if="scenario">
    <!-- 页头 -->
    <header class="page-header">
      <div class="page-title">
        <a-button type="text" class="page-title__back" @click="goBack" title="返回场景列表">
          <arrow-left-outlined />
        </a-button>
        <partition-outlined class="page-title__icon" />
        <span class="ellipsis">{{ scenario.name }}</span>
      </div>
      <a-space>
        <a-button @click="execute" :loading="running">
          <play-circle-outlined />执行
        </a-button>
        <a-popconfirm title="确认删除？" @confirm="remove">
          <a-button danger>
            <delete-outlined />删除
          </a-button>
        </a-popconfirm>
      </a-space>
    </header>

    <!-- 基本信息 -->
    <section class="panel editor-panel">
      <a-form layout="vertical" class="editor-form">
        <a-form-item label="名称">
          <a-input v-model:value="scenario.name" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea
            v-model:value="scenario.description"
            :auto-size="{ minRows: 1, maxRows: 4 }"
          />
        </a-form-item>
      </a-form>
    </section>

    <!-- 步骤区 -->
    <section class="panel editor-panel">
      <div class="panel__head">场景步骤</div>

      <div class="steps-list">
        <draggable v-model="steps" item-key="_id" :animation="180" handle=".step-handle">
          <template #item="{ element: step, index: idx }">
            <div class="step-card">
            <a-row :gutter="12" align="middle">
              <a-col flex="40px">
                <drag-outlined class="step-handle" />
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
              <a-col flex="80px" class="step-actions">
                <a-button
                  size="small"
                  type="text"
                  danger
                  @click="steps.splice(idx, 1)"
                >
                  删除
                </a-button>
              </a-col>
            </a-row>

            <a-tabs size="small" class="step-tabs">
              <a-tab-pane key="assertions" tab="断言">
                <AssertionEditor v-model="step.assertions" />
              </a-tab-pane>
              <a-tab-pane key="extracts" tab="提取">
                <ExtractEditor v-model="step.extracts" />
              </a-tab-pane>
            </a-tabs>
            </div>
          </template>
        </draggable>

        <a-button block type="dashed" class="add-step-btn" @click="addStep">
          <plus-outlined />新增步骤
        </a-button>
      </div>

      <div class="panel__foot">
        <a-button type="primary" @click="save" :loading="saving">
          保存场景
        </a-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlayCircleOutlined, DeleteOutlined, DragOutlined, PlusOutlined,
  PartitionOutlined, ArrowLeftOutlined
} from '@ant-design/icons-vue'
import draggable from 'vuedraggable'
import { ScenarioApi } from '@/api'
import { uid, ensureIds } from '@/utils/uid'
import { useProjectStore } from '@/stores/project'
import type { Scenario, AssertionResult, ExtractResult } from '@/types'
import AssertionEditor from '@/components/AssertionEditor.vue'
import ExtractEditor from '@/components/ExtractEditor.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

interface Step {
  /** 前端拖拽排序用的内部唯一标识（不参与业务语义） */
  _id?: string
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
  ensureIds(steps.value)
}

function addStep() {
  steps.value.push({
    _id: uid(),
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

/** 返回场景列表 */
function goBack() {
  router.push({ name: 'scenarios' })
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

/* 面板样式（通用） */
.editor-panel {
  padding: 0;
  margin-bottom: var(--sp-5);
  overflow: hidden;
}

.editor-panel .panel__head {
  padding: var(--sp-3) var(--sp-5);
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--tx-1);
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--bd-subtle);
}

.panel__foot {
  padding: var(--sp-4) var(--sp-5);
  border-top: 1px solid var(--bd-subtle);
  background: var(--bg-subtle);
}

/* 第一块面板（表单）稍微有点呼吸感 */
.editor-form {
  padding: var(--sp-5);
}

.editor-form :deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}

/* 步骤列表容器 */
.steps-list {
  padding: var(--sp-4) var(--sp-5) var(--sp-5);
}

/* 单条步骤卡片 */
.step-card {
  padding: var(--sp-3) var(--sp-3) 0;
  margin-bottom: var(--sp-3);
  background: var(--bg-card);
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  transition: border-color var(--dur-fast) var(--ease),
              box-shadow var(--dur-fast) var(--ease);
}

.step-card:hover {
  border-color: var(--bd-strong);
  box-shadow: var(--sd-xs);
}

/* 拖动手柄 */
.step-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 32px;
  font-size: 16px;
  color: var(--tx-4);
  cursor: grab;
  transition: color var(--dur-fast) var(--ease);
}

.step-handle:hover {
  color: var(--c-primary);
}

.step-handle:active {
  cursor: grabbing;
}

.step-actions {
  text-align: right;
}

/* 步骤内 Tab 容器 */
.step-tabs {
  margin: var(--sp-3) calc(-1 * var(--sp-3)) 0;
}

/* 新增步骤按钮 */
.add-step-btn {
  margin-top: var(--sp-2);
}
</style>
