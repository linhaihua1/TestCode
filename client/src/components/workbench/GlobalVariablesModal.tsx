/**
 * 全局变量管理弹窗（PRD 2.1）：变量 CRUD + 批量删除 + 密钥脱敏。
 */
import { useEffect, useState } from 'react'
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../../api/client'
import type { GlobalVariable } from '../../api/types'

interface Props {
  projectId?: string
  open: boolean
  onClose: () => void
}

const TYPE_OPTIONS = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '布尔' },
  { value: 'timestamp', label: '时间戳' },
  { value: 'random', label: '随机函数' },
  { value: 'secret', label: '密钥' },
]

export default function GlobalVariablesModal({ projectId, open, onClose }: Props) {
  const [vars, setVars] = useState<GlobalVariable[]>([])
  const [loading, setLoading] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  const [editing, setEditing] = useState<GlobalVariable | null>(null)
  const [form] = Form.useForm()

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setVars(await api.listGlobalVariables(projectId))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, projectId])

  const submit = async () => {
    const values = await form.validateFields()
    try {
      if (editing) {
        await api.updateGlobalVariable(editing.id, { value: values.value, description: values.description })
      } else {
        await api.createGlobalVariable(projectId!, {
          name: values.name,
          type: values.type,
          value: values.value,
          description: values.description,
          encrypted: values.type === 'secret',
        })
      }
      message.success('保存成功')
      setEditOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const remove = async (id: string) => {
    try {
      await api.deleteGlobalVariable(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<GlobalVariable> = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '类型',
      dataIndex: 'type',
      render: (t: string) => <Tag>{TYPE_OPTIONS.find((x) => x.value === t)?.label ?? t}</Tag>,
    },
    {
      title: '值',
      dataIndex: 'value',
      render: (v: string, r) => (r.encrypted ? '******' : v),
    },
    { title: '描述', dataIndex: 'description' },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => {
              setEditing(record)
              form.setFieldsValue({ name: record.name, type: record.type, value: record.value, description: record.description })
              setEditOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除？" onConfirm={() => remove(record.id)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Modal title="全局变量" open={open} onCancel={onClose} footer={null} width={760}>
      <div style={{ marginBottom: 12 }}>
        <Button
          type="primary"
          onClick={() => {
            setEditing(null)
            form.resetFields()
            form.setFieldsValue({ type: 'string' })
            setEditOpen(true)
          }}
        >
          新增变量
        </Button>
      </div>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={vars} size="small" pagination={false} />

      <Modal
        title={editing ? '编辑变量' : '新增变量'}
        open={editOpen}
        onOk={submit}
        onCancel={() => setEditOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="变量名" rules={[{ required: true, pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/, message: '字母/数字/下划线，不能以数字开头' }]}>
            <Input placeholder="如 appKey" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="value" label="值" rules={[{ required: true, message: '请输入值' }]}>
            <Input placeholder="变量值" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Modal>
  )
}
