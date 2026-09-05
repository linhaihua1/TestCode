/**
 * 右侧接口管理面板（PRD 第 4 期）：接口定义库。
 * 接口列表 + 请求构建器 + Swagger 导入 + Mock 配置。
 */
import { useEffect, useState } from 'react'
import { Button, Drawer, Form, Input, List, Modal, Popconfirm, Select, Space, Switch, Tag, message } from 'antd'
import { api, getErrorMessage } from '../../api/client'
import { HTTP_METHODS, type ApiDefinition } from '../../api/types'
import KeyValueEditor from '../KeyValueEditor'

interface Props {
  projectId?: string
  onCollapse: () => void
}

const METHOD_COLOR: Record<string, string> = {
  GET: 'green', POST: 'blue', PUT: 'orange', PATCH: 'purple', DELETE: 'red', HEAD: 'default', OPTIONS: 'default',
}

export default function ApiManagerPanel({ projectId, onCollapse }: Props) {
  const [apis, setApis] = useState<ApiDefinition[]>([])
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editing, setEditing] = useState<ApiDefinition | null>(null)
  const [form] = Form.useForm()

  const [importOpen, setImportOpen] = useState(false)
  const [importUrl, setImportUrl] = useState('')
  const [importing, setImporting] = useState(false)

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setApis(await api.listApis(projectId))
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

  const save = async () => {
    const values = await form.validateFields()
    const payload = {
      name: values.name,
      method: values.method,
      path: values.path,
      headers: values.headers ?? [],
      query: values.query ?? [],
      body: values.body ?? null,
      description: values.description,
      mockEnabled: values.mockEnabled ?? false,
      mockResponse: values.mockResponse ?? '',
    }
    try {
      if (editing) await api.updateApi(editing.id, payload)
      else await api.createApi(projectId!, payload)
      message.success('保存成功')
      setDrawerOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const remove = async (id: string) => {
    try {
      await api.deleteApi(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const importFromUrl = async () => {
    if (!importUrl.trim()) {
      message.warning('请输入 Swagger/OpenAPI 文档地址')
      return
    }
    setImporting(true)
    try {
      const res = await fetch(importUrl.trim())
      const doc = await res.json()
      const result = await api.importSwagger(projectId!, doc)
      message.success(`导入完成：新增 ${result.created} 个接口，跳过 ${result.skipped} 个`)
      setImportOpen(false)
      load()
    } catch (e) {
      message.error('导入失败：' + getErrorMessage(e))
    } finally {
      setImporting(false)
    }
  }

  const importFromFile = (file: File) => {
    const reader = new FileReader()
    reader.onload = async () => {
      try {
        const doc = JSON.parse(String(reader.result))
        const result = await api.importSwagger(projectId!, doc)
        message.success(`导入完成：新增 ${result.created} 个接口，跳过 ${result.skipped} 个`)
        setImportOpen(false)
        load()
      } catch (e) {
        message.error('导入失败：' + getErrorMessage(e))
      }
    }
    reader.readAsText(file)
  }

  const filtered = apis.filter((a) => a.name.includes(keyword) || a.path.includes(keyword))

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderLeft: '1px solid #eee', background: '#fff', minWidth: 0 }}>
      <div style={{ height: 40, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', borderBottom: '1px solid #f0f0f0', fontWeight: 600 }}>
        <span>接口管理</span>
        <Button type="text" size="small" onClick={onCollapse}>»</Button>
      </div>

      <div style={{ padding: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Input placeholder="搜索接口" size="small" value={keyword} onChange={(e) => setKeyword(e.target.value)} allowClear />
        <Space size={4}>
          <Button size="small" type="primary" onClick={() => { setEditing(null); form.resetFields(); form.setFieldsValue({ method: 'GET', mockEnabled: false }); setDrawerOpen(true) }}>
            新建接口
          </Button>
          <Button size="small" onClick={() => setImportOpen(true)}>Swagger 导入</Button>
          <Button size="small" onClick={load}>刷新</Button>
        </Space>
      </div>

      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px' }}>
        <List
          size="small"
          loading={loading}
          dataSource={filtered}
          renderItem={(a) => (
            <List.Item
              style={{ padding: '6px 8px' }}
              actions={[
                <Button key="edit" size="small" type="link" onClick={() => { setEditing(a); form.setFieldsValue({ ...a }); setDrawerOpen(true) }}>编辑</Button>,
                <Popconfirm key="del" title="确认删除？" onConfirm={() => remove(a.id)}>
                  <Button size="small" type="link" danger>删除</Button>
                </Popconfirm>,
              ]}
            >
              <div style={{ width: '100%' }}>
                <Space size={4}>
                  <Tag color={METHOD_COLOR[a.method]}>{a.method}</Tag>
                  {a.mockEnabled && <Tag color="purple">Mock</Tag>}
                  <span style={{ fontWeight: 600 }}>{a.name}</span>
                </Space>
                <div style={{ color: '#999', fontSize: 12 }}>{a.path}</div>
              </div>
            </List.Item>
          )}
        />
      </div>

      {/* 请求构建器 Drawer */}
      <Drawer
        title={editing ? '编辑接口' : '新建接口'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
        extra={<Button type="primary" onClick={save}>保存</Button>}
      >
        <Form form={form} layout="vertical">
          <Space align="baseline" style={{ display: 'flex' }}>
            <Form.Item name="method" label="方法" rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={HTTP_METHODS.map((m) => ({ value: m, label: m }))} />
            </Form.Item>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input placeholder="接口名称" style={{ width: 240 }} />
            </Form.Item>
          </Space>
          <Form.Item name="path" label="路径" rules={[{ required: true, message: '请输入路径' }]}>
            <Input placeholder="/api/users/${id}" />
          </Form.Item>
          <KeyValueEditor name="headers" label="请求头" keyPlaceholder="Header 名" />
          <div style={{ height: 16 }} />
          <KeyValueEditor name="query" label="Query 参数" />
          <div style={{ height: 16 }} />
          <Form.Item name="body" label="请求体（JSON 或文本，支持 ${变量}）">
            <Input.TextArea rows={4} style={{ fontFamily: 'monospace' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>

          {/* Mock 配置 */}
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12 }}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <span style={{ fontWeight: 600 }}>Mock</span>
              <Form.Item name="mockEnabled" valuePropName="checked" noStyle>
                <Switch />
              </Form.Item>
            </Space>
            <Form.Item noStyle shouldUpdate>
              {({ getFieldValue }) =>
                getFieldValue('mockEnabled') ? (
                  <>
                    <Form.Item name="mockResponse" label="Mock 响应（JSON）" style={{ marginTop: 12 }}>
                      <Input.TextArea rows={4} placeholder='{"code":0,"data":{}}' style={{ fontFamily: 'monospace' }} />
                    </Form.Item>
                    {editing && <div style={{ fontSize: 12, color: '#999' }}>Mock 地址：/mock/{editing.id}</div>}
                  </>
                ) : null
              }
            </Form.Item>
          </div>
        </Form>
      </Drawer>

      {/* Swagger 导入 Modal */}
      <Modal
        title="Swagger/OpenAPI 导入"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        footer={null}
        width={520}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div>
            <div style={{ marginBottom: 8 }}>URL 导入：</div>
            <Space.Compact style={{ width: '100%' }}>
              <Input placeholder="https://example.com/swagger.json" value={importUrl} onChange={(e) => setImportUrl(e.target.value)} />
              <Button type="primary" loading={importing} onClick={importFromUrl}>导入</Button>
            </Space.Compact>
          </div>
          <div>
            <div style={{ marginBottom: 8 }}>文件导入（JSON）：</div>
            <input type="file" accept=".json,.yaml,.yml" onChange={(e) => { const f = e.target.files?.[0]; if (f) importFromFile(f) }} />
          </div>
        </Space>
      </Modal>
    </div>
  )
}
