<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">测试任务</div>
      <a-space>
        <a-button @click="$router.push('/trend')">
          <line-chart-outlined />趋势
        </a-button>
        <a-button type="primary" @click="openCreate">
          <plus-outlined />新建任务
        </a-button>
      </a-space>
    </div>

    <!-- 任务表格 -->
    <div class="panel">
      <a-table
        :data-source="tasks"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :expanded-row-keys="expandedKeys"
        @expand="onExpand"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'caseCount'">
            <a-tag color="blue">{{ (record.caseIds || []).length }} 个用例</a-tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch :checked="record.enabled" @change="toggle(record)" />
          </template>
          <template v-else-if="column.key === 'cronExpr'">
            <a-tag v-if="record.cronExpr" color="blue">{{ record.cronExpr }}</a-tag>
            <a-tag v-else color="default">手动</a-tag>
          </template>
          <template v-else-if="column.key === 'environment'">
            <a-tag v-if="envName(record.environmentId)" color="cyan">{{ envName(record.environmentId) }}</a-tag>
            <a-tag v-else color="default">默认环境</a-tag>
          </template>
          <template v-else-if="column.key === 'executeMode'">
            <a-tag :color="record.executeMode === 'parallel' ? 'orange' : 'default'">
              {{ record.executeMode === 'parallel' ? '并行' : '顺序' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'failStrategy'">
            <a-tag color="purple">{{ strategyLabel(record.failStrategy) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" type="link" @click="runAsync(record)" :loading="runningId === record.id + 'a'">
                <api-outlined />运行
              </a-button>
              <a-button size="small" type="link" @click="runSync(record)" :loading="runningId === record.id + 's'">
                <thunderbolt-outlined />同步运行
              </a-button>
              <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>

        <template #expandedRowRender="{ record }">
          <a-table
            :data-source="runsByTask[record.id] || []"
            :columns="runColumns"
            row-key="id"
            size="small"
            :pagination="false"
          >
            <template #bodyCell="{ column, run }">
              <template v-if="column.key === 'result'">
                <a-tag :color="resultColor(run.result)">{{ run.result }}</a-tag>
              </template>
              <template v-else-if="column.key === 'duration'">
                {{ run.duration }} ms
              </template>
              <template v-else-if="column.key === 'startedAt'">
                {{ formatDate(run.startedAt) }}
              </template>
            </template>
          </a-table>
        </template>
      </a-table>
    </div>

    <!-- 新建/编辑任务弹窗 -->
    <a-modal
      v-model:open="modal"
      :title="form.id ? '编辑任务' : '新建任务'"
      :width="1000"
      :confirm-loading="saving"
      :mask-closable="false"
      @cancel="onCancel"
    >
      <template #footer>
        <a-space>
          <a-button @click="onCancel">取消</a-button>
          <a-button @click="save(false)" :loading="saving">保存</a-button>
          <a-button type="primary" @click="save(true)" :loading="saving">
            <play-circle-outlined />保存并运行
          </a-button>
        </a-space>
      </template>

      <a-tabs v-model:active-key="activeTab">
        <!-- ============ 基础信息 ============ -->
        <a-tab-pane key="basic" tab="基础信息">
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="任务名称" required>
                  <a-input v-model:value="form.name" placeholder="请输入任务名称" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="描述">
                  <a-input v-model:value="form.description" placeholder="任务描述（可选）" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="8">
                <a-form-item label="执行环境">
                  <a-select
                    v-model:value="form.environmentId"
                    :options="environmentOptions"
                    placeholder="选择执行环境"
                    allow-clear
                    style="width: 100%"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="超时时间 (ms)">
                  <a-input-number v-model:value="form.timeoutMs" :min="1000" :step="1000" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="失败重试次数">
                  <a-input-number v-model:value="form.retryCount" :min="0" :max="10" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="Cron 表达式（留空为手动）">
                  <a-input v-model:value="form.cronExpr" placeholder="0 0 2 * * ? (Quartz Cron)" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="启用">
                  <a-switch v-model:checked="form.enabled" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>

        <!-- ============ 用例编排 ============ -->
        <a-tab-pane key="cases" tab="用例编排">
          <div class="case-orchestration">
            <!-- 左：用例库 -->
            <div class="case-lib">
              <div class="case-lib__head">
                <span>接口用例库</span>
                <a-input-search
                  v-model:value="caseKeyword"
                  placeholder="搜索用例"
                  size="small"
                  style="width: 200px"
                />
              </div>
              <div class="case-lib__list">
                <a-empty v-if="!filteredAvailableCases.length" description="暂无可选用例" />
                <div
                  v-for="c in filteredAvailableCases"
                  :key="c.id"
                  class="case-lib__item"
                  @click="addCase(c)"
                >
                  <div class="case-lib__name">{{ c.name }}</div>
                  <a-tag v-if="c.priority" size="small" :color="priorityColor(c.priority)">{{ c.priority }}</a-tag>
                </div>
              </div>
            </div>

            <!-- 右：已选用例（有序） -->
            <div class="case-selected">
              <div class="case-selected__head">
                <span>已选用例（按序执行，共 {{ selectedCases.length }} 个）</span>
                <span class="case-selected__hint">拖拽或使用按钮调整顺序</span>
              </div>
              <div class="case-selected__list">
                <a-empty v-if="!selectedCases.length" description="从左侧点击添加用例" />
                <draggable
                  v-else
                  v-model="selectedCases"
                  item-key="id"
                  :animation="160"
                  handle=".drag-handle"
                >
                  <template #item="{ element: c, index: i }">
                    <div class="case-row">
                      <span class="case-row__idx">{{ i + 1 }}</span>
                      <drag-outlined class="drag-handle" />
                      <span class="case-row__name">{{ c.name }}</span>
                      <a-space size="2">
                        <a-tooltip title="置顶">
                          <a-button size="small" type="text" :disabled="i === 0" @click="moveCase(i, 'top')">
                            <vertical-align-top-outlined />
                          </a-button>
                        </a-tooltip>
                        <a-tooltip title="上移">
                          <a-button size="small" type="text" :disabled="i === 0" @click="moveCase(i, 'up')">
                            <arrow-up-outlined />
                          </a-button>
                        </a-tooltip>
                        <a-tooltip title="下移">
                          <a-button size="small" type="text" :disabled="i === selectedCases.length - 1" @click="moveCase(i, 'down')">
                            <arrow-down-outlined />
                          </a-button>
                        </a-tooltip>
                        <a-tooltip title="置尾">
                          <a-button size="small" type="text" :disabled="i === selectedCases.length - 1" @click="moveCase(i, 'bottom')">
                            <vertical-align-bottom-outlined />
                          </a-button>
                        </a-tooltip>
                        <a-tooltip title="删除">
                          <a-button size="small" type="text" danger @click="removeCase(i)">
                            <delete-outlined />
                          </a-button>
                        </a-tooltip>
                      </a-space>
                    </div>
                  </template>
                </draggable>
              </div>
            </div>
          </div>
        </a-tab-pane>

        <!-- ============ 执行策略 ============ -->
        <a-tab-pane key="execution" tab="执行策略">
          <a-form layout="vertical">
            <a-form-item label="执行模式">
              <a-radio-group v-model:value="form.executeMode">
                <a-radio value="sequential">顺序执行（按编排顺序依次执行）</a-radio>
                <a-radio value="parallel">并行执行（按并行池大小并发）</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-form-item v-if="form.executeMode === 'parallel'" label="并行池大小">
              <a-slider
                v-model:value="form.parallelPoolSize"
                :min="1"
                :max="50"
                :marks="{ 1: '1', 5: '5', 10: '10', 20: '20', 50: '50' }"
              />
              <span class="hint">当前：{{ form.parallelPoolSize || 5 }} 个并发线程（范围 1-200）</span>
            </a-form-item>
            <a-form-item label="失败策略">
              <a-select v-model:value="form.failStrategy" style="width: 100%">
                <a-select-option value="stop_on_fail">失败即停（任一用例失败即停止后续）</a-select-option>
                <a-select-option value="continue_all">失败继续（所有用例跑完）</a-select-option>
                <a-select-option value="retry_then_stop">重试后停（失败后按重试次数重试）</a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="用例级变量 (JSON)">
              <a-textarea
                v-model:value="variablesJson"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                placeholder='[{"key":"token","value":"{{loginToken}}"}]'
              />
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- ============ 执行机 ============ -->
        <a-tab-pane key="executor" tab="执行机">
          <a-form layout="vertical">
            <a-form-item>
              <a-alert
                type="info"
                message="配置执行机后，任务将投递到指定执行机执行；留空则使用默认执行机/本地执行。"
                show-icon
              />
            </a-form-item>
            <a-form-item label="执行机地址">
              <a-input
                v-model:value="form.executorUrl"
                placeholder="http://192.168.1.10:9000"
              />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :span="16">
                <a-form-item label="执行机域名 / IP">
                  <a-input v-model:value="form.executorHost" placeholder="192.168.1.10 或 executor.example.com" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="执行机端口">
                  <a-input-number v-model:value="form.executorPort" :min="1" :max="65535" style="width: 100%" placeholder="9000" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="baseUrl（覆盖环境）">
              <a-input v-model:value="form.baseUrl" placeholder="https://api.example.com" />
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <!-- ============ 通知 ============ -->
        <a-tab-pane key="notify" tab="通知渠道">
          <a-form layout="vertical">
            <a-form-item>
              <a-alert
                type="info"
                message="任务失败时通过配置的渠道通知。可添加多个,失败时按顺序发送。"
                show-icon
              />
            </a-form-item>
            <a-form-item label="通知渠道">
              <div v-for="(ch, idx) in form.notifyChannels" :key="idx" class="notify-row">
                <a-space>
                  <a-select v-model:value="ch.type" style="width: 140px">
                    <a-select-option value="email">邮箱</a-select-option>
                    <a-select-option value="dingtalk">钉钉</a-select-option>
                    <a-select-option value="feishu">飞书</a-select-option>
                    <a-select-option value="webhook">Webhook</a-select-option>
                  </a-select>
                  <a-input v-model:value="ch.target" :placeholder="targetPlaceholder(ch.type)" style="width: 320px" />
                  <a-input v-model:value="ch.secret" placeholder="密钥(可选)" style="width: 200px" />
                  <a-button type="link" danger @click="form.notifyChannels.splice(idx, 1)">删除</a-button>
                </a-space>
              </div>
              <a-button type="dashed" @click="addNotifyChannel">
                <plus-outlined />添加通知渠道
              </a-button>
            </a-form-item>
            <a-form-item label="回调通知 URL（兼容旧版）">
              <a-input v-model:value="form.notifyUrl" placeholder="https://ci.example.com/webhook" />
            </a-form-item>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  LineChartOutlined,
  PlayCircleOutlined,
  DragOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  VerticalAlignTopOutlined,
  VerticalAlignBottomOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import draggable from 'vuedraggable'
import { TestTaskApi, CaseApi, EnvironmentApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { TestTask, TestTaskRun, NotifyChannel, CaseInfo, Environment } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const tasks = ref<TestTask[]>([])
const runsByTask = ref<Record<string, TestTaskRun[]>>({})
const expandedKeys = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const runningId = ref<string>('')
const activeTab = ref<string>('basic')

const modal = ref(false)
const form = reactive<Partial<TestTask>>({
  name: '',
  description: '',
  timeoutMs: 300000,
  retryCount: 0,
  cronExpr: '',
  enabled: true,
  notifyUrl: '',
  baseUrl: '',
  environmentId: undefined,
  executorUrl: '',
  executorHost: '',
  executorPort: undefined,
  executeMode: 'sequential',
  failStrategy: 'stop_on_fail',
  parallelPoolSize: 5,
  notifyChannels: []
})
const variablesJson = ref('[]')

// ---- 用例编排状态 ----
const allCases = ref<CaseInfo[]>([])
const caseKeyword = ref('')
/** 已选用例（有序） */
const selectedCases = ref<CaseInfo[]>([])

// ---- 环境 ----
const environments = ref<Environment[]>([])

const environmentOptions = computed(() =>
  environments.value.map((e) => ({ value: e.id, label: e.name }))
)

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '用例数', key: 'caseCount', width: 100 },
  { title: '环境', key: 'environment', width: 120 },
  { title: '调度', key: 'cronExpr', width: 140 },
  { title: '模式', key: 'executeMode', width: 90 },
  { title: '失败策略', key: 'failStrategy', width: 120 },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '操作', key: 'action', width: 280 }
]

const runColumns = [
  { title: '状态', key: 'result', width: 100 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '开始时间', key: 'startedAt' }
]

/** 左侧用例库：排除已选 */
const filteredAvailableCases = computed(() => {
  const selectedIds = new Set(selectedCases.value.map((c) => c.id))
  const kw = caseKeyword.value.trim().toLowerCase()
  return allCases.value.filter((c) => {
    if (selectedIds.has(c.id)) return false
    if (kw && !(c.name || '').toLowerCase().includes(kw)) return false
    return true
  })
})

function resultColor(s: string) {
  return s === 'success' ? 'green'
    : s === 'failed' ? 'red'
    : s === 'error' ? 'volcano'
    : s === 'skipped' ? 'default'
    : 'blue'
}

function strategyLabel(s?: string) {
  switch (s) {
    case 'stop_on_fail': return '失败即停'
    case 'continue_all': return '失败继续'
    case 'retry_then_stop': return '重试后停'
    default: return '失败即停'
  }
}

function priorityColor(p: string) {
  return p === 'P0' ? 'red' : p === 'P1' ? 'orange' : p === 'P2' ? 'blue' : 'default'
}

function envName(id?: string) {
  return environments.value.find((e) => e.id === id)?.name || ''
}

function targetPlaceholder(t: string) {
  switch (t) {
    case 'email': return 'user@example.com'
    case 'dingtalk': return '钉钉群机器人 Webhook URL'
    case 'feishu': return '飞书机器人 Webhook URL'
    case 'webhook': return 'HTTPS 回调 URL'
    default: return ''
  }
}

function formatDate(iso?: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}

/* ---------------- 用例编排操作 ---------------- */

function addCase(c: CaseInfo) {
  selectedCases.value.push(c)
}

function removeCase(index: number) {
  selectedCases.value.splice(index, 1)
}

/** 手动排序：上移/下移/置顶/置尾 */
function moveCase(index: number, dir: 'up' | 'down' | 'top' | 'bottom') {
  const list = selectedCases.value
  const item = list[index]
  if (dir === 'top') {
    list.splice(index, 1)
    list.unshift(item)
  } else if (dir === 'bottom') {
    list.splice(index, 1)
    list.push(item)
  } else if (dir === 'up') {
    if (index === 0) return
    list.splice(index, 1)
    list.splice(index - 1, 0, item)
  } else if (dir === 'down') {
    if (index === list.length - 1) return
    list.splice(index, 1)
    list.splice(index + 1, 0, item)
  }
}

/* ---------------- 数据加载 ---------------- */

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    tasks.value = await TestTaskApi.list(projectId.value)
  } finally {
    loading.value = false
  }
}

async function loadCases() {
  if (!projectId.value) return
  try {
    allCases.value = await CaseApi.list({ projectId: projectId.value, size: 500 })
  } catch {
    allCases.value = []
  }
}

async function loadEnvironments() {
  if (!projectId.value) return
  try {
    environments.value = await EnvironmentApi.list(projectId.value)
  } catch {
    environments.value = []
  }
}

async function onExpand(expanded: boolean, record: TestTask) {
  if (expanded) {
    runsByTask.value[record.id] = await TestTaskApi.runs(record.id)
  }
}

/* ---------------- 弹窗打开/关闭 ---------------- */

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    timeoutMs: 300000,
    retryCount: 0,
    cronExpr: '',
    enabled: true,
    notifyUrl: '',
    baseUrl: '',
    environmentId: undefined,
    executorUrl: '',
    executorHost: '',
    executorPort: undefined,
    executeMode: 'sequential',
    failStrategy: 'stop_on_fail',
    parallelPoolSize: 5,
    notifyChannels: []
  })
  selectedCases.value = []
  caseKeyword.value = ''
  variablesJson.value = '[]'
  activeTab.value = 'basic'
  modal.value = true
}

function openEdit(record: TestTask) {
  Object.assign(form, {
    id: record.id,
    name: record.name,
    description: record.description,
    timeoutMs: record.timeoutMs,
    retryCount: record.retryCount,
    cronExpr: record.cronExpr,
    enabled: record.enabled,
    notifyUrl: record.notifyUrl,
    baseUrl: record.baseUrl,
    environmentId: record.environmentId,
    executorUrl: record.executorUrl,
    executorHost: record.executorHost,
    executorPort: record.executorPort,
    executeMode: record.executeMode || 'sequential',
    failStrategy: record.failStrategy || 'stop_on_fail',
    parallelPoolSize: record.parallelPoolSize || 5,
    notifyChannels: record.notifyChannels || []
  })
  variablesJson.value = JSON.stringify(record.variables || [], null, 2)
  // 回填已选用例（按 caseIds 顺序）
  const caseIds = record.caseIds || []
  const map = new Map(allCases.value.map((c) => [c.id, c]))
  selectedCases.value = caseIds
    .map((id) => map.get(id))
    .filter(Boolean) as CaseInfo[]
  caseKeyword.value = ''
  activeTab.value = 'basic'
  modal.value = true
}

function onCancel() {
  modal.value = false
}

/* ---------------- 保存/运行 ---------------- */

async function save(runAfter: boolean) {
  if (!form.name) {
    message.warning('请填写任务名称')
    activeTab.value = 'basic'
    return
  }
  if (!selectedCases.value.length) {
    message.warning('请至少添加一个用例')
    activeTab.value = 'cases'
    return
  }
  let variables: any[] = []
  try {
    variables = JSON.parse(variablesJson.value || '[]')
  } catch {
    message.warning('用例级变量 JSON 格式错误')
    activeTab.value = 'execution'
    return
  }
  saving.value = true
  try {
    const payload: any = {
      ...form,
      caseIds: selectedCases.value.map((c) => c.id),
      variables,
      notifyChannels: form.notifyChannels || [],
      projectId: projectId.value
    }
    // 删除多余字段
    delete payload.deletedAt
    delete payload.createdAt
    delete payload.updatedAt
    let taskId = form.id
    if (form.id) {
      await TestTaskApi.update(form.id, payload)
    } else {
      const created = await TestTaskApi.create(payload)
      taskId = created.id
      form.id = created.id
    }
    message.success('已保存')
    await reload()

    if (runAfter && taskId) {
      await runAsync({ id: taskId } as TestTask)
    }
    modal.value = false
  } finally {
    saving.value = false
  }
}

async function runAsync(record: TestTask) {
  runningId.value = record.id + 'a'
  try {
    const runs = await TestTaskApi.run(record.id)
    message.success(`已异步投递 ${runs.length} 个用例`)
    runsByTask.value[record.id] = runs
    expandedKeys.value = [record.id]
  } finally {
    runningId.value = ''
  }
}

async function runSync(record: TestTask) {
  runningId.value = record.id + 's'
  const hide = message.loading('同步执行中...', 0)
  try {
    const runs = await TestTaskApi.runSync(record.id)
    const passed = runs.filter((r) => r.result === 'success').length
    message.success(`同步执行完成：${passed}/${runs.length} 通过`)
    runsByTask.value[record.id] = runs
    expandedKeys.value = [record.id]
  } finally {
    runningId.value = ''
    hide()
  }
}

async function toggle(record: TestTask) {
  await TestTaskApi.toggle(record.id)
  await reload()
}

async function remove(record: TestTask) {
  await TestTaskApi.remove(record.id)
  await reload()
}

function addNotifyChannel() {
  if (!form.notifyChannels) form.notifyChannels = []
  form.notifyChannels.push({ type: 'email', target: '', secret: '' } as NotifyChannel)
}

watch(projectId, () => { reload(); loadCases(); loadEnvironments() }, { immediate: false })
onMounted(() => { reload(); loadCases(); loadEnvironments() })
</script>

<style scoped>
/* 表单内辅助提示文案 */
.hint {
  color: var(--tx-3);
  font-size: var(--fs-xs);
  margin-top: var(--sp-1);
  display: block;
}
/* 通知渠道行间距 */
.notify-row {
  margin-bottom: var(--sp-2);
}
/* Cron 表达式标签用等宽字体，便于阅读 */
:deep(.ant-table-tbody > tr > td .ant-tag) {
  font-family: var(--font-mono);
}

/* ============ 用例编排两栏布局 ============ */
.case-orchestration {
  display: flex;
  gap: var(--sp-4);
  min-height: 360px;
}

.case-lib {
  flex: 1;
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.case-lib__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--bd-subtle);
  background: var(--bg-subtle);
  font-weight: 600;
  color: var(--tx-1);
}

.case-lib__list {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-2);
  max-height: 360px;
}

.case-lib__item {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border-radius: var(--rd-sm);
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease);
}

.case-lib__item:hover {
  background: var(--bg-active);
}

.case-lib__name {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--tx-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-selected {
  flex: 1;
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.case-selected__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--bd-subtle);
  background: var(--bg-subtle);
  font-weight: 600;
  color: var(--tx-1);
}

.case-selected__hint {
  font-size: var(--fs-xs);
  font-weight: 400;
  color: var(--tx-4);
}

.case-selected__list {
  flex: 1;
  overflow-y: auto;
  padding: var(--sp-2);
  max-height: 360px;
}

.case-row {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--bd-subtle);
  border-radius: var(--rd-sm);
  margin-bottom: var(--sp-2);
  background: var(--bg-card);
}

.case-row__idx {
  width: 20px;
  text-align: center;
  color: var(--tx-4);
  font-variant-numeric: tabular-nums;
  font-size: var(--fs-xs);
}

.case-row__name {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--tx-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drag-handle {
  cursor: grab;
  color: var(--tx-4);
}
</style>
