/**
 * 用户管理页：用户列表 + 新增 / 编辑 / 删除 / 重置密码。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../api/client'
import type { User } from '../api/types'

export default function UserManagement() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(false)

  // 新增/编辑弹窗
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [form] = Form.useForm()

  // 重置密码弹窗
  const [pwdOpen, setPwdOpen] = useState(false)
  const [pwdUser, setPwdUser] = useState<User | null>(null)
  const [pwdForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      setUsers(await api.listUsers())
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  // 新增或编辑用户
  const handleSubmit = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await api.updateUser(editing.id, { username: values.username, role: values.role })
      } else {
        await api.createUser({
          username: values.username,
          password: values.password,
          role: values.role,
        })
      }
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除用户
  const handleDelete = async (id: string) => {
    try {
      await api.deleteUser(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 重置密码
  const handleResetPassword = async () => {
    const values = await pwdForm.validateFields()
    try {
      await api.resetUserPassword(pwdUser!.id, values.newPassword)
      message.success('密码已重置')
      setPwdOpen(false)
      pwdForm.resetFields()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<User> = [
    { title: '用户名', dataIndex: 'username' },
    {
      title: '角色',
      dataIndex: 'role',
      render: (r: string) => {
        const color = r === 'admin' ? 'blue' : r === 'member' ? 'green' : 'default'
        const label = r === 'admin' ? '管理员' : r === 'member' ? '成员' : '查看者'
        return <Tag color={color}>{label}</Tag>
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => {
              setEditing(record)
              form.setFieldsValue({ username: record.username, role: record.role })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          <Button
            size="small"
            type="link"
            onClick={() => {
              setPwdUser(record)
              pwdForm.resetFields()
              setPwdOpen(true)
            }}
          >
            改密码
          </Button>
          <Popconfirm title="确认删除该用户？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card
      title="用户管理"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            setOpen(true)
          }}
        >
          新增用户
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={users} />

      {/* 新增/编辑用户 */}
      <Modal
        title={editing ? '编辑用户' : '新增用户'}
        open={open}
        onOk={handleSubmit}
        onCancel={() => setOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          {!editing && (
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="role" label="角色" initialValue="admin">
            <Select
              options={[
                { value: 'admin', label: '管理员' },
                { value: 'member', label: '成员' },
                { value: 'viewer', label: '查看者' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码 */}
      <Modal
        title={`重置密码：${pwdUser?.username ?? ''}`}
        open={pwdOpen}
        onOk={handleResetPassword}
        onCancel={() => setPwdOpen(false)}
        destroyOnClose
      >
        <Form form={pwdForm} layout="vertical">
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[{ required: true, message: '请输入新密码' }]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
