import { Layout, Menu } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'

const { Sider, Content } = Layout

export default function AppLayout() {
  const { projectId } = useParams()
  const navigate = useNavigate()
  const location = useLocation()

  const items: MenuProps['items'] = [
    { key: '/projects', label: '项目列表' },
    { key: `/projects/${projectId}/apis`, label: '接口管理', disabled: !projectId },
    { key: `/projects/${projectId}/scenarios`, label: '场景自动化', disabled: !projectId },
    { key: `/projects/${projectId}/environments`, label: '环境管理', disabled: !projectId },
    { key: `/projects/${projectId}/reports`, label: '测试报告', disabled: !projectId },
  ]

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
