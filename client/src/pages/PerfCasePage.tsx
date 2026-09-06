/**
 * 性能测试 → 创建用例
 *
 * 以结构化表单编辑 JMeter 压测方案（并发/Ramp-Up/循环/时长/思考时间 + HTTP 请求步骤），
 * 页面右上角提供 JMeter(.jmx) 的导入与导出入口：
 *   - 导入：上传 .jmx，解析线程组与 HTTP 采样器回填为可编辑用例（支持多选批量）
 *   - 导出：单个用例导出为 .jmx；选中多个（或不选表示全部）合并为一个多线程组计划
 */
import { useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Col,
  Divider,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Upload,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { PerfCase, PerfEnvStatus, PerfStep } from '../api/types'
import KeyValueEditor from '../components/KeyValueEditor'

const METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'].map((m) => ({ value: m, label: m }))

const ON_ERROR_OPTIONS = [
  { value: 'continue', label: '继续执行' },
  { value: 'startnext', label: '启动下一线程' },
  { value: 'stopthread', label: '停止当前线程' },
  { value: 'stoptest', label: '停止整个测试' },
]

const ASSERT_TYPE = [
  { value: 'responseCode', label: '响应码' },
  { value: 'responseText', label: '响应文本' },
]

const ASSERT_OP = [
  { value: 'equals', label: '等于' },
  { value: 'contains', label: '包含' },
]

/** 生成新的请求步骤（带唯一 id） */
function newStep(): PerfStep {
  return {
    id: `s${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`,
    name: '',
    method: 'GET',
    url: '',
    headers: [],
    query: [],
    body: '',
    assertions: [],
    enabled: true,
  }
}

export default function PerfCasePage() {
  const { projectId } = useProject()
  const navigate = useNavigate()

  const [cases, setCases] = useState<PerfCase[]>([])
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState<string[]>([])
  const [env, setEnv] = useState<PerfEnvStatus | null>(null)
  const [importing, setImporting] = useState(false)
  const [runningId, setRunningId] = useState<string | null>(null)

  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<PerfCase | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setCases(await api.listPerfCases(projectId))
      setSelected([])
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    api.getPerfEnv().then(setEnv).catch(() => setEnv(null))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // ---------- 编辑 ----------
  const openEditor = (record?: PerfCase) => {
    setEditing(record ?? null)
    if (record) {
      form.setFieldsValue({
        name: record.name,
        description: record.description ?? '',
        threads: record.threads,
        rampUp: record.rampUp,
        loops: record.loops,
        duration: record.duration,
        thinkTime: record.thinkTime,
        onSampleError: record.onSampleError,
        loadProfile: record.profile?.loadProfile ?? 'constant',
        stepping: record.profile?.stepping ?? {},
        concurrency: record.profile?.concurrency ?? {},
        variables: record.variables ?? [],
        steps: (record.steps ?? []).map((s) => ({ ...s, headers: s.headers ?? [], query: s.query ?? [], assertions: s.assertions ?? [] })),
      })
    } else {
      form.resetFields()
      form.setFieldsValue({ steps: [newStep()] })
    }
    setOpen(true)
  }

  const handleSubmit = async () => {
    const values = await form.validateFields()
    setSaving(true)
    try {
      if (editing) await api.updatePerfCase(editing.id, values)
      else await api.createPerfCase(projectId!, values)
      message.success('保存成功')
      setOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.deletePerfCase(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // ---------- 执行 ----------
  const handleRun = async (record: PerfCase) => {
    if (!record.steps?.length) {
      message.warning('该用例没有请求步骤，请先编辑添加')
      return
    }
    const isTimed = record.duration > 0
    if (isTimed) {
      const ok = await new Promise<boolean>((resolve) =>
        Modal.confirm({
          title: '确认开始压测？',
          content: `将以 ${record.threads} 并发持续压测 ${record.duration} 秒，会真实请求目标服务。`,
          onOk: () => resolve(true),
          onCancel: () => resolve(false),
        }),
      )
      if (!ok) return
    }
    setRunningId(record.id)
    try {
      await api.runPerfCase(record.id)
      message.success(`压测「${record.name}」已启动，可在测试报告页查看实时进度`)
      navigate('/perf-reports')
    } catch (e) {
      message.error('执行失败：' + getErrorMessage(e))
    } finally {
      setRunningId(null)
    }
  }

  // ---------- 导入 / 导出 ----------
  const handleImport = async (files: File[]) => {
    if (!projectId) return
    setImporting(true)
    try {
      const payloads = await Promise.all(files.map(async (f) => ({ filename: f.name, content: await f.text() })))
      const result = await api.importJmeter(projectId, payloads)
      if (result.failed.length) {
        Modal.warning({
          title: `导入完成：成功 ${result.created.length} 个，失败 ${result.failed.length} 个`,
          width: 520,
          content: (
            <ul style={{ paddingLeft: 18, margin: '8px 0 0' }}>
              {result.failed.map((f) => (
                <li key={f.filename}>
                  {f.filename}：{f.reason}
                </li>
              ))}
            </ul>
          ),
        })
      } else {
        message.success(`导入成功 ${result.created.length} 个压测用例`)
      }
      load()
    } catch (e) {
      message.error('导入失败：' + getErrorMessage(e))
    } finally {
      setImporting(false)
    }
  }

  const handleExport = async () => {
    if (!projectId) return
    const targets = selected.length ? cases.filter((c) => selected.includes(c.id)) : cases
    if (!targets.length) {
      message.warning('暂无可导出的用例')
      return
    }
    try {
      if (targets.length === 1) {
        await api.exportJmx(targets[0].id, targets[0].name)
      } else {
        await api.exportJmxBundle(projectId, targets.map((t) => t.id), `性能测试计划-${targets.length}个用例`)
      }
      message.success(`已导出 ${targets.length} 个用例的 JMeter 计划`)
    } catch (e) {
      message.error('导出失败：' + getErrorMessage(e))
    }
  }

  const columns: ColumnsType<PerfCase> = [
    {
      title: '用例名称',
      dataIndex: 'name',
      render: (v: string, r) => (
        <Tooltip title={r.description}>
          <span>{v}</span>
        </Tooltip>
      ),
    },
    { title: '并发线程', dataIndex: 'threads', width: 90 },
    { title: 'Ramp-Up(s)', dataIndex: 'rampUp', width: 100 },
    {
      title: '压测方式',
      width: 140,
      render: (_, r) => (r.duration > 0 ? <Tag color="blue">按时长 {r.duration}s</Tag> : <Tag>按循环 {r.loops} 次</Tag>),
    },
    { title: '思考时间(ms)', dataIndex: 'thinkTime', width: 120 },
    {
      title: '请求数',
      width: 90,
      render: (_, r) => (
        <Tooltip title={(r.steps ?? []).map((s) => `${s.method} ${s.url}`).join('\n')}>
          <span>{(r.steps ?? []).length}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      width: 240,
      render: (_, record) => (
        <Space size={0}>
          <Button size="small" type="link" loading={runningId === record.id} onClick={() => handleRun(record)}>
            执行
          </Button>
          <Button size="small" type="link" onClick={() => openEditor(record)}>
            编辑
          </Button>
          <Button
            size="small"
            type="link"
            onClick={async () => {
              try {
                await api.exportJmx(record.id, record.name)
                message.success('已导出 .jmx')
              } catch (e) {
                message.error(getErrorMessage(e))
              }
            }}
          >
            导出
          </Button>
          <Popconfirm title="确认删除该压测用例？" onConfirm={() => handleDelete(record.id)}>
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
      title={
        <Space>
          性能测试 · 创建用例
          {env &&
            (env.available ? (
              <Tooltip title={`JMeter 运行时：${env.source === 'embedded' ? '工程内置' : env.source === 'env' ? 'JMETER_HOME' : 'PATH'}（${env.home ?? ''}）`}>
                <Tag color="green">JMeter 就绪</Tag>
              </Tooltip>
            ) : (
              <Tag color="red">JMeter 未就绪</Tag>
            ))}
        </Space>
      }
      extra={
        <Space>
          <Upload
            accept=".jmx,.xml"
            multiple
            showUploadList={false}
            beforeUpload={(file, list) => {
              // 多选时 beforeUpload 会对每个文件触发一次，只在首个文件时批量处理
              if (list.length > 1 && file.uid !== list[0].uid) return false
              handleImport(list as unknown as File[])
              return false
            }}
          >
            <Button loading={importing}>导入 JMeter</Button>
          </Upload>
          <Button onClick={handleExport} disabled={!cases.length}>
            导出 JMeter{selected.length > 1 ? `（${selected.length}）` : ''}
          </Button>
          <Button type="primary" onClick={() => openEditor()}>
            新建用例
          </Button>
        </Space>
      }
    >
      {env && !env.available && (
        <Alert
          style={{ marginBottom: 12 }}
          type="warning"
          showIcon
          message="未检测到 JMeter 运行时"
          description="用例的编辑与导入导出不受影响，但无法执行压测。请将 JMeter 解压到工程 server/jmeter/ 目录，或设置环境变量 JMETER_HOME 后重启服务。"
        />
      )}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={cases}
        rowSelection={{ selectedRowKeys: selected, onChange: (keys) => setSelected(keys as string[]) }}
        pagination={false}
      />

      <Drawer
        title={editing ? '编辑压测用例' : '新建压测用例'}
        width={900}
        open={open}
        onClose={() => setOpen(false)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={handleSubmit}>
              保存
            </Button>
          </Space>
        }
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            threads: 1,
            rampUp: 1,
            loops: 1,
            duration: 0,
            thinkTime: 0,
            onSampleError: 'continue',
            loadProfile: 'constant',
            variables: [],
            steps: [],
          }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="用例名称" rules={[{ required: true, message: '请输入用例名称' }]}>
                <Input placeholder="如：登录接口压测" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="description" label="描述">
                <Input placeholder="压测目的、目标环境等" />
              </Form.Item>
            </Col>
          </Row>

          <Divider titlePlacement="left" plain>
            压测配置（对应 JMeter 线程组）
          </Divider>
          <Row gutter={12}>
            <Col span={6}>
              <Form.Item
                name="loadProfile"
                label="加压方式"
                tooltip="固定并发：原生线程组；阶梯加压/目标并发：JMeter 插件线程组（需运行时已装对应插件）"
              >
                <Select
                  options={[
                    { value: 'constant', label: '固定并发' },
                    { value: 'stepping', label: '阶梯加压' },
                    { value: 'concurrency', label: '目标并发' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={18}>
              <Form.Item noStyle shouldUpdate={(prev, cur) => prev.loadProfile !== cur.loadProfile}>
                {({ getFieldValue }) => {
                  const lp = getFieldValue('loadProfile') ?? 'constant'
                  if (lp === 'stepping') {
                    return (
                      <Row gutter={12}>
                        <Col span={6}>
                          <Form.Item name={['stepping', 'initialDelay']} label="初始延迟(s)">
                            <InputNumber min={0} max={3600} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item name={['stepping', 'batchThreads']} label="每批+线程">
                            <InputNumber min={1} max={2000} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item name={['stepping', 'batchInterval']} label="每批间隔(s)">
                            <InputNumber min={1} max={3600} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={6}>
                          <Form.Item name={['stepping', 'flightTime']} label="峰值保持(s)">
                            <InputNumber min={0} max={86400} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                      </Row>
                    )
                  }
                  if (lp === 'concurrency') {
                    return (
                      <Row gutter={12}>
                        <Col span={8}>
                          <Form.Item name={['concurrency', 'steps']} label="阶梯数">
                            <InputNumber min={1} max={1000} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={8}>
                          <Form.Item name={['concurrency', 'holdTarget']} label="达标保持">
                            <InputNumber min={0} max={86400} style={{ width: '100%' }} />
                          </Form.Item>
                        </Col>
                        <Col span={8}>
                          <Form.Item name={['concurrency', 'unit']} label="单位">
                            <Select
                              options={[
                                { value: 'S', label: '秒' },
                                { value: 'M', label: '分' },
                                { value: 'H', label: '时' },
                                { value: 'D', label: '天' },
                              ]}
                            />
                          </Form.Item>
                        </Col>
                      </Row>
                    )
                  }
                  return null
                }}
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={5}>
              <Form.Item name="threads" label="并发线程数" rules={[{ required: true, message: '必填' }]}>
                <InputNumber min={1} max={2000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="rampUp" label="Ramp-Up（秒）" tooltip="多少秒内把线程全部拉起">
                <InputNumber min={1} max={3600} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="loops" label="循环次数" tooltip="持续时长 > 0 时按时间压测，此项失效">
                <InputNumber min={1} max={100000} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="duration" label="持续时长（秒）" tooltip="填 0 表示按循环次数；> 0 表示在时长内循环压测">
                <InputNumber min={0} max={86400} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={4}>
              <Form.Item name="thinkTime" label="思考时间（毫秒）" tooltip="每个请求后的停顿">
                <InputNumber min={0} max={600000} step={100} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={12}>
            <Col span={8}>
              <Form.Item name="onSampleError" label="请求出错时">
                <Select options={ON_ERROR_OPTIONS} />
              </Form.Item>
            </Col>
          </Row>

          <KeyValueEditor
            name="variables"
            label="自定义变量（JMeter 用户定义变量）"
            keyPlaceholder="变量名，如 baseUrl"
            valuePlaceholder="变量值，如 http://127.0.0.1:4000"
          />
          <div style={{ color: '#999', fontSize: 12, margin: '4px 0 8px' }}>
            在下面的请求 URL / 请求头 / 请求体中可用 <code>{'${变量名}'}</code> 引用，替换由 JMeter 执行时完成。
          </div>

          <Divider titlePlacement="left" plain>
            请求步骤（对应 JMeter HTTP 请求）
          </Divider>
          <Form.List name="steps">
            {(fields, { add, remove, move }) => (
              <div>
                {fields.map((field, index) => (
                  <Card
                    key={field.key}
                    size="small"
                    style={{ marginBottom: 12 }}
                    title={
                      <Space>
                        <span>请求 {index + 1}</span>
                        <Form.Item name={[field.name, 'name']} noStyle>
                          <Input variant="borderless" placeholder="请求名称（报告中作为接口名）" style={{ width: 320 }} />
                        </Form.Item>
                      </Space>
                    }
                    extra={
                      <Space>
                        <Form.Item name={[field.name, 'enabled']} valuePropName="checked" noStyle>
                          <Checkbox>启用</Checkbox>
                        </Form.Item>
                        <Button size="small" type="text" disabled={index === 0} onClick={() => move(index, index - 1)}>
                          上移
                        </Button>
                        <Button size="small" type="text" disabled={index === fields.length - 1} onClick={() => move(index, index + 1)}>
                          下移
                        </Button>
                        <Popconfirm title="确认删除该请求？" onConfirm={() => remove(field.name)}>
                          <Button size="small" type="text" danger>
                            删除
                          </Button>
                        </Popconfirm>
                      </Space>
                    }
                  >
                    <Form.Item
                      name={[field.name, 'url']}
                      label="请求 URL"
                      rules={[{ required: true, message: '请输入压测目标 URL' }]}
                    >
                      <Input placeholder="http://127.0.0.1:4000/api/auth/login 或 ${baseUrl}/api/auth/login" />
                    </Form.Item>
                    <Row gutter={12}>
                      <Col span={6}>
                        <Form.Item name={[field.name, 'method']} label="请求方法">
                          <Select options={METHOD_OPTIONS} />
                        </Form.Item>
                      </Col>
                    </Row>
                    <KeyValueEditor name={[field.name, 'headers']} label="请求头" keyPlaceholder="Header 名" valuePlaceholder="Header 值" />
                    <div style={{ height: 8 }} />
                    <KeyValueEditor name={[field.name, 'query']} label="Query 参数" keyPlaceholder="参数名" valuePlaceholder="参数值" />
                    <Form.Item
                      name={[field.name, 'body']}
                      label="请求体（raw，填写后优先于 Query 参数）"
                      style={{ marginTop: 12 }}
                    >
                      <Input.TextArea rows={4} placeholder='{"username":"admin","password":"admin@123"}' style={{ fontFamily: 'monospace' }} />
                    </Form.Item>

                    <div style={{ fontWeight: 500, marginBottom: 8 }}>断言（判定请求是否成功）</div>
                    <Form.List name={[field.name, 'assertions']}>
                      {(aFields, { add: addA, remove: removeA }) => (
                        <div>
                          {aFields.map((af) => (
                            <Space key={af.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
                              <Form.Item name={[af.name, 'type']} style={{ marginBottom: 0 }}>
                                <Select options={ASSERT_TYPE} style={{ width: 120 }} />
                              </Form.Item>
                              <Form.Item name={[af.name, 'operator']} style={{ marginBottom: 0 }}>
                                <Select options={ASSERT_OP} style={{ width: 90 }} />
                              </Form.Item>
                              <Form.Item
                                name={[af.name, 'expected']}
                                rules={[{ required: true, message: '请输入期望值' }]}
                                style={{ marginBottom: 0 }}
                              >
                                <Input placeholder="如 200 或响应包含的文本" style={{ width: 300 }} />
                              </Form.Item>
                              <Button type="text" danger onClick={() => removeA(af.name)}>
                                删除
                              </Button>
                            </Space>
                          ))}
                          <Button
                            type="dashed"
                            onClick={() => addA({ type: 'responseCode', operator: 'equals', expected: '200' })}
                            block
                          >
                            添加断言
                          </Button>
                        </div>
                      )}
                    </Form.List>
                  </Card>
                ))}
                <Space>
                  <Button type="primary" ghost onClick={() => add(newStep())}>
                    添加请求步骤
                  </Button>
                </Space>
              </div>
            )}
          </Form.List>
        </Form>
      </Drawer>
    </Card>
  )
}
