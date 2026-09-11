<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">UI 用例执行</div>
      <a-space>
        <a-button v-can-write type="primary" @click="openCreate">
          <plus-outlined />新建任务
        </a-button>
      </a-space>
    </div>

    <!-- 场景（测试执行任务）列表 -->
    <div class="panel">
      <a-table :data-source="scenarios" :columns="columns" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'caseCount'">
            <a-tag color="blue">{{ caseCount(record) }} 个用例</a-tag>
          </template>
          <template v-else-if="column.key === 'environment'">
            <a-tag v-if="envName(record.environmentId)" color="cyan">{{ envName(record.environmentId) }}</a-tag>
            <a-tag v-else color="default">默认环境</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-can-write size="small" type="link" @click="runScenario(record)" :loading="runningId === record.id">
                <play-circle-outlined />运行
              </a-button>
              <a-button v-can-write size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除？" @confirm="remove(record)">
                <a-button v-can-write size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 新建/编辑测试执行任务弹窗 -->
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
                <a-form-item label="失败重试次数">
                  <a-input-number v-model:value="form.retryCount" :min="0" :max="10" style="width: 100%" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="超时时间 (ms)">
                  <a-input-number v-model:value="form.timeoutMs" :min="1000" :step="1000" style="width: 100%" />
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
                <span>UI 用例库</span>
                <a-input-search
                  v-model:value="keyword"
                  placeholder="搜索用例"
                  size="small"
                  style="width: 200px"
                />
              </div>
              <div class="case-lib__list">
                <a-empty v-if="!filteredAvailable.length" description="暂无可选用例" />
                <div
                  v-for="c in filteredAvailable"
                  :key="c.id"
                  class="case-lib__item"
                  @click="addCase(c)"
                >
                  <div class="case-lib__name">{{ c.name }}</div>
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

        <!-- ============ 执行参数 ============ -->
        <a-tab-pane key="params" tab="执行参数">
          <a-form layout="vertical">
            <a-form-item>
              <a-alert
                type="info"
                message="执行参数会覆盖同名环境变量，用于在不同环境中执行用例。"
                show-icon
              />
            </a-form-item>
            <a-form-item label="用例级变量 (JSON)">
              <a-textarea
                v-model:value="variablesJson"
                :auto-size="{ minRows: 2, maxRows: 6 }"
                placeholder='[{"key":"host","value":"https://staging.example.com"}]'
              />
              <span class="hint">格式：[{"key":"变量名","value":"变量值"}]</span>
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
                placeholder="http://192.168.1.10:4444"
              />
            </a-form-item>
            <a-row :gutter="16">
              <a-col :span="16">
                <a-form-item label="执行机域名 / IP">
                  <a-input v-model:value="form.executorHost" placeholder="192.168.1.10 或 selenium-hub.example.com" />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item label="执行机端口">
                  <a-input-number v-model:value="form.executorPort" :min="1" :max="65535" style="width: 100%" placeholder="4444" />
                </a-form-item>
              </a-col>
            </a-row>
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
  PlayCircleOutlined,
  DragOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  VerticalAlignTopOutlined,
  VerticalAlignBottomOutlined,
  DeleteOutlined
} from '@ant-design/icons-vue'
import draggable from 'vuedraggable'
import { UiApi, EnvironmentApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { UiTestCase, UiScenario, Environment } from '@/types'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const scenarios = ref<UiScenario[]>([])
const tests = ref<UiTestCase[]>([])
const environments = ref<Environment[]>([])
const loading = ref(false)
const runningId = ref('')

const modal = ref(false)
const saving = ref(false)
const activeTab = ref<string>('basic')
const form = reactive<Partial<UiScenario>>({
  name: '',
  description: '',
  environmentId: undefined,
  retryCount: 0,
  timeoutMs: 0,
  executorUrl: '',
  executorHost: '',
  executorPort: undefined
})
const variablesJson = ref('[]')
const keyword = ref('')
/** 已选用例（有序） */
const selectedCases = ref<UiTestCase[]>([])

const environmentOptions = computed(() =>
  environments.value.map((e) => ({ value: e.id, label: e.name }))
)

/** 场景步骤数（按已加载的 steps 缓存） */
const stepCountMap = ref<Record<string, number>>({})

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '用例数', key: 'caseCount', width: 120 },
  { title: '环境', key: 'environment', width: 120 },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '操作', key: 'action', width: 200 }
]

/** 左侧用例库：排除已选 */
const filteredAvailable = computed(() => {
  const selectedIds = new Set(selectedCases.value.map((c) => c.id))
  const kw = keyword.value.trim().toLowerCase()
  return tests.value.filter((c) => {
    if (selectedIds.has(c.id)) return false
    if (kw && !(c.name || '').toLowerCase().includes(kw)) return false
    return true
  })
})

function caseCount(record: UiScenario) {
  return stepCountMap.value[record.id] || 0
}

function envName(id?: string) {
  return environments.value.find((e) => e.id === id)?.name || ''
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    scenarios.value = await UiApi.listScenarios(projectId.value)
    // 并行拉取各场景步骤数
    await Promise.all(
      scenarios.value.map(async (s) => {
        try {
          const detail = await UiApi.getScenario(s.id)
          stepCountMap.value[s.id] = ((detail.steps as any[]) || []).length
        } catch {
          stepCountMap.value[s.id] = 0
        }
      })
    )
  } finally {
    loading.value = false
  }
}

async function loadTests() {
  if (!projectId.value) return
  try {
    tests.value = await UiApi.listTests(projectId.value)
  } catch {
    tests.value = []
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

async function runScenario(record: UiScenario) {
  runningId.value = record.id
  try {
    const list = await UiApi.runScenario(record.id)
    message.success(`已投递 ${list.length} 个用例`)
  } finally {
    runningId.value = ''
  }
}

async function remove(record: UiScenario) {
  await UiApi.deleteScenario(record.id)
  await reload()
}

/* ---------------- 任务编辑 ---------------- */

function openCreate() {
  Object.assign(form, {
    id: undefined,
    name: '',
    description: '',
    environmentId: undefined,
    retryCount: 0,
    timeoutMs: 0,
    executorUrl: '',
    executorHost: '',
    executorPort: undefined
  })
  selectedCases.value = []
  keyword.value = ''
  variablesJson.value = '[]'
  activeTab.value = 'basic'
  modal.value = true
}

async function openEdit(record: UiScenario) {
  Object.assign(form, {
    id: record.id,
    name: record.name,
    description: record.description,
    environmentId: record.environmentId,
    retryCount: record.retryCount || 0,
    timeoutMs: record.timeoutMs || 0,
    executorUrl: record.executorUrl || '',
    executorHost: record.executorHost || '',
    executorPort: record.executorPort
  })
  variablesJson.value = JSON.stringify(record.variables || [], null, 2)
  selectedCases.value = []
  keyword.value = ''
  modal.value = true
  // 拉取场景已有步骤，回填已选用例（按 sortOrder 顺序）
  try {
    const detail = await UiApi.getScenario(record.id)
    const steps = (detail.steps as any[]) || []
    const ids = steps.map((s) => s.uiTestCaseId).filter(Boolean)
    const map = new Map(tests.value.map((t) => [t.id, t]))
    selectedCases.value = ids.map((id) => map.get(id)).filter(Boolean) as UiTestCase[]
  } catch {
    // 忽略详情加载失败，仍允许编辑名称/描述
  }
  activeTab.value = 'basic'
}

function onCancel() {
  modal.value = false
}

/* ---------------- 用例编排操作 ---------------- */

function addCase(c: UiTestCase) {
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
    message.warning('执行参数 JSON 格式错误')
    activeTab.value = 'params'
    return
  }
  saving.value = true
  try {
    const steps = selectedCases.value.map((c) => ({ uiTestCaseId: c.id }))
    const payload: any = {
      ...form,
      variables,
      steps,
      projectId: projectId.value
    }
    delete payload.deletedAt
    delete payload.createdAt
    delete payload.updatedAt
    let scenarioId = form.id
    if (form.id) {
      await UiApi.updateScenario(form.id, payload)
    } else {
      const created = await UiApi.createScenario(payload)
      scenarioId = created.id
      form.id = created.id
    }
    message.success('已保存')
    await reload()

    if (runAfter && scenarioId) {
      await runScenario({ id: scenarioId } as UiScenario)
    }
    modal.value = false
  } finally {
    saving.value = false
  }
}

watch(projectId, () => { reload(); loadTests(); loadEnvironments() }, { immediate: false })
onMounted(() => { reload(); loadTests(); loadEnvironments() })
</script>

<style scoped>
/* 表单内提示文案 */
.hint {
  color: var(--tx-3);
  font-size: var(--fs-xs);
  margin-top: var(--sp-1);
  display: block;
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
