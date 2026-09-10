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
            v-if:value="record.value"
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
import { computed, ref, watch } from 'vue'
import type { KeyValueItem } from '@/types'

const props = defineProps<{
  modelValue: KeyValueItem[]
  keyLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: KeyValueItem[]]
}>()

const rows = ref<Array<KeyValueItem & { rowKey: string }>>(
  normalize(props.modelValue)
)

watch(
  () => props.modelValue,
  (val) => {
    rows.value = normalize(val ?? [])
  },
  { deep: true }
)

function normalize(list: KeyValueItem[]) {
  const base = list && list.length > 0 ? [...list] : [{ key: '', value: '', enabled: true }]
  return base.map((r, i) => ({ ...r, rowKey: String(i) + '-' + Math.random() }))
}

const columns = computed(() => [
  {
    title: '启用',
    key: 'enabled',
    width: 70
  },
  {
    title: props.keyLabel ?? 'Key',
    dataIndex: 'key',
    width: 200
  },
  {
    title: 'Value',
    key: 'value'
  },
  {
    title: '操作',
    key: 'action',
    width: 120
  }
])

function addRow() {
  rows.value.push({ key: '', value: '', enabled: true, rowKey: String(Date.now()) })
  emitChange()
}

function removeRow(rowKey: string) {
  rows.value = rows.value.filter((r) => r.rowKey !== rowKey)
  if (rows.value.length === 0) {
    rows.value.push({ key: '', value: '', enabled: true, rowKey: String(Date.now()) })
  }
  emitChange()
}

function emitChange() {
  emit(
    'update:modelValue',
    rows.value.map(({ key, value, enabled }) => ({
      key: key ?? '',
      value: value ?? '',
      enabled: enabled ?? true
    }))
  )
}
</script>

<style scoped>
.kv-editor {
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  background: var(--bg-card);
}
</style>