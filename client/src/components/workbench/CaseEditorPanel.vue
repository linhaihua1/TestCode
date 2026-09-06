<template>
  <div class="case-editor">
    <a-page-header :title="form.name || '未命名用例'">
      <template #extra>
        <a-space>
          <a-tag color="blue">v{{ form.version }}</a-tag>
          <a-select
            v-model:value="form.status"
            :options="statusOptions"
            size="small"
            style="width: 120px"
          />
          <a-button @click="save" :loading="saving">
            <save-outlined />保存
          </a-button>
          <a-button type="primary" @click="debug" :loading="debugging">
            <play-circle-outlined />调试
          </a-button>
          <a-dropdown>
            <a-button>
              评审 <down-outlined />
            </a-button>
            <template #overlay>
              <a-menu>
                <a-menu-item @click="review('submit')">提交评审</a-menu-item>
                <a-menu-item @click="review('approve')">通过</a-menu-item>
                <a-menu-item @click="review('reject')">驳回</a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
          <a-popconfirm title="确认删除？" @confirm="remove">
            <a-button danger>
              <delete-outlined />
            </a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-page-header>

    <a-form layout="vertical">
      <a-row :gutter="12">
        <a-col :span="12">
          <a-form-item label="用例名称" required>
            <a-input v-model:value="form.name" />
          </a-form-item>
        </a-col>
        <a-col :span="6">
          <a-form-item label="优先级">
            <a-select v-model:value="form.priority" :options="priorityOptions" />
          </a-form-item>
        </a-col>
        <a-col :span="6">
          <a-form-item label="标签">
            <a-select v-model:value="form.tags" mode="tags" placeholder="输入后回车" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="描述">
        <a-textarea
          v-model:value="form.description"
          :auto-size="{ minRows: 1, maxRows: 4 }"
        />
      </a-form-item>
    </a-form>

    <a-divider>测试步骤（{{ form.steps?.length || 0 }}）</a-divider>

    <a-empty v-if="!form.steps?.length" description="暂无步骤，点击下方按钮新增">
    </a-empty>

    <VueDraggable
      v-model="form.steps"
      :animation="180"
      handle=".step-handle"
    >
      <a-card
        v-for="(step, idx) in form.steps"
        :key="idx"
        size="small"
        :title="null"
        style="margin-bottom: 8px"
      >
        <template #extra>
          <a-space>
            <a-tag>{{ step.type }}</a-tag>
            <a-button
              size="small"
              type="text"
              danger
              @click="form.steps.splice(idx, 1)"
            >删除</a-button>
          </a-space>
        </template>
        <div class="step-header">
          <drag-outlined class="step-handle" style="cursor: grab; color: #aaa; margin-right: 8px" />
          <a-input
            v-model:value="step.name"
            placeholder="步骤名称"
            style="max-width: 320px"
          />
          <a-select
            v-model:value="step.type"
            :options="stepTypeOptions"
            size="small"
            style="width: 110px; margin-left: 8px"
            @change="resetStepShape(step)"
          />
        </div>

        <template v-if="step.type === 'http'">
          <a-row :gutter="8" style="margin-top: 8px">
            <a-col :span="3">
              <a-select v-model:value="step.method" :options="methodOptions" />
            </a-col>
            <a-col :span="21">
              <a-input v-model:value="step.url" placeholder="请求 URL（支持 {{var}}）" />
            </a-col>
          </a-row>
          <a-tabs v-model:value="step.__activeTab" size="small" style="margin-top: 8px">
            <a-tab-pane key="headers" tab="请求头">
              <KeyValueEditor v-model="step.headers" />
            </a-tab-pane>
            <a-tab-pane key="query" tab="Query">
              <KeyValueEditor v-model="step.query" key-label="Param" />
            </a-tab-pane>
            <a-tab-pane key="body" tab="Body">
              <Monaco
                v-model="step.body"
                language="json"
                height="180px"
              />
            </a-tab-pane>
            <a-tab-pane key="assertions" tab="断言">
              <AssertionEditor v-model="step.assertions" />
            </a-tab-pane>
            <a-tab-pane key="extracts" tab="提取">
              <ExtractEditor v-model="step.extracts" />
            </a-tab-pane>
          </a-tabs>
        </template>

        <template v-else-if="step.type === 'if' || step.type === 'while'">
          <a-input
            v-model:value="step.condition"
            placeholder="条件表达式（支持 {{var}} 与 JS 简单表达式）"
            style="margin-top: 8px"
          />
        </template>

        <template v-else-if="step.type === 'for'">
          <a-input-number
            v-model:value="step.loopCount"
            :min="1"
            style="margin-top: 8px"
          />
          <span style="margin-left: 8px">循环次数</span>
        </template>

        <template v-else-if="step.type === 'script'">
          <Monaco
            v-model="step.script"
            language="javascript"
            height="180px"
          />
        </template>
      </a-card>
    </VueDraggable>

    <a-button block type="dashed" style="margin-top: 8px" @click="addStep">
      <plus-outlined />新增步骤
    </a-button>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  SaveOutlined, DeleteOutlined, DownOutlined, PlayCircleOutlined,
  DragOutlined, PlusOutlined
} from '@ant-design/icons-vue'
import { VueDraggable } from 'vue-draggable-plus'
import { CaseApi } from '@/api'
import type { CaseInfo, AssertionResult, ExtractResult } from '@/types'
import KeyValueEditor from '@/components/KeyValueEditor.vue'
import AssertionEditor from '@/components/AssertionEditor.vue'
import ExtractEditor from '@/components/ExtractEditor.vue'
import Monaco from '@/components/Monaco.vue'

const props = defineProps<{ caseData: CaseInfo }>()
const emit = defineEmits<{ saved: []; debugFinished: [caseId: string] }>()

interface Step {
  type: 'http' | 'if' | 'for' | 'while' | 'script'
  name?: string
  method?: string
  url?: string
  headers?: any[]
  query?: any[]
  body?: string
  assertions?: AssertionResult[]
  extracts?: ExtractResult[]
  condition?: string
  loopCount?: number
  script?: string
  __activeTab?: string
}

const form = reactive<CaseInfo & { steps: Step[] }>({
  ...props.caseData,
  steps: (props.caseData.steps || []) as Step[]
})
const saving = ref(false)
const debugging = ref(false)

const statusOptions = [
  { value: 'draft', label: '草稿' },
  { value: 'reviewing', label: '评审中' },
  { value: 'pass', label: '通过' },
  { value: 'fail', label: '失败' }
]

const priorityOptions = ['P0', 'P1', 'P2', 'P3'].map((v) => ({ value: v, label: v }))

const stepTypeOptions = [
  { value: 'http', label: 'HTTP 请求' },
  { value: 'if', label: 'IF 条件' },
  { value: 'for', label: 'FOR 循环' },
  { value: 'while', label: 'WHILE 循环' },
  { value: 'script', label: 'JS 脚本' }
]

const methodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].map((v) => ({
  value: v,
  label: v
}))

watch(
  () => props.caseData,
  (v) => {
    Object.assign(form, v)
    form.steps = (v.steps || []) as Step[]
  },
  { deep: true }
)

function addStep() {
  form.steps.push({
    type: 'http',
    name: `步骤 ${form.steps.length + 1}`,
    method: 'GET',
    url: '',
    headers: [],
    query: [],
    body: '',
    assertions: [],
    extracts: [],
    __activeTab: 'headers'
  })
}

function resetStepShape(step: Step) {
  step.headers = []
  step.query = []
  step.body = ''
  step.assertions = []
  step.extracts = []
}

async function save() {
  if (!form.name) {
    message.warning('请填写用例名称')
    return
  }
  saving.value = true
  try {
    await CaseApi.update(form.id, form)
    emit('saved')
  } finally {
    saving.value = false
  }
}

async function remove() {
  await CaseApi.remove(form.id)
  emit('saved')
}

async function review(action: 'submit' | 'approve' | 'reject') {
  await CaseApi.review(form.id, action)
  message.success('评审已提交')
  form.status =
    action === 'approve' ? 'pass' : action === 'reject' ? 'draft' : 'reviewing'
}

async function debug() {
  debugging.value = true
  try {
    await CaseApi.debug(form.id)
    message.success('调试完成')
    emit('debugFinished', form.id)
  } finally {
    debugging.value = false
  }
}
</script>

<style scoped>
.step-header {
  display: flex;
  align-items: center;
}
:deep(.ant-card-body) {
  padding: 8px 12px;
}
</style>