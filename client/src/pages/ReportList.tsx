import { useEffect, useState } from 'react'
import { Alert, Card, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { Report } from '../api/types'

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

export default function ReportList() {
  const { projectId } = useParams<{ projectId: string }>()
  const [reports, setReports] = useState<Report[]>([])
  const [loading, setLoading] = useState(false)

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

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  const columns: ColumnsType<Report> = [
    { title: '场景', dataIndex: 'name' },
    {
      title: '结果',
      dataIndex: 'status',
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
      render: (v: string) => new Date(v).toLocaleString(),
    },
  ]

  return (
    <Card title="测试报告">
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={reports}
        expandable={{
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
  )
}
