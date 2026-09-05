/**
 * 测试执行页（接口自动化二级菜单）。
 * 创建测试任务：收集接口用例、手动排序（上移/下移/置顶/置尾）、配置执行参数/环境/执行机/重试/超时。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { CaseInfo, Environment, KeyValue, TestTask } from '../api/types'

const STATUS_COLOR: Record<string, string> = {
  PASS: 'green', FAIL: 'red', ERROR: 'volcano', pending: 'default', SKIP: 'default',
}

function StatusTag({ status }: { status: string }) {
  return <Tag color={STATUS_COLOR[status] ?? 'default'}>{status}</Tag>
}

export default function TestExecutionPage() {
  const { projectId } = useProject()
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<TestTask[]>([])
  const [loading, setLoading] = useState(false)
  const [runningId, setRunningId] = useState<string | null>(null)

  // 任务表单
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<TestTask | null>(null)
  const [form] = Form.useForm()
  const [cases, setCases] = useState<CaseInfo[]>([])
  const [envs, setEnvs] = useState<Environment[]>([])

  // 用例排序 + 执行参数 + 执行机地址（状态管理，非 Form 字段）
  const [selectedCaseIds, setSelectedCaseIds] = useState<string[]>([])
  const [casePick, setCasePick] = useState<string | undefined>()
  const [vars, setVars] = useState<KeyValue[]>([])
  const [baseUrl, setBaseUrl] = useState('')

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setTasks(await api.listTestTasks(projectId))
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

  const prepareForm = async () => {
    if (!projectId) return
    const [cList, eList] = await Promise.all([
      api.listCaseLibraryCases(projectId),
      api.listEnvironments(projectId),
    ])
    setCases(cList)
    setEnvs(eList)
  }

  const openCreate = async () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ executeMode: 'sequential', retryCount: 0, timeout: 300000, enabled: true })
    setSelectedCaseIds([])
    setVars([])
    setBaseUrl('')
    try {
      await prepareForm()
      setFormOpen(true)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const openEdit = async (t: TestTask) => {
    setEditing(t)
    try {
      await prepareForm()
      form.setFieldsValue({
        name: t.name,
        description: t.description,
        environmentId: t.environmentId,
        executeMode: t.executeMode,
        retryCount: t.retryCount,
        timeout: t.timeout,
        enabled: t.enabled,
      })
      setSelectedCaseIds(t.caseIds ?? [])
      setVars((t.variables ?? []) as KeyValue[])
      setBaseUrl(t.baseUrl ?? '')
      setFormOpen(true)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const submit = async () => {
    const values = await form.validateFields()
    if (selectedCaseIds.length === 0) {
      message.warning('请至少添加一个用例')
      return
    }
    try {
      const payload = {
        ...values,
        caseIds: selectedCaseIds,
        variables: vars.filter((v) => v.key),
        baseUrl: baseUrl.trim() || null,
      }
      if (editing) await api.updateTestTask(editing.id, payload)
      else await api.createTestTask(projectId!, payload)
      message.success('保存成功')
      setFormOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const remove = async (id: string) => {
    try {
      await api.deleteTestTask(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const run = async (t: TestTask) => {
    setRunningId(t.id)
    try {
      const run = await api.runTestTask(t.id)
      const s = run.summary
      message.success(s ? `执行完成：通过 ${s.passed} / 失败 ${s.failed} / 错误 ${s.error}` : '执行完成')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunningId(null)
    }
  }

  // ---------- 用例排序 ----------
  const moveCase = (index: number, dir: -1 | 1) => {
    setSelectedCaseIds((prev) => {
      const target = index + dir
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }
  const moveToTop = (index: number) => setSelectedCaseIds((prev) => [prev[index], ...prev.filter((_, i) => i !== index)])
  const moveToBottom = (index: number) => setSelectedCaseIds((prev) => [...prev.filter((_, i) => i !== index), prev[index]])
  const removeCase = (index: number) => setSelectedCaseIds((prev) => prev.filter((_, i) => i !== index))
  const addCase = (id: string) => setSelectedCaseIds((prev) => (prev.includes(id) ? prev : [...prev, id]))

  const availableCases = cases.filter((c) => !selectedCaseIds.includes(c.id))

  const taskColumns: ColumnsType<TestTask> = [
    { title: '任务名', dataIndex: 'name' },
    { title: '用例数', render: (_, r) => r.caseIds.length },
    {
      title: '模式',
      dataIndex: 'executeMode',
      render: (m: string) => (m === 'parallel' ? <Tag color="blue">并行</Tag> : <Tag>串行</Tag>),
    },
    {
      title: '最近结果',
      render: (_, r) => (r.latestRun ? <StatusTag status={r.latestRun.result} /> : <span style={{ color: '#999' }}>未执行</span>),
    },
    {
      title: '操作',
      width: 220,
      render: (_, r) => (
        <Space size={0}>
          <Button size="small" type="link" loading={runningId === r.id} onClick={() => run(r)}>执行</Button>
          <Button size="small" type="link" onClick={() => openEdit(r)}>编辑</Button>
          <Button size="small" type="link" onClick={() => navigate(`/test-reports?taskId=${r.id}`)}>报告</Button>
          <Popconfirm title="确认删除？" onConfirm={() => remove(r.id)}>
            <Button size="small" type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Card
      title="测试执行"
      extra={
        <Space>
          <Button type="primary" onClick={openCreate}>新建任务</Button>
          <Button onClick={load}>刷新</Button>
        </Space>
      }
    >
      <Table rowKey="id" loading={loading} columns={taskColumns} dataSource={tasks} size="small" pagination={false} />

      {/* 任务表单 */}
      <Modal
        title={editing ? '编辑任务' : '新建任务'}
        open={formOpen}
        onOk={submit}
        onCancel={() => setFormOpen(false)}
        width={680}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="任务名" rules={[{ required: true, message: '请输入任务名' }]}>
            <Input placeholder="如：登录流程回归" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>

          {/* 用例收集 + 排序 */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>用例列表（{selectedCaseIds.length}，按顺序执行）</div>
            <Select
              style={{ width: '100%', marginBottom: 8 }}
              placeholder="选择用例添加"
              value={casePick}
              onChange={(v) => { if (v) { addCase(v); setCasePick(undefined) } }}
              options={availableCases.map((c) => ({ value: c.id, label: c.name }))}
              showSearch
              optionFilterProp="label"
            />
            {selectedCaseIds.length === 0 && <div style={{ color: '#999' }}>请先添加用例</div>}
            {selectedCaseIds.map((cid, i) => {
              const c = cases.find((x) => x.id === cid)
              return (
                <div key={cid} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '4px 0', borderBottom: '1px solid #f0f0f0' }}>
                  <span style={{ width: 24, color: '#999', textAlign: 'center' }}>{i + 1}</span>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c?.name ?? cid}</span>
                  <Space size={0}>
                    <Button size="small" type="text" disabled={i === 0} title="上移" onClick={() => moveCase(i, -1)}>↑</Button>
                    <Button size="small" type="text" disabled={i === selectedCaseIds.length - 1} title="下移" onClick={() => moveCase(i, 1)}>↓</Button>
                    <Button size="small" type="text" disabled={i === 0} title="置顶" onClick={() => moveToTop(i)}>⏫</Button>
                    <Button size="small" type="text" disabled={i === selectedCaseIds.length - 1} title="置尾" onClick={() => moveToBottom(i)}>⏬</Button>
                    <Button size="small" type="text" danger onClick={() => removeCase(i)}>删除</Button>
                  </Space>
                </div>
              )
            })}
          </div>

          <Form.Item name="environmentId" label="执行环境">
            <Select
              allowClear
              placeholder="选择执行环境（使用其变量与 Base URL）"
              options={envs.map((e) => ({ value: e.id, label: `${e.name}${e.baseUrl ? ' (' + e.baseUrl + ')' : ''}` }))}
            />
          </Form.Item>

          {/* 执行参数（任务级变量） */}
          <div style={{ marginBottom: 16 }}>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>执行参数（任务级变量，优先级高于环境/全局变量）</div>
            {vars.map((kv, i) => (
              <Space key={i} size={4} style={{ marginBottom: 4, display: 'flex' }}>
                <Input size="small" style={{ width: 180 }} placeholder="参数名" value={kv.key} onChange={(e) => setVars((prev) => prev.map((x, j) => (j === i ? { ...x, key: e.target.value } : x)))} />
                <Input size="small" style={{ width: 220 }} placeholder="参数值" value={kv.value} onChange={(e) => setVars((prev) => prev.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))} />
                <Button size="small" type="text" danger onClick={() => setVars((prev) => prev.filter((_, j) => j !== i))}>删</Button>
              </Space>
            ))}
            <Button size="small" type="dashed" block onClick={() => setVars((prev) => [...prev, { key: '', value: '' }])}>添加参数</Button>
          </div>

          <Form.Item label="执行机地址（Base URL）">
            <Input placeholder="如 http://192.168.1.100:8080，留空则使用执行环境的 Base URL" value={baseUrl} onChange={(e) => setBaseUrl(e.target.value)} />
          </Form.Item>

          <Space size={16} style={{ display: 'flex' }}>
            <Form.Item name="executeMode" label="执行模式" style={{ minWidth: 140 }}>
              <Select options={[{ value: 'sequential', label: '串行' }, { value: 'parallel', label: '并行' }]} />
            </Form.Item>
            <Form.Item name="retryCount" label="失败重试次数">
              <InputNumber min={0} max={10} />
            </Form.Item>
            <Form.Item name="timeout" label="超时(ms)">
              <InputNumber min={0} step={10000} style={{ width: 130 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  )
}
