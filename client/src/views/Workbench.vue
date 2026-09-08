<!--
  工作台主页面（需求文档 §1 + 开发文档 §5.1）。

  <h3>布局</h3>
  <ul>
    <li>顶部全局配置栏：全局变量 / 调试记录 / 回收站（由 AppLayout 提供）</li>
    <li>分割线（由 AppLayout 提供）</li>
    <li>下方三栏：左 = 用例库（含模块树+用例列表）/ 中 = 编写用例 / 右 = 接口管理</li>
  </ul>

  <h3>快捷键</h3>
  <ul>
    <li>Ctrl+S：保存当前用例</li>
    <li>Ctrl+Enter：调试当前用例</li>
    <li>Ctrl+F：聚焦到用例名称搜索框</li>
  </ul>
-->
<template>
  <div class="workbench-root">
    <ThreePaneLayout
      storage-key="workbench.pane-widths"
      left-title="用例库"
      middle-title="编写用例"
      right-title="接口管理"
    >
      <template #left>
        <WorkbenchLeftPanel
          :project-id="projectId"
          :current-case-id="currentCase?.id"
          @open="openCase"
        />
      </template>
      <template #middle>
        <CaseEditorPanel
          v-if="currentCase"
          ref="editorRef"
          :case-data="currentCase"
          @saved="onCaseSaved"
          @debug-finished="onDebugFinished"
        />
        <a-empty
          v-else
          description="从左侧选择用例，或在用例列表新建"
          style="margin-top: 80px"
        />
      </template>
      <template #right>
        <ApiManagerPanel :project-id="projectId" />
      </template>
    </ThreePaneLayout>

    <DebugRecordsModal
      v-model:open="debugOpen"
      :case-id="debuggingCaseId"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useProjectStore } from '@/stores/project'
import { useShortcuts } from '@/hooks/useShortcuts'
import type { CaseInfo } from '@/types'
import ThreePaneLayout from '@/components/ThreePaneLayout.vue'
import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'
import CaseEditorPanel from '@/components/workbench/CaseEditorPanel.vue'
import ApiManagerPanel from '@/components/workbench/ApiManagerPanel.vue'
import DebugRecordsModal from '@/components/workbench/DebugRecordsModal.vue'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const currentCase = ref<CaseInfo | null>(null)

const debugOpen = ref(false)
const debuggingCaseId = ref('')

const editorRef = ref<InstanceType<typeof CaseEditorPanel> | null>(null)

// 全局快捷键：Ctrl+S 保存、Ctrl+Enter 调试
useShortcuts({
  onSave: () => {
    if (!currentCase.value) {
      message.info('请先选择一个用例')
      return
    }
    editorRef.value?.save?.()
  },
  onDebug: () => {
    if (!currentCase.value) {
      message.info('请先选择一个用例')
      return
    }
    editorRef.value?.debug?.()
  }
})

function openCase(c: CaseInfo) {
  currentCase.value = c
}

function onCaseSaved() {
  message.success('已保存')
}

function onDebugFinished(caseId: string) {
  debuggingCaseId.value = caseId
  debugOpen.value = true
}

watch(projectId, () => {
  // 项目切换时清空当前用例
  currentCase.value = null
})

onMounted(async () => {
  await projectStore.fetchAll()
  if (projectStore.projects.length && !projectId.value) {
    projectStore.setCurrent(projectStore.projects[0].id)
  }
})
</script>

<style scoped>
.workbench-root {
  height: calc(100vh - 96px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}
</style>