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
import { HTTP_METHODS, type ApiCase, type ApiDefinition, type Report } from '../api/types'
import KeyValueEditor from '../components/KeyValueEditor'
import AssertionEditor from '../components/AssertionEditor'
import ExtractEditor from '../components/ExtractEditor'
import EnvironmentSelect from '../components/EnvironmentSelect'

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

// 执行状态与标签颜色的映射
const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

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
  const [debugEnvId, setDebugEnvId] = useState<string | undefined>() // 选中的调试环境
  const [debugResult, setDebugResult] = useState<{
    response: { status: number; body: unknown; duration: number }
    extracted: Record<string, string>
    assertions: Array<{ passed: boolean; message: string }>
    passed: boolean
  } | null>(null) // 调试结果
  const [debugLoading, setDebugLoading] = useState(false) // 调试请求进行中

  // 运行相关状态
  const [runOpen, setRunOpen] = useState(false) // 运行弹窗是否打开
  const [runCaseData, setRunCaseData] = useState<ApiCase | null>(null) // 正在运行的用例
  const [runEnvId, setRunEnvId] = useState<string | undefined>() // 选中的运行环境
  const [runResult, setRunResult] = useState<Report | null>(null) // 运行报告结果
  const [runLoading, setRunLoading] = useState(false) // 运行请求进行中

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
    // 将步骤列表映射为 stepDefs（多步骤定义）
    const stepDefs = (values.steps ?? []).map(
      (s: { apiId: string; name?: string; assertions?: unknown; extracts?: unknown }) => ({
        apiId: s.apiId,
        name: s.name,
        assertions: s.assertions ?? [],
        extracts: s.extracts ?? [],
      }),
    )
    const payload = {
      name: values.name,
      stepDefs,
      assertions: [],
      extracts: [],
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

  // 打开调试弹窗（环境由 EnvironmentSelect 自动加载并默认选中第一个）
  const openDebug = (record: ApiCase) => {
    setDebugCaseData(record)
    setDebugResult(null)
    setDebugEnvId(undefined)
    setDebugOpen(true)
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

  // 打开运行弹窗（环境由 EnvironmentSelect 自动加载并默认选中第一个）
  const openRun = (record: ApiCase) => {
    setRunCaseData(record)
    setRunResult(null)
    setRunEnvId(undefined)
    setRunOpen(true)
  }

  // 独立运行用例（多步骤），生成报告
  const runCase = async () => {
    setRunLoading(true)
    setRunResult(null)
    try {
      const report = await api.runCase(runCaseData!.id, runEnvId)
      setRunResult(report)
      message.success(report.status === 'PASS' ? '运行通过' : '运行完成，存在失败步骤')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunLoading(false)
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
      title: '步骤数',
      // 多步骤用例显示 stepDefs 数量；单接口旧用例为 1
      render: (_, record) => (record.stepDefs?.length ?? 0) > 0 ? record.stepDefs.length : 1,
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          {/* 编辑：回填用例表单（多步骤或单接口转步骤）并打开编辑弹窗 */}
          <Button
            size="small"
            type="link"
            onClick={() => {
              setEditingCase(record)
              // 多步骤用例用 stepDefs；单接口旧用例转成一个步骤
              const stepDefs =
                (record.stepDefs?.length ?? 0) > 0
                  ? record.stepDefs
                  : [
                      {
                        apiId: record.apiId,
                        name: record.name,
                        assertions: record.assertions ?? [],
                        extracts: record.extracts ?? [],
                      },
                    ]
              caseForm.setFieldsValue({ name: record.name, steps: stepDefs })
              setCaseEditOpen(true)
            }}
          >
            编辑
          </Button>
          {/* 调试：发送请求查看响应、提取结果与断言结果 */}
          <Button size="small" type="link" onClick={() => openDebug(record)}>
            调试
          </Button>
          {/* 运行：独立运行用例生成报告 */}
          <Button size="small" type="link" onClick={() => openRun(record)}>
            运行
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

      {/* 用例编辑（多步骤） */}
      <Modal
        title={editingCase ? '编辑用例' : '新建用例'}
        open={caseEditOpen}
        onOk={saveCase}
        onCancel={() => setCaseEditOpen(false)}
        width={920}
        destroyOnClose
      >
        <Form form={caseForm} layout="vertical">
          {/* 用例名称 */}
          <Form.Item name="name" label="用例名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如：登录并查询用户" />
          </Form.Item>

          {/* 多步骤列表：每步引用一个接口 + 断言 + 提取 */}
          <Form.List name="steps">
            {(fields, { add, remove }) => (
              <div>
                <div style={{ fontWeight: 500, marginBottom: 8 }}>步骤（按顺序执行，变量在步骤间传递）</div>
                {fields.map((field, index) => (
                  <Card
                    key={field.key}
                    size="small"
                    title={`步骤 ${index + 1}${index === 0 ? '（起）' : index === fields.length - 1 ? '（止）' : ''}`}
                    style={{ marginBottom: 12 }}
                    extra={
                      <Button size="small" danger onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    }
                  >
                    {/* 步骤名 */}
                    <Form.Item name={[field.name, 'name']} label="步骤名">
                      <Input placeholder="如：登录（前置准备）" />
                    </Form.Item>
                    {/* 该步骤引用的接口 */}
                    <Form.Item
                      name={[field.name, 'apiId']}
                      label="接口"
                      rules={[{ required: true, message: '请选择接口' }]}
                    >
                      <Select
                        placeholder="选择接口"
                        showSearch
                        optionFilterProp="label"
                        options={apis.map((a) => ({ value: a.id, label: `${a.method} ${a.name}` }))}
                      />
                    </Form.Item>
                    {/* 该步骤的断言 */}
                    <AssertionEditor name={[field.name, 'assertions']} />
                    <div style={{ height: 8 }} />
                    {/* 该步骤的提取 */}
                    <ExtractEditor name={[field.name, 'extracts']} />
                  </Card>
                ))}
                <Button
                  type="dashed"
                  block
                  onClick={() => add({ apiId: undefined, name: '', assertions: [], extracts: [] })}
                >
                  添加步骤
                </Button>
              </div>
            )}
          </Form.List>
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
          <EnvironmentSelect
            projectId={projectId!}
            style={{ width: 240 }}
            placeholder="选择环境（提供 baseUrl）"
            value={debugEnvId}
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

      {/* 用例运行：独立运行多步骤用例并展示报告 */}
      <Modal
        title={`运行用例：${runCaseData?.name ?? ''}`}
        open={runOpen}
        onCancel={() => setRunOpen(false)}
        footer={null}
        width={760}
      >
        <Space style={{ marginBottom: 16 }}>
          <span>环境：</span>
          <EnvironmentSelect
            projectId={projectId!}
            style={{ width: 240 }}
            placeholder="选择环境（提供 baseUrl）"
            value={runEnvId}
            onChange={setRunEnvId}
          />
          <Button type="primary" loading={runLoading} onClick={runCase}>
            运行
          </Button>
        </Space>

        {runResult && (
          <div>
            <div style={{ marginBottom: 12 }}>
              <Tag color={STATUS_COLOR[runResult.status]}>{runResult.status}</Tag>
              <span style={{ color: '#999' }}>耗时 {runResult.duration}ms</span>
            </div>
            {runResult.details.map((d, i) => (
              <Card
                key={i}
                size="small"
                title={
                  <Space>
                    <span>
                      步骤 {i + 1}：{d.stepName}
                    </span>
                    <Tag color={STATUS_COLOR[d.status]}>{d.status}</Tag>
                  </Space>
                }
                style={{ marginBottom: 8 }}
              >
                {d.error && <Alert type="error" message={d.error} style={{ marginBottom: 8 }} />}
                {(d.assertions ?? []).map((a, j) => (
                  <Alert
                    key={j}
                    type={a.passed ? 'success' : 'error'}
                    showIcon
                    message={a.message}
                    style={{ marginBottom: 8 }}
                  />
                ))}
              </Card>
            ))}
          </div>
        )}
      </Modal>
    </Card>
  )
}
