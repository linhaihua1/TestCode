/**
 * 环境配置页
 *
 * 职责：管理指定项目下的运行环境（如 dev / test / prod），
 * 每个环境包含 Base URL、环境变量和公共请求头，供场景执行时选用。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, Modal, Popconfirm, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { Environment } from '../api/types'
import KeyValueEditor from '../components/KeyValueEditor'

export default function EnvironmentPage() {
  const { projectId } = useProject()
  const [envs, setEnvs] = useState<Environment[]>([]) // 环境列表数据
  const [loading, setLoading] = useState(false) // 表格加载状态
  const [open, setOpen] = useState(false) // 新建/编辑弹窗是否打开
  const [editing, setEditing] = useState<Environment | null>(null) // 正在编辑的环境（null 表示新建）
  const [form] = Form.useForm() // 弹窗表单实例

  // 加载当前项目下的环境列表
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

  // 项目切换时重新拉取环境列表
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // 处理新建/编辑弹窗提交
  const handleSubmit = async () => {
    const values = await form.validateFields()
    // 组装提交负载，缺失的数组字段兜底为空数组
    const payload = {
      name: values.name,
      baseUrl: values.baseUrl ?? '',
      variables: values.variables ?? [],
      headers: values.headers ?? [],
    }
    try {
      // 有编辑对象则更新，否则新建
      if (editing) await api.updateEnvironment(editing.id, payload)
      else await api.createEnvironment(projectId!, payload)
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除环境
  const handleDelete = async (id: string) => {
    try {
      await api.deleteEnvironment(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 表格列定义
  const columns: ColumnsType<Environment> = [
    { title: '名称', dataIndex: 'name' },
    { title: 'Base URL', dataIndex: 'baseUrl' },
    {
      title: '变量',
      dataIndex: 'variables',
      // 以标签形式展示键值对
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
          {/* 编辑：回填表单（含变量与公共请求头）并打开弹窗 */}
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
          {/* 删除：带二次确认 */}
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
