/**
 * 右侧接口管理面板（PRD 第 4 期）：接口定义库。
 * 接口列表 + 请求构建器 + Swagger 导入 + Mock 配置。
 */
import { useEffect, useState } from 'react'
import { Button, Divider, Drawer, Form, Input, List, Modal, Popconfirm, Select, Space, Switch, Tag, message } from 'antd'
import * as XLSX from 'xlsx'
import { api, getErrorMessage } from '../../api/client'
import { HTTP_METHODS, type ApiDefinition, type ApiImportItem } from '../../api/types'
import KeyValueEditor from '../KeyValueEditor'

interface Props {
  projectId?: string
  onCollapse: () => void
}

const METHOD_COLOR: Record<string, string> = {
  GET: 'green', POST: 'blue', PUT: 'orange', PATCH: 'purple', DELETE: 'red', HEAD: 'default', OPTIONS: 'default',
}

// ---------- Excel 导入相关 ----------
// 模板列名（中文表头）
const EXCEL_HEADERS = {
  name: '名称',
  method: '方法',
  path: '路径',
  headers: '请求头',
  query: '查询参数',
  body: '请求体',
  description: '描述',
  module: '模块',
  mock: 'Mock',
  mockResponse: 'Mock响应',
}

/** 解析 JSON 数组字段（请求头/查询参数），非法或留空返回 [] */
function parseJsonField(v: unknown): Array<{ key: string; value: string }> {
  if (v == null || v === '') return []
  if (Array.isArray(v)) return v
  const s = String(v).trim()
  if (!s) return []
  try {
    const j = JSON.parse(s)
    return Array.isArray(j) ? j : []
  } catch {
    return []
  }
}

/** 解析 Mock 布尔字段 */
function parseMockBool(v: unknown): boolean {
  if (v == null || v === '') return false
  if (typeof v === 'boolean') return v
  const s = String(v).trim().toLowerCase()
  return ['是', 'true', '1', 'yes', 'y'].includes(s)
}

/** 单行 Excel 数据 → 导入条目；缺少必填字段返回 null */
function rowToItem(r: Record<string, unknown>): ApiImportItem | null {
  const name = String(r[EXCEL_HEADERS.name] ?? '').trim()
  const method = String(r[EXCEL_HEADERS.method] ?? '').trim().toUpperCase()
  const path = String(r[EXCEL_HEADERS.path] ?? '').trim()
  if (!name || !method || !path) return null
  return {
    name,
    method,
    path,
    headers: parseJsonField(r[EXCEL_HEADERS.headers]),
    query: parseJsonField(r[EXCEL_HEADERS.query]),
    body: r[EXCEL_HEADERS.body] ? String(r[EXCEL_HEADERS.body]) : null,
    description: r[EXCEL_HEADERS.description] ? String(r[EXCEL_HEADERS.description]) : '',
    module: String(r[EXCEL_HEADERS.module] ?? '').trim(),
    mockEnabled: parseMockBool(r[EXCEL_HEADERS.mock]),
    mockResponse: r[EXCEL_HEADERS.mockResponse] ? String(r[EXCEL_HEADERS.mockResponse]) : null,
  }
}

/** 读取 Excel 文件并解析为导入条目 */
function parseExcelFile(file: File): Promise<ApiImportItem[]> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = new Uint8Array(e.target!.result as ArrayBuffer)
        const wb = XLSX.read(data, { type: 'array' })
        const sheet = wb.Sheets[wb.SheetNames[0]]
        const rows = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, { defval: '' })
        resolve(rows.map(rowToItem).filter((x): x is ApiImportItem => x != null))
      } catch (err) {
        reject(err instanceof Error ? err : new Error('Excel 解析失败'))
      }
    }
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsArrayBuffer(file)
  })
}

/** 下载 Excel 导入模板（含示例数据 + 填写说明两个 sheet） */
function downloadExcelTemplate() {
  const header = [EXCEL_HEADERS.name, EXCEL_HEADERS.method, EXCEL_HEADERS.path, EXCEL_HEADERS.headers, EXCEL_HEADERS.query, EXCEL_HEADERS.body, EXCEL_HEADERS.description, EXCEL_HEADERS.module, EXCEL_HEADERS.mock, EXCEL_HEADERS.mockResponse]
  const examples = [
    ['登录', 'POST', '/demo/login', '[{"key":"Content-Type","value":"application/json"}]', '', '{"username":"admin"}', '登录接口', '用户模块', '否', ''],
    ['查询用户', 'GET', '/demo/users/${userId}', '[{"key":"Authorization","value":"Bearer ${token}"}]', '', '', '查询用户信息', '用户模块', '否', ''],
  ]
  const ws = XLSX.utils.aoa_to_sheet([header, ...examples])
  ws['!cols'] = header.map((_, i) => ({ wch: i === 3 || i === 4 || i === 9 ? 40 : 16 }))

  const note = XLSX.utils.aoa_to_sheet([
    ['接口导入模板填写说明'],
    ['名称', '接口名称（必填）'],
    ['方法', 'GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS（必填）'],
    ['路径', '接口路径，支持 ${变量} 占位，如 /demo/users/${userId}（必填）'],
    ['请求头', 'JSON 数组：[{"key":"Content-Type","value":"application/json"}]，留空表示无'],
    ['查询参数', 'JSON 数组，格式同请求头，留空表示无'],
    ['请求体', '请求体内容，支持 ${变量}'],
    ['描述', '接口描述'],
    ['模块', '接口所属模块名，导入时自动创建 api 类型模块'],
    ['Mock', '是否启用 Mock：填「是」或 true 或 1 表示启用，其余为否'],
    ['Mock响应', '启用 Mock 时返回的响应内容（JSON）'],
  ])
  note['!cols'] = [{ wch: 12 }, { wch: 60 }]

  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '接口导入模板')
  XLSX.utils.book_append_sheet(wb, note, '填写说明')
  XLSX.writeFile(wb, '接口导入模板.xlsx')
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

  const generateCase = async (apiId: string) => {
    try {
      const c = await api.generateCaseFromApi(apiId)
      message.success(`已生成用例「${c.name}」`)
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

  const importFromExcel = async (file: File) => {
    setImporting(true)
    try {
      const items = await parseExcelFile(file)
      if (items.length === 0) {
        message.warning('未解析到有效数据，请按模板填写（名称/方法/路径为必填）')
        return
      }
      const result = await api.importApis(projectId!, items)
      message.success(`导入完成：新增 ${result.created} 个接口，跳过 ${result.skipped} 个`)
      setImportOpen(false)
      load()
    } catch (e) {
      message.error('导入失败：' + getErrorMessage(e))
    } finally {
      setImporting(false)
    }
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
                <Button key="gen" size="small" type="link" onClick={() => generateCase(a.id)}>生成用例</Button>,
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

      {/* 接口导入 Modal（Swagger + Excel 模板） */}
      <Modal
        title="接口导入"
        open={importOpen}
        onCancel={() => setImportOpen(false)}
        footer={null}
        width={620}
      >
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <div>
            <div style={{ fontWeight: 600, marginBottom: 8 }}>Swagger/OpenAPI 导入</div>
            <div style={{ marginBottom: 8 }}>URL 导入：</div>
            <Space.Compact style={{ width: '100%' }}>
              <Input placeholder="https://example.com/swagger.json" value={importUrl} onChange={(e) => setImportUrl(e.target.value)} />
              <Button type="primary" loading={importing} onClick={importFromUrl}>导入</Button>
            </Space.Compact>
            <div style={{ margin: '8px 0' }}>文件导入（JSON）：</div>
            <input type="file" accept=".json" onChange={(e) => { const f = e.target.files?.[0]; if (f) importFromFile(f) }} />
          </div>

          <Divider style={{ margin: '8px 0' }} />

          <div>
            <div style={{ fontWeight: 600, marginBottom: 8 }}>Excel 模板导入</div>
            <Space>
              <Button onClick={downloadExcelTemplate}>下载导入模板</Button>
              <span style={{ fontSize: 12, color: '#999' }}>模板含「接口导入模板」和「填写说明」两个 Sheet</span>
            </Space>
            <div style={{ margin: '8px 0' }}>上传填写好的 Excel：</div>
            <input type="file" accept=".xlsx,.xls" onChange={(e) => { const f = e.target.files?.[0]; if (f) importFromExcel(f) }} />
            <div style={{ fontSize: 12, color: '#999', marginTop: 8 }}>
              模板列：名称 / 方法 / 路径 / 请求头(JSON) / 查询参数(JSON) / 请求体 / 描述 / 模块 / Mock(是/否) / Mock响应
            </div>
          </div>
        </Space>
      </Modal>
    </div>
  )
}
