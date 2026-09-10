<!--
  编写用例面板（需求文档 §4）。

  <h3>结构</h3>
  <ul>
    <li>顶部：用例名称 + 保存 / 调试 / 删除</li>
    <li>Tab：脚本 / 调试记录</li>
    <li>脚本 Tab：前置步骤 / 测试步骤 / 后置步骤 三个分区（各自可拖拽排序）</li>
    <li>调试记录 Tab：当前用例的调试记录</li>
  </ul>
-->
<template>
  <div class="case-editor">
    <!-- 用例名称 + 操作 -->
    <div class="editor-header">
      <a-input
        v-model:value="form.name"
        class="editor-name"
        placeholder="请输入接口用例名称"
        size="large"
      />
      <a-space class="editor-actions">
        <a-button @click="save" :loading="saving">
          <save-outlined />保存
        </a-button>
        <a-button type="primary" @click="debug" :loading="debugging">
          <play-circle-outlined />调试
        </a-button>
        <a-popconfirm title="确认删除该用例？" @confirm="remove">
          <a-button danger>
            <delete-outlined />
          </a-button>
        </a-popconfirm>
      </a-space>
    </div>

    <!-- 脚本 / 调试记录 -->
    <a-tabs v-model:active-key="activeTab" class="editor-tabs" size="small">
      <!-- ============ 脚本 Tab ============ -->
      <a-tab-pane key="script" tab="脚本">
        <!-- 用例元信息（紧凑） -->
        <div class="meta-row">
          <a-select v-model:value="form.priority" :options="priorityOptions" size="small" style="width: 100px" />
          <a-select v-model:value="form.tags" mode="tags" size="small" placeholder="标签" style="flex: 1" />
          <a-input v-model:value="form.description" size="small" placeholder="用例描述（可选）" style="flex: 2" />
        </div>

        <!-- 前置步骤 -->
        <section class="step-section">
          <div class="step-section__head">
            <span class="step-section__title">前置步骤</span>
            <span class="step-section__hint">管理该接口用例的前置条件</span>
          </div>
          <draggable v-model="preSteps" item-key="_id" :animation="180" handle=".step-handle">
            <template #item="{ element: step, index: idx }">
              <StepCard
                :step="step"
                @remove="removeStep('PRE', idx)"
                @copy="copyStep(step)"
                @paste="pasteStep('PRE', idx)"
              />
            </template>
          </draggable>
          <a-empty v-if="!preSteps.length" description="暂无前置步骤" :image-style="{ height: '40px' }" />
          <a-button block type="dashed" size="small" @click="addStep('PRE')">
            <plus-outlined />新增前置步骤
          </a-button>
        </section>

        <!-- 测试步骤 -->
        <section class="step-section">
          <div class="step-section__head">
            <span class="step-section__title">测试步骤</span>
            <span class="step-section__hint">管理接口用例步骤，支持多接口拖拽排序</span>
          </div>
          <draggable v-model="testSteps" item-key="_id" :animation="180" handle=".step-handle">
            <template #item="{ element: step, index: idx }">
              <StepCard
                :step="step"
                @remove="removeStep('TEST', idx)"
                @copy="copyStep(step)"
                @paste="pasteStep('TEST', idx)"
              />
            </template>
          </draggable>
          <a-empty v-if="!testSteps.length" description="暂无测试步骤" :image-style="{ height: '40px' }" />
          <a-button block type="dashed" size="small" @click="addStep('TEST')">
            <plus-outlined />新增测试步骤
          </a-button>
        </section>

        <!-- 后置步骤 -->
        <section class="step-section">
          <div class="step-section__head">
            <span class="step-section__title">后置步骤</span>
            <span class="step-section__hint">执行完成后清理测试数据等操作</span>
          </div>
          <draggable v-model="postSteps" item-key="_id" :animation="180" handle=".step-handle">
            <template #item="{ element: step, index: idx }">
              <StepCard
                :step="step"
                @remove="removeStep('POST', idx)"
                @copy="copyStep(step)"
                @paste="pasteStep('POST', idx)"
              />
            </template>
          </draggable>
          <a-empty v-if="!postSteps.length" description="暂无后置步骤" :image-style="{ height: '40px' }" />
          <a-button block type="dashed" size="small" @click="addStep('POST')">
            <plus-outlined />新增后置步骤
          </a-button>
        </section>
      </a-tab-pane>

      <!-- ============ 调试记录 Tab ============ -->
      <a-tab-pane key="debug" tab="调试记录">
        <div class="debug-list">
          <a-empty v-if="!records.length && !recordsLoading" description="暂无调试记录" />
          <a-spin :spinning="recordsLoading">
            <a-list :data-source="records" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <a-space size="small">
                        <a-tag :color="statusColor(item.result)">{{ item.result }}</a-tag>
                        <span class="text-3">{{ formatTime(item.createdAt) }}</span>
                        <span class="text-3">{{ item.totalDuration }} ms</span>
                      </a-space>
                    </template>
                    <template #description>
                      <a-collapse ghost size="small">
                        <a-collapse-panel key="steps" header="步骤明细">
                          <a-steps :current="item.stepResults?.length || 0" size="small" direction="vertical">
                            <a-step
                              v-for="(s, i) in item.stepResults"
                              :key="i"
                              :title="s.stepName"
                              :status="s.status === 'success' ? 'finish' : 'error'"
                              :description="`${s.durationMs}ms${s.error ? ' - ' + s.error : ''}`"
                            />
                          </a-steps>
                        </a-collapse-panel>
                        <a-collapse-panel key="asserts" header="断言结果">
                          <a-list :data-source="item.assertionResults" size="small">
                            <template #renderItem="{ item: a }">
                              <a-list-item>
                                <a-space size="small">
                                  <a-tag :color="a.passed ? 'green' : 'red'">{{ a.passed ? '✓' : '✗' }}</a-tag>
                                  <span>{{ a.message }}</span>
                                </a-space>
                              </a-list-item>
                            </template>
                          </a-list>
                        </a-collapse-panel>
                        <a-collapse-panel key="extract" header="提取变量">
                          <pre class="json-pre">{{ JSON.stringify(item.extractedVariables, null, 2) }}</pre>
                        </a-collapse-panel>
                      </a-collapse>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-spin>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  SaveOutlined, DeleteOutlined, PlayCircleOutlined, PlusOutlined
} from '@ant-design/icons-vue'
import draggable from 'vuedraggable'
import { CaseApi } from '@/api'
import { uid, ensureIds } from '@/utils/uid'
import type { CaseInfo, CaseStep, DebugRecord } from '@/types'
import StepCard from '@/components/workbench/StepCard.vue'

const props = defineProps<{ caseData: CaseInfo }>()
const emit = defineEmits<{
  saved: []
  debug: [caseId: string]
}>()

const form = reactive<CaseInfo & { steps: CaseStep[] }>({
  ...props.caseData,
  steps: (props.caseData.steps || []) as CaseStep[]
})

const saving = ref(false)
const debugging = ref(false)
const activeTab = ref<'script' | 'debug'>('script')

const records = ref<DebugRecord[]>([])
const recordsLoading = ref(false)

const priorityOptions = ['P0', 'P1', 'P2', 'P3'].map((v) => ({ value: v, label: v }))

/* ============================================================
   三个步骤分区（computed get/set，供 draggable v-model 排序）
   ============================================================ */
function makePartition(position: 'PRE' | 'TEST' | 'POST') {
  return computed<CaseStep[]>({
    get: () => form.steps.filter((s) => (s.position || 'TEST') === position),
    set: (sorted) => {
      let i = 0
      form.steps = form.steps.map((s) =>
        (s.position || 'TEST') === position ? sorted[i++] : s
      )
    }
  })
}
const preSteps = makePartition('PRE')
const testSteps = makePartition('TEST')
const postSteps = makePartition('POST')

/* ============================================================
   步骤增删 + 复制粘贴
   ============================================================ */
function addStep(position: 'PRE' | 'TEST' | 'POST') {
  const step: CaseStep = {
    _id: uid(),
    position,
    enabled: true,
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
  }
  if (position === 'PRE') {
    form.steps.unshift(step)
  } else if (position === 'POST') {
    form.steps.push(step)
  } else {
    const firstPostIdx = form.steps.findIndex((s) => (s.position || 'TEST') === 'POST')
    if (firstPostIdx === -1) form.steps.push(step)
    else form.steps.splice(firstPostIdx, 0, step)
  }
}

function removeStep(position: 'PRE' | 'TEST' | 'POST', idx: number) {
  const posSteps = form.steps.filter((s) => (s.position || 'TEST') === position)
  const target = posSteps[idx]
  if (target) {
    form.steps.splice(form.steps.indexOf(target), 1)
  }
}

const clipboard = ref<CaseStep | null>(null)

function copyStep(step: CaseStep) {
  clipboard.value = JSON.parse(JSON.stringify(step))
  clipboard.value!._id = uid()
  message.success('已复制步骤')
}

function pasteStep(position: 'PRE' | 'TEST' | 'POST', idx: number) {
  if (!clipboard.value) {
    message.warning('请先复制一个步骤')
    return
  }
  const clone: CaseStep = JSON.parse(JSON.stringify(clipboard.value))
  clone._id = uid()
  clone.position = position
  const posSteps = form.steps.filter((s) => (s.position || 'TEST') === position)
  const insertAt = posSteps[idx] ? form.steps.indexOf(posSteps[idx]) + 1 : form.steps.length
  form.steps.splice(insertAt, 0, clone)
  message.success('已粘贴步骤')
}

/* ============================================================
   保存 / 调试 / 删除
   ============================================================ */
async function save() {
  if (!form.name) {
    message.warning('请填写用例名称')
    return
  }
  saving.value = true
  try {
    await CaseApi.update(form.id, { ...form, steps: form.steps })
    emit('saved')
  } finally {
    saving.value = false
  }
}

async function debug() {
  // 调试走弹窗（环境变量选择），由 Workbench 打开 DebugModal
  emit('debug', form.id)
}

async function remove() {
  await CaseApi.remove(form.id)
  emit('saved')
}

/* ============================================================
   调试记录
   ============================================================ */
function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'red' : s === 'error' ? 'volcano' : 'default'
}

function formatTime(iso: string) {
  return dayjs(iso).format('YYYY-MM-DD HH:mm:ss')
}

async function loadRecords() {
  if (!form.id) return
  recordsLoading.value = true
  try {
    records.value = await CaseApi.debugRecords(form.id)
  } finally {
    recordsLoading.value = false
  }
}

// 供父组件（Workbench）在调试完成后调用，切换到调试记录 Tab
function showDebugRecords(_caseId: string) {
  activeTab.value = 'debug'
  loadRecords()
}

defineExpose({ save, debug, showDebugRecords })

/* ============================================================
   监听 caseData 变化
   ============================================================ */
function ensureStepShape(s: CaseStep) {
  if (!s.headers) s.headers = []
  if (!s.query) s.query = []
  if (!s.assertions) s.assertions = []
  if (!s.extracts) s.extracts = []
  if (s.enabled === undefined) s.enabled = true
  if (!s.position) s.position = 'TEST'
}

watch(
  () => props.caseData,
  (v) => {
    Object.assign(form, v)
    form.steps = (v.steps || []) as CaseStep[]
    ensureIds(form.steps)
    form.steps.forEach(ensureStepShape)
  },
  { deep: true }
)

// 初始加载也补齐一次（旧数据兼容）
form.steps.forEach(ensureStepShape)
</script>

<style scoped>
.case-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: var(--sp-3);
}

.editor-header {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-shrink: 0;
  margin-bottom: var(--sp-2);
}

.editor-name {
  flex: 1;
  font-weight: 600;
}

.editor-actions {
  flex-shrink: 0;
}

.editor-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.editor-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  overflow: auto;
}

/* 用例元信息行 */
.meta-row {
  display: flex;
  gap: var(--sp-2);
  margin-bottom: var(--sp-3);
  align-items: center;
}

/* 步骤分区 */
.step-section {
  margin-bottom: var(--sp-4);
}

.step-section__head {
  display: flex;
  align-items: baseline;
  gap: var(--sp-2);
  margin-bottom: var(--sp-2);
}

.step-section__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--tx-1);
}

.step-section__hint {
  font-size: var(--fs-xs);
  color: var(--tx-4);
}

.debug-list {
  padding: var(--sp-2) 0;
}

.json-pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  background: var(--bg-subtle);
  padding: var(--sp-2);
  border-radius: var(--rd-sm);
  margin: 0;
}
</style>
