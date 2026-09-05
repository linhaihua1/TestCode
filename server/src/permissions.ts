/**
 * 角色权限助手（RBAC）。
 * 角色：admin（管理员）/ member（成员）/ viewer（只读）。
 * admin 拥有全部权限；member 可读写业务数据；viewer 只读。
 */

export type Role = 'admin' | 'member' | 'viewer'

export interface AuthedUser {
  userId: string
  username: string
  role?: string
}

/** 判断用户是否拥有指定角色之一（admin 视为拥有全部角色） */
export function hasRole(user: AuthedUser | undefined | null, ...roles: Role[]): boolean {
  if (!user) return false
  const r = (user.role ?? 'viewer') as Role
  if (r === 'admin') return true
  return roles.includes(r)
}

/** 是否管理员 */
export function isAdmin(user: AuthedUser | undefined | null): boolean {
  return user?.role === 'admin'
}
