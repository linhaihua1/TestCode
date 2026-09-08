/**
 * 全局快捷键 hook（开发文档 §5.1.6）。
 *
 * <h3>支持的快捷键</h3>
 * <ul>
 *   <li>{@code Ctrl + S}：保存用例（在工作台触发保存事件）</li>
 *   <li>{@code Ctrl + Enter}：快速调试当前用例</li>
 *   <li>{@code Ctrl + F}：搜索当前面板内容</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 *   import { useShortcuts } from '@/hooks/useShortcuts'
 *   useShortcuts({
 *     onSave: () => { ... },
 *     onDebug: () => { ... },
 *     onSearch: () => { ... }
 *   })
 * </pre>
 *
 * <p>Mac 上 {@code Ctrl} 自动识别为 {@code Meta}（⌘）。
 */
import { onBeforeUnmount, onMounted } from 'vue'

export interface ShortcutOptions {
  onSave?: () => void
  onDebug?: () => void
  onSearch?: () => void
}

function isModKey(e: KeyboardEvent): boolean {
  // Mac: metaKey；Windows/Linux: ctrlKey
  return e.metaKey || e.ctrlKey
}

export function useShortcuts(opts: ShortcutOptions) {
  function handler(e: KeyboardEvent) {
    if (!isModKey(e)) {
      return
    }
    const key = e.key.toLowerCase()
    // Ctrl+S 保存
    if (key === 's' && !e.shiftKey) {
      e.preventDefault()
      opts.onSave?.()
      return
    }
    // Ctrl+Enter 调试
    if (key === 'enter') {
      e.preventDefault()
      opts.onDebug?.()
      return
    }
    // Ctrl+F 搜索
    if (key === 'f' && !e.shiftKey) {
      e.preventDefault()
      opts.onSearch?.()
      return
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handler)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('keydown', handler)
  })
}