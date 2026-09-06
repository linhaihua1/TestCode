import { defineStore } from 'pinia'
import { ref } from 'vue'
import { AuthApi } from '@/api'
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
    return user.value?.role === 'admin'
  }

  return { token, user, restore, login, logout, isAdmin }
})