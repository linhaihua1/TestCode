/**
 * 测试报告页（接口自动化二级菜单）。
 * 选择测试任务，查看各次执行报告：汇总 + 用例明细 + 步骤树。
 */
import { useEffect, useState } from 'react'
import { Card, Empty, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useSearchParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { CaseRunDetail, TestTask, TestTaskRun } from '../api/types'

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

export default function TestReportPage() {
  const { projectId } = useProject()
  const [searchParams, setSearchParams] = useSearchParams()
  const [tasks, setTasks] = useState<TestTask[]>([])
  const [taskId, setTaskId] = useState<string | undefined>(searchParams.get('taskId') ?? undefined)
  const [runs, setRuns] = useState<TestTaskRun[]>([])
  const [activeRun, setActiveRun] = useState<TestTaskRun | null>(null)
  const [loading, setLoading] = useState(false)

  // 加载任务列表（用于任务选择器）
  useEffect(() => {
    if (!projectId) return
    api
      .listTestTasks(projectId)
      .then(setTasks)
      .catch((e) => message.error(getErrorMessage(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // 任务变化时加载其报告列表
  useEffect(() => {
    if (!taskId) {
      setRuns([])
      setActiveRun(null)
      return
    }
    setLoading(true)
    api
      .listTestTaskRuns(taskId)
      .then((list) => {
        setRuns(list)
        setActiveRun(list[0] ?? null)
      })
      .catch((e) => message.error(getErrorMessage(e)))
      .finally(() => setLoading(false))
  }, [taskId])

  const onTaskChange = (id?: string) => {
    setTaskId(id)
    setSearchParams(id ? { taskId: id } : {})
  }

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
        <span style={{ color: '#1677ff', cursor: 'pointer' }} onClick={() => setActiveRun(r)}>查看</span>
      ),
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

  const stepColumns: ColumnsType<Record<string, unknown>> = [
    { title: '步骤', dataIndex: 'name' },
    { title: '类型', dataIndex: 'type', render: (v: string) => <Tag>{v}</Tag> },
    { title: '状态', dataIndex: 'status', render: (v: string) => <StatusTag status={v} /> },
    { title: '说明', dataIndex: 'message' },
    {
      title: '断言',
      render: (_, r) => {
        const list = (r.assertions as Array<{ passed: boolean }>) ?? []
        if (list.length === 0) return '-'
        return `${list.filter((a) => a.passed).length}/${list.length}`
      },
    },
  ]

  const activeSummary = activeRun?.summary

  return (
    <Card
      title="测试报告"
      extra={
        <Space>
          <span style={{ color: '#999' }}>选择任务：</span>
          <Select
            style={{ width: 240 }}
            placeholder="选择测试任务"
            value={taskId}
            allowClear
            options={tasks.map((t) => ({ value: t.id, label: t.name }))}
            onChange={onTaskChange}
          />
        </Space>
      }
    >
      <Table rowKey="id" loading={loading} columns={runColumns} dataSource={runs} size="small" pagination={false} />

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
          <Empty description="请选择任务查看报告" />
        )}
      </div>
    </Card>
  )
}
