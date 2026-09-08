<template>
  <a-card :bordered="false" v-if="report">
    <a-page-header
      :title="report.name"
      :sub-title="`触发方式：${triggerLabel(report.triggerType)} · 耗时 ${report.duration || 0} ms`"
      @back="() => router.back()"
    >
      <template #extra>
        <a-space>
          <a-button @click="openTrend" type="link">
            <line-chart-outlined />趋势
          </a-button>
          <a-button @click="exportHtml" type="link">
            <download-outlined />HTML
          </a-button>
          <a-button @click="exportPdf" type="link">
            <file-pdf-outlined />PDF
          </a-button>
          <a-button @click="openShare" type="primary">
            <share-alt-outlined />分享报告
          </a-button>
        </a-space>
      </template>
    </a-page-header>

    <!-- 统计卡片 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-statistic title="用例总数" :value="report.totalCases || 0" />
      </a-col>
      <a-col :span="6">
        <a-statistic
          title="通过率"
          :value="passRate"
          :precision="2"
          suffix="%"
          :value-style="{ color: passRate >= 80 ? '#52c41a' : passRate >= 60 ? '#faad14' : '#ff4d4f' }"
        />
      </a-col>
      <a-col :span="6">
        <a-statistic title="断言" :value="report.totalAssertions || 0">
          <template #suffix>
            <span style="font-size: 12px; color: #888">
              通过 {{ report.passedAssertions || 0 }} / 失败 {{ report.failedAssertions || 0 }}
            </span>
          </template>
        </a-statistic>
      </a-col>
      <a-col :span="6">
        <a-statistic title="平均响应时间" :value="report.avgResponseTime || 0" suffix="ms" />
      </a-col>
    </a-row>

    <!-- Tab 切换：明细 / HTML预览 / 分享记录 -->
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="details" tab="步骤明细">
        <a-list :data-source="details" :loading="loadingDetails">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <a-space>
                    <a-tag :color="statusColor(item.status)">{{ item.status }}</a-tag>
                    <span>{{ item.stepName }}</span>
                    <a-tag v-if="item.durationMs" color="default">{{ item.durationMs }} ms</a-tag>
                  </a-space>
                </template>
                <template #description>
                  <a-space direction="vertical" style="width: 100%">
                    <a-alert v-if="item.error" type="error" :message="item.error" />
                    <a-collapse ghost>
                      <a-collapse-panel :header="`断言结果 (${item.assertions?.length || 0})`">
                        <a-list :data-source="item.assertions" size="small">
                          <template #renderItem="{ item: a }">
                            <a-list-item>
                              <a-space>
                                <a-tag :color="a.passed ? 'green' : 'red'">
                                  {{ a.passed ? '✓' : '✗' }}
                                </a-tag>
                                <span>{{ a.message }}</span>
                              </a-space>
                            </a-list-item>
                          </template>
                        </a-list>
                      </a-collapse-panel>
                      <a-collapse-panel header="提取变量">
                        <pre>{{ JSON.stringify(item.extracts, null, 2) }}</pre>
                      </a-collapse-panel>
                      <a-collapse-panel v-if="item.requestSummary" header="请求摘要">
                        <pre>{{ item.requestSummary }}</pre>
                      </a-collapse-panel>
                      <a-collapse-panel v-if="item.responseSummary" header="响应摘要">
                        <pre>{{ item.responseSummary }}</pre>
                      </a-collapse-panel>
                    </a-collapse>
                  </a-space>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </a-tab-pane>

      <a-tab-pane key="html" tab="HTML 预览">
        <div class="html-preview-wrap">
          <iframe
            v-if="htmlPreview"
            :srcdoc="htmlPreview"
            class="html-frame"
            sandbox=""
          />
          <a-empty v-else description="暂无 HTML 报告,点击" />
          <a-button v-if="!htmlPreview" @click="loadHtmlPreview">加载 HTML 报告</a-button>
        </div>
      </a-tab-pane>

      <a-tab-pane key="shares" tab="分享记录">
        <a-button type="primary" style="margin-bottom: 12px" @click="openShare">
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
              {{ record.accessCount }}{{ record.maxAccessCount ? `/${record.maxAccessCount}` : '' }}
            </template>
            <template v-else-if="column.key === 'hasPassword'">
              <a-tag v-if="record.hasPassword" color="orange">已设置</a-tag>
              <a-tag v-else color="default">无</a-tag>
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
      </a-tab-pane>
    </a-tabs>

    <!-- 创建分享弹窗 -->
    <a-modal
      v-model:open="shareModal"
      title="分享报告"
      @ok="createShare"
      :confirm-loading="creatingShare"
    >
      <a-form layout="vertical">
        <a-form-item label="有效期（天）">
          <a-input-number v-model:value="shareForm.expireDays" :min="1" :max="365" style="width: 100%" />
        </a-form-item>
        <a-form-item label="访问密码（可选,留空表示无需密码）">
          <a-input-password v-model:value="shareForm.password" placeholder="设置后访问时需要输入" />
        </a-form-item>
        <a-form-item label="最大访问次数（可选）">
          <a-input-number v-model:value="shareForm.maxAccessCount" :min="0" style="width: 100%" placeholder="留空不限" />
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
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  LineChartOutlined,
  DownloadOutlined,
  FilePdfOutlined,
  ShareAltOutlined,
  PlusOutlined
} from '@ant-design/icons-vue'
import { ReportApi } from '@/api'
import type { Report, ReportDetail, ReportShare as ReportShareType } from '@/types'

const route = useRoute()
const router = useRouter()
const reportId = route.params.id as string

const report = ref<Report | null>(null)
const details = ref<ReportDetail[]>([])
const shares = ref<ReportShareType[]>([])
const htmlPreview = ref<string>('')

const loadingDetails = ref(false)
const loadingShares = ref(false)
const activeTab = ref<string>('details')

const shareModal = ref(false)
const shareForm = reactive({
  expireDays: 30,
  password: '',
  maxAccessCount: undefined as number | undefined,
  allowedIps: ''
})
const creatingShare = ref(false)

const passRate = computed(() => {
  if (!report.value || !report.value.totalCases) return 0
  return (report.value.passedCases || 0) * 100 / report.value.totalCases
})

function statusColor(s: string) {
  return s === 'success' ? 'green'
    : s === 'failed' ? 'red'
    : s === 'error' ? 'volcano'
    : s === 'skipped' ? 'default'
    : 'blue'
}

function triggerLabel(t?: string) {
  switch (t) {
    case 'manual': return '手动'
    case 'schedule': return '定时'
    case 'webhook': return 'Webhook'
    case 'api': return 'API'
    default: return '手动'
  }
}

function formatDate(iso?: string) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}

function isExpired(iso: string) {
  return new Date(iso).getTime() < Date.now()
}

function sharePublicUrl(s: ReportShareType) {
  return `${window.location.origin}/#/share/${s.token}`
}

async function loadReport() {
  report.value = await ReportApi.get(reportId)
}

async function loadDetails() {
  loadingDetails.value = true
  try {
    details.value = await ReportApi.details(reportId)
  } finally {
    loadingDetails.value = false
  }
}

async function loadShares() {
  loadingShares.value = true
  try {
    shares.value = await ReportApi.listShares(reportId)
    shares.value = shares.value.map((s) => ({ ...s, url: sharePublicUrl(s) }))
  } finally {
    loadingShares.value = false
  }
}

async function loadHtmlPreview() {
  const r = await fetch(`/api/v1/reports/${reportId}/export/html`, {
    headers: { Authorization: `Bearer ${localStorage.getItem('token') || ''}` }
  })
  htmlPreview.value = await r.text()
}

function exportHtml() {
  window.open(ReportApi.exportHtmlUrl(reportId), '_blank')
}

function exportPdf() {
  window.open(ReportApi.exportPdfUrl(reportId), '_blank')
}

function openTrend() {
  router.push('/trend')
}

function openShare() {
  shareForm.expireDays = 30
  shareForm.password = ''
  shareForm.maxAccessCount = undefined
  shareForm.allowedIps = ''
  shareModal.value = true
  loadShares()
}

async function createShare() {
  creatingShare.value = true
  try {
    const s = await ReportApi.createShare(reportId, {
      expireDays: shareForm.expireDays,
      password: shareForm.password || undefined
    } as any)
    message.success(`分享已创建：${sharePublicUrl(s)}`, 8)
    shareModal.value = false
    await loadShares()
    activeTab.value = 'shares'
  } finally {
    creatingShare.value = false
  }
}

async function revokeShare(s: ReportShareType) {
  await ReportApi.revokeShare(s.id)
  message.success('已撤销')
  await loadShares()
}

onMounted(async () => {
  await loadReport()
  await loadDetails()
})
</script>

<style scoped>
.html-preview-wrap {
  height: 70vh;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}
.html-frame {
  width: 100%;
  height: 100%;
  border: none;
}
</style>