<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">性能测试用例</div>
      <a-space>
        <a-button v-can-write type="primary" @click="openCreate">
          <plus-outlined />新建用例
        </a-button>
        <a-button v-can-write @click="exportBatch" :disabled="!selectedRowKeys.length">
          <download-outlined />导出 JMX
          <template v-if="selectedRowKeys.length">({{ selectedRowKeys.length }})</template>
        </a-button>
        <a-upload
          :show-upload-list="false"
          accept=".jmx"
          :before-upload="handleImportJmx"
        >
          <a-button v-can-write>
            <import-outlined />导入 JMX
          </a-button>
        </a-upload>
      </a-space>
    </div>

    <!-- 用例列表 -->
    <div class="panel">
      <a-table
        :data-source="cases"
        :columns="caseColumns"
        row-key="id"
        :loading="loading"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'threads'">
            {{ record.threads }} 线程 / {{ record.rampUp }}s ramp-up
            <a-tag v-if="durationFor(record)" color="purple">{{ record.duration }}s</a-tag>
            <a-tag v-if="(record.steps || []).length" color="blue">{{ (record.steps || []).length }} 个请求</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" type="link" @click="run(record)" :loading="runningId === record.id">
                <play-circle-outlined />运行
              </a-button>
              <a-button size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" type="link" @click="exportJmx(record)">
                <download-outlined />导出 JMX
              </a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 新建/编辑用例弹窗（JMeter 风格） -->
    <a-modal
      v-model:open="modal"
      :title="form.id ? '编辑用例' : '新建用例'"
      :width="980"
      :confirm-loading="saving"
      :mask-closable="false"
      @cancel="onCancel"
    >
      <template #footer>
        <a-space>
          <a-button @click="onCancel">取消</a-button>
          <a-button @click="save" :loading="saving">保存</a-button>
        </a-space>
      </template>

      <a-tabs v-model:active-key="editTab">
        <!-- ============ 线程组 ============ -->
        <a-tab-pane key="thread" tab="线程组">
          <a-form layout="vertical">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="用例名称" required>
                  <a-input v-model:value="form.name" placeholder="请输入用例名称" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="描述">
                  <a-input v-model:value="form.description" placeholder="用例描述（可选）" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-divider style="margin: 8px 0 16px">线程属性</a-divider>
            <a-row :gutter="16">
              <a-col :span="6">
                <a-form-item label="线程数" tooltip="并发用户数">
                  <a-input-number v-model:value="form.threads" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="Ramp-Up (秒)" tooltip="启动所有线程所需时间">
                  <a-input-number v-model:value="form.rampUp" :min="1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="循环次数" tooltip="-1 表示永久循环">
                  <a-input-number v-model:value="form.loops" :min="-1" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="6">
                <a-form-item label="持续时间 (秒)" tooltip="0 表示不限制">
                  <a-input-number v-model:value="form.duration" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="思考时间 (毫秒)">
                  <a-input-number v-model:value="form.thinkTime" :min="0" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="采样错误后动作">
                  <a-select v-model:value="form.onSampleError" style="width: 100%">
                    <a-select-option value="continue">继续</a-select-option>
                    <a-select-option value="startnextloop">开始下一循环</a-select-option>
                    <a-select-option value="stopthread">停止线程</a-select-option>
                    <a-select-option value="stoptest">停止测试</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="加压方式">
                  <a-select v-model:value="profileForm.loadProfile" style="width: 100%">
                    <a-select-option value="fixed">固定并发</a-select-option>
                    <a-select-option value="stepping">阶梯加压</a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-tab-pane>

        <!-- ============ HTTP 请求步骤 ============ -->
        <a-tab-pane key="requests" tab="HTTP 请求">
          <div class="request-list">
            <div class="request-list__head">
              <span>HTTP 请求采样器（共 {{ form.steps?.length || 0 }} 个）</span>
              <a-button size="small" type="dashed" @click="addStep">
                <plus-outlined />添加 HTTP 请求
              </a-button>
            </div>
            <a-empty v-if="!form.steps?.length" description="暂无 HTTP 请求，点击右上角添加" />
            <a-collapse v-else v-model:active-key="activeStepKeys">
              <a-collapse-panel
                v-for="(step, idx) in form.steps"
                :key="idx"
                :header="stepHeader(step, idx)"
              >
                <a-form layout="vertical">
                  <a-row :gutter="12">
                    <a-col :span="8">
                      <a-form-item label="请求名称">
                        <a-input v-model:value="step.name" placeholder="请求名称" />
                      </a-form-item>
                    </a-col>
                    <a-col :span="4">
                      <a-form-item label="协议">
                        <a-select v-model:value="step.protocol">
                          <a-select-option value="https">https</a-select-option>
                          <a-select-option value="http">http</a-select-option>
                        </a-select>
                      </a-form-item>
                    </a-col>
                    <a-col :span="6">
                      <a-form-item label="方法">
                        <a-select v-model:value="step.method">
                          <a-select-option value="GET">GET</a-select-option>
                          <a-select-option value="POST">POST</a-select-option>
                          <a-select-option value="PUT">PUT</a-select-option>
                          <a-select-option value="DELETE">DELETE</a-select-option>
                          <a-select-option value="PATCH">PATCH</a-select-option>
                          <a-select-option value="HEAD">HEAD</a-select-option>
                          <a-select-option value="OPTIONS">OPTIONS</a-select-option>
                        </a-select>
                      </a-form-item>
                    </a-col>
                    <a-col :span="6">
                      <a-form-item label="编码">
                        <a-input v-model:value="step.encoding" placeholder="UTF-8" />
                      </a-form-item>
                    </a-col>
                  </a-row>
                  <a-row :gutter="12">
                    <a-col :span="10">
                      <a-form-item label="服务器名称或 IP" required>
                        <a-input v-model:value="step.host" placeholder="example.com" />
                      </a-form-item>
                    </a-col>
                    <a-col :span="6">
                      <a-form-item label="端口">
                        <a-input v-model:value="step.port" placeholder="443" />
                      </a-form-item>
                    </a-col>
                    <a-col :span="8">
                      <a-form-item label="路径">
                        <a-input v-model:value="step.path" placeholder="/api/v1/users" />
                      </a-form-item>
                    </a-col>
                  </a-row>

                  <a-divider style="margin: 4px 0 12px">URL 参数</a-divider>
                  <div v-for="(q, qi) in step.queryParams" :key="qi" class="kv-row">
                    <a-input v-model:value="q.key" placeholder="参数名" style="width: 40%" />
                    <a-input v-model:value="q.value" placeholder="参数值" style="width: 40%" />
                    <a-button type="text" danger @click="step.queryParams!.splice(qi, 1)">
                      <delete-outlined />
                    </a-button>
                  </div>
                  <a-button size="small" type="dashed" block @click="addQueryParam(step)">
                    <plus-outlined />添加参数
                  </a-button>

                  <a-divider style="margin: 12px 0">请求头</a-divider>
                  <div v-for="(h, hi) in step.headers" :key="hi" class="kv-row">
                    <a-input v-model:value="h.key" placeholder="Header 名（如 Content-Type）" style="width: 40%" />
                    <a-input v-model:value="h.value" placeholder="Header 值" style="width: 40%" />
                    <a-button type="text" danger @click="step.headers!.splice(hi, 1)">
                      <delete-outlined />
                    </a-button>
                  </div>
                  <a-button size="small" type="dashed" block @click="addHeader(step)">
                    <plus-outlined />添加请求头
                  </a-button>

                  <a-divider style="margin: 12px 0">请求体（Body）</a-divider>
                  <a-form-item>
                    <a-textarea
                      v-model:value="step.body"
                      :auto-size="{ minRows: 3, maxRows: 8 }"
                      placeholder='{"key":"value"} 或 form 数据，GET 请求留空'
                    />
                  </a-form-item>

                  <div class="request-actions">
                    <a-button size="small" danger @click="removeStep(idx)">删除此请求</a-button>
                  </div>
                </a-form>
              </a-collapse-panel>
            </a-collapse>
          </div>
        </a-tab-pane>

        <!-- ============ 用户变量 ============ -->
        <a-tab-pane key="variables" tab="用户变量">
          <a-form layout="vertical">
            <a-form-item>
              <a-alert
                type="info"
                message="用户定义的变量，可在 HTTP 请求的 URL / 参数 / 请求体中以 ${变量名} 引用。"
                show-icon
              />
            </a-form-item>
            <div v-for="(v, vi) in form.variables" :key="vi" class="kv-row">
              <a-input v-model:value="v.key" placeholder="变量名" style="width: 40%" />
              <a-input v-model:value="v.value" placeholder="变量值" style="width: 40%" />
              <a-button type="text" danger @click="form.variables!.splice(vi, 1)">
                <delete-outlined />
              </a-button>
            </div>
            <a-button size="small" type="dashed" block @click="addVariable">
              <plus-outlined />添加变量
            </a-button>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  PlusOutlined,
  PlayCircleOutlined,
  ImportOutlined,
  DownloadOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import { PerfApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { PerfCase, PerfStep } from '@/types'

const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const cases = ref<PerfCase[]>([])
const loading = ref(false)
const runningId = ref('')
const saving = ref(false)

const selectedRowKeys = ref<string[]>([])

const modal = ref(false)
const editTab = ref('thread')
const activeStepKeys = ref<(string | number)[]>([])

const form = reactive<Partial<PerfCase>>({
  name: '',
  description: '',
  threads: 10,
  rampUp: 5,
  loops: 1,
  duration: 0,
  thinkTime: 0,
  onSampleError: 'continue',
  variables: [],
  steps: [],
  profile: { loadProfile: 'fixed' }
})
const profileForm = reactive({ loadProfile: 'fixed' })

const caseColumns = [
  { title: '名称', dataIndex: 'name' },
  { title: '压测参数', key: 'threads' },
  { title: '操作', key: 'action', width: 300 }
]

function durationFor(c: PerfCase) {
  return c.duration > 0
}

function stepHeader(step: PerfStep, idx: number) {
  const method = step.method || 'GET'
  const url = `${step.protocol || 'https'}://${step.host || '...'}${step.path || ''}`
  return `#${idx + 1} ${method} ${url}`
}

/* ---------------- 步骤/变量编辑 ---------------- */

function addStep() {
  if (!form.steps) form.steps = []
  const step: PerfStep = {
    name: `HTTP请求 ${form.steps.length + 1}`,
    protocol: 'https',
    method: 'GET',
    host: '',
    port: '',
    path: '/',
    encoding: 'UTF-8',
    queryParams: [],
    headers: [],
    body: '',
    followRedirects: true,
    useKeepAlive: true
  }
  form.steps.push(step)
  activeStepKeys.value = [form.steps.length - 1]
}

function removeStep(idx: number) {
  form.steps!.splice(idx, 1)
  // 收起面板
  activeStepKeys.value = activeStepKeys.value.filter((k) => k !== idx)
}

function addQueryParam(step: PerfStep) {
  if (!step.queryParams) step.queryParams = []
  step.queryParams.push({ key: '', value: '' })
}

function addHeader(step: PerfStep) {
  if (!step.headers) step.headers = []
  step.headers.push({ key: '', value: '' })
}

function addVariable() {
  if (!form.variables) form.variables = []
  form.variables.push({ key: '', value: '' })
}

/* ---------------- 数据加载 ---------------- */

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    cases.value = await PerfApi.listCases(projectId.value)
  } finally {
    loading.value = false
  }
}

/* ---------------- 弹窗 ---------------- */

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    threads: 10,
    rampUp: 5,
    loops: 1,
    duration: 0,
    thinkTime: 0,
    onSampleError: 'continue',
    variables: [],
    steps: []
  })
  profileForm.loadProfile = 'fixed'
  activeStepKeys.value = []
  editTab.value = 'thread'
  modal.value = true
}

function openEdit(record: PerfCase) {
  Object.assign(form, {
    id: record.id,
    name: record.name,
    description: record.description,
    threads: record.threads,
    rampUp: record.rampUp,
    loops: record.loops,
    duration: record.duration,
    thinkTime: record.thinkTime,
    onSampleError: record.onSampleError,
    variables: record.variables ? JSON.parse(JSON.stringify(record.variables)) : [],
    steps: record.steps ? JSON.parse(JSON.stringify(record.steps)) : []
  })
  // 兼容旧数据：步骤可能缺字段
  if (form.steps) {
    form.steps = form.steps.map((s: any) => ({
      protocol: 'https',
      method: 'GET',
      port: '',
      path: '/',
      encoding: 'UTF-8',
      queryParams: [],
      headers: [],
      body: '',
      ...s
    }))
  }
  profileForm.loadProfile = record.profile?.loadProfile ?? 'fixed'
  activeStepKeys.value = (form.steps || []).map((_, i) => i)
  editTab.value = 'thread'
  modal.value = true
}

function onCancel() {
  modal.value = false
}

async function save() {
  if (!form.name) {
    message.warning('请填写用例名称')
    editTab.value = 'thread'
    return
  }
  // 校验：每个步骤需要有 host
  const steps = form.steps || []
  if (steps.some((s: any) => !s.host)) {
    message.warning('存在 HTTP 请求未填写服务器名称或 IP')
    editTab.value = 'requests'
    return
  }
  form.profile = { loadProfile: profileForm.loadProfile as any }
  saving.value = true
  try {
    if (form.id) {
      await PerfApi.updateCase(form.id, form)
    } else {
      await PerfApi.createCase({ ...form, projectId: projectId.value })
    }
    message.success('已保存')
    modal.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

/* ---------------- 运行 / 删除 ---------------- */

async function run(record: PerfCase) {
  runningId.value = record.id
  try {
    await PerfApi.run(record.id)
    message.success('已投递到 RabbitMQ，等待执行机消费')
    // 跳转到独立的性能测试报告页
    router.push({ name: 'perf-reports' })
  } finally {
    runningId.value = ''
  }
}

async function remove(record: PerfCase) {
  await PerfApi.deleteCase(record.id)
  await reload()
}

/* ---------------- 导出 / 导入 JMX ---------------- */

function onSelectChange(keys: (string | number)[]) {
  selectedRowKeys.value = keys as string[]
}

async function exportBatch() {
  if (!selectedRowKeys.value.length) {
    message.warning('请先勾选要导出的用例')
    return
  }
  try {
    const blob = await PerfApi.exportBatch(selectedRowKeys.value) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `perf-batch-${selectedRowKeys.value.length}-cases.jmx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success(`已导出 ${selectedRowKeys.value.length} 个用例为 JMX`)
  } catch {
    message.error('批量导出失败')
  }
}

async function exportJmx(record: PerfCase) {
  try {
    const blob = await PerfApi.exportJmx(record.id) as unknown as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${record.name || 'perf-case'}.jmx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    message.success('已导出 JMX 文件')
  } catch {
    message.error('导出失败')
  }
}

async function handleImportJmx(file: File) {
  try {
    const text = await file.text()
    const parsed = await PerfApi.importJmx(text)
    message.success('JMX 解析成功，请完善名称后保存')
    // 用解析结果填充表单（保留新用例状态）
    Object.assign(form, {
      id: undefined,
      name: parsed.name || file.name.replace(/\.jmx$/i, ''),
      description: '',
      threads: parsed.threads,
      rampUp: parsed.rampUp,
      loops: parsed.loops,
      duration: parsed.duration,
      thinkTime: parsed.thinkTime,
      onSampleError: parsed.onSampleError,
      variables: parsed.variables || [],
      steps: parsed.steps || []
    })
    profileForm.loadProfile = parsed.profile?.loadProfile ?? 'fixed'
    activeStepKeys.value = (form.steps || []).map((_, i) => i)
    editTab.value = 'thread'
    modal.value = true
  } catch (e: any) {
    message.error(e?.message || 'JMX 导入失败')
  }
  // 阻止默认上传行为
  return false
}

watch(projectId, () => {
  reload()
}, { immediate: false })
onMounted(() => {
  reload()
})
</script>

<style scoped>
/* HTTP 请求列表 */
.request-list__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--sp-3);
  font-weight: 600;
  color: var(--tx-1);
}

/* 键值对行（参数/请求头/变量） */
.kv-row {
  display: flex;
  gap: var(--sp-2);
  align-items: center;
  margin-bottom: var(--sp-2);
}

/* 请求操作行 */
.request-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-2);
}
</style>
