/**
 * UI 自动化测试报告页：参考 Allure 的图表化概览（独立于接口测试报告）。
 */
import { useEffect, useRef, useState } from 'react'
import { Alert, Button, Card, Col, Row, Space, Statistic, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import { exportPdf } from '../utils/pdf'
import { Pie, Line } from '@ant-design/plots'
import type { UiReport, UiScenarioCaseResult, UiStepResult } from '../api/types'

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

/** 单个步骤结果（含错误信息与截图） */
function StepResultRow(props: { step: UiStepResult }) {
  const { step } = props
  return (
    <div style={{ marginBottom: 4 }}>
      <Alert
        type={step.status === 'PASS' ? 'success' : 'error'}
        showIcon
        message={`${step.action}${step.target ? `（${step.target}）` : ''}：${step.message}`}
        style={{ marginBottom: 4 }}
      />
      {step.screenshot && (
        <img
          src={`data:image/png;base64,${step.screenshot}`}
          alt="失败截图"
          style={{ maxWidth: '100%', border: '1px solid #eee' }}
        />
      )}
    </div>
  )
}

/** 判断详情项是「场景用例结果」还是「单步骤结果」 */
function isCaseResult(item: UiStepResult | UiScenarioCaseResult): item is UiScenarioCaseResult {
  return Array.isArray((item as UiScenarioCaseResult).steps)
}

export default function UiReportList() {
  const { projectId } = useProject()
  const [reports, setReports] = useState<UiReport[]>([])
  const [loading, setLoading] = useState(false)
  const printRef = useRef<HTMLDivElement>(null)

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setReports(await api.listUiReports(projectId))
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

  // ---------- 统计数据 ----------
  const total = reports.length
  const passCount = reports.filter((r) => r.status === 'PASS').length
  const failCount = reports.filter((r) => r.status === 'FAIL').length
  const errorCount = reports.filter((r) => r.status === 'ERROR').length
  const passRate = total > 0 ? Math.round((passCount / total) * 100) : 0
  const avgDuration = total > 0 ? Math.round(reports.reduce((s, r) => s + r.duration, 0) / total) : 0

  // 环形图数据
  const pieData = [
    { type: '通过', value: passCount },
    { type: '失败', value: failCount },
    { type: '错误', value: errorCount },
  ].filter((d) => d.value > 0)

  // 折线图数据：最近 10 次（按时间先后排列）
  const trendData = [...reports]
    .reverse()
    .slice(-10)
    .map((r, i) => ({ index: `第${i + 1}次`, duration: r.duration }))

  const columns: ColumnsType<UiReport> = [
    { title: '用例名称', dataIndex: 'name' },
    {
      title: '结果',
      dataIndex: 'status',
      render: (s: string) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    { title: '耗时', dataIndex: 'duration', render: (v: number) => `${v}ms` },
    {
      title: '执行时间',
      dataIndex: 'startedAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  return (
    <Card
      title="UI 测试报告"
      extra={<Button onClick={() => exportPdf('UI 测试报告', printRef.current)}>导出 PDF</Button>}
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

      {/* 报告明细 */}
      <Card title="报告明细">
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={reports}
          expandable={{
            expandedRowRender: (report) => (
              <div>
                {(report.details ?? []).map((item, i) => {
                  if (isCaseResult(item)) {
                    // 场景/批量执行：每个 item 是一个 UI 用例，内含步骤明细
                    return (
                      <Card
                        key={i}
                        size="small"
                        title={
                          <Space>
                            <span>用例 {i + 1}：{item.name}</span>
                            <Tag color={STATUS_COLOR[item.status]}>{item.status}</Tag>
                          </Space>
                        }
                        style={{ marginBottom: 8 }}
                      >
                        {item.steps.map((st, j) => (
                          <StepResultRow key={j} step={st} />
                        ))}
                      </Card>
                    )
                  }
                  // 单用例执行：每个 item 是一个步骤
                  return (
                    <Card
                      key={i}
                      size="small"
                      title={
                        <Space>
                          <span>
                            步骤 {i + 1}：{item.action}
                            {item.target ? `（${item.target}）` : ''}
                          </span>
                          <Tag color={STATUS_COLOR[item.status]}>{item.status}</Tag>
                        </Space>
                      }
                      style={{ marginBottom: 8 }}
                    >
                      <StepResultRow step={item} />
                    </Card>
                  )
                })}
              </div>
            ),
          }}
        />
      </Card>
      </div>
    </Card>
  )
}
