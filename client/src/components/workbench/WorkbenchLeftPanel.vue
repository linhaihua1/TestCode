<!--
  工作台左栏（需求文档 §3）：模块树 + 用例列表 + 搜索。

  <h3>布局</h3>
  <ul>
    <li>顶部搜索框：按用例名称模糊匹配</li>
    <li>模块树（按 case 类型）：点击模块过滤右侧用例列表</li>
    <li>用例列表：选中后向父组件 emit('open', case)</li>
    <li>顶部 + 按钮：新建用例</li>
  </ul>
-->
<template>
  <div class="workbench-left">
    <div class="left-toolbar">
      <a-input-search
        v-model:value="keyword"
        placeholder="搜索用例（Ctrl+F）"
        size="small"
        style="flex: 1"
        @search="reloadCases"
      />
      <a-button v-can-write size="small" type="primary" @click="openCreate">
        <plus-outlined />新建
      </a-button>
    </div>

    <a-tabs v-model:active-key="activeTab" size="small" class="left-tabs">
      <a-tab-pane key="tree" tab="模块树">
        <div class="tree-wrap">
          <VirtualTree
            v-if="moduleTree.length"
            :tree="moduleTree"
            :selected-key="selectedModuleKey"
            :keyword="''"
            :height="320"
            @select="onModuleSelect"
          />
          <a-empty v-else description="暂无模块" />
          <div class="tree-actions">
            <a-button v-can-write size="small" type="dashed" block @click="createModule()">
              <folder-add-outlined />新建模块
            </a-button>
          </div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="list" tab="用例">
        <a-select
          v-model:value="statusFilter"
          :options="statusOptions"
          size="small"
          style="width: 100%; margin-bottom: 6px"
          @change="reloadCases"
        />
        <a-list :data-source="cases" :loading="loading" size="small" class="case-list">
          <template #renderItem="{ item }">
            <a-list-item
              class="case-item"
              :class="{ active: currentCaseId === item.id }"
              @click="emit('open', item)"
            >
              <a-list-item-meta>
                <template #title>
                  <div class="case-item__title">
                    <span class="case-name ellipsis">{{ item.name }}</span>
                    <a-tag color="blue">v{{ item.version }}</a-tag>
                  </div>
                </template>
                <template #description>
                  <a-space size="small">
                    <a-tag :color="statusColor(item.status)">{{ statusLabel(item.status) }}</a-tag>
                    <a-tag>{{ item.priority }}</a-tag>
                  </a-space>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
          <template v-if="!cases.length && !loading" #footer>
            <a-empty description="暂无用例" />
          </template>
        </a-list>
      </a-tab-pane>
    </a-tabs>

    <!-- 新建用例弹窗 -->
    <a-modal v-model:open="createOpen" title="新建用例" width="480" @ok="create">
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-select v-model:value="form.priority" :options="priorityOptions" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 工作台左栏：用例目录树 + 用例列表（支持关键字与状态筛选）与新建用例入口。
 *
 * <p>交互：选中目录自动切到"列表"页签并重新加载；点击用例通过 emit('open') 交给父组件
 * （Workbench）载入右侧编辑器。目录树用 VirtualTree 渲染，避免大量节点卡顿。</p>
 */
import { onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined, FolderAddOutlined } from '@ant-design/icons-vue';
import { CaseApi, ModuleApi } from '@/api';
import {
  CASE_STATUS_FILTER_OPTIONS,
  PRIORITY_OPTIONS,
  caseStatusColor,
  caseStatusLabel,
} from '@/constants';
import type { CaseInfo, ModuleNode } from '@/types';
import VirtualTree from '@/components/VirtualTree.vue';

const props = defineProps<{
  projectId: string;
  currentCaseId?: string;
}>();

const emit = defineEmits<{
  (e: 'open', c: CaseInfo): void;
}>();

const keyword = ref('');
/** 状态筛选：all 表示不过滤 */
const statusFilter = ref<string>('all');
/** 左栏页签：tree=目录树 / list=用例列表 */
const activeTab = ref<'tree' | 'list'>('list');

const moduleTree = ref<ModuleNode[]>([]);
/** 当前选中的目录节点 key；空串表示全部 */
const selectedModuleKey = ref<string>('');

const cases = ref<CaseInfo[]>([]);
const loading = ref(false);

const createOpen = ref(false);
/** 新建用例表单 */
const form = reactive<Partial<CaseInfo>>({
  name: '',
  priority: 'P2',
  description: '',
});

const statusOptions = [{ label: '全部', value: 'all' }, ...CASE_STATUS_FILTER_OPTIONS];
const priorityOptions = PRIORITY_OPTIONS;

function statusLabel(s: string | undefined) {
  return caseStatusLabel(s);
}

function statusColor(s: string | undefined) {
  return caseStatusColor(s);
}

/** 加载用例目录树 */
async function loadModules() {
  if (!props.projectId) return;
  moduleTree.value = await ModuleApi.tree(props.projectId, 'case');
}

/** 按关键字 + 目录 + 状态重新拉取用例列表 */
async function reloadCases() {
  if (!props.projectId) return;
  loading.value = true;
  try {
    const params: any = { projectId: props.projectId, keyword: keyword.value || undefined };
    if (selectedModuleKey.value) params.moduleId = selectedModuleKey.value;
    if (statusFilter.value && statusFilter.value !== 'all') params.status = statusFilter.value;
    cases.value = await CaseApi.list(params);
  } finally {
    loading.value = false;
  }
}

function onModuleSelect(key: string) {
  selectedModuleKey.value = key;
  activeTab.value = 'list';
  reloadCases();
}

/** 在当前选中目录下新建子模块（未选中则建在根级） */
async function createModule() {
  const name = prompt('模块名称');
  if (!name) return;
  await ModuleApi.create({
    projectId: props.projectId,
    parentId: selectedModuleKey.value || null,
    name,
    type: 'case',
    sortOrder: 0,
  });
  await loadModules();
  message.success('已创建模块');
}

function openCreate() {
  form.name = '';
  form.priority = 'P2';
  form.description = '';
  createOpen.value = true;
}

/** 新建用例：固定初始状态为 draft，步骤为空数组，保存后由用户继续编排 */
async function create() {
  if (!form.name?.trim()) {
    message.warning('用例名称不能为空');
    return;
  }
  await CaseApi.create({
    projectId: props.projectId,
    moduleId: selectedModuleKey.value || null,
    name: form.name,
    priority: form.priority,
    description: form.description,
    status: 'draft',
    tags: [],
    steps: [],
  } as Partial<CaseInfo>);
  createOpen.value = false;
  await reloadCases();
  message.success('用例已创建');
}

watch(
  () => props.projectId,
  () => {
    selectedModuleKey.value = '';
    loadModules();
    reloadCases();
  },
  { immediate: false },
);

onMounted(async () => {
  await loadModules();
  await reloadCases();
});
</script>

<style scoped>
.workbench-left {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.left-toolbar {
  display: flex;
  gap: var(--sp-2);
  padding: var(--sp-3);
  border-bottom: 1px solid var(--bd-subtle);
  background: var(--pane-bg);
}

.left-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.left-tabs :deep(.ant-tabs-nav) {
  margin: 0;
  padding: 0 var(--sp-3);
  border-bottom: 1px solid var(--bd-subtle);
}

.left-tabs :deep(.ant-tabs-content-holder) {
  flex: 1;
  overflow: auto;
}

.tree-wrap {
  padding: var(--sp-2);
}

.tree-actions {
  margin-top: var(--sp-2);
}

.case-list {
  overflow: auto;
}

/* 用例条目：紧凑、hover 与选中态明确 */
.case-list :deep(.ant-list-item) {
  padding: var(--sp-3);
  border-radius: var(--rd-md);
  border-block-end: none;
  cursor: pointer;
  transition: background var(--dur-fast) var(--ease);
}

.case-list :deep(.ant-list-item:hover) {
  background: var(--bg-hover);
}

.case-list :deep(.ant-list-item.active) {
  background: var(--c-primary-bg);
  box-shadow: inset 2px 0 0 var(--c-primary);
}

.case-item__title {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  min-width: 0;
}

.case-name {
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--tx-1);
  flex: 1;
  min-width: 0;
}

.case-list :deep(.ant-list-item-meta-description) {
  margin-top: var(--sp-1);
}
</style>
