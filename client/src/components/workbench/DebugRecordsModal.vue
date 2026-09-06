<template>
  <a-modal
    :open="open"
    @cancel="emit('update:open', false)"
    :footer="null"
    width="900"
    :title="`调试记录 - ${records.length} 条`"
  >
    <a-list :data-source="records" size="small">
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta>
            <template #title>
              <a-space>
                <a-tag :color="statusColor(item.result)">{{ item.result }}</a-tag>
                <span>{{ formatTime(item.createdAt) }}</span>
                <span style="color: #999">{{ item.totalDuration }} ms</span>
                <span v-if="item.environmentId" style="color: #999">env: {{ item.environmentId }}</span>
              </a-space>
            </template>
            <template #description>
              <a-collapse ghost>
                <a-collapse-panel header="步骤明细">
                  <a-steps
                    :current="item.stepResults?.length || 0"
                    size="small"
                    direction="vertical"
                  >
                    <a-step
                      v-for="(s, i) in item.stepResults"
                      :key="i"
                      :title="s.stepName"
                      :status="s.status === 'success' ? 'finish' : 'error'"
                      :description="`${s.durationMs}ms ${
                        s.error ? ' - ' + s.error : ''
                      }`"
                    />
                  </a-steps>
                </a-collapse-panel>
                <a-collapse-panel header="断言结果">
                  <a-list
                    :data-source="item.assertionResults"
                    size="small"
                  >
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
                  <pre style="white-space: pre-wrap">{{ JSON.stringify(item.extractedVariables, null, 2) }}</pre>
                </a-collapse-panel>
              </a-collapse>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import dayjs from 'dayjs'
import { CaseApi } from '@/api'
import type { DebugRecord } from '@/types'

const props = defineProps<{ open: boolean; caseId: string }>()
const emit = defineEmits<{ 'update:open': [v: boolean] }>()

const records = ref<DebugRecord[]>([])

function statusColor(s: string) {
  return s === 'success' ? 'green' : s === 'failed' ? 'red' : s === 'error' ? 'volcano' : 'default'
}

function formatTime(iso: string) {
  return dayjs.utc(iso).local().format('YYYY-MM-DD HH:mm:ss')
}

watch(
  () => [props.open, props.caseId],
  async ([open, id]) => {
    if (open && id) {
      records.value = await CaseApi.debugRecords(id as string)
    }
  }
)
</script>