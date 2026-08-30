/**
 * UI 自动化测试报告页：展示 UI 测试执行报告（独立于接口测试报告）。
 */
import { useEffect, useState } from 'react'
import { Alert, Card, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { UiReport } from '../api/types'

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

export default function UiReportList() {
  const { projectId } = useParams<{ projectId: string }>()
  const [reports, setReports] = useState<UiReport[]>([])
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      setReports(await api.listUiReports(projectId!))
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
    <Card title="UI 测试报告">
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
                        步骤 {i + 1}：{d.action}
                        {d.target ? `（${d.target}）` : ''}
                      </span>
                      <Tag color={STATUS_COLOR[d.status]}>{d.status}</Tag>
                    </Space>
                  }
                  style={{ marginBottom: 8 }}
                >
                  <Alert
                    type={d.status === 'PASS' ? 'success' : 'error'}
                    showIcon
                    message={d.message}
                    style={{ marginBottom: 8 }}
                  />
                  {d.screenshot && (
                    <img
                      src={`data:image/png;base64,${d.screenshot}`}
                      alt="失败截图"
                      style={{ maxWidth: '100%', border: '1px solid #eee' }}
                    />
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
