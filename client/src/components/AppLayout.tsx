/**
 * 应用整体布局组件
 *
 * 职责：提供统一的页面框架——左侧固定导航菜单 + 右侧内容区。
 * 菜单项会根据当前 URL 中的 projectId 动态拼接，并通过 Outlet 渲染子路由页面。
 */
import { Layout, Menu } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'

// 解构出布局用到的侧边栏与内容区组件
const { Sider, Content } = Layout

export default function AppLayout() {
  const { projectId } = useParams() // 从路由中读取当前项目 ID
  const navigate = useNavigate() // 用于菜单点击后跳转
  const location = useLocation() // 用于根据当前路径高亮菜单

  // 侧边栏菜单项；未进入具体项目时，项目相关的菜单置灰
  const items: MenuProps['items'] = [
    { key: '/projects', label: '项目列表' },
    { key: `/projects/${projectId}/apis`, label: '接口管理', disabled: !projectId },
    { key: `/projects/${projectId}/scenarios`, label: '场景自动化', disabled: !projectId },
    { key: `/projects/${projectId}/environments`, label: '环境管理', disabled: !projectId },
    { key: `/projects/${projectId}/reports`, label: '测试报告', disabled: !projectId },
  ]

  // 根据当前路径前缀匹配需要高亮的菜单项
  const selectedKey = items
    .map((i) => i?.key as string)
    .find((key) => location.pathname.startsWith(key))

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider theme="dark" width={200}>
        <div
          style={{
            color: '#fff',
            padding: '16px 16px 12px',
            fontWeight: 600,
            fontSize: 16,
            whiteSpace: 'nowrap',
          }}
        >
          API 自动化平台
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKey ? [selectedKey] : []}
          items={items}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Content style={{ padding: 24, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
