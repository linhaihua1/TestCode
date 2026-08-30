/**
 * 接口测试报告页：参考 Allure 的图表化概览。
 * 顶部为统计卡片（总数/通过率/耗时），中间为状态分布环形图与耗时趋势折线图，下方为报告明细列表。
 */
import { useEffect, useState } from 'react'
import { Alert, Card, Col, Row, Space, Statistic, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
import { Pie, Line } from '@ant-design/plots'
import { api, getErrorMessage } from '../api/client'
import type { Report } from '../api/types'

// 执行状态与标签颜色的映射
const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

export default function ReportList() {
  const { projectId } = useParams<{ projectId: string }>() // 当前项目 ID
  const [reports, setReports] = useState<Report[]>([]) // 报告列表数据
  const [loading, setLoading] = useState(false) // 表格加载状态

  // 加载当前项目下的报告列表
  const load = async () => {
    setLoading(true)
    try {
      setReports(await api.listReports(projectId!))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // 项目切换时重新拉取报告列表
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  // ---------- 统计数据 ----------
  const total = reports.length // 执行总数
  const passCount = reports.filter((r) => r.status === 'PASS').length // 通过数
  const failCount = reports.filter((r) => r.status === 'FAIL').length // 失败数
  const errorCount = reports.filter((r) => r.status === 'ERROR').length // 错误数
  const passRate = total > 0 ? Math.round((passCount / total) * 100) : 0 // 通过率
  const avgDuration = total > 0 ? Math.round(reports.reduce((s, r) => s + r.duration, 0) / total) : 0 // 平均耗时

  // 环形图数据：状态分布
  const pieData = [
    { type: '通过', value: passCount },
    { type: '失败', value: failCount },
    { type: '错误', value: errorCount },
  ].filter((d) => d.value > 0)

  // 折线图数据：最近 10 次执行的耗时趋势（按时间正序）
  const trendData = [...reports]
    .reverse()
    .slice(0, 10)
    .map((r, i) => ({ index: `第${i + 1}次`, duration: r.duration, status: r.status }))

  // 表格列定义
  const columns: ColumnsType<Report> = [
    { title: '场景', dataIndex: 'name' },
    {
      title: '结果',
      dataIndex: 'status',
      // 根据状态显示对应颜色的标签
      render: (s: string) => <Tag color={STATUS_COLOR[s]}>{s}</Tag>,
    },
    {
      title: '耗时',
      dataIndex: 'duration',
      render: (v: number) => `${v}ms`,
    },
    {
      title: '执行时间',
      dataIndex: 'startedAt',
      // 将 ISO 时间字符串格式化为本地可读时间
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  return (
    <div>
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

      {/* 报告明细列表 */}
      <Card title="报告明细">
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={reports}
          expandable={{
            // 展开行渲染：展示该次执行的各步骤明细
            expandedRowRender: (report) => (
              <div>
                {(report.details ?? []).map((d, i) => (
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
                    {/* 步骤出错时展示错误信息 */}
                    {d.error && <Alert type="error" message={d.error} style={{ marginBottom: 8 }} />}
                    {/* 逐条展示断言结果：通过为成功、未通过为错误 */}
                    {(d.assertions ?? []).map((a, j) => (
                      <Alert
                        key={j}
                        type={a.passed ? 'success' : 'error'}
                        showIcon
                        message={a.message}
                        style={{ marginBottom: 8 }}
                      />
                    ))}
                    {/* 无错误且无断言时给出占位提示 */}
                    {(!d.error && (d.assertions ?? []).length === 0) && (
                      <span style={{ color: '#999' }}>无断言</span>
                    )}
                  </Card>
                ))}
              </div>
            ),
          }}
        />
      </Card>
    </div>
  )
}
