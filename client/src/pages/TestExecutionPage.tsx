/**
 * 测试执行页（接口自动化二级菜单）。
 * 收集接口用例编写的用例，组成测试任务并手动执行（串行/并行、失败重试、超时）。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { CaseInfo, Environment, TestTask } from '../api/types'

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
      message.success(s ? `执行完成：通过 ${s.passed} / 失败 ${s.failed} / 错误 ${s.error}` : '执行完成')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunningId(null)
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
              placeholder="从接口用例库选择"
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
    </Card>
  )
}
