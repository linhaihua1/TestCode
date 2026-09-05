/**
 * UI 自动化测试列表页：用例列表 + 新建 / 编辑 / 删除 / 进入编排。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { UiTestCase } from '../api/types'

export default function UiTestList() {
  const { projectId } = useProject()
  const [tests, setTests] = useState<UiTestCase[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<UiTestCase | null>(null)
  const [form] = Form.useForm()
  const navigate = useNavigate()

  const load = async () => {
    setLoading(true)
    try {
      setTests(await api.listUiTests(projectId!))
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
      if (editing) {
        await api.updateUiTest(editing.id, {
          name: values.name,
          baseUrl: values.baseUrl,
          description: values.description,
        })
      } else {
        await api.createUiTest(projectId!, {
          name: values.name,
          baseUrl: values.baseUrl,
          description: values.description,
          steps: [],
        })
      }
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.deleteUiTest(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<UiTestCase> = [
    { title: '名称', dataIndex: 'name' },
    { title: 'Base URL', dataIndex: 'baseUrl' },
    {
      title: '步骤数',
      dataIndex: 'steps',
      render: (v: UiTestCase['steps']) => (v ?? []).length,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => navigate(`/ui-tests/${record.id}`)}
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
          <Popconfirm title="确认删除该用例？" onConfirm={() => handleDelete(record.id)}>
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
      title="UI 自动化测试"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            setOpen(true)
          }}
        >
          新建用例
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={tests} />
      <Modal
        title={editing ? '编辑用例' : '新建用例'}
        open={open}
        onOk={handleSubmit}
        onCancel={() => setOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="baseUrl" label="Base URL">
            <Input placeholder="如 https://www.baidu.com" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  )
}
