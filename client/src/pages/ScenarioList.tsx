import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { Scenario } from '../api/types'

export default function ScenarioList() {
  const { projectId } = useParams<{ projectId: string }>()
  const [scenarios, setScenarios] = useState<Scenario[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Scenario | null>(null)
  const [form] = Form.useForm()
  const navigate = useNavigate()

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

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  const handleSubmit = async () => {
    const values = await form.validateFields()
    try {
      if (editing) await api.updateScenario(editing.id, values)
      else await api.createScenario(projectId!, values)
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.deleteScenario(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<Scenario> = [
    { title: '名称', dataIndex: 'name' },
    { title: '描述', dataIndex: 'description' },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => navigate(`/projects/${projectId}/scenarios/${record.id}`)}
          >
            编排
          </Button>
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
      title="场景自动化"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            setOpen(true)
          }}
        >
          新建场景
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={scenarios} />
      <Modal
        title={editing ? '编辑场景' : '新建场景'}
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
