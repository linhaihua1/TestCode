<!--
  工作台主页面 —— 接口用例（需求文档 §1~§5）。

  <h3>布局</h3>
  <ul>
    <li>顶部配置栏：右上角「设置全局变量 / 调试记录 / 回收站」，用分割线与下方三栏分隔</li>
    <li>下方三栏：左 = 用例库 / 中 = 编写用例 / 右 = 接口管理</li>
  </ul>

  <h3>快捷键</h3>
  <ul>
    <li>Ctrl+S：保存当前用例</li>
    <li>Ctrl+Enter：调试当前用例</li>
  </ul>
-->
<template>
  <div class="workbench-root">
    <!-- 顶部配置栏 -->
    <div class="config-bar">
      <div class="config-bar__left">
        <span class="config-bar__title">接口用例</span>
      </div>
      <div class="config-bar__right">
        <a-button v-can-write type="text" size="small" @click="goTo('global-variables')">
          <global-outlined />设置全局变量
        </a-button>
        <a-button type="text" size="small" @click="goTo('debug-records')">
          <file-search-outlined />调试记录
        </a-button>
        <a-button v-can-write type="text" size="small" @click="goTo('recycle-bin')">
          <delete-outlined />回收站
        </a-button>
      </div>
    </div>

    <!-- 分割线 -->
    <div class="divider" />

    <!-- 三栏 -->
    <div class="panes">
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
            @debug="onDebugRequest"
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
    </div>

    <!-- 调试弹窗（环境变量选择） -->
    <DebugModal
      v-model:open="debugModalOpen"
      :case-id="debuggingCaseId"
      :project-id="projectId"
      @finished="onDebugFinished"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { GlobalOutlined, FileSearchOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import { useProjectStore } from '@/stores/project'
import { useShortcuts } from '@/hooks/useShortcuts'
import type { CaseInfo } from '@/types'
import ThreePaneLayout from '@/components/ThreePaneLayout.vue'
import WorkbenchLeftPanel from '@/components/workbench/WorkbenchLeftPanel.vue'
import CaseEditorPanel from '@/components/workbench/CaseEditorPanel.vue'
import ApiManagerPanel from '@/components/workbench/ApiManagerPanel.vue'
import DebugModal from '@/components/workbench/DebugModal.vue'

const router = useRouter()
const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const currentCase = ref<CaseInfo | null>(null)

const debugModalOpen = ref(false)
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

function goTo(name: string) {
  router.push({ name })
}

function openCase(c: CaseInfo) {
  currentCase.value = c
}

function onCaseSaved() {
  message.success('已保存')
}

// 用户点击「调试」→ 弹出调试弹窗
function onDebugRequest(caseId: string) {
  debuggingCaseId.value = caseId
  debugModalOpen.value = true
}

// 调试完成后：打开调试记录（由 CaseEditorPanel 内部 Tab 展示）
function onDebugFinished(caseId: string) {
  // 通知编辑器切换到「调试记录」Tab
  editorRef.value?.showDebugRecords?.(caseId)
}

watch(projectId, () => {
  currentCase.value = null
})

onMounted(async () => {
  // fetchAll 内部会自动选中第一个项目（无缓存时）
  await projectStore.fetchAll()
})
</script>

<style scoped>
.workbench-root {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 顶部配置栏 */
.config-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  height: var(--confbar-h, 44px);
  padding: 0 var(--sp-5);
  background: var(--bg-card);
  flex-shrink: 0;
}

.config-bar__title {
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--tx-1);
}

.config-bar__right {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
}

/* 分割线 */
.divider {
  height: 1px;
  background: var(--bd-base);
  flex-shrink: 0;
}

/* 三栏占满剩余高度 */
.panes {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>
