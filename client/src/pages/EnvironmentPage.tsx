import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { Environment } from '../api/types'
import KeyValueEditor from '../components/KeyValueEditor'

export default function EnvironmentPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const [envs, setEnvs] = useState<Environment[]>([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<Environment | null>(null)
  const [form] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      setEnvs(await api.listEnvironments(projectId!))
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
    const payload = {
      name: values.name,
      baseUrl: values.baseUrl ?? '',
      variables: values.variables ?? [],
      headers: values.headers ?? [],
    }
    try {
      if (editing) await api.updateEnvironment(editing.id, payload)
      else await api.createEnvironment(projectId!, payload)
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.deleteEnvironment(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<Environment> = [
    { title: '名称', dataIndex: 'name' },
    { title: 'Base URL', dataIndex: 'baseUrl' },
    {
      title: '变量',
      dataIndex: 'variables',
      render: (v: Environment['variables']) => (
        <Space wrap>
          {(v ?? []).map((kv) => (
            <Tag key={kv.key}>
              {kv.key}={kv.value}
            </Tag>
          ))}
        </Space>
      ),
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
              form.setFieldsValue({
                name: record.name,
                baseUrl: record.baseUrl,
                variables: record.variables ?? [],
                headers: record.headers ?? [],
              })
              setOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该环境？" onConfirm={() => handleDelete(record.id)}>
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
      title="环境管理"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            setOpen(true)
          }}
        >
          新建环境
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={envs} />
      <Modal
        title={editing ? '编辑环境' : '新建环境'}
        open={open}
        onOk={handleSubmit}
        onCancel={() => setOpen(false)}
        width={720}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：dev / test / prod" />
          </Form.Item>
          <Form.Item name="baseUrl" label="Base URL">
            <Input placeholder="如：http://127.0.0.1:8080" />
          </Form.Item>
          <KeyValueEditor name="variables" label="变量" keyPlaceholder="变量名" valuePlaceholder="变量值" />
          <div style={{ height: 16 }} />
          <KeyValueEditor name="headers" label="公共请求头" keyPlaceholder="Header 名" />
        </Form>
      </Modal>
    </Card>
  )
}
