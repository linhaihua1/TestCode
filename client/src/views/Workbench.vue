<template>
  <a-card :bordered="false" body-style="padding: 12px">
    <a-row :gutter="12" style="height: calc(100vh - 130px)">
      <!-- 左侧：模块树 + 用例列表 -->
      <a-col :span="6" style="height: 100%">
        <a-card title="用例库" size="small" style="height: 100%">
          <template #extra>
            <a-space>
              <a-tooltip title="新建模块">
                <a-button size="small" type="text" @click="createModule()">
                  <folder-add-outlined />
                </a-button>
              </a-tooltip>
            </a-space>
          </template>
          <a-input-search
            v-model:value="moduleKeyword"
            placeholder="搜索模块"
            style="margin-bottom: 8px"
            size="small"
          />
          <VirtualTree
            v-if="moduleTree.length"
            :tree="moduleTree"
            :selected-key="selectedModuleKey"
            :keyword="moduleKeyword"
            :height="300"
            @select="onModuleSelect"
          />
          <a-empty v-else description="暂无模块" />
        </a-card>
      </a-col>

      <!-- 中间：用例编辑 -->
      <a-col :span="12" style="height: 100%; overflow: auto">
        <CaseEditorPanel
          v-if="currentCase"
          :case-data="currentCase"
          @saved="onCaseSaved"
          @debug-finished="onDebugFinished"
        />
        <a-empty
          v-else
          description="从右侧选择用例，或在用例列表新建"
          style="margin-top: 80px"
        />
      </a-col>

      <!-- 右侧：用例列表 + 接口管理 -->
      <a-col :span="6" style="height: 100%">
        <a-tabs v-model:active-key="rightTab" size="small" style="height: 100%">
          <a-tab-pane key="cases" tab="用例">
            <CaseLibraryPanel
              :project-id="projectId"
              :module-id="selectedModuleKey"
              @open="openCase"
            />
          </a-tab-pane>
          <a-tab-pane key="apis" tab="接口">
            <ApiManagerPanel :project-id="projectId" />
          </a-tab-pane>
        </a-tabs>
      </a-col>
    </a-row>

    <!-- 调试记录弹窗 -->
    <DebugRecordsModal
      v-model:open="debugOpen"
      :case-id="debuggingCaseId"
    />
  </a-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { FolderAddOutlined } from '@ant-design/icons-vue'
import { ModuleApi } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { ModuleNode, CaseInfo } from '@/types'
import CaseEditorPanel from '@/components/workbench/CaseEditorPanel.vue'
import CaseLibraryPanel from '@/components/workbench/CaseLibraryPanel.vue'
import ApiManagerPanel from '@/components/workbench/ApiManagerPanel.vue'
import DebugRecordsModal from '@/components/workbench/DebugRecordsModal.vue'

const projectStore = useProjectStore()
const projectId = computed(() => projectStore.currentProjectId)

const moduleTree = ref<ModuleNode[]>([])
const moduleKeyword = ref('')
const selectedModuleKey = ref<string>('')
const rightTab = ref<'cases' | 'apis'>('cases')

const currentCase = ref<CaseInfo | null>(null)

const debugOpen = ref(false)
const debuggingCaseId = ref('')

async function loadModules() {
  if (!projectId.value) return
  moduleTree.value = await ModuleApi.tree(projectId.value, 'case')
}

function onModuleSelect(key: string) {
  selectedModuleKey.value = key
}

async function createModule(parentId?: string) {
  const name = prompt('模块名称')
  if (!name) return
  await ModuleApi.create({
    projectId: projectId.value,
    parentId: parentId ?? selectedModuleKey.value || null,
    name,
    type: 'case',
    sortOrder: 0
  })
  await loadModules()
  message.success('已创建模块')
}

async function openCase(c: CaseInfo) {
  currentCase.value = c
}

function onCaseSaved() {
  message.success('已保存')
}

function onDebugFinished(caseId: string) {
  debuggingCaseId.value = caseId
  debugOpen.value = true
}

watch(projectId, loadModules, { immediate: false })
onMounted(async () => {
  await projectStore.fetchAll()
  await loadModules()
})
</script>