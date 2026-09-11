import type { Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 权限指令 v-can-write：
 * 查看者（viewer）角色下，自动禁用目标元素的交互，并附加 tooltip 提示。
 *
 * 用法：
 *   <a-button v-can-write type="primary" @click="create">新建</a-button>
 *   <a-button v-can-write @click="del">删除</a-button>
 *
 * 说明：
 * - admin / member 不受影响
 * - viewer：设置 disabled + aria-disabled，阻止点击；对非 button 元素则加禁用样式
 */
export const canWrite: Directive<HTMLElement> = {
  mounted(el) {
    const auth = useAuthStore()
    if (!auth.isViewer()) return
    applyDisabled(el, true)
  },
  updated(el) {
    const auth = useAuthStore()
    applyDisabled(el, auth.isViewer())
  }
}

function applyDisabled(el: HTMLElement, disabled: boolean) {
  const isBtn = el.tagName === 'BUTTON'
  if (isBtn) {
    ;(el as HTMLButtonElement).disabled = disabled
    el.setAttribute('aria-disabled', String(disabled))
  } else {
    // 非 button（如 div 包裹的可点击项）：加禁用样式 + 阻止事件
    el.style.pointerEvents = disabled ? 'none' : ''
    el.style.opacity = disabled ? '0.5' : ''
  }
  if (disabled) {
    el.title = '查看者无操作权限'
  }
}
