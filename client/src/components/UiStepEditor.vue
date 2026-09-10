<template>
  <div>
    <a-empty v-if="!steps.length" description="暂无步骤">
      <a-button type="primary" @click="addStep"><plus-outlined />新增步骤</a-button>
    </a-empty>

    <draggable v-else v-model="steps" item-key="_id" :animation="180" handle=".step-handle">
      <template #item="{ element: s, index: idx }">
        <a-card size="small" style="margin-bottom: 8px">
          <a-row :gutter="8" align="middle">
            <a-col flex="32px">
              <drag-outlined class="step-handle" style="cursor: grab; color: #aaa" />
            </a-col>
            <a-col flex="140px">
              <a-select v-model:value="s.action" :options="actionOptions" />
            </a-col>
            <a-col flex="180px">
              <a-select v-model:value="s.locator" :options="locatorOptions" placeholder="定位方式" allow-clear />
            </a-col>
            <a-col flex="auto">
              <a-input v-model:value="s.selector" placeholder="选择器" />
            </a-col>
            <a-col flex="200px">
              <a-input v-model:value="s.value" placeholder="值 / 期望" />
            </a-col>
            <a-col flex="80px">
              <a-button danger size="small" @click="steps.splice(idx, 1)">删除</a-button>
            </a-col>
          </a-row>
        </a-card>
      </template>
    </draggable>

    <a-button v-if="steps.length" block type="dashed" @click="addStep">
      <plus-outlined />新增步骤
    </a-button>
  </div>
</template>

<script setup lang="ts">
import draggable from 'vuedraggable'
import { watch } from 'vue'
import { PlusOutlined, DragOutlined } from '@ant-design/icons-vue'
import { uid, ensureIds } from '@/utils/uid'
import type { UiStep } from '@/types'

const steps = defineModel<UiStep[]>({ required: true })

// 从后端加载的旧数据可能没有 _id，补齐以支撑拖拽排序
watch(steps, (list) => ensureIds(list), { immediate: true, deep: true })

const actionOptions = [
  { value: 'open', label: '打开 URL' },
  { value: 'click', label: '点击' },
  { value: 'input', label: '输入' },
  { value: 'clear', label: '清空' },
  { value: 'submit', label: '提交表单' },
  { value: 'wait', label: '等待' },
  { value: 'assert_text', label: '断言文本' },
  { value: 'assert_title', label: '断言标题' },
  { value: 'screenshot', label: '截图' },
  { value: 'script', label: 'JS 脚本' }
]

const locatorOptions = [
  { value: 'id', label: 'ID' },
  { value: 'name', label: 'Name' },
  { value: 'css', label: 'CSS Selector' },
  { value: 'xpath', label: 'XPath' },
  { value: 'class', label: 'Class' },
  { value: 'tag', label: 'Tag' },
  { value: 'link_text', label: 'Link Text' },
  { value: 'partial_link_text', label: 'Partial Link' }
]

function addStep() {
  steps.value.push({
    _id: uid(),
    action: 'click',
    locator: 'css',
    selector: '',
    value: ''
  })
}
</script>