<template>
  <div class="page">
    <!-- 页头 -->
    <div class="page-header">
      <div class="page-title">调试记录</div>
      <a-space>
        <a-select
          v-model:value="resultFilter"
          :options="resultOptions"
          style="width: 140px"
          @change="reload"
        />
        <a-button @click="reload"><reload-outlined />刷新</a-button>
      </a-space>
    </div>

    <!-- 调试记录表格 -->
    <div class="panel">
      <a-table
        :data-source="records"
        :columns="columns"
        row-key="id"
        :loading="loading"
        :pagination="{ current: page, pageSize: size, total, onChange: onPageChange }"
        :expand-column-width="60"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'result'">
            <a-tag :color="statusColor(record.result)">{{ statusLabel(record.result) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'duration'">
            {{ record.totalDuration != null ? `${record.totalDuration} ms` : '-' }}
          </template>
        </template>

        <!-- 展开行：请求/响应/断言详情 -->
        <template #expandedRowRender="{ record }">
          <div class="record-detail">
            <div class="record-detail__block">
              <div class="record-detail__title">请求摘要</div>
              <pre class="record-detail__pre">{{ pretty(record.requestSummary) }}</pre>
            </div>
            <div class="record-detail__block">
              <div class="record-detail__title">响应摘要</div>
              <pre class="record-detail__pre">{{ pretty(record.responseSummary) }}</pre>
            </div>
            <div v-if="record.assertionResults" class="record-detail__block">
              <div class="record-detail__title">断言结果</div>
              <pre class="record-detail__pre">{{ pretty(record.assertionResults) }}</pre>
            </div>
            <div v-if="record.extractedVariables" class="record-detail__block">
              <div class="record-detail__title">提取变量</div>
              <pre class="record-detail__pre">{{ pretty(record.extractedVariables) }}</pre>
            </div>
            <div v-if="record.errorLog" class="record-detail__block">
              <pre class="record-detail__pre record-detail__error">{{ record.errorLog }}</pre>
            </div>
          </div>
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 全局调试记录页（文档 §2.2）：按项目聚合全部用例的调试记录。
 *
 * <p>与工作台「调试记录」Tab（单用例维度）不同，本页是全局视图，
 * 支持按执行结果筛选 + 展开查看请求/响应/断言/提取详情。</p>
 */
import { onMounted, ref } from 'vue';
import { ReloadOutlined } from '@ant-design/icons-vue';
import { CaseApi } from '@/api';
import { formatDateTime } from '@/utils/format';
import { statusColor, statusLabel } from '@/constants';
import { useProjectStore } from '@/stores/project';
import type { DebugRecord } from '@/types';

const projectStore = useProjectStore();

const records = ref<DebugRecord[]>([]);
const loading = ref(false);
const resultFilter = ref<string>();
const page = ref(1);
const size = ref(20);
const total = ref(0);

const columns = [
  { title: '调试时间', key: 'createdAt', width: 180 },
  { title: '用例名称', dataIndex: 'caseNameSnapshot', width: 200 },
  { title: '执行方式', dataIndex: 'executeMode', width: 120 },
  { title: '执行结果', key: 'result', width: 100 },
  { title: '耗时', key: 'duration', width: 110 },
  { title: '执行人', dataIndex: 'createdBy', width: 120 },
];

const resultOptions = [
  { value: undefined, label: '全部结果' },
  { value: 'success', label: '成功' },
  { value: 'failed', label: '失败' },
  { value: 'error', label: '异常' },
  { value: 'skipped', label: '跳过' },
];

/** 时间展示：统一走 utils/format（UTC → 本地时区） */
function formatTime(iso: string) {
  return formatDateTime(iso);
}

/** JSON 字段格式化展示（字符串可能是 JSON 文本） */
function pretty(json?: string | null): string {
  if (!json) return '—';
  try {
    const parsed = JSON.parse(json);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return json;
  }
}

/** 按当前项目 + 筛选条件 + 分页加载全局调试记录 */
async function reload() {
  const projectId = projectStore.currentProjectId;
  if (!projectId) return;
  loading.value = true;
  try {
    const list = await CaseApi.allDebugRecords({
      projectId,
      page: page.value,
      size: size.value,
    });
    // 前端按结果筛选（后端当前仅按项目聚合，筛选放前端更轻量）
    records.value = resultFilter.value
      ? list.filter((r) => r.result === resultFilter.value)
      : list;
    total.value = records.value.length;
  } finally {
    loading.value = false;
  }
}

function onPageChange(p: number) {
  page.value = p;
  reload();
}

onMounted(reload);
</script>

<style scoped>
.record-detail {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--sp-3);
  padding: var(--sp-3);
}

.record-detail__block {
  min-width: 0;
}

.record-detail__title {
  font-size: var(--fs-xs);
  color: var(--tx-3);
  margin-bottom: var(--sp-2);
  font-weight: 600;
}

.record-detail__pre {
  background: var(--bg-subtle);
  border-radius: var(--rd-sm);
  padding: var(--sp-2);
  font-size: var(--fs-xs);
  max-height: 220px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.record-detail__error {
  color: var(--c-danger, #dc2626);
}
</style>
