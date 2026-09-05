/**
 * 项目列表页
 *
 * 职责：展示所有项目，支持新建、编辑、删除项目，
 * 并通过「进入」按钮跳转到指定项目的接口管理页。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { Project } from '../api/types'

export default function ProjectList() {
  const [projects, setProjects] = useState<Project[]>([]) // 项目列表数据
  const [loading, setLoading] = useState(false) // 表格加载状态
  const [open, setOpen] = useState(false) // 新建/编辑弹窗是否打开
  const [editing, setEditing] = useState<Project | null>(null) // 正在编辑的项目（null 表示新建）
  const [form] = Form.useForm() // 弹窗表单实例
  const navigate = useNavigate() // 路由跳转
  const { setProjectId } = useProject()

  // 加载项目列表
  const load = async () => {
    setLoading(true)
    try {
      setProjects(await api.listProjects())
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // 组件挂载时拉取项目列表
  useEffect(() => {
    load()
  }, [])

  // 处理新建/编辑弹窗提交
  const handleSubmit = async () => {
    const values = await form.validateFields()
    try {
      // 有编辑对象则更新，否则新建
      if (editing) await api.updateProject(editing.id, values)
      else await api.createProject(values)
      message.success('保存成功')
      setOpen(false)
      form.resetFields()
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除项目
  const handleDelete = async (id: string) => {
    try {
      await api.deleteProject(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 表格列定义
  const columns: ColumnsType<Project> = [
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description' },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      // 将 ISO 时间字符串格式化为本地可读时间
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          {/* 进入该项目：设置全局项目并跳转到接口自动化工作台 */}
          <Button
            size="small"
            type="link"
            onClick={() => {
              setProjectId(record.id)
              navigate('/workbench')
            }}
          >
            进入
          </Button>
          {/* 编辑：回填表单并打开弹窗 */}
          <Button
            size="small"
            type="link"
            onClick={() => {
              setEditing(record)
              form.setFieldsValue(record)
              setOpen(true)
            }}
          >
            编辑
          </Button>
          {/* 删除：带二次确认 */}
          <Popconfirm title="确认删除该项目？" onConfirm={() => handleDelete(record.id)}>
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
      title="项目列表"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            setOpen(true)
          }}
        >
          新建项目
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={projects} />
      <Modal
        title={editing ? '编辑项目' : '新建项目'}
        open={open}
        onOk={handleSubmit}
        onCancel={() => setOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
