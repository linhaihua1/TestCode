/**
 * 应用整体布局组件
 *
 * 职责：提供统一的页面框架——左侧导航（四个入口：接口自动化 / UI 自动化 / 环境配置 / 用户管理）+
 * 顶部栏（全局项目选择器 + 项目管理 + 当前用户下拉）+ 右侧内容区。
 */
import { useState } from 'react'
import { Dropdown, Form, Input, Layout, Menu, Modal, Select, message } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { clearAuth, getCurrentUser } from '../api/auth'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'

const { Sider, Header, Content } = Layout

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getCurrentUser()
  const { projects, projectId, setProjectId } = useProject()

  const [pwdOpen, setPwdOpen] = useState(false)
  const [pwdForm] = Form.useForm()

  const handleLogout = () => {
    clearAuth()
    navigate('/login')
  }

  const handleChangePassword = async () => {
    const values = await pwdForm.validateFields()
    try {
      await api.changePassword(values.oldPassword, values.newPassword)
      message.success('密码已修改，请重新登录')
      setPwdOpen(false)
      handleLogout()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const userMenuItems: MenuProps['items'] = [
    { key: 'password', label: '修改密码' },
    { type: 'divider' },
    { key: 'logout', label: '退出登录' },
  ]

  const onUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') handleLogout()
    if (key === 'password') {
      pwdForm.resetFields()
      setPwdOpen(true)
    }
  }

  // 侧边栏菜单：四个顶层入口
  const items: MenuProps['items'] = [
    {
      key: 'api-automation',
      label: '接口自动化',
      children: [
        {
          key: 'api-case',
          label: '接口用例',
          children: [
            { key: '/workbench', label: '工作台' },
          ],
        },
        { key: '/test-execution', label: '测试执行' },
        { key: '/test-reports', label: '测试报告' },
      ],
    },
    {
      key: 'ui-automation',
      label: 'UI 自动化',
      children: [
        { key: '/ui-tests', label: 'UI 用例' },
        { key: '/ui-scenarios', label: 'UI 用例执行' },
        { key: '/ui-reports', label: 'UI 测试报告' },
      ],
    },
    { key: '/environments', label: '环境配置' },
    { key: '/users', label: '用户管理' },
  ]

  // 递归收集所有叶子路由 key（以 / 开头），用于最长前缀高亮
  const allKeys: string[] = []
  const collectKeys = (list: MenuProps['items']) => {
    for (const item of list ?? []) {
      if (item && 'children' in item && Array.isArray(item.children)) {
        collectKeys(item.children)
      } else if (item?.key) {
        allKeys.push(String(item.key))
      }
    }
  }
  collectKeys(items)
  const selectedKey = allKeys
    .filter((key) => location.pathname === key || location.pathname.startsWith(key + '/'))
    .sort((a, b) => b.length - a.length)[0]

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
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
          defaultOpenKeys={['api-automation', 'api-case', 'ui-automation']}
          selectedKeys={selectedKey ? [selectedKey] : []}
          items={items}
          onClick={({ key }) => {
            if (key.startsWith('/')) navigate(key)
          }}
        />
      </Sider>
      <Layout style={{ display: 'flex', flexDirection: 'column' }}>
        <Header
          style={{
            background: '#fff',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            borderBottom: '1px solid #f0f0f0',
            height: 48,
            lineHeight: '48px',
          }}
        >
          <span style={{ color: '#999', fontSize: 13 }}>当前项目</span>
          <Select
            style={{ width: 200 }}
            size="small"
            placeholder="选择项目"
            value={projectId}
            options={projects.map((p) => ({ value: p.id, label: p.name }))}
            onChange={(v) => setProjectId(v)}
          />
          <span
            style={{ color: '#1677ff', fontSize: 13, cursor: 'pointer' }}
            onClick={() => navigate('/projects')}
          >
            项目管理
          </span>
          <div style={{ flex: 1 }} />
          <Dropdown menu={{ items: userMenuItems, onClick: onUserMenuClick }}>
            <span style={{ cursor: 'pointer', color: '#333' }}>{user?.username ?? ''}</span>
          </Dropdown>
        </Header>
        <Content style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <Outlet />
        </Content>
      </Layout>

      {/* 修改密码弹窗 */}
      <Modal
        title="修改密码"
        open={pwdOpen}
        onOk={handleChangePassword}
        onCancel={() => setPwdOpen(false)}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical">
          <Form.Item name="oldPassword" label="旧密码" rules={[{ required: true, message: '请输入旧密码' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  )
}
