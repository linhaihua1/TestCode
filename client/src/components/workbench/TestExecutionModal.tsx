/**
 * 测试执行与报告弹窗（PRD 第 5 期）。
 * 测试任务 CRUD + 手动执行 + 执行报告查看。
 */
import { useEffect, useState } from 'react'
import {
  Button, Empty, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../../api/client'
import type { CaseInfo, CaseRunDetail, Environment, TestTask, TestTaskRun } from '../../api/types'

interface Props {
  projectId?: string
  open: boolean
  onClose: () => void
}

const STATUS_COLOR: Record<string, string> = {
  PASS: 'green', FAIL: 'red', ERROR: 'volcano', pending: 'default', SKIP: 'default',
}

function StatusTag({ status }: { status: string }) {
  return <Tag color={STATUS_COLOR[status] ?? 'default'}>{status}</Tag>
}

/** 把步骤结果转成 antd Table 可渲染的树形数据（控制器子步骤作为 children） */
function toStepRows(steps: CaseRunDetail['stepResults']): Array<Record<string, unknown>> {
  return (steps ?? []).map((s) => ({
    key: s.id,
    ...s,
    children: s.children ? toStepRows(s.children as CaseRunDetail['stepResults']) : undefined,
  }))
}

export default function TestExecutionModal({ projectId, open, onClose }: Props) {
  const [tasks, setTasks] = useState<TestTask[]>([])
  const [loading, setLoading] = useState(false)
  const [runningId, setRunningId] = useState<string | null>(null)

  // 任务表单
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<TestTask | null>(null)
  const [form] = Form.useForm()
  const [cases, setCases] = useState<CaseInfo[]>([])
  const [envs, setEnvs] = useState<Environment[]>([])

  // 报告
  const [reportTask, setReportTask] = useState<TestTask | null>(null)
  const [runs, setRuns] = useState<TestTaskRun[]>([])
  const [runsLoading, setRunsLoading] = useState(false)
  const [activeRun, setActiveRun] = useState<TestTaskRun | null>(null)

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
    if (open) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, projectId])

  // 打开新建/编辑表单前，预加载用例与环境选项
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
        caseIds: t.caseIds,
        environmentId: t.environmentId,
        executeMode: t.executeMode,
        retryCount: t.retryCount,
        timeout: t.timeout,
        enabled: t.enabled,
      })
      setFormOpen(true)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const submit = async () => {
    const values = await form.validateFields()
    try {
      if (editing) await api.updateTestTask(editing.id, values)
      else await api.createTestTask(projectId!, values)
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
      message.success(
        s ? `执行完成：通过 ${s.passed} / 失败 ${s.failed} / 错误 ${s.error}` : '执行完成',
      )
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunningId(null)
    }
  }

  const openReport = async (t: TestTask) => {
    setReportTask(t)
    setActiveRun(null)
    setRunsLoading(true)
    try {
      const list = await api.listTestTaskRuns(t.id)
      setRuns(list)
      setActiveRun(list[0] ?? null)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunsLoading(false)
    }
  }

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
          <Button size="small" type="link" loading={runningId === r.id} onClick={() => run(r)}>
            执行
          </Button>
          <Button size="small" type="link" onClick={() => openEdit(r)}>编辑</Button>
          <Button size="small" type="link" onClick={() => openReport(r)}>报告</Button>
          <Popconfirm title="确认删除？" onConfirm={() => remove(r.id)}>
            <Button size="small" type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const runColumns: ColumnsType<TestTaskRun> = [
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    { title: '结果', dataIndex: 'result', render: (v: string) => <StatusTag status={v} /> },
    { title: '耗时(ms)', dataIndex: 'duration' },
    {
      title: '通过/失败/错误',
      render: (_, r) => {
        const s = r.summary
        return s ? `${s.passed}/${s.failed}/${s.error}` : '-'
      },
    },
    {
      title: '操作',
      render: (_, r) => (
        <Button size="small" type="link" onClick={() => setActiveRun(r)}>查看</Button>
      ),
    },
  ]

  const stepColumns: ColumnsType<Record<string, unknown>> = [
    { title: '步骤', dataIndex: 'name' },
    {
      title: '类型',
      dataIndex: 'type',
      render: (v: string) => <Tag>{v}</Tag>,
    },
    { title: '状态', dataIndex: 'status', render: (v: string) => <StatusTag status={v} /> },
    { title: '说明', dataIndex: 'message' },
    {
      title: '断言',
      render: (_, r) => {
        const list = (r.assertions as Array<{ passed: boolean; message: string }>) ?? []
        if (list.length === 0) return '-'
        const passed = list.filter((a) => a.passed).length
        return `${passed}/${list.length}`
      },
    },
  ]

  const caseColumns: ColumnsType<CaseRunDetail> = [
    { title: '用例', dataIndex: 'caseName' },
    { title: '状态', dataIndex: 'status', render: (v: string) => <StatusTag status={v} /> },
    { title: '耗时(ms)', dataIndex: 'duration' },
    { title: '重试', dataIndex: 'retries' },
    {
      title: '错误',
      dataIndex: 'error',
      ellipsis: true,
      render: (v?: string) => (v ? <span style={{ color: '#cf1322' }}>{v}</span> : '-'),
    },
  ]

  const activeSummary = activeRun?.summary

  return (
    <Modal title="测试执行与报告" open={open} onCancel={onClose} footer={null} width={960}>
      <div style={{ marginBottom: 12 }}>
        <Space>
          <Button type="primary" onClick={openCreate}>新建任务</Button>
          <Button onClick={load}>刷新</Button>
        </Space>
      </div>
      <Table rowKey="id" loading={loading} columns={taskColumns} dataSource={tasks} size="small" pagination={false} />

      {/* 任务表单 */}
      <Modal
        title={editing ? '编辑任务' : '新建任务'}
        open={formOpen}
        onOk={submit}
        onCancel={() => setFormOpen(false)}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="任务名" rules={[{ required: true, message: '请输入任务名' }]}>
            <Input placeholder="如：登录流程回归" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="caseIds" label="选择用例" rules={[{ required: true, message: '请至少选择一个用例' }]}>
            <Select
              mode="multiple"
              placeholder="从用例库选择"
              optionFilterProp="label"
              options={cases.map((c) => ({ value: c.id, label: c.name }))}
            />
          </Form.Item>
          <Form.Item name="environmentId" label="执行环境">
            <Select
              allowClear
              placeholder="不选则仅用全局变量（无 baseUrl）"
              options={envs.map((e) => ({ value: e.id, label: `${e.name}${e.baseUrl ? ' (' + e.baseUrl + ')' : ''}` }))}
            />
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

      {/* 报告弹窗 */}
      <Modal
        title={`执行报告 - ${reportTask?.name ?? ''}`}
        open={!!reportTask}
        onCancel={() => setReportTask(null)}
        footer={null}
        width={980}
      >
        <Table rowKey="id" loading={runsLoading} columns={runColumns} dataSource={runs} size="small" pagination={false} />

        <div style={{ marginTop: 16 }}>
          {activeRun ? (
            <>
              <Space size={12} style={{ marginBottom: 12 }}>
                <span style={{ fontWeight: 600 }}>本次汇总：</span>
                <Tag>总数 {activeSummary?.total ?? 0}</Tag>
                <Tag color="green">通过 {activeSummary?.passed ?? 0}</Tag>
                <Tag color="red">失败 {activeSummary?.failed ?? 0}</Tag>
                <Tag color="volcano">错误 {activeSummary?.error ?? 0}</Tag>
                <span style={{ color: '#999' }}>耗时 {activeRun.duration}ms</span>
              </Space>
              <Table
                rowKey="caseId"
                columns={caseColumns}
                dataSource={activeRun.details}
                size="small"
                pagination={false}
                expandable={{
                  expandedRowRender: (rec) => (
                    <Table
                      rowKey="key"
                      columns={stepColumns}
                      dataSource={toStepRows(rec.stepResults)}
                      size="small"
                      pagination={false}
                    />
                  ),
                }}
              />
            </>
          ) : (
            <Empty description="暂无执行记录" />
          )}
        </div>
      </Modal>
    </Modal>
  )
}
