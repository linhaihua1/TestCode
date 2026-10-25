<template>
  <div v-if="report" class="page report-page">
    <!-- 页头 -->
    <header class="page-header">
      <div class="page-title">
        <file-text-outlined class="page-title__icon" />
        <span class="ellipsis">{{ report.name }}</span>
        <span class="sub">
          触发方式：{{ triggerLabel(report.triggerType) }} · 耗时 {{ report.duration || 0 }} ms
        </span>
      </div>
      <a-space>
        <a-button @click="openTrend"> <line-chart-outlined />趋势 </a-button>
        <a-button @click="exportHtml"> <download-outlined />HTML </a-button>
        <a-button @click="exportPdf"> <file-pdf-outlined />PDF </a-button>
        <a-button type="primary" @click="openShare"> <share-alt-outlined />分享报告 </a-button>
      </a-space>
    </header>

    <!-- Allure 风格总体概览 -->
    <AllureSummary v-model:active="statusFilter" :stats="statusStats" @filter="onStatusFilter" />

    <!-- 关键指标卡片 -->
    <section class="stat-grid">
      <div class="stat-card">
        <div class="label">通过率</div>
        <div
          class="value tabular"
          :class="{
            success: passRate >= 80,
            warning: passRate >= 60 && passRate < 80,
            error: passRate < 60,
          }"
        >
          {{ passRate.toFixed(2) }}<span class="unit">%</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">断言</div>
        <div class="value tabular">{{ report.totalAssertions || 0 }}</div>
        <div class="sub-stat">
          通过 {{ report.passedAssertions || 0 }} / 失败 {{ report.failedAssertions || 0 }}
        </div>
      </div>
      <div class="stat-card">
        <div class="label">平均响应时间</div>
        <div class="value tabular primary">
          {{ report.avgResponseTime || 0 }}<span class="unit"> ms</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="label">P95 响应</div>
        <div class="value tabular">
          {{ report.p95ResponseTime || 0 }}<span class="unit"> ms</span>
        </div>
      </div>
    </section>

    <!-- Tab 切换 -->
    <section class="panel report-panel">
      <a-tabs v-model:active-key="activeTab" class="report-tabs">
        <!-- 步骤明细 -->
        <a-tab-pane key="details" tab="步骤明细">
          <div v-if="statusFilter" class="filter-banner">
            <a-tag :color="allureStatusColor(statusFilter)" closable @close="clearFilter">
              筛选：{{ allureStatusLabel(statusFilter) }}（{{ filteredDetails.length }} 条）
            </a-tag>
          </div>
          <a-list class="report-list" :data-source="filteredDetails" :loading="loadingDetails">
            <template #renderItem="{ item }">
              <a-list-item class="report-list__item">
                <a-list-item-meta>
                  <template #title>
                    <div class="step-title">
                      <a-tag :color="statusColor(item.status)" class="step-tag">
                        {{ item.status }}
                      </a-tag>
                      <span class="step-name">{{ item.stepName }}</span>
                      <a-tag v-if="item.durationMs" class="step-duration">
                        {{ item.durationMs }} ms
                      </a-tag>
                    </div>
                  </template>
                  <template #description>
                    <div class="step-body">
                      <a-alert
                        v-if="item.error"
                        type="error"
                        :message="item.error"
                        show-icon
                        class="step-error"
                      />
                      <a-collapse ghost class="step-collapse">
                        <a-collapse-panel :header="`断言结果 (${item.assertions?.length || 0})`">
                          <a-list
                            :data-source="item.assertions"
                            size="small"
                            class="assertion-list"
                          >
                            <template #renderItem="{ item: a }">
                              <a-list-item class="assertion-item">
                                <a-tag :color="a.passed ? 'green' : 'red'" class="assertion-mark">
                                  {{ a.passed ? '✓' : '✗' }}
                                </a-tag>
                                <span>{{ a.message }}</span>
                              </a-list-item>
                            </template>
                          </a-list>
                        </a-collapse-panel>
                        <a-collapse-panel header="提取变量">
                          <pre class="json-pre">{{ JSON.stringify(item.extracts, null, 2) }}</pre>
                        </a-collapse-panel>
                        <a-collapse-panel v-if="item.requestSummary" header="请求摘要">
                          <pre class="json-pre">{{ item.requestSummary }}</pre>
                        </a-collapse-panel>
                        <a-collapse-panel v-if="item.responseSummary" header="响应摘要">
                          <pre class="json-pre">{{ item.responseSummary }}</pre>
                        </a-collapse-panel>
                      </a-collapse>
                    </div>
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
        </a-tab-pane>

        <!-- HTML 预览 -->
        <a-tab-pane key="html" tab="HTML 预览">
          <div class="html-preview-wrap">
            <iframe v-if="htmlPreview" :srcdoc="htmlPreview" class="html-frame" sandbox="" />
            <a-empty v-else description="暂无 HTML 报告，点击下方按钮加载" class="html-empty" />
            <a-button
              v-if="!htmlPreview"
              type="primary"
              class="html-load-btn"
              @click="loadHtmlPreview"
            >
              <download-outlined />加载 HTML 报告
            </a-button>
          </div>
        </a-tab-pane>

        <!-- 分享记录 -->
        <a-tab-pane key="shares" tab="分享记录">
          <div class="shares-wrap">
            <a-button type="primary" class="shares-create-btn" @click="openShare">
              <plus-outlined />创建分享
            </a-button>
            <a-table
              :data-source="shares"
              row-key="id"
              :loading="loadingShares"
              :pagination="false"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'token'">
                  <a-typography-paragraph :copyable="{ text: sharePublicUrl(record) }">
                    <a-tag>{{ record.token.substring(0, 8) }}...</a-tag>
                  </a-typography-paragraph>
                </template>
                <template v-else-if="column.key === 'expiresAt'">
                  <a-tag :color="isExpired(record.expiresAt) ? 'red' : 'green'">
                    {{ formatDate(record.expiresAt) }}
                  </a-tag>
                </template>
                <template v-else-if="column.key === 'accessCount'">
                  {{ record.accessCount
                  }}{{ record.maxAccessCount ? `/${record.maxAccessCount}` : '' }}
                </template>
                <template v-else-if="column.key === 'hasPassword'">
                  <a-tag v-if="record.hasPassword" color="orange">已设置</a-tag>
                  <a-tag v-else>无</a-tag>
                </template>
                <template v-else-if="column.key === 'revoked'">
                  <a-tag v-if="record.revoked" color="red">已撤销</a-tag>
                  <a-tag v-else color="green">有效</a-tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <a-button
                    size="small"
                    type="link"
                    danger
                    :disabled="record.revoked"
                    @click="revokeShare(record)"
                  >
                    撤销
                  </a-button>
                </template>
              </template>
            </a-table>
          </div>
        </a-tab-pane>
      </a-tabs>
    </section>

    <!-- 创建分享弹窗 -->
    <a-modal
      v-model:open="shareModal"
      title="分享报告"
      :confirm-loading="creatingShare"
      @ok="createShare"
    >
      <a-form layout="vertical">
        <a-form-item label="有效期（天）">
          <a-input-number
            v-model:value="shareForm.expireDays"
            :min="1"
            :max="365"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="访问密码（可选,留空表示无需密码）">
          <a-input-password v-model:value="shareForm.password" placeholder="设置后访问时需要输入" />
        </a-form-item>
        <a-form-item label="最大访问次数（可选）">
          <a-input-number
            v-model:value="shareForm.maxAccessCount"
            :min="0"
            style="width: 100%"
            placeholder="留空不限"
          />
        </a-form-item>
        <a-form-item label="允许的 IP（可选,逗号分隔,支持 /24 CIDR）">
          <a-input v-model:value="shareForm.allowedIps" placeholder="192.168.1.0/24,10.0.0.1" />
        </a-form-item>
        <a-alert
          type="warning"
          message="分享链接一旦创建,任何获取 URL 的人均可访问。请妥善保管,避免泄漏到公开渠道。"
          show-icon
        />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 测试报告详情页：查看报告概览、步骤明细、断言与变量提取结果，并支持导出。
 *
 * <p>报告体量较大时，明细数据可能存放在对象存储中，前端按引用地址再取一次；
 * 页面同时展示 Allure 风格的状态汇总卡片。</p>
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  LineChartOutlined,
  DownloadOutlined,
  FilePdfOutlined,
  FileTextOutlined,
  ShareAltOutlined,
  PlusOutlined,
} from '@ant-design/icons-vue';
import { ReportApi } from '@/api';
import {
  ALLURE_STATUS_COLORS,
  ALLURE_STATUS_LABELS,
  ALLURE_TO_RUN_STATUS,
  TRIGGER_LABELS,
  TRIGGER_TYPES,
  statusColor,
} from '@/constants';
import { formatDateTime, isExpired } from '@/utils/format';
import type { Report, ReportDetail, ReportShare as ReportShareType } from '@/types';
import AllureSummary, { type StatusStat } from '@/components/AllureSummary.vue';

const route = useRoute();
const router = useRouter();
const reportId = route.params.id as string;

const report = ref<Report | null>(null);
const details = ref<ReportDetail[]>([]);
const shares = ref<ReportShareType[]>([]);
const htmlPreview = ref<string>('');

const loadingDetails = ref(false);
const loadingShares = ref(false);
const activeTab = ref<string>('details');

/** 状态过滤（配合 AllureSummary 卡片点击） */
const statusFilter = ref<string | null>(null);

const shareModal = ref(false);
const shareForm = reactive({
  expireDays: 30,
  password: '',
  maxAccessCount: undefined as number | undefined,
  allowedIps: '',
});
const creatingShare = ref(false);

const passRate = computed(() => {
  if (!report.value || !report.value.totalCases) return 0;
  return ((report.value.passedCases || 0) * 100) / report.value.totalCases;
});

/** Allure 状态统计（来自报告用例计数） */
const statusStats = computed<StatusStat[]>(() => [
  { key: 'passed', label: ALLURE_STATUS_LABELS.passed, value: report.value?.passedCases || 0 },
  { key: 'failed', label: ALLURE_STATUS_LABELS.failed, value: report.value?.failedCases || 0 },
  { key: 'broken', label: ALLURE_STATUS_LABELS.broken, value: report.value?.errorCases || 0 },
  { key: 'skipped', label: ALLURE_STATUS_LABELS.skipped, value: report.value?.skippedCases || 0 },
]);

/** 按状态过滤后的步骤明细 */
const filteredDetails = computed(() => {
  if (!statusFilter.value) return details.value;
  const target = ALLURE_TO_RUN_STATUS[statusFilter.value];
  return details.value.filter((d) => (d.status || '').toLowerCase() === target);
});

function onStatusFilter(key: string | null) {
  statusFilter.value = key;
}

function clearFilter() {
  statusFilter.value = null;
}

/** Allure 统计键 → 中文名 */
function allureStatusLabel(key: string) {
  return ALLURE_STATUS_LABELS[key] ?? key;
}

/** Allure 统计键 → 标签颜色 */
function allureStatusColor(key: string) {
  return ALLURE_STATUS_COLORS[key] ?? 'blue';
}

function triggerLabel(t?: string) {
  return TRIGGER_LABELS[t ?? ''] ?? TRIGGER_LABELS[TRIGGER_TYPES.MANUAL];
}

function formatDate(iso?: string) {
  return formatDateTime(iso);
}

function sharePublicUrl(s: ReportShareType) {
  return `${window.location.origin}/#/share/${s.token}`;
}

async function loadReport() {
  report.value = await ReportApi.get(reportId);
}

async function loadDetails() {
  loadingDetails.value = true;
  try {
    details.value = await ReportApi.details(reportId);
  } finally {
    loadingDetails.value = false;
  }
}

async function loadShares() {
  loadingShares.value = true;
  try {
    shares.value = await ReportApi.listShares(reportId);
    shares.value = shares.value.map((s) => ({ ...s, url: sharePublicUrl(s) }));
  } finally {
    loadingShares.value = false;
  }
}

async function loadHtmlPreview() {
  const r = await fetch(`/api/v1/reports/${reportId}/export/html`, {
    headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
  });
  htmlPreview.value = await r.text();
}

function exportHtml() {
  window.open(ReportApi.exportHtmlUrl(reportId), '_blank');
}

function exportPdf() {
  window.open(ReportApi.exportPdfUrl(reportId), '_blank');
}

function openTrend() {
  router.push('/trend');
}

function openShare() {
  shareForm.expireDays = 30;
  shareForm.password = '';
  shareForm.maxAccessCount = undefined;
  shareForm.allowedIps = '';
  shareModal.value = true;
  loadShares();
}

async function createShare() {
  creatingShare.value = true;
  try {
    const s = await ReportApi.createShare(reportId, {
      expireDays: shareForm.expireDays,
      password: shareForm.password || undefined,
    } as any);
    message.success(`分享已创建：${sharePublicUrl(s)}`, 8);
    shareModal.value = false;
    await loadShares();
    activeTab.value = 'shares';
  } finally {
    creatingShare.value = false;
  }
}

async function revokeShare(s: ReportShareType) {
  await ReportApi.revokeShare(reportId, s.id);
  message.success('已撤销');
  await loadShares();
}

onMounted(async () => {
  await loadReport();
  await loadDetails();
});
</script>

<style scoped>
/* AppLayout 外层已经提供了页面 padding，这里避免重复 */
.report-page {
  padding: 0;
}

.page-title__icon {
  font-size: var(--fs-lg);
  color: var(--c-primary);
  flex-shrink: 0;
}

/* 数值后缀单位 */
.stat-card .unit {
  font-size: var(--fs-sm);
  font-weight: 400;
  color: var(--tx-3);
  margin-left: 2px;
}

/* 统计卡片下方辅助行 */
.sub-stat {
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--tx-3);
  font-variant-numeric: tabular-nums;
}

/* Tab 所在面板（带圆角+阴影） */
.report-panel {
  padding: 0;
  overflow: hidden;
}

/* 状态过滤提示条 */
.filter-banner {
  margin-bottom: var(--sp-3);
}

.report-tabs {
  padding: 0 var(--sp-5);
}

.report-tabs :deep(.ant-tabs-content-holder) {
  padding: var(--sp-4) 0 var(--sp-5);
}

/* === 步骤明细列表 === */
.report-list :deep(.ant-list-item) {
  padding: var(--sp-4) 0;
  border-bottom: 1px solid var(--bd-subtle);
}

.report-list :deep(.ant-list-item:last-child) {
  border-bottom: none;
}

.step-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.step-tag {
  text-transform: capitalize;
}

.step-name {
  color: var(--tx-1);
  font-weight: 500;
}

.step-duration {
  margin-left: auto;
  color: var(--tx-3);
}

.step-body {
  width: 100%;
  margin-top: var(--sp-2);
}

.step-error {
  margin-bottom: var(--sp-3);
}

.step-collapse {
  background: transparent;
}

/* === 断言子列表 === */
.assertion-list :deep(.ant-list-item) {
  padding: var(--sp-2) 0;
  border-bottom: 1px dashed var(--bd-subtle);
}

.assertion-list :deep(.ant-list-item:last-child) {
  border-bottom: none;
}

.assertion-item {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  color: var(--tx-2);
  font-size: var(--fs-sm);
}

.assertion-mark {
  flex-shrink: 0;
  margin: 0;
  min-width: 24px;
  text-align: center;
}

/* === JSON / 摘要预格式化块 === */
.json-pre {
  margin: 0;
  padding: var(--sp-3);
  background: var(--bg-subtle);
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--tx-2);
  line-height: 1.6;
  overflow-x: auto;
  max-height: 320px;
}

/* === HTML 预览 === */
.html-preview-wrap {
  position: relative;
  height: 70vh;
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  overflow: hidden;
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: var(--sp-4);
}

.html-frame {
  width: 100%;
  height: 100%;
  border: none;
}

.html-load-btn {
  align-self: center;
}

/* === 分享记录 === */
.shares-wrap {
  padding: 0 var(--sp-1);
}

.shares-create-btn {
  margin-bottom: var(--sp-4);
}
</style>
