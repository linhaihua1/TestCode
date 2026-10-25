<template>
  <div class="page">
    <!-- 页头：标题 + 主操作 -->
    <div class="page-header">
      <div class="page-title">CI/CD 流水线</div>
      <a-space>
        <a-button @click="goJenkins"> <setting-outlined />Jenkins 服务器 </a-button>
        <a-button v-can-write type="primary" @click="openCreate"> <plus-outlined />新建流水线 </a-button>
      </a-space>
    </div>

    <!-- 流水线列表 -->
    <div class="panel">
      <a-table :data-source="pipelines" :columns="columns" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'testType'">
            <a-tag color="blue">{{ pipelineTestTypeLabel(record.testType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'execMode'">
            {{ pipelineExecModeLabel(record.execMode) }}
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">
              {{ record.enabled ? '已启用' : '已停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-can-write size="small" type="link" @click="trigger(record)">触发</a-button>
              <a-button size="small" type="link" @click="openRuns(record)">执行记录</a-button>
              <a-button size="small" type="link" @click="openJenkinsfile(record)"
                >Jenkinsfile</a-button
              >
              <a-button v-can-write size="small" type="link" @click="rotateWebhook(record)">CI Token</a-button>
              <a-button v-can-write size="small" type="link" @click="openEdit(record)">编辑</a-button>
              <a-button v-can-write size="small" type="link" @click="toggle(record)">
                {{ record.enabled ? '停用' : '启用' }}
              </a-button>
              <a-popconfirm title="确认删除？执行记录会保留" @confirm="remove(record)">
                <a-button v-can-write size="small" type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 新建 / 编辑流水线 -->
    <a-drawer
      v-model:open="editorOpen"
      :title="form.id ? '编辑流水线' : '新建流水线'"
      width="720"
      @close="editorOpen = false"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" placeholder="如：核心接口回归（Jenkins 每日构建）" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="2" />
        </a-form-item>

        <a-row :gutter="12">
          <a-col :span="12">
            <a-form-item label="测试类型" required>
              <a-select
                v-model:value="form.testType"
                :options="PIPELINE_TEST_TYPE_OPTIONS"
                @change="onTestTypeChange"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="执行目标" required>
              <a-select
                v-model:value="targetValue"
                :options="targetOptions"
                placeholder="请先选择测试类型"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="执行方式" required>
          <a-radio-group v-model:value="form.execMode">
            <a-radio :value="PIPELINE_EXEC_MODES.LOCAL"
              >平台执行（由本平台跑用例并判定门禁）</a-radio
            >
            <a-radio :value="PIPELINE_EXEC_MODES.JENKINS"
              >Jenkins 执行（触发远端 Job 并跟踪构建）</a-radio
            >
          </a-radio-group>
        </a-form-item>

        <!-- Jenkins 执行方式的配置 -->
        <template v-if="form.execMode === PIPELINE_EXEC_MODES.JENKINS">
          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="Jenkins 服务器" required>
                <a-select
                  v-model:value="form.jenkinsServerId"
                  :options="jenkinsOptions"
                  placeholder="请选择"
                  @change="loadJobs"
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="Jenkins Job" required>
                <a-select
                  v-model:value="form.jenkinsJobName"
                  :options="jobOptions"
                  show-search
                  placeholder="可从服务器拉取，也可手动输入"
                />
              </a-form-item>
            </a-col>
          </a-row>
        </template>

        <!-- 质量门禁规则 -->
        <a-form-item label="质量门禁（命中任意规则即判定流水线未通过）">
          <div v-for="(rule, idx) in gateRules" :key="idx" class="gate-row">
            <a-select
              v-model:value="rule.metric"
              :options="QUALITY_GATE_METRIC_OPTIONS"
              style="width: 180px"
            />
            <a-select
              v-model:value="rule.operator"
              :options="QUALITY_GATE_OPERATOR_OPTIONS"
              style="width: 130px"
            />
            <a-input-number v-model:value="rule.threshold" style="width: 130px" />
            <a-button danger type="text" @click="removeRule(idx)">删除</a-button>
          </div>
          <a-button type="dashed" block @click="addRule"> <plus-outlined />添加规则 </a-button>
        </a-form-item>
      </a-form>

      <template #footer>
        <a-space>
          <a-button @click="editorOpen = false">取消</a-button>
          <a-button type="primary" :loading="saving" @click="save">保存</a-button>
        </a-space>
      </template>
    </a-drawer>

    <!-- 执行记录 -->
    <a-drawer v-model:open="runsOpen" title="执行记录" width="900">
      <a-table
        :data-source="runs"
        :columns="runColumns"
        row-key="id"
        :loading="runsLoading"
        :pagination="false"
      >
        <template #expandedRowRender="{ record }">
          <div class="run-detail">
            <!-- 执行统计 -->
            <div class="run-section">
              <div class="run-section-title">执行统计</div>
              <pre>{{ formatJson(record.summary) }}</pre>
            </div>
            <!-- 门禁判定明细 -->
            <div v-if="parseGate(record).length" class="run-section">
              <div class="run-section-title">质量门禁</div>
              <a-table
                :data-source="parseGate(record)"
                :pagination="false"
                size="small"
                row-key="metric"
              >
                <template #bodyCell="{ column, record: g }">
                  <template v-if="column.key === 'passed'">
                    <a-tag :color="g.passed ? 'green' : 'red'">{{
                      g.passed ? '通过' : '违规'
                    }}</a-tag>
                  </template>
                </template>
              </a-table>
            </div>
            <!-- Jenkins 构建信息 -->
            <div v-if="record.jenkinsBuildUrl" class="run-section">
              <div class="run-section-title">Jenkins 构建</div>
              <div>
                #{{ record.jenkinsBuildNo }} · {{ record.jenkinsResult || '进行中' }}
                <a :href="record.jenkinsBuildUrl" target="_blank">打开构建详情</a>
              </div>
            </div>
            <!-- 日志 -->
            <div v-if="record.log" class="run-section">
              <div class="run-section-title">日志</div>
              <pre class="run-log">{{ record.log }}</pre>
            </div>
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="pipelineStatusColor(record.status)">
              {{ pipelineStatusLabel(record.status) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'gatePassed'">
            <a-tag v-if="record.gatePassed === 1" color="green">通过</a-tag>
            <a-tag v-else-if="record.gatePassed === 0" color="red">未通过</a-tag>
            <span v-else>-</span>
          </template>
        </template>
      </a-table>
    </a-drawer>

    <!-- CI Token -->
    <a-modal v-model:open="tokenOpen" title="CI 触发 Token（仅显示一次，请妥善保管）" width="720">
      <a-alert
        type="info"
        show-icon
        message="在 Jenkins 的 Shell / Pipeline 步骤中调用下面的地址即可触发本流水线；轮询返回的执行记录可判断门禁是否通过。"
        style="margin-bottom: 12px"
      />
      <a-form layout="vertical">
        <a-form-item label="触发地址">
          <a-input :value="tokenInfo.url" readonly />
        </a-form-item>
        <a-form-item label="curl 示例">
          <a-textarea :value="tokenInfo.curl" :rows="3" readonly />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-button type="primary" @click="tokenOpen = false">我已保存</a-button>
      </template>
    </a-modal>

    <!-- Jenkinsfile -->
    <a-modal v-model:open="jfOpen" title="Jenkins Pipeline 脚本" width="900">
      <a-alert
        type="info"
        show-icon
        message="复制以下内容到 Jenkins 任务的 Pipeline script 即可：脚本会触发本流水线、轮询执行结果，并在质量门禁未通过时让构建失败。"
        style="margin-bottom: 12px"
      />
      <pre class="jf-content">{{ jfContent }}</pre>
      <template #footer>
        <a-space>
          <a-button @click="copyJf">复制</a-button>
          <a-button type="primary" @click="jfOpen = false">关闭</a-button>
        </a-space>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * CI/CD 流水线管理页。
 *
 * <h3>能力</h3>
 * <ul>
 *   <li>流水线 CRUD：测试类型（接口/UI/性能）+ 执行目标 + 执行方式（平台执行 / Jenkins 执行）</li>
 *   <li>质量门禁：按指标（通过率、失败率、响应时间、TPS…）配置阈值，判定流水线是否通过</li>
 *   <li>手动触发与执行记录查看；执行中的记录会自动刷新状态</li>
 *   <li>生成 CI 触发 Token 与可直接粘贴到 Jenkins 的 Pipeline 脚本</li>
 * </ul>
 *
 * <p>门禁规则在表单中是对象数组，保存前序列化为 JSON 字符串（后端以 JSON 列存储）。</p>
 */
import { onMounted, reactive, ref, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { PlusOutlined, SettingOutlined } from '@ant-design/icons-vue';
import { CiApi, JenkinsApi } from '@/api';
import { useProjectStore } from '@/stores/project';
import {
  PIPELINE_EXEC_MODES,
  PIPELINE_EXEC_MODE_LABELS,
  PIPELINE_TEST_TYPE_LABELS,
  PIPELINE_TEST_TYPE_OPTIONS,
  PIPELINE_TEST_TYPES,
  PIPELINE_TARGET_TYPE_LABELS,
  QUALITY_GATE_METRIC_OPTIONS,
  QUALITY_GATE_OPERATOR_OPTIONS,
  QUALITY_GATE_OPERATORS,
  QUALITY_GATE_METRICS,
  isPipelineTerminal,
  pipelineStatusColor,
  pipelineStatusLabel,
} from '@/constants';
import type {
  CiPipeline,
  CiPipelineRun,
  CiTarget,
  QualityGateResult,
  QualityGateRule,
} from '@/types';
import { formatDateTime, formatDuration } from '@/utils/format';

const router = useRouter();
const projectStore = useProjectStore();
const projectId = ref(projectStore.currentProjectId);

// ---------------- 列表 ----------------
const pipelines = ref<CiPipeline[]>([]);
const loading = ref(false);

const columns = [
  { title: '名称', dataIndex: 'name' },
  { title: '测试类型', key: 'testType', width: 120 },
  { title: '执行方式', key: 'execMode', width: 110 },
  { title: '状态', key: 'enabled', width: 90 },
  { title: '创建人', dataIndex: 'createdBy', width: 120 },
  {
    title: '更新时间',
    key: 'updatedAt',
    width: 180,
    customRender: ({ record }: any) => formatDateTime(record.updatedAt),
  },
  { title: '操作', key: 'action', width: 420 },
];

// ---------------- 编辑器 ----------------
const editorOpen = ref(false);
const saving = ref(false);
const form = reactive<CiPipeline>({
  projectId: projectId.value,
  name: '',
  description: '',
  testType: PIPELINE_TEST_TYPES.API,
  targetType: 'test_task',
  targetId: '',
  execMode: PIPELINE_EXEC_MODES.LOCAL,
});

/** 门禁规则（表单态）：保存时序列化为 qualityGate */
const gateRules = ref<QualityGateRule[]>([]);
/** 执行目标下拉：value 用 `targetType:id` 组合，便于同时携带类型信息 */
const targetValue = ref<string>('');
const targets = ref<CiTarget[]>([]);
const jenkinsServers = ref<{ id: string; name: string }[]>([]);
const jobOptions = ref<{ value: string; label: string }[]>([]);

const targetOptions = ref<{ value: string; label: string }[]>([]);
const jenkinsOptions = ref<{ value: string; label: string }[]>([]);

// ---------------- 执行记录 ----------------
const runsOpen = ref(false);
const runsLoading = ref(false);
const runs = ref<CiPipelineRun[]>([]);
const currentPipelineId = ref('');
/** 自动刷新定时器句柄：存在未结束的执行记录时轮询 */
let pollTimer: number | undefined;

const runColumns = [
  { title: '状态', key: 'status', width: 100 },
  { title: '触发方式', dataIndex: 'triggerType', width: 100 },
  { title: '触发者', dataIndex: 'triggerBy', width: 160 },
  { title: '门禁', key: 'gatePassed', width: 90 },
  {
    title: '耗时',
    key: 'duration',
    width: 100,
    customRender: ({ record }: any) => formatDuration(record.duration),
  },
  {
    title: '开始时间',
    key: 'startedAt',
    width: 180,
    customRender: ({ record }: any) => formatDateTime(record.startedAt),
  },
];

// ---------------- Token / Jenkinsfile ----------------
const tokenOpen = ref(false);
const tokenInfo = reactive<{ url: string; curl: string }>({ url: '', curl: '' });
const jfOpen = ref(false);
const jfContent = ref('');

/* ---------------- 查询 ---------------- */

async function reload() {
  if (!projectId.value) return;
  loading.value = true;
  try {
    pipelines.value = await CiApi.list(projectId.value);
  } finally {
    loading.value = false;
  }
}

/** 切换测试类型后重新加载可选目标（不同类型目标是不同的表） */
async function loadTargets() {
  if (!projectId.value) return;
  try {
    targets.value = await CiApi.targets(projectId.value, form.testType);
    targetOptions.value = targets.value.map((t) => ({
      value: `${t.targetType}:${t.id}`,
      label: `${t.name}（${PIPELINE_TARGET_TYPE_LABELS[t.targetType] ?? t.targetType}）`,
    }));
  } catch {
    targets.value = [];
    targetOptions.value = [];
  }
}

/** 加载 Jenkins 服务器列表（流水线表单与执行都需要） */
async function loadJenkinsServers() {
  try {
    const list = await JenkinsApi.list();
    jenkinsServers.value = list.map((s) => ({ id: s.id as string, name: s.name }));
    jenkinsOptions.value = list.map((s) => ({ value: s.id as string, label: s.name }));
  } catch {
    jenkinsServers.value = [];
    jenkinsOptions.value = [];
  }
}

/** 拉取所选 Jenkins 上的 Job 列表，便于下拉选择（失败时仍可手动填写） */
async function loadJobs() {
  jobOptions.value = [];
  if (!form.jenkinsServerId) return;
  try {
    const jobs = await JenkinsApi.jobs(form.jenkinsServerId);
    jobOptions.value = jobs.map((j) => ({ value: j, label: j }));
  } catch {
    message.warning('拉取 Jenkins Job 列表失败，可手动填写 Job 名称');
  }
}

/* ---------------- 编辑器 ---------------- */

function openCreate() {
  Object.assign(form, {
    id: undefined,
    projectId: projectId.value,
    name: '',
    description: '',
    testType: PIPELINE_TEST_TYPES.API,
    targetType: 'test_task',
    targetId: '',
    execMode: PIPELINE_EXEC_MODES.LOCAL,
    jenkinsServerId: undefined,
    jenkinsJobName: undefined,
  });
  gateRules.value = [];
  targetValue.value = '';
  jobOptions.value = [];
  editorOpen.value = true;
  loadTargets();
  loadJenkinsServers();
}

function openEdit(record: CiPipeline) {
  Object.assign(form, {
    id: record.id,
    projectId: record.projectId,
    name: record.name,
    description: record.description,
    testType: record.testType,
    targetType: record.targetType,
    targetId: record.targetId,
    execMode: record.execMode,
    jenkinsServerId: record.jenkinsServerId,
    jenkinsJobName: record.jenkinsJobName,
  });
  gateRules.value = parseRules(record.qualityGate);
  targetValue.value = record.targetId ? `${record.targetType}:${record.targetId}` : '';
  editorOpen.value = true;
  loadTargets();
  loadJenkinsServers();
  if (record.jenkinsServerId) {
    form.jenkinsServerId = record.jenkinsServerId;
    loadJobs();
  }
}

function onTestTypeChange() {
  // 测试类型变化后原目标不再适用，清空并重新加载
  targetValue.value = '';
  form.targetId = '';
  loadTargets();
}

function addRule() {
  gateRules.value.push({
    metric: QUALITY_GATE_METRICS.FAIL_RATE,
    operator: QUALITY_GATE_OPERATORS.GT,
    threshold: 0,
  });
}

function removeRule(idx: number) {
  gateRules.value.splice(idx, 1);
}

/** 保存流水线：把门禁规则数组序列化为 JSON 字符串 */
async function save() {
  if (!form.name?.trim()) {
    message.warning('请填写流水线名称');
    return;
  }
  if (!targetValue.value) {
    message.warning('请选择执行目标');
    return;
  }
  if (
    form.execMode === PIPELINE_EXEC_MODES.JENKINS &&
    (!form.jenkinsServerId || !form.jenkinsJobName)
  ) {
    message.warning('Jenkins 执行方式需要选择服务器与 Job');
    return;
  }
  // targetValue 形如 `targetType:id`
  const [targetType, targetId] = targetValue.value.split(':');
  saving.value = true;
  try {
    const payload: CiPipeline = {
      ...form,
      projectId: projectId.value,
      targetType: targetType as CiPipeline['targetType'],
      targetId,
      qualityGate: JSON.stringify(gateRules.value),
    };
    if (form.id) {
      await CiApi.update(form.id, payload);
    } else {
      await CiApi.create(payload);
    }
    message.success('已保存');
    editorOpen.value = false;
    await reload();
  } finally {
    saving.value = false;
  }
}

async function toggle(record: CiPipeline) {
  if (!record.id) return;
  await CiApi.toggle(record.id);
  await reload();
}

async function remove(record: CiPipeline) {
  if (!record.id) return;
  await CiApi.remove(record.id);
  message.success('已删除');
  await reload();
}

/* ---------------- 触发与执行记录 ---------------- */

/** 触发执行：默认异步（本地执行可能耗时较久），执行记录抽屉会自动轮询刷新 */
async function trigger(record: CiPipeline) {
  if (!record.id) return;
  try {
    const run = await CiApi.trigger(record.id, { wait: false });
    message.success(`已触发执行（${run.id}）`);
    currentPipelineId.value = record.id;
    await openRuns(record);
  } catch (e: any) {
    message.error(e?.message || '触发失败');
  }
}

async function openRuns(record: CiPipeline) {
  if (!record.id) return;
  currentPipelineId.value = record.id;
  runsOpen.value = true;
  await reloadRuns();
}

async function reloadRuns() {
  if (!currentPipelineId.value) return;
  runsLoading.value = true;
  try {
    runs.value = await CiApi.runs(currentPipelineId.value, 50);
  } finally {
    runsLoading.value = false;
  }
  // 存在未结束的记录时启动轮询，全部结束后停止
  const running = runs.value.some((r) => !isPipelineTerminal(r.status));
  if (running && pollTimer === undefined) {
    pollTimer = window.setInterval(reloadRuns, 5000);
  } else if (!running && pollTimer !== undefined) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

/* ---------------- Token / Jenkinsfile ---------------- */

async function rotateWebhook(record: CiPipeline) {
  if (!record.id) return;
  const resp = await CiApi.rotateWebhook(record.id);
  tokenInfo.url = resp.url;
  tokenInfo.curl = resp.curl;
  tokenOpen.value = true;
}

async function openJenkinsfile(record: CiPipeline) {
  if (!record.id) return;
  const resp = await CiApi.jenkinsfile(record.id);
  jfContent.value = resp.content;
  jfOpen.value = true;
}

async function copyJf() {
  try {
    await navigator.clipboard.writeText(jfContent.value);
    message.success('已复制到剪贴板');
  } catch {
    message.warning('复制失败，请手动选择文本复制');
  }
}

function goJenkins() {
  router.push({ name: 'ci-jenkins' });
}

/* ---------------- 工具 ---------------- */

function parseRules(json?: string): QualityGateRule[] {
  if (!json) return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function parseGate(run: CiPipelineRun): QualityGateResult[] {
  if (!run.gateResult) return [];
  try {
    const parsed = JSON.parse(run.gateResult);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function formatJson(json?: string): string {
  if (!json) return '-';
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

function pipelineTestTypeLabel(type?: string): string {
  return PIPELINE_TEST_TYPE_LABELS[type ?? ''] ?? type ?? '-';
}

function pipelineExecModeLabel(mode?: string): string {
  return PIPELINE_EXEC_MODE_LABELS[mode ?? ''] ?? '-';
}

/* ---------------- 生命周期 ---------------- */

watch(
  () => projectStore.currentProjectId,
  (id) => {
    projectId.value = id;
    reload();
  },
);

onMounted(() => {
  reload();
  loadJenkinsServers();
});

onUnmounted(() => {
  if (pollTimer !== undefined) window.clearInterval(pollTimer);
});
</script>

<style scoped>
.gate-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}

.run-detail {
  background: #fafafa;
  padding: 12px;
}

.run-section {
  margin-bottom: 12px;
}

.run-section-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.run-log,
.jf-content {
  max-height: 320px;
  overflow: auto;
  background: #1f2937;
  color: #e5e7eb;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
