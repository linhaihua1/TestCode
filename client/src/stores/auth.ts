/**
 * 认证状态（Pinia）：保存 token 与当前用户，提供登录、登出与角色判断。
 *
 * <p>token 持久化到 localStorage，请求头由 api/client 统一附加；
 * isAdmin / isViewer 等判断用于菜单与各页面的写权限控制。</p>
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { AuthApi } from '@/api'
import { ROLES, ROLE_LABELS } from '@/constants'
import type { User } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('')
  const user = ref<User | null>(null)

  function restore() {
    const t = localStorage.getItem('token')
    const u = localStorage.getItem('user')
    if (t) {
      token.value = t
    }
    if (u) {
      try {
        user.value = JSON.parse(u)
      } catch {
        // ignore
      }
    }
  }

  async function login(username: string, password: string) {
    const data = await AuthApi.login(username, password)
    token.value = data.token
    user.value = data.user
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data.user))
    return data.user
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  function isAdmin() {
    return user.value?.role === ROLES.ADMIN
  }

  /** 是否为成员（可操作接口/UI/性能/环境，但不能进管理页） */
  function isMember() {
    return user.value?.role === ROLES.MEMBER
  }

  /** 是否为查看者（只读，禁止一切操作） */
  function isViewer() {
    return user.value?.role === ROLES.VIEWER
  }

  /** 当前角色名（用于展示） */
  const roleLabel = computed(() => {
    const code = user.value?.role
    return code ? (ROLE_LABELS[code] ?? '') : ''
  })

  return { token, user, restore, login, logout, isAdmin, isMember, isViewer, roleLabel }
})