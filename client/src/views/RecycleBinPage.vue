<!--
  回收站管理页（需求文档 §2.3）。

  <h3>支持的回收对象</h3>
  <ul>
    <li>目录 / 接口定义 / 用例 / 任务</li>
  </ul>

  <h3>操作</h3>
  <ul>
    <li>列表查看（按类型筛选）</li>
    <li>批量还原（冲突自动重命名）</li>
    <li>批量永久删除（二次确认）</li>
    <li>配置自动清理周期</li>
  </ul>
-->
<template>
  <div class="page">
    <!-- 页头：标题 + 清理配置提示与入口 -->
    <div class="page-header">
      <div class="page-title">回收站</div>
      <a-space>
        <span class="text-3" style="font-size: var(--fs-sm)">
          自动清理：{{ config.cleanupDays === 0 ? '不自动清理' : `${config.cleanupDays} 天` }}
        </span>
        <a-button @click="configOpen = true">配置</a-button>
      </a-space>
    </div>

    <!-- 批量操作工具条 -->
    <div class="panel toolbar">
      <a-space>
        <a-select v-model:value="filterType" :options="typeOptions" style="width: 140px" />
        <a-button @click="reload">刷新</a-button>
        <a-popconfirm title="确认批量还原？" @confirm="batchRestore">
          <a-button type="primary" :disabled="!selectedRowKeys.length">批量还原</a-button>
        </a-popconfirm>
        <a-popconfirm
          title="永久删除不可恢复！确认批量永久删除？"
          ok-text="确认永久删除"
          cancel-text="取消"
          @confirm="batchPermanent"
        >
          <a-button danger :disabled="!selectedRowKeys.length">批量永久删除</a-button>
        </a-popconfirm>
      </a-space>
    </div>

    <!-- 回收站表格 -->
    <div class="panel" style="margin-top: var(--sp-4)">
      <a-table
        :data-source="items"
        :columns="columns"
        :loading="loading"
        :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
        row-key="id"
        size="middle"
        :pagination="{ pageSize: 20 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'type'">
            <a-tag :color="typeColor(record.type)">{{ typeLabel(record.type) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'deletedAt'">
            {{ formatTime(record.deletedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="restore(record)">还原</a-button>
              <a-popconfirm title="永久删除不可恢复，确认？" @confirm="permanent(record.id)">
                <a-button size="small" danger>永久删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <!-- 自动清理配置弹窗 -->
    <a-modal
      v-model:open="configOpen"
      title="回收站自动清理配置"
      @ok="saveConfig"
      width="480"
    >
      <a-form layout="vertical">
        <a-form-item label="自动清理周期" required>
          <a-select v-model:value="config.cleanupDays" :options="daysOptions" />
        </a-form-item>
        <a-alert
          type="info"
          show-icon
          message="到达指定天数后，每日凌晨 03:00 的定时任务会自动物理删除回收站数据。"
        />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { RecycleBinApi, type RecycleBinItem, type RecycleBinConfig } from '@/api'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const filterType = ref<RecycleBinItem['type']>('CASE')
const items = ref<RecycleBinItem[]>([])
const loading = ref(false)
const selectedRowKeys = ref<string[]>([])

const configOpen = ref(false)
const config = reactive<RecycleBinConfig>({
  projectId: '',
  cleanupDays: 30
})

const typeOptions = [
  { label: '用例', value: 'CASE' },
  { label: '接口定义', value: 'API' },
  { label: '模块目录', value: 'MODULE' },
  { label: '测试任务', value: 'TASK' }
]

const daysOptions = [
  { label: '不自动清理', value: 0 },
  { label: '7 天', value: 7 },
  { label: '15 天', value: 15 },
  { label: '30 天', value: 30 },
  { label: '60 天', value: 60 }
]

const columns = [
  { title: '对象', dataIndex: 'type', key: 'type', width: 100 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '附加信息', dataIndex: 'extra', key: 'extra' },
  { title: '删除时间', dataIndex: 'deletedAt', key: 'deletedAt', width: 180 },
  { title: '操作', key: 'actions', width: 180 }
]

function typeLabel(t: string) {
  return typeOptions.find(o => o.value === t)?.label || t
}
function typeColor(t: string) {
  return ({ CASE: 'blue', API: 'green', MODULE: 'orange', TASK: 'purple' } as any)[t] || 'default'
}
function formatTime(t?: string) {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '—'
}

function onSelectChange(keys: string[]) {
  selectedRowKeys.value = keys
}

async function reload() {
  if (!projectId.value) return
  loading.value = true
  try {
    items.value = await RecycleBinApi.list(projectId.value, filterType.value)
  } finally {
    loading.value = false
  }
}

async function loadConfig() {
  if (!projectId.value) return
  const cfg = await RecycleBinApi.getConfig(projectId.value)
  Object.assign(config, cfg)
}

async function saveConfig() {
  config.projectId = projectId.value
  await RecycleBinApi.updateConfig({ ...config })
  configOpen.value = false
  message.success('配置已保存')
}

async function restore(record: RecycleBinItem) {
  const result = await RecycleBinApi.restore([record.id], record.type)
  message.success(`已还原 ${result.restoredCount} 项${result.message ? '：' + result.message : ''}`)
  await reload()
}

async function batchRestore() {
  const result = await RecycleBinApi.restore(selectedRowKeys.value, filterType.value)
  message.success(`已还原 ${result.restoredCount} 项${result.message ? '：' + result.message : ''}`)
  selectedRowKeys.value = []
  await reload()
}

async function permanent(id: string) {
  await RecycleBinApi.permanent([id], filterType.value)
  message.success('已永久删除')
  await reload()
}

async function batchPermanent() {
  await RecycleBinApi.permanent(selectedRowKeys.value, filterType.value)
  selectedRowKeys.value = []
  message.success('批量永久删除完成')
  await reload()
}

watch(filterType, reload)
watch(projectId, () => {
  loadConfig()
  reload()
})

onMounted(async () => {
  await loadConfig()
  await reload()
})
</script>

<style scoped>
/* 工具条 panel：横向 padding 收紧，纵向留白让控件更紧凑 */
.toolbar {
  padding: var(--sp-3) var(--sp-4);
}
</style>