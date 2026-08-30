/**
 * 接口管理页
 *
 * 职责：管理指定项目下的接口定义及其用例，是本平台的核心页面之一。
 * 包含：
 * 1. 接口列表（增删改查）；
 * 2. 接口请求构建器（Drawer，配置方法/路径/请求头/Query/请求体）；
 * 3. 用例管理（Modal 列表 + 编辑弹窗，含断言与提取规则）。
 */
import { useEffect, useState } from 'react'
import {
  Alert,
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
import { HTTP_METHODS, type ApiCase, type ApiDefinition, type Environment } from '../api/types'
import KeyValueEditor from '../components/KeyValueEditor'
import AssertionEditor from '../components/AssertionEditor'
import ExtractEditor from '../components/ExtractEditor'

// HTTP 方法与标签颜色的映射
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
  const { projectId } = useParams<{ projectId: string }>() // 当前项目 ID
  const [apis, setApis] = useState<ApiDefinition[]>([]) // 接口列表数据
  const [loading, setLoading] = useState(false) // 表格加载状态

  // 接口编辑 Drawer 相关状态
  const [drawerOpen, setDrawerOpen] = useState(false) // 请求构建器 Drawer 是否打开
  const [editingApi, setEditingApi] = useState<ApiDefinition | null>(null) // 正在编辑的接口（null 表示新建）
  const [apiForm] = Form.useForm() // 接口表单实例

  // 用例管理相关状态
  const [caseModalOpen, setCaseModalOpen] = useState(false) // 用例列表弹窗是否打开
  const [currentApi, setCurrentApi] = useState<ApiDefinition | null>(null) // 当前查看用例的接口
  const [cases, setCases] = useState<ApiCase[]>([]) // 当前接口的用例列表
  const [caseEditOpen, setCaseEditOpen] = useState(false) // 用例编辑弹窗是否打开
  const [editingCase, setEditingCase] = useState<ApiCase | null>(null) // 正在编辑的用例（null 表示新建）
  const [caseForm] = Form.useForm() // 用例表单实例

  // 调试相关状态
  const [debugOpen, setDebugOpen] = useState(false) // 调试弹窗是否打开
  const [debugCaseData, setDebugCaseData] = useState<ApiCase | null>(null) // 正在调试的用例
  const [debugEnvList, setDebugEnvList] = useState<Environment[]>([]) // 可用的环境列表
  const [debugEnvId, setDebugEnvId] = useState<string | undefined>() // 选中的调试环境
  const [debugResult, setDebugResult] = useState<{
    response: { status: number; body: unknown; duration: number }
    extracted: Record<string, string>
    assertions: Array<{ passed: boolean; message: string }>
    passed: boolean
  } | null>(null) // 调试结果
  const [debugLoading, setDebugLoading] = useState(false) // 调试请求进行中

  // 加载当前项目下的接口列表
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

  // 项目切换时重新拉取接口列表
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // 保存接口（新建或更新请求构建器内容）
  const saveApi = async () => {
    const values = await apiForm.validateFields()
    // 组装提交负载，数组字段兜底为空数组，body 兜底为 null
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
      // 有编辑对象则更新，否则新建
      if (editingApi) await api.updateApi(editingApi.id, payload)
      else await api.createApi(projectId!, payload)
      message.success('保存成功')
      setDrawerOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除接口
  const deleteApi = async (id: string) => {
    try {
      await api.deleteApi(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 打开指定接口的用例管理弹窗并加载其用例列表
  const openCases = async (apiDef: ApiDefinition) => {
    setCurrentApi(apiDef)
    setCaseModalOpen(true)
    try {
      setCases(await api.listCases(apiDef.id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 保存用例（新建或更新断言/提取规则）
  const saveCase = async () => {
    const values = await caseForm.validateFields()
    // 组装提交负载，断言与提取列表兜底为空数组
    const payload = {
      name: values.name,
      assertions: values.assertions ?? [],
      extracts: values.extracts ?? [],
    }
    try {
      // 有编辑对象则更新，否则在当前接口下新建
      if (editingCase) await api.updateCase(editingCase.id, payload)
      else await api.createCase(currentApi!.id, payload)
      message.success('保存成功')
      setCaseEditOpen(false)
      setCases(await api.listCases(currentApi!.id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除用例
  const deleteCase = async (id: string) => {
    try {
      await api.deleteCase(id)
      message.success('删除成功')
      setCases(await api.listCases(currentApi!.id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 打开调试弹窗：加载环境列表，默认选中第一个环境
  const openDebug = async (record: ApiCase) => {
    setDebugCaseData(record)
    setDebugResult(null)
    setDebugOpen(true)
    try {
      const envs = await api.listEnvironments(projectId!)
      setDebugEnvList(envs)
      setDebugEnvId(envs[0]?.id)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 执行调试：发送请求并展示响应、提取结果、断言结果
  const runDebug = async () => {
    setDebugLoading(true)
    setDebugResult(null)
    try {
      const result = await api.debugCase(debugCaseData!.id, debugEnvId)
      setDebugResult(result)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setDebugLoading(false)
    }
  }

  // 接口列表列定义
  const apiColumns: ColumnsType<ApiDefinition> = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '方法',
      dataIndex: 'method',
      // 用带颜色的标签展示 HTTP 方法
      render: (m: string) => <Tag color={METHOD_COLOR[m]}>{m}</Tag>,
    },
    { title: '路径', dataIndex: 'path' },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          {/* 编辑：回填请求构建器表单并打开 Drawer */}
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
          {/* 打开该接口的用例管理弹窗 */}
          <Button size="small" type="link" onClick={() => openCases(record)}>
            用例
          </Button>
          {/* 删除：带二次确认 */}
          <Popconfirm title="确认删除该接口？" onConfirm={() => deleteApi(record.id)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  // 用例列表列定义
  const caseColumns: ColumnsType<ApiCase> = [
    { title: '名称', dataIndex: 'name' },
    {
      title: '断言数',
      dataIndex: 'assertions',
      // 统计断言条数
      render: (v: ApiCase['assertions']) => (v ?? []).length,
    },
    {
      title: '提取数',
      dataIndex: 'extracts',
      // 统计提取规则条数
      render: (v: ApiCase['extracts']) => (v ?? []).length,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          {/* 编辑：回填用例表单并打开编辑弹窗 */}
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
          {/* 调试：发送请求查看响应、提取结果与断言结果 */}
          <Button size="small" type="link" onClick={() => openDebug(record)}>
            调试
          </Button>
          {/* 删除：带二次确认 */}
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
        // 新建接口：清空编辑态并默认方法为 GET，然后打开 Drawer
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
          {/* 方法与名称并排显示 */}
          <Space align="baseline" style={{ display: 'flex' }}>
            {/* 请求方法下拉选择 */}
            <Form.Item name="method" label="方法" rules={[{ required: true }]}>
              <Select style={{ width: 130 }} options={HTTP_METHODS.map((m) => ({ value: m, label: m }))} />
            </Form.Item>
            {/* 接口名称 */}
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input placeholder="接口名称" style={{ width: 260 }} />
            </Form.Item>
          </Space>
          {/* 请求路径，支持 ${变量} 占位 */}
          <Form.Item name="path" label="路径" rules={[{ required: true, message: '请输入路径' }]}>
            <Input placeholder="如 /api/users/${id}" />
          </Form.Item>
          {/* 请求头键值对编辑器 */}
          <KeyValueEditor name="headers" label="请求头" keyPlaceholder="Header 名" />
          <div style={{ height: 16 }} />
          {/* Query 参数键值对编辑器 */}
          <KeyValueEditor name="query" label="Query 参数" />
          <div style={{ height: 16 }} />
          {/* 请求体，支持变量插值 */}
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
          {/* 新建用例：清空编辑态并打开用例编辑弹窗 */}
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
          {/* 用例名称 */}
          <Form.Item name="name" label="用例名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：登录成功" />
          </Form.Item>
          {/* 断言列表编辑器 */}
          <AssertionEditor />
          <div style={{ height: 16 }} />
          {/* 提取规则列表编辑器 */}
          <ExtractEditor />
        </Form>
      </Modal>

      {/* 用例调试：发送请求查看响应、提取结果、断言结果 */}
      <Modal
        title={`调试用例：${debugCaseData?.name ?? ''}`}
        open={debugOpen}
        onCancel={() => setDebugOpen(false)}
        footer={null}
        width={760}
      >
        <Space style={{ marginBottom: 16 }}>
          <span>环境：</span>
          <Select
            style={{ width: 240 }}
            placeholder="选择环境（提供 baseUrl）"
            value={debugEnvId}
            options={debugEnvList.map((e) => ({ value: e.id, label: e.name }))}
            onChange={setDebugEnvId}
          />
          <Button type="primary" loading={debugLoading} onClick={runDebug}>
            发送请求
          </Button>
        </Space>

        {debugResult && (
          <div>
            {/* 响应状态与耗时 */}
            <div style={{ marginBottom: 12 }}>
              <Tag color={debugResult.response.status < 400 ? 'green' : 'red'}>
                状态码 {debugResult.response.status}
              </Tag>
              <span style={{ color: '#999' }}>耗时 {debugResult.response.duration}ms</span>
            </div>

            {/* 提取到的变量 */}
            {Object.keys(debugResult.extracted).length > 0 && (
              <Card size="small" title="提取结果" style={{ marginBottom: 12 }}>
                <Space wrap>
                  {Object.entries(debugResult.extracted).map(([k, v]) => (
                    <Tag key={k} color="blue">
                      {k} = {v}
                    </Tag>
                  ))}
                </Space>
              </Card>
            )}

            {/* 断言结果 */}
            {debugResult.assertions.length > 0 && (
              <Card size="small" title="断言结果" style={{ marginBottom: 12 }}>
                {debugResult.assertions.map((a, i) => (
                  <Alert
                    key={i}
                    type={a.passed ? 'success' : 'error'}
                    showIcon
                    message={a.message}
                    style={{ marginBottom: 8 }}
                  />
                ))}
              </Card>
            )}

            {/* 响应体 */}
            <Card size="small" title="响应体">
              <pre
                style={{
                  margin: 0,
                  maxHeight: 320,
                  overflow: 'auto',
                  background: '#f5f5f5',
                  padding: 12,
                  borderRadius: 4,
                  fontSize: 13,
                }}
              >
                {typeof debugResult.response.body === 'string'
                  ? debugResult.response.body
                  : JSON.stringify(debugResult.response.body, null, 2)}
              </pre>
            </Card>
          </div>
        )}
      </Modal>
    </Card>
  )
}
