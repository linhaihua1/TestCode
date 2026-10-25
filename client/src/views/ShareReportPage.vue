<template>
  <div class="share-page">
    <header class="share-page__head">
      <div class="share-page__brand">Api-Web</div>
      <div class="share-page__desc">测试报告分享（只读）</div>
    </header>

    <a-spin v-if="loading" class="share-page__spin" tip="加载报告中…" />

    <div v-else-if="error" class="share-page__state">
      <a-result status="warning" :title="error">
        <template v-if="needPassword" #extra>
          <a-input-password
            v-model:value="password"
            placeholder="请输入访问密码"
            style="max-width: 260px"
            @press-enter="load"
          />
          <a-button type="primary" style="margin-left: 8px" @click="load">查看</a-button>
        </template>
      </a-result>
    </div>

    <template v-else-if="report">
      <section class="share-page__summary">
        <h1 class="share-page__title">{{ report.name }}</h1>
        <div class="share-page__meta">
          <a-tag :color="statusColor">{{ statusLabel }}</a-tag>
          <span>开始：{{ fmt(report.startedAt) }}</span>
          <span>耗时：{{ report.duration || 0 }} ms</span>
        </div>
        <a-row :gutter="12" class="share-page__stats">
          <a-col :span="4"><div class="stat"><b>{{ report.totalCases || 0 }}</b><span>用例</span></div></a-col>
          <a-col :span="4"><div class="stat"><b class="ok">{{ report.passedCases || 0 }}</b><span>通过</span></div></a-col>
          <a-col :span="4"><div class="stat"><b class="bad">{{ report.failedCases || 0 }}</b><span>失败</span></div></a-col>
          <a-col :span="4"><div class="stat"><b class="err">{{ report.errorCases || 0 }}</b><span>异常</span></div></a-col>
          <a-col :span="4"><div class="stat"><b>{{ report.totalAssertions || 0 }}</b><span>断言</span></div></a-col>
          <a-col :span="4"><div class="stat"><b>{{ passRate }}%</b><span>通过率</span></div></a-col>
        </a-row>
      </section>

      <section class="share-page__details">
        <h2>步骤明细</h2>
        <a-table
          :data-source="details"
          :columns="columns"
          row-key="id"
          size="middle"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="detailColor(record.status)">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.key === 'error'">
              <span class="err-text">{{ record.error || '—' }}</span>
            </template>
          </template>
        </a-table>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 报告分享页（公开路由 /#/share/:token，无需登录）。
 *
 * <p>只为分享链接服务：凭 token（可选密码）读取报告与步骤明细，不展示任何管理入口。</p>
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ReportApi } from '@/api';
import { formatDateTime } from '@/utils/format';
import type { Report, ReportDetail } from '@/types';

const route = useRoute();
const token = String(route.params.token || '');
const loading = ref(true);
const error = ref('');
const needPassword = ref(false);
const password = ref('');
const report = ref<Report | null>(null);
const details = ref<ReportDetail[]>([]);

const columns = [
  { title: '步骤', dataIndex: 'stepName', key: 'stepName' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '失败原因', dataIndex: 'error', key: 'error' },
];

const statusColor = computed(
  () => ({ passed: 'green', failed: 'red', error: 'orange', running: 'blue' })[report.value?.status || ''] || 'default',
);
const statusLabel = computed(
  () => ({ passed: '通过', failed: '失败', error: '异常', running: '执行中' })[report.value?.status || ''] || report.value?.status,
);
const passRate = computed(() => {
  const total = report.value?.totalCases || 0;
  if (!total) return 0;
  return Math.round(((report.value?.passedCases || 0) / total) * 1000) / 10;
});

function detailColor(status: string) {
  return ({ success: 'green', failed: 'red', error: 'orange', skipped: 'default' } as Record<string, string>)[status] || 'default';
}
function fmt(v?: string) {
  return v ? formatDateTime(v) : '—';
}

async function load() {
  loading.value = true;
  error.value = '';
  needPassword.value = false;
  try {
    const data = await ReportApi.publicContent(token, password.value || undefined);
    report.value = data.report;
    details.value = data.details || [];
  } catch (e: any) {
    error.value = e?.message || '分享链接不可用';
    needPassword.value = /密码/.test(error.value);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.share-page {
  min-height: 100vh;
  background: var(--color-bg-layout, #f5f7fa);
  padding: 24px;
}
.share-page__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.share-page__brand {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary, #2563eb);
}
.share-page__desc {
  color: var(--color-text-secondary, #6b7280);
}
.share-page__spin {
  display: block;
  margin: 120px auto;
}
.share-page__state {
  background: #fff;
  border-radius: 8px;
  padding: 40px;
}
.share-page__summary,
.share-page__details {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}
.share-page__title {
  font-size: 18px;
  margin: 0 0 8px;
}
.share-page__meta {
  display: flex;
  gap: 16px;
  color: var(--color-text-secondary, #6b7280);
  font-size: 13px;
  margin-bottom: 16px;
}
.share-page__stats {
  margin-top: 8px;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.stat b {
  font-size: 20px;
}
.stat .ok {
  color: #16a34a;
}
.stat .bad {
  color: #dc2626;
}
.stat .err {
  color: #ea580c;
}
.stat span {
  color: var(--color-text-secondary, #6b7280);
  font-size: 12px;
}
.err-text {
  color: #dc2626;
  font-size: 12px;
}
</style>
