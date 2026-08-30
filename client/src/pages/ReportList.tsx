/**
 * 测试报告页
 *
 * 职责：展示指定项目下的历史执行报告列表。
 * 每条报告可展开查看各步骤的执行状态、断言结果与错误信息。
 */
import { useEffect, useState } from 'react'
import { Alert, Card, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useParams } from 'react-router-dom'
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
    <Card title="测试报告">
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
  )
}
