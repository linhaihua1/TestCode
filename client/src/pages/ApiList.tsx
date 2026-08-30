import { useEffect, useState } from 'react'
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { HTTP_METHODS, type ApiCase, type ApiDefinition } from '../api/types'
import KeyValueEditor from '../components/KeyValueEditor'
import AssertionEditor from '../components/AssertionEditor'
import ExtractEditor from '../components/ExtractEditor'

const METHOD_COLOR: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'orange',
  PATCH: 'purple',
  DELETE: 'red',
  HEAD: 'default',
  OPTIONS: 'default',
}

export default function ApiList() {
  const { projectId } = useParams<{ projectId: string }>()
  const [apis, setApis] = useState<ApiDefinition[]>([])
  const [loading, setLoading] = useState(false)

  // 接口编辑 Drawer
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingApi, setEditingApi] = useState<ApiDefinition | null>(null)
  const [apiForm] = Form.useForm()

  // 用例管理
  const [caseModalOpen, setCaseModalOpen] = useState(false)
  const [currentApi, setCurrentApi] = useState<ApiDefinition | null>(null)
  const [cases, setCases] = useState<ApiCase[]>([])
  const [caseEditOpen, setCaseEditOpen] = useState(false)
  const [editingCase, setEditingCase] = useState<ApiCase | null>(null)
  const [caseForm] = Form.useForm()

  const load = async () => {
    setLoading(true)
    try {
      setApis(await api.listApis(projectId!))
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

  const saveApi = async () => {
    const values = await apiForm.validateFields()
    const payload = {
      name: values.name,
      method: values.method,
      path: values.path,
      headers: values.headers ?? [],
      query: values.query ?? [],
      body: values.body ?? null,
      description: values.description,
    }
    try {
      if (editingApi) await api.updateApi(editingApi.id, payload)
      else await api.createApi(projectId!, payload)
      message.success('保存成功')
      setDrawerOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const deleteApi = async (id: string) => {
    try {
      await api.deleteApi(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const openCases = async (apiDef: ApiDefinition) => {
    setCurrentApi(apiDef)
    setCaseModalOpen(true)
    try {
      setCases(await api.listCases(apiDef.id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const saveCase = async () => {
    const values = await caseForm.validateFields()
    const payload = {
      name: values.name,
      assertions: values.assertions ?? [],
      extracts: values.extracts ?? [],
    }
    try {
      if (editingCase) await api.updateCase(editingCase.id, payload)
      else await api.createCase(currentApi!.id, payload)
      message.success('保存成功')
      setCaseEditOpen(false)
      setCases(await api.listCases(currentApi!.id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const deleteCase = async (id: string) => {
    try {
      await api.deleteCase(id)
      message.success('删除成功')
      setCases(await api.listCases(currentApi!.id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const apiColumns: ColumnsType<ApiDefinition> = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '方法',
      dataIndex: 'method',
      render: (m: string) => <Tag color={METHOD_COLOR[m]}>{m}</Tag>,
    },
    { title: '路径', dataIndex: 'path' },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => {
              setEditingApi(record)
              apiForm.setFieldsValue({
                name: record.name,
                method: record.method,
                path: record.path,
                headers: record.headers ?? [],
                query: record.query ?? [],
                body: record.body ?? '',
                description: record.description,
              })
              setDrawerOpen(true)
            }}
          >
            编辑
          </Button>
          <Button size="small" type="link" onClick={() => openCases(record)}>
            用例
          </Button>
          <Popconfirm title="确认删除该接口？" onConfirm={() => deleteApi(record.id)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const caseColumns: ColumnsType<ApiCase> = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '断言数',
      dataIndex: 'assertions',
      render: (v: ApiCase['assertions']) => (v ?? []).length,
    },
    {
      title: '提取数',
      dataIndex: 'extracts',
      render: (v: ApiCase['extracts']) => (v ?? []).length,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            type="link"
            onClick={() => {
              setEditingCase(record)
              caseForm.setFieldsValue({
                name: record.name,
                assertions: record.assertions ?? [],
                extracts: record.extracts ?? [],
              })
              setCaseEditOpen(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该用例？" onConfirm={() => deleteCase(record.id)}>
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
      title="接口管理"
      extra={
        <Button
          type="primary"
          onClick={() => {
            setEditingApi(null)
            apiForm.resetFields()
            apiForm.setFieldsValue({ method: 'GET' })
            setDrawerOpen(true)
          }}
        >
          新建接口
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={apiColumns} dataSource={apis} />

      {/* 接口请求构建器 */}
      <Drawer
        title={editingApi ? '编辑接口' : '新建接口'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={720}
        extra={
          <Button type="primary" onClick={saveApi}>
            保存
          </Button>
        }
      >
        <Form form={apiForm} layout="vertical">
          <Space align="baseline" style={{ display: 'flex' }}>
            <Form.Item name="method" label="方法" rules={[{ required: true }]}>
              <Select style={{ width: 130 }} options={HTTP_METHODS.map((m) => ({ value: m, label: m }))} />
            </Form.Item>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input placeholder="接口名称" style={{ width: 260 }} />
            </Form.Item>
          </Space>
          <Form.Item name="path" label="路径" rules={[{ required: true, message: '请输入路径' }]}>
            <Input placeholder="如 /api/users/${id}" />
          </Form.Item>
          <KeyValueEditor name="headers" label="请求头" keyPlaceholder="Header 名" />
          <div style={{ height: 16 }} />
          <KeyValueEditor name="query" label="Query 参数" />
          <div style={{ height: 16 }} />
          <Form.Item name="body" label="请求体（JSON 或文本，支持 ${变量}）">
            <Input.TextArea rows={6} placeholder='如 {"name": "${name}"}' style={{ fontFamily: 'monospace' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Drawer>

      {/* 用例列表 */}
      <Modal
        title={`用例管理：${currentApi?.name ?? ''}`}
        open={caseModalOpen}
        onCancel={() => setCaseModalOpen(false)}
        footer={null}
        width={760}
      >
        <div style={{ marginBottom: 16 }}>
          <Button
            type="primary"
            onClick={() => {
              setEditingCase(null)
              caseForm.resetFields()
              setCaseEditOpen(true)
            }}
          >
            新建用例
          </Button>
        </div>
        <Table rowKey="id" columns={caseColumns} dataSource={cases} pagination={false} size="small" />
      </Modal>

      {/* 用例编辑 */}
      <Modal
        title={editingCase ? '编辑用例' : '新建用例'}
        open={caseEditOpen}
        onOk={saveCase}
        onCancel={() => setCaseEditOpen(false)}
        width={860}
        destroyOnClose
      >
        <Form form={caseForm} layout="vertical">
          <Form.Item name="name" label="用例名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：登录成功" />
          </Form.Item>
          <AssertionEditor />
          <div style={{ height: 16 }} />
          <ExtractEditor />
        </Form>
      </Modal>
    </Card>
  )
}
