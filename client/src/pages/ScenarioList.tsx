/**
 * 场景列表页
 *
 * 职责：展示指定项目下的自动化场景，支持新建、编辑、删除场景，
 * 并通过「编排」按钮进入场景编排与执行页。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { Scenario } from '../api/types'

export default function ScenarioList() {
  const { projectId } = useParams<{ projectId: string }>() // 当前项目 ID
  const [scenarios, setScenarios] = useState<Scenario[]>([]) // 场景列表数据
  const [loading, setLoading] = useState(false) // 表格加载状态
  const [open, setOpen] = useState(false) // 新建/编辑弹窗是否打开
  const [editing, setEditing] = useState<Scenario | null>(null) // 正在编辑的场景（null 表示新建）
  const [form] = Form.useForm() // 弹窗表单实例
  const navigate = useNavigate() // 路由跳转

  // 加载当前项目下的场景列表
  const load = async () => {
    setLoading(true)
    try {
      setScenarios(await api.listScenarios(projectId!))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // 项目切换时重新拉取场景列表
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // 处理新建/编辑弹窗提交
  const handleSubmit = async () => {
    const values = await form.validateFields()
    try {
      // 有编辑对象则更新，否则新建
      if (editing) await api.updateScenario(editing.id, values)
      else await api.createScenario(projectId!, values)
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除场景
  const handleDelete = async (id: string) => {
    try {
      await api.deleteScenario(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 表格列定义
  const columns: ColumnsType<Scenario> = [
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description' },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          {/* 进入场景编排与执行页 */}
          <Button
            size="small"
            type="link"
            onClick={() => navigate(`/projects/${projectId}/scenarios/${record.id}`)}
          >
            编排
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
          <Popconfirm title="确认删除该场景？" onConfirm={() => handleDelete(record.id)}>
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
      title="用例执行"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            setOpen(true)
          }}
        >
          新建用例执行
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={scenarios} />
      <Modal
        title={editing ? '编辑用例执行' : '新建用例执行'}
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
