<template>
  <div class="assertion-editor">
    <a-empty v-if="!assertions.length" description="尚未配置断言" />
    <draggable v-else v-model="assertions" item-key="_id" :animation="180" handle=".drag-handle">
      <template #item="{ element: a, index: idx }">
        <a-card size="small" style="margin-bottom: 8px">
          <a-row :gutter="8" align="middle">
            <a-col flex="32px">
              <drag-outlined class="drag-handle" style="cursor: grab; color: #aaa" />
            </a-col>
            <a-col flex="120px">
              <a-select v-model:value="a.source" :options="sourceOptions" />
            </a-col>
            <a-col flex="160px">
              <a-input v-model:value="a.property" placeholder="属性（如 $.data.id）" />
            </a-col>
            <a-col flex="140px">
              <a-select v-model:value="a.operator" :options="operatorOptions" />
            </a-col>
            <a-col flex="auto">
              <a-input v-model:value="a.expected" placeholder="期望值" />
            </a-col>
            <a-col flex="80px">
              <a-button danger size="small" @click="remove(idx)">删除</a-button>
            </a-col>
          </a-row>
        </a-card>
      </template>
    </draggable>
    <a-button block type="dashed" style="margin-top: 8px" @click="add">
      <plus-outlined /> 新增断言
    </a-button>
  </div>
</template>

<script setup lang="ts">
import draggable from 'vuedraggable'
import { PlusOutlined, DragOutlined } from '@ant-design/icons-vue'
import { uid, ensureIds } from '@/utils/uid'
import { watch } from 'vue'
import type { AssertionResult } from '@/types'

const props = defineProps<{ modelValue: AssertionResult[] }>()
const emit = defineEmits<{ 'update:modelValue': [v: AssertionResult[]] }>()

const assertions = defineModel<AssertionResult[]>({ required: true })

// 从后端加载的旧数据可能没有 _id，补齐以支撑拖拽排序
watch(assertions, (list) => ensureIds(list), { immediate: true, deep: true })

const sourceOptions = [
  { value: 'status', label: '响应状态码' },
  { value: 'header', label: '响应头' },
  { value: 'body', label: '响应体' },
  { value: 'jsonpath', label: 'JSONPath' },
  { value: 'responsetime', label: '响应时间 (ms)' }
]

const operatorOptions = [
  { value: 'equals', label: '等于' },
  { value: 'not_equals', label: '不等于' },
  { value: 'contains', label: '包含' },
  { value: 'not_contains', label: '不包含' },
  { value: 'regex', label: '正则' },
  { value: 'gt', label: '>' },
  { value: 'gte', label: '>=' },
  { value: 'lt', label: '<' },
  { value: 'lte', label: '<=' },
  { value: 'empty', label: '为空' },
  { value: 'not_empty', label: '不为空' }
]

function add() {
  assertions.value.push({
    _id: uid(),
    source: 'status',
    operator: 'equals',
    expected: '200',
    passed: true,
    message: ''
  } as AssertionResult)
}

function remove(idx: number) {
  assertions.value.splice(idx, 1)
}
</script>

<style scoped>
.assertion-editor :deep(.ant-card-body) {
  padding: var(--sp-3) var(--sp-4);
}
</style>