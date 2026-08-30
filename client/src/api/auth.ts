/**
 * 登录态管理：token 与用户信息的本地存取。
 * 使用 localStorage 持久化，刷新页面后登录态依然保留。
 */

const TOKEN_KEY = 'api-web-token'
const USER_KEY = 'api-web-user'

export interface AuthUser {
  id: string
  username: string
  role: string
}

/** 获取当前登录 token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 保存登录态（token + 用户信息） */
export function setAuth(token: string, user: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 清除登录态（退出登录 / 登录失效时调用） */
export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/** 获取当前登录用户信息 */
export function getCurrentUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

/** 是否已登录 */
export function isAuthenticated(): boolean {
  return getToken() !== null
}
