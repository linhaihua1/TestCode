<template>
  <a-card :bordered="false" v-if="details">
    <a-page-header :title="`报告详情 - ${details.length} 个步骤`" @back="() => router.back()" />
    <a-list :data-source="details">
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta>
            <template #title>
              <a-space>
                <a-tag :color="statusColor(item.status)">{{ item.status }}</a-tag>
                <span>{{ item.stepName }}</span>
              </a-space>
            </template>
            <template #description>
              <a-space direction="vertical" style="width: 100%">
                <a-alert v-if="item.error" type="error" :message="item.error" />
                <a-collapse ghost>
                  <a-collapse-panel header="断言结果 ({{ item.assertions?.length || 0 }})">
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
                </a-collapse>
              </a-space>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>
  </a-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ReportApi } from '@/api'
import type { ReportDetail } from '@/types'

const route = useRoute()
const router = useRouter()
const details = ref<ReportDetail[]>([])

function statusColor(s: string) {
  return s === 'success' ? 'green' : 'volcano'
}

onMounted(async () => {
  details.value = await ReportApi.detail(route.params.id as string)
})
</script>