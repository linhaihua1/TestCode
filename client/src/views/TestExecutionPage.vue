<template>
  <a-card title="测试任务" :bordered="false">
    <template #extra>
      <a-space>
        <a-button @click="$router.push('/trend')">
          <line-chart-outlined />趋势
        </a-button>
        <a-button type="primary" @click="openCreate">
          <plus-outlined />新建任务
        </a-button>
      </a-space>
    </template>

    <a-table
      :data-source="tasks"
      :columns="columns"
      row-key="id"
      :loading="loading"
      :expanded-row-keys="expandedKeys"
      @expand="onExpand"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-switch :checked="record.enabled" @change="toggle(record)" />
        </template>
        <template v-else-if="column.key === 'cronExpr'">
          <a-tag v-if="record.cronExpr" color="blue">{{ record.cronExpr }}</a-tag>
          <a-tag v-else color="default">手动</a-tag>
        </template>
        <template v-else-if="column.key === 'executeMode'">
          <a-tag :color="record.executeMode === 'parallel' ? 'orange' : 'default'">
            {{ record.executeMode === 'parallel' ? '并行' : '顺序' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'failStrategy'">
          <a-tag color="purple">{{ strategyLabel(record.failStrategy) }}</a-tag>
        </template>
        <template v-else-if="column.key === 'webhookEnabled'">
          <a-tag v-if="record.webhookEnabled" color="green">已启用</a-tag>
          <a-tag v-else color="default">未启用</a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button size="small" type="link" @click="runAsync(record)" :loading="runningId === record.id + 'a'">
              <api-outlined />异步
            </a-button>
            <a-button size="small" type="link" @click="runSync(record)" :loading="runningId === record.id + 's'">
              <thunderbolt-outlined />同步
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

    <!-- 新建/编辑弹窗 -->
    <a-modal
      v-model:open="modal"
      :title="form.id ? '编辑任务' : '新建任务'"
      @ok="save"
      width="780"
      :confirm-loading="saving"
    >
      <a-tabs v-model:active-key="activeTab">
        <a-tab-pane key="basic" tab="基础">
          <a-form layout="vertical">
            <a-form-item label="名称" required>
              <a-input v-model:value="form.name" />
            </a-form-item>
            <a-form-item label="描述">
              <a-textarea v-model:value="form.description" :auto-size="{ minRows: 1, maxRows: 3 }" />
            </a-form-item>
            <a-form-item label="用例 ID（每行一个或英文逗号分隔）" required>
              <a-textarea
                v-model:value="caseIdsText"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                placeholder="abc123, def456"
              />
            </a-form-item>
            <a-row :gutter="8">
              <a-col :span="8">
                <a-form-item label="超时 (ms)">
                  <a-input-number v-model:value="form.timeoutMs" :min="1000" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="失败重试次数">
                  <a-input-number v-model:value="form.retryCount" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="baseUrl（覆盖环境）">
                  <a-input v-model:value="form.baseUrl" placeholder="https://api.example.com" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-form-item label="Cron 表达式（留空为手动）">
              <a-input v-model:value="form.cronExpr" placeholder="0 0 2 * * ? (Quartz Cron)" />
            </a-form-item>
            <a-form-item label="启用">
              <a-switch v-model:checked="form.enabled" />
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="execution" tab="执行策略">
          <a-form layout="vertical">
            <a-form-item label="执行模式">
              <a-radio-group v-model:value="form.executeMode">
                <a-radio value="sequential">顺序执行（一个失败即停止）</a-radio>
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

        <a-tab-pane key="webhook" tab="CI/CD Webhook">
          <a-form layout="vertical">
            <a-form-item>
              <a-alert
                type="info"
                message="启用 Webhook 后,CI 系统可通过 HTTP POST 触发任务执行,适合 GitHub Actions / GitLab CI / Jenkins 等场景。"
                show-icon
              />
            </a-form-item>
            <a-form-item label="启用 Webhook 触发">
              <a-switch v-model:checked="form.webhookEnabled" />
            </a-form-item>
            <a-form-item label="触发后自动执行">
              <a-switch v-model:checked="form.webhookAutoExecute" />
            </a-form-item>
            <a-form-item v-if="form.id" label="Webhook Token（仅显示一次）">
              <a-input-search
                :value="webhookUrl || '保存后生成'"
                readonly
                @search="rotateWebhook()"
                search-text="重新生成"
              >
                <template #addonBefore>
                  <a-tag color="blue">POST</a-tag>
                </template>
              </a-input-search>
              <div class="hint">调用方式：<code>POST /api/v1/webhook/test-tasks/trigger?token=xxx&triggerBy=git-sha</code></div>
            </a-form-item>
            <a-form-item label="频率限制">
              <span class="hint">同一任务 5 秒内仅允许一次触发,防止 CI 误触发风暴</span>
            </a-form-item>
          </a-form>
        </a-tab-pane>

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
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  ApiOutlined,
  ThunderboltOutlined,
  LineChartOutlined
} from '@ant-design/icons-vue'
import { TestTaskApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { TestTask, TestTaskRun, NotifyChannel } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const tasks = ref<TestTask[]>([])
const runsByTask = ref<Record<string, TestTaskRun[]>>({})
const expandedKeys = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const runningId = ref<string>('')
const webhookUrl = ref<string>('')
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
  executeMode: 'sequential',
  failStrategy: 'stop_on_fail',
  parallelPoolSize: 5,
  webhookEnabled: false,
  webhookAutoExecute: true,
  notifyChannels: []
})
const caseIdsText = ref('')
const variablesJson = ref('[]')

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '调度', key: 'cronExpr', width: 140 },
  { title: '模式', key: 'executeMode', width: 90 },
  { title: '失败策略', key: 'failStrategy', width: 130 },
  { title: 'Webhook', key: 'webhookEnabled', width: 100 },
  { title: '启用', key: 'enabled', width: 80 },
  { title: '操作', key: 'action', width: 280 }
]

const runColumns = [
  { title: '状态', key: 'result', width: 100 },
  { title: '耗时', key: 'duration', width: 100 },
  { title: '开始时间', key: 'startedAt' }
]

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

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    tasks.value = await TestTaskApi.list(projectId.value)
  } finally {
    loading.value = false
  }
}

async function onExpand(expanded: boolean, record: TestTask) {
  if (expanded) {
    runsByTask.value[record.id] = await TestTaskApi.runs(record.id)
  }
}

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
    executeMode: 'sequential',
    failStrategy: 'stop_on_fail',
    parallelPoolSize: 5,
    webhookEnabled: false,
    webhookAutoExecute: true,
    notifyChannels: []
  })
  caseIdsText.value = ''
  variablesJson.value = '[]'
  webhookUrl.value = ''
  activeTab.value = 'basic'
  modal.value = true
}

function openEdit(record: TestTask) {
  Object.assign(form, record)
  caseIdsText.value = (record.caseIds || []).join(', ')
  variablesJson.value = JSON.stringify(record.variables || [], null, 2)
  form.notifyChannels = record.notifyChannels || []
  form.failStrategy = record.failStrategy || 'stop_on_fail'
  form.executeMode = record.executeMode || 'sequential'
  form.parallelPoolSize = record.parallelPoolSize || 5
  form.webhookEnabled = record.webhookEnabled || false
  form.webhookAutoExecute = record.webhookAutoExecute !== false
  activeTab.value = 'basic'
  webhookUrl.value = ''
  modal.value = true
}

async function save() {
  if (!form.name) {
    message.warning('请填写名称')
    activeTab.value = 'basic'
    return
  }
  if (!form.id && caseIdsText.value.trim() === '') {
    message.warning('请填写用例 ID')
    activeTab.value = 'basic'
    return
  }
  saving.value = true
  try {
    const ids = caseIdsText.value.split(/[\n,]+/).map((s) => s.trim()).filter(Boolean)
    let variables: any[] = []
    try {
      variables = JSON.parse(variablesJson.value || '[]')
    } catch {
      message.warning('用例级变量 JSON 格式错误')
      return
    }
    const payload: any = {
      ...form,
      caseIds: ids,
      variables,
      notifyChannels: form.notifyChannels || [],
      projectId: projectId.value
    }
    // 删除多余字段
    delete payload.deletedAt
    delete payload.createdAt
    delete payload.updatedAt
    if (form.id) {
      await TestTaskApi.update(form.id, payload)
    } else {
      const created = await TestTaskApi.create(payload)
      // 创建后展示 webhook
      if (created.webhookToken) {
        webhookUrl.value = `/api/v1/webhook/test-tasks/trigger?token=${created.webhookToken}`
      }
    }
    message.success('已保存')
    if (!form.id) {
      // 新建后保持弹窗,让用户继续配置 webhook
      form.id = (await TestTaskApi.list(projectId.value))[0]?.id
    }
    await reload()
  } finally {
    saving.value = false
  }
}

async function rotateWebhook() {
  if (!form.id) return
  const r = await TestTaskApi.rotateWebhook(form.id)
  webhookUrl.value = r.url
  await message.success(`已生成新 Token,请妥善保管`, 5)
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

watch(projectId, reload, { immediate: false })
onMounted(reload)
</script>

<style scoped>
.hint {
  color: #888;
  font-size: 12px;
  margin-top: 4px;
  display: block;
}
.notify-row {
  margin-bottom: 8px;
}
</style>