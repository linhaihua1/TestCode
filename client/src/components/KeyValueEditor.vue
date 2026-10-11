<template>
  <div class="kv-editor">
    <a-table
      :data-source="rows"
      :columns="columns"
      :pagination="false"
      size="small"
      row-key="rowKey"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'enabled'">
          <a-switch v-model:checked="record.enabled" size="small" />
        </template>
        <template v-else-if="column.key === 'value'">
          <a-textarea
            v-if="record.value"
            v-model:value="record.value"
            :auto-size="{ minRows: 1, maxRows: 4 }"
            placeholder="值（支持 {{var}} 占位符）"
          />
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button size="small" type="link" @click="addRow">新增</a-button>
            <a-button
              v-if="rows.length > 1"
              size="small"
              type="link"
              danger
              @click="removeRow(record.rowKey)"
            >
              删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
/**
 * 键值对编辑组件（请求头、Query 参数、环境变量通用），支持 v-model 双向绑定。
 *
 * <p>内部为每行生成 rowKey 供表格与动画使用，输出时剔除该字段；
 * 删除最后一行时自动补一个空行，保证用户始终能继续输入。</p>
 */
import { computed, ref, watch } from 'vue';
import type { KeyValueItem } from '@/types';
import { normalizeRows, appendEmptyRow, removeRowByKey, toKeyValueItems } from '@/utils/kvRows';

const props = defineProps<{
  modelValue: KeyValueItem[];
  keyLabel?: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: KeyValueItem[]];
}>();

const rows = ref(normalizeRows(props.modelValue));

watch(
  () => props.modelValue,
  (val) => {
    rows.value = normalizeRows(val ?? []);
  },
  { deep: true },
);

const columns = computed(() => [
  {
    title: '启用',
    key: 'enabled',
    width: 70,
  },
  {
    title: props.keyLabel ?? 'Key',
    dataIndex: 'key',
    width: 200,
  },
  {
    title: 'Value',
    key: 'value',
  },
  {
    title: '操作',
    key: 'action',
    width: 120,
  },
]);

function addRow() {
  rows.value = appendEmptyRow(rows.value);
  emitChange();
}

function removeRow(rowKey: string) {
  rows.value = removeRowByKey(rows.value, rowKey);
  emitChange();
}

function emitChange() {
  emit('update:modelValue', toKeyValueItems(rows.value));
}
</script>

<style scoped>
.kv-editor {
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  background: var(--bg-card);
}
</style>
