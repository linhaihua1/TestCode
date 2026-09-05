/**
 * 页面内容容器：为非全屏页面提供统一的内边距与滚动区域。
 * 全屏页（如接口自动化工作台）直接铺满 Content，不经过此容器。
 */
import { Outlet } from 'react-router-dom'

export default function PageContainer() {
  return (
    <div style={{ flex: 1, overflow: 'auto', padding: 24 }}>
      <Outlet />
    </div>
  )
}
