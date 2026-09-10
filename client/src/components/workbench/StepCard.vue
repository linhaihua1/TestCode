<!--
  单个接口用例步骤卡片（编写用例面板内使用）。

  功能：
  - 左侧开关：禁用 / 启用该步骤（禁用后执行时跳过）
  - 拖拽手柄（.step-handle）：供外层 draggable 排序
  - 步骤类型编辑：HTTP 请求 / 等待时间 / IF / FOR / WHILE / JS 脚本
  - HTTP 请求内嵌 请求头 / Query / Body / 断言 / 提取 五个 Tab
  - 鼠标右键：复制 / 粘贴该步骤
-->
<template>
  <a-dropdown :trigger="['contextmenu']" :disabled="disabled">
    <a-card
      size="small"
      class="step-card"
      :class="{ 'step-card--disabled': !isEnabled }"
    >
      <template #extra>
        <a-space>
          <a-tag :color="typeColor(step.type)">{{ typeLabel(step.type) }}</a-tag>
          <a-button size="small" type="text" danger @click="emit('remove')">
            <delete-outlined />
          </a-button>
        </a-space>
      </template>

      <div class="step-header">
        <a-tooltip :title="isEnabled ? '禁用该步骤' : '启用该步骤'">
          <a-switch
            :checked="isEnabled"
            size="small"
            @change="onToggleEnabled"
          />
        </a-tooltip>
        <drag-outlined class="step-handle" />
        <a-input v-model:value="step.name" placeholder="步骤名称" />
        <a-select
          v-model:value="step.type"
          :options="stepTypeOptions"
          size="small"
          class="step-type"
          @change="resetStepShape"
        />
      </div>

      <!-- HTTP 请求 -->
      <template v-if="step.type === 'http'">
        <a-row :gutter="8" class="step-row">
          <a-col :span="4">
            <a-select v-model:value="step.method" :options="methodOptions" />
          </a-col>
          <a-col :span="20">
            <a-input v-model:value="step.url" placeholder="请求 URL（支持 {{var}} 参数化）" />
          </a-col>
        </a-row>
        <a-tabs v-model:value="activeTab" size="small" class="step-tabs">
          <a-tab-pane key="headers" tab="请求头">
            <KeyValueEditor v-model="step.headers" />
          </a-tab-pane>
          <a-tab-pane key="query" tab="Query">
            <KeyValueEditor v-model="step.query" key-label="Param" />
          </a-tab-pane>
          <a-tab-pane key="body" tab="Body">
            <Monaco v-model="step.body" language="json" height="160px" />
          </a-tab-pane>
          <a-tab-pane key="assertions" tab="断言">
            <AssertionEditor v-model="step.assertions" />
          </a-tab-pane>
          <a-tab-pane key="extracts" tab="提取">
            <ExtractEditor v-model="step.extracts" />
          </a-tab-pane>
        </a-tabs>
      </template>

      <!-- 等待时间 -->
      <template v-else-if="step.type === 'wait'">
        <a-row :gutter="8" class="step-row" align="middle">
          <a-col flex="auto">
            <a-input-number
              v-model:value="step.waitMs"
              :min="0"
              :step="100"
              style="width: 100%"
              placeholder="等待时间"
            />
          </a-col>
          <a-col flex="60px">
            <span class="step-unit">毫秒</span>
          </a-col>
        </a-row>
      </template>

      <!-- IF / WHILE 条件 -->
      <template v-else-if="step.type === 'if' || step.type === 'while'">
        <a-input
          v-model:value="step.condition"
          class="step-row"
          placeholder="条件表达式（支持 {{var}} 与 JS 简单表达式）"
        />
      </template>

      <!-- FOR 循环 -->
      <template v-else-if="step.type === 'for'">
        <a-row :gutter="8" class="step-row" align="middle">
          <a-col flex="auto">
            <a-input-number
              v-model:value="step.loopCount"
              :min="1"
              style="width: 100%"
              placeholder="循环次数"
            />
          </a-col>
          <a-col flex="60px">
            <span class="step-unit">次</span>
          </a-col>
        </a-row>
      </template>

      <!-- JS 脚本 -->
      <template v-else-if="step.type === 'script'">
        <Monaco
          v-model="step.script"
          language="javascript"
          height="160px"
          class="step-row"
        />
      </template>
    </a-card>

    <template #overlay>
      <a-menu>
        <a-menu-item key="copy" @click="emit('copy')">
          <copy-outlined /> 复制该步骤
        </a-menu-item>
        <a-menu-item key="paste" @click="emit('paste')">
          <snippets-outlined /> 粘贴到下方
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  DeleteOutlined, DragOutlined, CopyOutlined, SnippetsOutlined
} from '@ant-design/icons-vue'
import type { CaseStep } from '@/types'
import KeyValueEditor from '@/components/KeyValueEditor.vue'
import AssertionEditor from '@/components/AssertionEditor.vue'
import ExtractEditor from '@/components/ExtractEditor.vue'
import Monaco from '@/components/Monaco.vue'

const props = defineProps<{ step: CaseStep; disabled?: boolean }>()
const emit = defineEmits<{
  remove: []
  copy: []
  paste: []
}>()

const step = computed(() => props.step)

const isEnabled = computed(() => props.step.enabled !== false)

// 内部维护 __activeTab，兼容旧数据无该字段
const activeTab = computed({
  get: () => props.step.__activeTab || 'headers',
  set: (v: string) => { props.step.__activeTab = v }
})

const stepTypeOptions = [
  { value: 'http', label: 'HTTP 请求' },
  { value: 'wait', label: '等待时间' },
  { value: 'if', label: 'IF 条件' },
  { value: 'for', label: 'FOR 循环' },
  { value: 'while', label: 'WHILE 循环' },
  { value: 'script', label: 'JS 脚本' }
]

const methodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map((v) => ({
  value: v,
  label: v
}))

function typeLabel(t: string) {
  return stepTypeOptions.find((o) => o.value === t)?.label || t
}

function typeColor(t: string) {
  switch (t) {
    case 'http': return 'blue'
    case 'wait': return 'default'
    case 'if': return 'orange'
    case 'for': return 'purple'
    case 'while': return 'purple'
    case 'script': return 'cyan'
    default: return 'default'
  }
}

function onToggleEnabled(v: boolean) {
  props.step.enabled = v
}

function resetStepShape() {
  const s = props.step
  s.headers = []
  s.query = []
  s.body = ''
  s.assertions = []
  s.extracts = []
  s.condition = ''
  s.loopCount = 1
  s.waitMs = 1000
  s.script = ''
  s.__activeTab = 'headers'
}
</script>

<style scoped>
.step-card {
  margin-bottom: var(--sp-2);
}

.step-card--disabled {
  opacity: 0.55;
}

.step-card--disabled :deep(.ant-card-body) {
  pointer-events: none;
}

.step-card--disabled .step-header {
  pointer-events: auto;
}

.step-header {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.step-handle {
  cursor: grab;
  color: var(--tx-4);
  font-size: 14px;
  flex-shrink: 0;
}

.step-type {
  width: 110px;
  flex-shrink: 0;
}

.step-row {
  margin-top: var(--sp-2);
}

.step-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.step-unit {
  color: var(--tx-3);
  font-size: var(--fs-sm);
}
</style>
