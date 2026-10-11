<template>
  <a-tree
    :tree-data="filteredTree"
    :selected-keys="selectedKeys"
    :expanded-keys="expandedKeys"
    block-node
    show-line
    :height="height"
    :default-expand-all="defaultExpandAll"
    :filter-tree-node="filterFn"
    @select="onSelect"
    @expand="onExpand"
  >
    <template #title="{ title, key }">
      <a-space>
        <component :is="iconFor(key)" />
        <span>{{ title }}</span>
        <a-tag v-if="action" size="small" color="blue">{{ action }}</a-tag>
      </a-space>
    </template>
  </a-tree>
</template>

<script setup lang="ts">
/**
 * 模块目录树组件：基于 Ant Design Vue 目录树，支持关键字过滤与按类型显示图标。
 *
 * <p>过滤采用"命中节点及其祖先保留"的策略，避免父节点被过滤掉后子节点无处挂载；
 * 树数据由父组件按 type（case/api/ui/perf）加载后传入。</p>
 */
import { computed, ref, watch } from 'vue';
import {
  FolderOutlined,
  AppstoreOutlined,
  CodeOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue';
import type { ModuleNode } from '@/types';
import { filterTreeDeeply } from '@/utils/treeFilter';

const props = withDefaults(
  defineProps<{
    tree: ModuleNode[];
    selectedKey?: string;
    keyword?: string;
    height?: number;
    defaultExpandAll?: boolean;
    showActionTag?: boolean;
  }>(),
  {
    height: 480,
    defaultExpandAll: false,
  },
);

const emit = defineEmits<{
  select: [key: string, node: ModuleNode];
}>();

const selectedKeys = computed(() => (props.selectedKey ? [props.selectedKey] : []));
const expandedKeys = ref<string[]>([]);

watch(
  () => props.tree,
  (t) => {
    // 默认展开第一层
    expandedKeys.value = t.map((n) => n.key);
  },
  { immediate: true },
);

/**
 * 命中关键词的节点及其所有祖先保持可见。
 */
function filterFn(searchValue: string) {
  return (node: any) => {
    if (!searchValue) return true;
    return pathContains(node, searchValue);
  };
}

function pathContains(node: any, keyword: string): boolean {
  let cur = node;
  while (cur) {
    if (
      String(cur.title ?? '')
        .toLowerCase()
        .includes(keyword.toLowerCase())
    ) {
      return true;
    }
    cur = cur.parent ?? null;
  }
  return false;
}

const filteredTree = computed(() => {
  if (!props.keyword) return props.tree;
  return filterTreeDeeply(props.tree, props.keyword);
});

function iconFor(key: string) {
  const node = findNode(props.tree, key);
  if (!node) return FolderOutlined;
  return node.type === 'api'
    ? CodeOutlined
    : node.type === 'ui'
      ? RobotOutlined
      : node.type === 'perf'
        ? ThunderboltOutlined
        : AppstoreOutlined;
}

function findNode(tree: ModuleNode[], key: string): ModuleNode | null {
  for (const n of tree) {
    if (n.key === key) return n;
    const f = findNode(n.children || [], key);
    if (f) return f;
  }
  return null;
}

const action = computed(() => (props.showActionTag ? 'module' : ''));

function onSelect(keys: any, info: any) {
  if (keys.length > 0) {
    emit('select', keys[0], info.node);
  }
}

function onExpand(keys: any) {
  expandedKeys.value = keys;
}
</script>

<style scoped>
:deep(.ant-tree-node-content-wrapper) {
  white-space: nowrap;
}
</style>
