/**
 * 性能测试 → 测试报告
 *
 * 展示每次 JMeter 压测的结果：汇总指标（含 P90/P95/P99、TPS、错误率）、
 * 响应时间与 TPS 趋势、成功失败分布、分接口统计与错误 TOP。
 */
import { useCallback, useEffect, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Popconfirm,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Line, Pie } from '@ant-design/plots'
import { api, getErrorMessage } from '../api/client'
import { useProject } from '../context/ProjectContext'
import type { PerfErrorItem, PerfLabelStat, PerfMetrics, PerfReport, PerfSeriesPoint } from '../api/types'

const STATUS_TAG: Record<string, { color: string; text: string }> = {
  success: { color: 'green', text: '通过' },
  failed: { color: 'orange', text: '有失败' },
  error: { color: 'red', text: '执行失败' },
}

const METRIC_ZERO: PerfMetrics = {
  samples: 0,
  errors: 0,
  errorRate: 0,
  avg: 0,
  min: 0,
  max: 0,
  median: 0,
  p90: 0,
  p95: 0,
  p99: 0,
  throughput: 0,
}

/** 安全读取 summary（防御历史数据缺字段） */
function metricsOf(report: { summary?: Partial<PerfMetrics> | null }): PerfMetrics {
  return { ...METRIC_ZERO, ...(report.summary ?? {}) }
}

function formatTime(value: string): string {
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}

export default function PerfReportPage() {
  const { projectId } = useProject()
  const [reports, setReports] = useState<PerfReport[]>([])
  const [loading, setLoading] = useState(false)
  const [detail, setDetail] = useState<PerfReport | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const load = useCallback(async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setReports(await api.listPerfReports(projectId))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }, [projectId])

  useEffect(() => {
    load()
  }, [load])

  const openDetail = async (id: string) => {
    setDetailLoading(true)
    try {
      setDetail(await api.getPerfReport(id))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setDetailLoading(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await api.deletePerfReport(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<PerfReport> = [
    {
      title: '用例名称',
      dataIndex: 'name',
      render: (v: string, r) => (r.perfCase?.deletedAt ? <Tooltip title="用例已删除">{v}</Tooltip> : v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (v: string, r) => {
        const tag = STATUS_TAG[v] ?? { color: 'default', text: v }
        return v === 'error' && r.message ? (
          <Tooltip title={r.message}>
            <Tag color={tag.color}>{tag.text}</Tag>
          </Tooltip>
        ) : (
          <Tag color={tag.color}>{tag.text}</Tag>
        )
      },
    },
    { title: '请求数', width: 90, render: (_, r) => metricsOf(r).samples },
    {
      title: '错误率',
      width: 90,
      render: (_, r) => {
        const m = metricsOf(r)
        return <span style={{ color: m.errorRate > 0 ? '#ff4d4f' : undefined }}>{m.errorRate}%</span>
      },
    },
    { title: '平均RT(ms)', width: 110, render: (_, r) => metricsOf(r).avg },
    { title: 'P95(ms)', width: 100, render: (_, r) => metricsOf(r).p95 },
    { title: 'TPS', width: 90, render: (_, r) => metricsOf(r).throughput },
    { title: '耗时(s)', width: 90, render: (_, r) => Math.round(r.duration / 1000) },
    { title: '开始时间', dataIndex: 'startedAt', width: 180, render: formatTime },
    {
      title: '操作',
      width: 130,
      render: (_, record) => (
        <Space size={0}>
          <Button size="small" type="link" onClick={() => openDetail(record.id)}>
            详情
          </Button>
          <Popconfirm title="确认删除该报告？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" type="link" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const detailSummary = detail ? metricsOf(detail) : METRIC_ZERO
  const series = (detail?.series ?? []) as PerfSeriesPoint[]
  const rtData = series.map((p) => ({ t: p.t, value: p.avg, type: '平均响应(ms)' }))
  const maxData = series.map((p) => ({ t: p.t, value: p.max, type: '最大响应(ms)' }))
  const tpsData = series.map((p) => ({ t: p.t, value: p.samples, type: 'TPS' }))
  const pieData = detail
    ? [
        { type: '成功', value: Math.max(0, detailSummary.samples - detailSummary.errors) },
        { type: '失败', value: detailSummary.errors },
      ].filter((d) => d.value > 0)
    : []

  const labelColumns: ColumnsType<PerfLabelStat> = [
    { title: '接口（请求名称）', dataIndex: 'label' },
    { title: '请求数', dataIndex: 'samples', width: 90 },
    { title: '错误数', dataIndex: 'errors', width: 90 },
    {
      title: '错误率',
      dataIndex: 'errorRate',
      width: 90,
      render: (v: number) => <span style={{ color: v > 0 ? '#ff4d4f' : undefined }}>{v}%</span>,
    },
    { title: '平均(ms)', dataIndex: 'avg', width: 100 },
    { title: '最小', dataIndex: 'min', width: 80 },
    { title: '最大', dataIndex: 'max', width: 80 },
    { title: 'P90', dataIndex: 'p90', width: 90 },
    { title: 'P95', dataIndex: 'p95', width: 90 },
    { title: 'P99', dataIndex: 'p99', width: 90 },
    { title: 'TPS', dataIndex: 'throughput', width: 90 },
  ]

  const errorColumns: ColumnsType<PerfErrorItem> = [
    { title: '响应码', dataIndex: 'code', width: 100 },
    { title: '错误信息', dataIndex: 'message' },
    { title: '次数', dataIndex: 'count', width: 90 },
  ]

  return (
    <Card
      title="性能测试 · 测试报告"
      extra={
        <Space>
          <Button onClick={load}>刷新</Button>
        </Space>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={reports} />

      <Drawer
        title={detail ? `压测报告：${detail.name}` : '压测报告'}
        width={1080}
        open={Boolean(detail) || detailLoading}
        onClose={() => setDetail(null)}
        destroyOnHidden
        loading={detailLoading}
      >
        {detail && (
          <div>
            {detail.status === 'error' && (
              <Alert style={{ marginBottom: 16 }} type="error" showIcon message="压测执行失败" description={detail.message} />
            )}
            {detail.status === 'failed' && (
              <Alert style={{ marginBottom: 16 }} type="warning" showIcon message={detail.message ?? '存在失败请求'} />
            )}

            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={4}>
                <Card size="small">
                  <Statistic title="请求总数" value={detailSummary.samples} />
                </Card>
              </Col>
              <Col span={4}>
                <Card size="small">
                  <Statistic title="失败数" value={detailSummary.errors} suffix={`(${detailSummary.errorRate}%)`} valueStyle={{ color: detailSummary.errors ? '#ff4d4f' : undefined }} />
                </Card>
              </Col>
              <Col span={4}>
                <Card size="small">
                  <Statistic title="平均响应" value={detailSummary.avg} suffix="ms" />
                </Card>
              </Col>
              <Col span={4}>
                <Card size="small">
                  <Statistic title="P95" value={detailSummary.p95} suffix="ms" />
                </Card>
              </Col>
              <Col span={4}>
                <Card size="small">
                  <Statistic title="TPS" value={detailSummary.throughput} />
                </Card>
              </Col>
              <Col span={4}>
                <Card size="small">
                  <Statistic title="总耗时" value={Math.round(detail.duration / 1000)} suffix="s" />
                </Card>
              </Col>
            </Row>

            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={8}>
                <Card title="成功 / 失败分布" size="small">
                  {pieData.length ? (
                    <Pie
                      data={pieData}
                      angleField="value"
                      colorField="type"
                      innerRadius={0.6}
                      height={260}
                      label={{ text: 'value', position: 'outside' }}
                      legend={{ color: { position: 'bottom' } }}
                      scale={{ color: { range: ['#52c41a', '#ff4d4f'] } }}
                    />
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据" style={{ padding: '80px 0' }} />
                  )}
                </Card>
              </Col>
              <Col span={16}>
                <Card title="响应时间趋势（ms）" size="small">
                  {rtData.length ? (
                    <Line
                      data={[...rtData, ...maxData]}
                      xField="t"
                      yField="value"
                      colorField="type"
                      height={260}
                      style={{ lineWidth: 2 }}
                      legend={{ color: { position: 'top-right' } }}
                      axis={{ x: { title: '秒' }, y: { title: 'ms' } }}
                    />
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无时序数据" style={{ padding: '80px 0' }} />
                  )}
                </Card>
              </Col>
            </Row>

            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={24}>
                <Card title="吞吐量趋势（TPS）" size="small">
                  {tpsData.length ? (
                    <Line
                      data={tpsData}
                      xField="t"
                      yField="value"
                      height={220}
                      style={{ lineWidth: 2 }}
                      axis={{ x: { title: '秒' }, y: { title: '请求/秒' } }}
                    />
                  ) : (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无时序数据" style={{ padding: '60px 0' }} />
                  )}
                </Card>
              </Col>
            </Row>

            <Card title="分接口统计" size="small" style={{ marginBottom: 16 }}>
              <Table
                rowKey="label"
                size="small"
                pagination={false}
                columns={labelColumns}
                dataSource={(detail.labels ?? []) as PerfLabelStat[]}
              />
            </Card>

            <Card title="错误 TOP" size="small">
              <Table
                rowKey={(r) => `${r.code}-${r.message}`}
                size="small"
                pagination={false}
                columns={errorColumns}
                dataSource={(detail.errors ?? []) as PerfErrorItem[]}
                locale={{ emptyText: '没有失败请求' }}
              />
            </Card>
          </div>
        )}
      </Drawer>
    </Card>
  )
}
