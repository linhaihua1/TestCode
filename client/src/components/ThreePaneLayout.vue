<!--
  可拖拽三栏布局组件（开发文档 §5.1 + 需求文档 §1）。

  <h3>功能</h3>
  <ul>
    <li>三栏面板（左侧 / 中间 / 右侧）</li>
    <li>拖拽调整宽度（最小宽度：左 220px、右 280px）</li>
    <li>可折叠/展开，折叠后以 32px 窄条图标栏展示</li>
    <li>宽度记忆到 localStorage，刷新后保持</li>
  </ul>

  <h3>使用</h3>
  <pre>
    <ThreePaneLayout
      storage-key="workbench.pane-widths"
      left-title="用例库"
      middle-title="编写用例"
      right-title="接口管理"
    >
      <template #left>...</template>
      <template #middle>...</template>
      <template #right>...</template>
    </ThreePaneLayout>
  </pre>
-->
<template>
  <div class="three-pane-layout" :class="{ dragging }">
    <!-- 左侧面板 -->
    <aside class="pane pane-left" :class="{ 'pane--collapsed': widths.leftCollapsed }" :style="leftStyle">
      <div class="pane-header">
        <span v-if="widths.leftCollapsed" class="pane-icon">📁</span>
        <template v-else>
          <span class="pane-title">{{ leftTitle || '左侧' }}</span>
          <button class="pane-toggle" @click="toggleLeft" title="折叠">
            <LeftOutlined />
          </button>
        </template>
      </div>
      <div v-if="widths.leftCollapsed" class="pane-rail" @click="toggleLeft" title="展开">
        <span class="pane-icon">📁</span>
      </div>
      <div v-else class="pane-body">
        <slot name="left" />
      </div>
    </aside>

    <!-- 左侧拖拽手柄 -->
    <div
      v-if="!widths.leftCollapsed"
      class="pane-resizer"
      @mousedown="startDrag('left', $event)"
    />

    <!-- 中间面板 -->
    <main class="pane pane-middle" :style="middleStyle">
      <div class="pane-header pane-header--middle">
        <span class="pane-title">{{ middleTitle || '中间' }}</span>
      </div>
      <div class="pane-body">
        <slot name="middle" />
      </div>
    </main>

    <!-- 右侧拖拽手柄 -->
    <div
      v-if="!widths.rightCollapsed"
      class="pane-resizer"
      @mousedown="startDrag('right', $event)"
    />

    <!-- 右侧面板 -->
    <aside class="pane pane-right" :class="{ 'pane--collapsed': widths.rightCollapsed }" :style="rightStyle">
      <div class="pane-header">
        <span v-if="widths.rightCollapsed" class="pane-icon">📡</span>
        <template v-else>
          <span class="pane-title">{{ rightTitle || '右侧' }}</span>
          <button class="pane-toggle" @click="toggleRight" title="折叠">
            <RightOutlined />
          </button>
        </template>
      </div>
      <div v-if="widths.rightCollapsed" class="pane-rail" @click="toggleRight" title="展开">
        <span class="pane-icon">📡</span>
      </div>
      <div v-else class="pane-body">
        <slot name="right" />
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue'

interface PaneWidths {
  left: number
  right: number
  leftCollapsed: boolean
  rightCollapsed: boolean
}

const DEFAULTS: PaneWidths = {
  left: 300,
  right: 350,
  leftCollapsed: false,
  rightCollapsed: false
}

const MIN_LEFT = 220
const MIN_RIGHT = 280

const props = defineProps<{
  storageKey: string
  leftTitle?: string
  middleTitle?: string
  rightTitle?: string
}>()

const widths = ref<PaneWidths>({ ...DEFAULTS })
const dragging = ref<'' | 'left' | 'right'>('')

function loadWidths() {
  try {
    const raw = localStorage.getItem(props.storageKey)
    if (raw) {
      const parsed = JSON.parse(raw)
      widths.value = {
        left: typeof parsed.left === 'number' ? parsed.left : DEFAULTS.left,
        right: typeof parsed.right === 'number' ? parsed.right : DEFAULTS.right,
        leftCollapsed: !!parsed.leftCollapsed,
        rightCollapsed: !!parsed.rightCollapsed
      }
    }
  } catch (e) {
    console.warn('[ThreePaneLayout] 读取 localStorage 失败', e)
  }
}

function persistWidths() {
  try {
    localStorage.setItem(props.storageKey, JSON.stringify(widths.value))
  } catch (e) {
    console.warn('[ThreePaneLayout] 写入 localStorage 失败', e)
  }
}

onMounted(loadWidths)
watch(widths, persistWidths, { deep: true })

const leftStyle = computed(() => ({
  width: widths.value.leftCollapsed ? '32px' : `${widths.value.left}px`,
  flex: '0 0 auto'
}))

const rightStyle = computed(() => ({
  width: widths.value.rightCollapsed ? '32px' : `${widths.value.right}px`,
  flex: '0 0 auto'
}))

const middleStyle = computed(() => ({
  flex: '1 1 auto',
  minWidth: '400px'
}))

function startDrag(side: 'left' | 'right', e: MouseEvent) {
  dragging.value = side
  e.preventDefault()
  const startX = e.clientX
  const startLeft = widths.value.left
  const startRight = widths.value.right

  function onMove(ev: MouseEvent) {
    const dx = ev.clientX - startX
    if (side === 'left') {
      widths.value.left = Math.max(MIN_LEFT, startLeft + dx)
    } else {
      widths.value.right = Math.max(MIN_RIGHT, startRight - dx)
    }
  }
  function onUp() {
    dragging.value = ''
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function toggleLeft() {
  widths.value = { ...widths.value, leftCollapsed: !widths.value.leftCollapsed }
}
function toggleRight() {
  widths.value = { ...widths.value, rightCollapsed: !widths.value.rightCollapsed }
}
</script>

<style scoped>
.three-pane-layout {
  display: flex;
  height: 100%;
  width: 100%;
  overflow: hidden;
  background: var(--bg-page);
  border-radius: var(--rd-lg);
  border: 1px solid var(--bd-base);
  box-shadow: var(--sd-sm);
}

.three-pane-layout.dragging {
  cursor: col-resize;
  user-select: none;
}

.pane {
  display: flex;
  flex-direction: column;
  background: var(--pane-bg);
  overflow: hidden;
}

/* 面板头部：统一高度与浅底 */
.pane-header {
  height: var(--pane-header-h);
  flex: 0 0 var(--pane-header-h);
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 0 var(--sp-3);
  border-bottom: 1px solid var(--bd-subtle);
  background: var(--pane-header-bg);
}

.pane-header--middle {
  background: var(--pane-bg);
}

.pane-title {
  flex: 1;
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--tx-2);
  letter-spacing: 0.2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pane-icon {
  font-size: 14px;
  line-height: 1;
}

.pane-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  padding: 0;
  font-size: 11px;
  color: var(--tx-4);
  background: transparent;
  border: none;
  border-radius: var(--rd-sm);
  cursor: pointer;
  transition: color var(--dur-fast) var(--ease),
              background var(--dur-fast) var(--ease);
}

.pane-toggle:hover {
  color: var(--c-primary);
  background: var(--c-primary-bg);
}

/* 折叠态：窄条 + 竖排提示 */
.pane--collapsed .pane-header {
  padding: 0;
  justify-content: center;
}

.pane-rail {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: var(--sp-3);
  cursor: pointer;
  background: var(--pane-header-bg);
  transition: background var(--dur-fast) var(--ease);
}

.pane-rail:hover {
  background: var(--c-primary-bg);
}

.pane-body {
  flex: 1;
  overflow: auto;
  min-height: 0;
}

/* 拖拽手柄：默认隐形，hover/拖拽时显色 */
.pane-resizer {
  width: 5px;
  flex: 0 0 5px;
  background: transparent;
  cursor: col-resize;
  position: relative;
  transition: background var(--dur-fast) var(--ease);
}

.pane-resizer::after {
  content: '';
  position: absolute;
  inset: 0 2px;
  background: transparent;
  transition: background var(--dur-fast) var(--ease);
}

.pane-resizer:hover::after,
.three-pane-layout.dragging .pane-resizer::after {
  background: var(--c-primary);
}
</style>