/**
 * 路由守卫：未登录时重定向到登录页，已登录则渲染子路由。
 */
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { isAuthenticated } from '../api/auth'

export default function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation()

  // 未登录：跳转登录页，并记录来源路径以便登录后返回
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <>{children}</>
}
