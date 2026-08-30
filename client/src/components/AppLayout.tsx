/**
 * 应用整体布局组件
 *
 * 职责：提供统一的页面框架——顶部栏（当前用户下拉菜单：修改密码/退出登录）+ 左侧导航菜单 + 右侧内容区。
 * 菜单项会根据当前 URL 中的 projectId 动态拼接，并通过 Outlet 渲染子路由页面。
 */
import { useState } from 'react'
import { Dropdown, Form, Input, Layout, Menu, Modal, message } from 'antd'
import type { MenuProps } from 'antd'
import { Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'
import { clearAuth, getCurrentUser } from '../api/auth'
import { api, getErrorMessage } from '../api/client'

// 解构出布局用到的侧边栏、顶部栏与内容区组件
const { Sider, Header, Content } = Layout

export default function AppLayout() {
  const { projectId } = useParams() // 从路由中读取当前项目 ID
  const navigate = useNavigate() // 用于菜单点击后跳转
  const location = useLocation() // 用于根据当前路径高亮菜单
  const user = getCurrentUser() // 当前登录用户

  // 修改密码弹窗状态
  const [pwdOpen, setPwdOpen] = useState(false)
  const [pwdForm] = Form.useForm()

  // 退出登录：清除本地登录态并跳转登录页
  const handleLogout = () => {
    clearAuth()
    navigate('/login')
  }

  // 提交修改密码
  const handleChangePassword = async () => {
    const values = await pwdForm.validateFields()
    try {
      await api.changePassword(values.oldPassword, values.newPassword)
      message.success('密码已修改，请重新登录')
      setPwdOpen(false)
      handleLogout() // 修改后需重新登录
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 右上角用户下拉菜单项
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

  // 侧边栏菜单项；未进入具体项目时，项目相关的菜单置灰
  // 侧边栏菜单项：接口自动化与 UI 自动化分成两个独立分组，互不干扰
  const items: MenuProps['items'] = [
    { key: '/projects', label: '项目列表' },
    {
      type: 'group',
      label: '接口自动化',
      children: [
        { key: `/projects/${projectId}/apis`, label: '接口管理', disabled: !projectId },
        { key: `/projects/${projectId}/scenarios`, label: '用例执行', disabled: !projectId },
        { key: `/projects/${projectId}/environments`, label: '环境管理', disabled: !projectId },
        { key: `/projects/${projectId}/reports`, label: '接口测试报告', disabled: !projectId },
      ],
    },
    {
      type: 'group',
      label: 'UI 自动化',
      children: [
        { key: `/projects/${projectId}/ui-tests`, label: 'UI 测试用例', disabled: !projectId },
        { key: `/projects/${projectId}/ui-reports`, label: 'UI 测试报告', disabled: !projectId },
      ],
    },
    { key: '/users', label: '用户管理' },
  ]

  // 从所有菜单项（含分组内的子项）中收集 key，用于根据当前路径高亮
  const allKeys: string[] = []
  for (const item of items) {
    if (item && 'children' in item && Array.isArray(item.children)) {
      for (const child of item.children) {
        if (child?.key) allKeys.push(String(child.key))
      }
    } else if (item?.key) {
      allKeys.push(String(item.key))
    }
  }
  const selectedKey = allKeys.find((key) => location.pathname.startsWith(key))

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
        {/* 顶部栏：当前用户下拉菜单（修改密码/退出登录） */}
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <Dropdown menu={{ items: userMenuItems, onClick: onUserMenuClick }}>
            <span style={{ cursor: 'pointer', color: '#333' }}>{user?.username ?? ''}</span>
          </Dropdown>
        </Header>
        <Content style={{ padding: 24, overflow: 'auto' }}>
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
          <Form.Item
            name="oldPassword"
            label="旧密码"
            rules={[{ required: true, message: '请输入旧密码' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, message: '请输入新密码' }]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  )
}
