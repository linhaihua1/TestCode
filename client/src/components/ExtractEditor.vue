<template>
  <div class="extract-editor">
    <a-empty v-if="!extracts.length" description="尚未配置提取" />
    <draggable v-else v-model="extracts" item-key="_id" :animation="180" handle=".drag-handle">
      <template #item="{ element: e, index: idx }">
        <a-card size="small" style="margin-bottom: 8px">
          <a-row :gutter="8" align="middle">
            <a-col flex="32px">
              <drag-outlined class="drag-handle" style="cursor: grab; color: #aaa" />
            </a-col>
            <a-col flex="120px">
              <a-select v-model:value="e.type" :options="typeOptions" />
            </a-col>
            <a-col flex="auto">
              <a-input v-model:value="e.expression" placeholder="表达式（如 $.data.token）" />
            </a-col>
            <a-col flex="200px">
              <a-input v-model:value="e.variable" placeholder="变量名（供后续引用）" />
            </a-col>
            <a-col flex="80px">
              <a-button danger size="small" @click="remove(idx)">删除</a-button>
            </a-col>
          </a-row>
        </a-card>
      </template>
    </draggable>
    <a-button block type="dashed" style="margin-top: 8px" @click="add">
      <plus-outlined /> 新增提取
    </a-button>
  </div>
</template>

<script setup lang="ts">
/**
 * 变量提取编辑组件：配置"提取方式 + 表达式 + 保存到的变量名"，支持拖拽排序。
 *
 * <p>提取方式（JSONPath / 正则 / Header / Cookie / XPath）取自 constants，
 * 与后端 ExtractType 枚举一一对应。</p>
 */
import draggable from 'vuedraggable'
import { watch } from 'vue'
import { PlusOutlined, DragOutlined } from '@ant-design/icons-vue'
import { uid, ensureIds } from '@/utils/uid'
import { EXTRACT_TYPE_OPTIONS, EXTRACT_TYPES } from '@/constants'
import type { ExtractRule } from '@/types'

const extracts = defineModel<ExtractRule[]>({ required: true })

// 从后端加载的旧数据可能没有 _id，补齐以支撑拖拽排序
watch(extracts, (list) => ensureIds(list), { immediate: true, deep: true })

/** 提取方式选项：与后端 ExtractType 枚举一一对应，不再局部硬编码 */
const typeOptions = EXTRACT_TYPE_OPTIONS

function add() {
  extracts.value.push({
    _id: uid(),
    type: EXTRACT_TYPES.JSONPATH,
    expression: '',
    variable: ''
  })
}

function remove(idx: number) {
  extracts.value.splice(idx, 1)
}
</script>

<style scoped>
.extract-editor :deep(.ant-card-body) {
  padding: var(--sp-3) var(--sp-4);
}
</style>