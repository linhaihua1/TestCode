<template>
  <div ref="container" class="monaco-container" :style="{ height }"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    language?: string
    height?: string
    readOnly?: boolean
    theme?: 'vs-dark' | 'vs' | 'hc-black'
  }>(),
  {
    modelValue: '',
    language: 'javascript',
    height: '240px',
    readOnly: false,
    theme: 'vs'
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const container = ref<HTMLElement | null>(null)
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(() => {
  if (!container.value) return
  editor = monaco.editor.create(container.value, {
    value: props.modelValue ?? '',
    language: props.language,
    theme: props.theme,
    readOnly: props.readOnly,
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbers: 'on',
    scrollBeyondLastLine: false,
    wordWrap: 'on'
  })
  editor.onDidChangeModelContent(() => {
    if (editor) {
      emit('update:modelValue', editor.getValue())
    }
  })
})

watch(
  () => props.modelValue,
  (val) => {
    if (editor && val !== editor.getValue()) {
      editor.setValue(val ?? '')
    }
  }
)

watch(
  () => props.language,
  (lang) => {
    if (editor) {
      monaco.editor.setModelLanguage(editor.getModel()!, lang)
    }
  }
)

onBeforeUnmount(() => {
  editor?.dispose()
  editor = null
})
</script>

<style scoped>
.monaco-container {
  width: 100%;
  border: 1px solid var(--bd-base);
  border-radius: var(--rd-md);
  overflow: hidden;
}
</style>