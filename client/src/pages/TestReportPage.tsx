/**
 * 测试报告页（接口自动化二级菜单）。
 * 选择测试任务，查看各次执行报告；参照 UI 自动化报告：统计卡片 + 状态分布/耗时趋势图表 + 报告明细。
 */
import { useEffect, useRef, useState } from 'react'
import { Button, Card, Col, Empty, Row, Select, Space, Statistic, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useSearchParams } from 'react-router-dom'
import { Pie, Line } from '@ant-design/plots'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import { exportPdf } from '../utils/pdf'
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
  const printRef = useRef<HTMLDivElement>(null)
  const taskName = tasks.find((t) => t.id === taskId)?.name ?? '接口测试'

  // 加载任务列表（用于任务选择器）
  useEffect(() => {
    if (!projectId) return
    api
      .listTestTasks(projectId)
      .then(setTasks)
      .catch((e) => message.error(getErrorMessage(e)))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // 同步：URL 参数（taskId）变化时更新选中任务（同一路由下切换）
  useEffect(() => {
    const id = searchParams.get('taskId') ?? undefined
    setTaskId((prev) => (prev === id ? prev : id))
  }, [searchParams])

  // 默认选中第一个任务（无 taskId 时自动加载其报告）
  useEffect(() => {
    if (!taskId && tasks.length > 0) {
      setTaskId(tasks[0].id)
      setSearchParams({ taskId: tasks[0].id })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tasks, taskId])

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

  // ---------- 统计数据（基于所选任务的各次执行） ----------
  const total = runs.length
  const passCount = runs.filter((r) => r.result === 'PASS').length
  const failCount = runs.filter((r) => r.result === 'FAIL').length
  const errorCount = runs.filter((r) => r.result === 'ERROR').length
  const passRate = total > 0 ? Math.round((passCount / total) * 100) : 0
  const avgDuration = total > 0 ? Math.round(runs.reduce((s, r) => s + r.duration, 0) / total) : 0

  // 状态分布环形图数据
  const pieData = [
    { type: '通过', value: passCount },
    { type: '失败', value: failCount },
    { type: '错误', value: errorCount },
  ].filter((d) => d.value > 0)

  // 耗时趋势：最近 10 次，按时间先后排列
  const trendData = [...runs]
    .reverse()
    .slice(-10)
    .map((r, i) => ({ index: `第${i + 1}次`, duration: r.duration }))

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
          <Button onClick={() => exportPdf(`${taskName} · 测试报告`, printRef.current)}>导出 PDF</Button>
        </Space>
      }
    >
      <div ref={printRef}>
      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={4}>
          <Card size="small">
            <Statistic title="执行总数" value={total} />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic title="通过" value={passCount} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={4}>
          <Card size="small">
            <Statistic title="失败/错误" value={failCount + errorCount} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="通过率"
              value={passRate}
              suffix="%"
              valueStyle={{ color: passRate >= 80 ? '#52c41a' : '#fa8c16' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="平均耗时" value={avgDuration} suffix="ms" />
          </Card>
        </Col>
      </Row>

      {/* 图表区 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={10}>
          <Card title="状态分布" size="small">
            {pieData.length > 0 ? (
              <Pie
                data={pieData}
                angleField="value"
                colorField="type"
                innerRadius={0.6}
                height={280}
                label={{ text: 'value', position: 'outside' }}
                legend={{ color: { position: 'bottom' } }}
                scale={{ color: { range: ['#52c41a', '#ff4d4f', '#fa8c16'] } }}
              />
            ) : (
              <div style={{ height: 280, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
                暂无数据
              </div>
            )}
          </Card>
        </Col>
        <Col span={14}>
          <Card title="耗时趋势（最近 10 次）" size="small">
            {trendData.length > 0 ? (
              <Line
                data={trendData}
                xField="index"
                yField="duration"
                height={280}
                point={{ shapeField: 'circle', sizeField: 4 }}
                style={{ lineWidth: 2 }}
              />
            ) : (
              <div style={{ height: 280, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999' }}>
                暂无数据
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* 报告明细：执行记录列表 + 选中记录详情 */}
      <Card title="报告明细" size="small" style={{ marginBottom: 16 }}>
        <Table rowKey="id" loading={loading} columns={runColumns} dataSource={runs} size="small" pagination={false} />
      </Card>

      {activeRun ? (
        <Card
          size="small"
          title={
            <Space>
              <span>本次详情</span>
              <StatusTag status={activeRun.result} />
              <span style={{ color: '#999' }}>开始于 {new Date(activeRun.startedAt).toLocaleString()}</span>
            </Space>
          }
        >
          <Space size={12} style={{ marginBottom: 12 }}>
            <span style={{ fontWeight: 600 }}>汇总：</span>
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
        </Card>
      ) : (
        <Empty description="请选择任务查看报告" />
      )}
      </div>
    </Card>
  )
}
