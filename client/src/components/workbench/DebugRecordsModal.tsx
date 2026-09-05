/**
 * 调试记录弹窗（PRD 2.2）：全局调试记录列表 + 筛选 + 详情 + 删除。
 */
import { useEffect, useState } from 'react'
import { Button, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../../api/client'
import type { DebugRecord } from '../../api/types'

interface Props {
  projectId?: string
  open: boolean
  onClose: () => void
}

const STATUS_COLOR: Record<string, string> = { success: 'green', fail: 'red', error: 'orange', pending: 'default' }

export default function DebugRecordsModal({ projectId, open, onClose }: Props) {
  const [records, setRecords] = useState<DebugRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<string | undefined>()

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setRecords(await api.listDebugRecords(projectId, result ? { result } : undefined))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, projectId, result])

  const remove = async (id: string) => {
    try {
      await api.deleteDebugRecord(id)
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<DebugRecord> = [
    { title: '用例', dataIndex: 'caseNameSnapshot' },
    {
      title: '结果',
      dataIndex: 'result',
      render: (r: string) => <Tag color={STATUS_COLOR[r]}>{r}</Tag>,
    },
    { title: '耗时', dataIndex: 'totalDuration', render: (v: number) => `${v}ms` },
    {
      title: '时间',
      dataIndex: 'createdAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '操作',
      render: (_, record) => (
        <Popconfirm title="确认删除？" onConfirm={() => remove(record.id)}>
          <Button size="small" type="link" danger>
            删除
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <Modal title="调试记录" open={open} onCancel={onClose} footer={null} width={820}>
      <Space style={{ marginBottom: 12 }}>
        <span>结果：</span>
        <Select
          style={{ width: 140 }}
          allowClear
          placeholder="全部"
          value={result}
          options={[
            { value: 'success', label: '成功' },
            { value: 'fail', label: '失败' },
            { value: 'error', label: '错误' },
          ]}
          onChange={setResult}
        />
      </Space>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        size="small"
        expandable={{
          expandedRowRender: (record) => {
            const steps = (record.stepResults ?? []) as Array<{ name: string; type: string; status: string; message: string }>
            return (
              <div>
                {steps.map((s, i) => (
                  <div key={i} style={{ padding: '4px 0', fontSize: 12 }}>
                    <Tag color={STATUS_COLOR[s.status] ?? 'default'}>{s.status}</Tag>
                    <span style={{ marginRight: 8 }}>{s.name}</span>
                    <span style={{ color: '#999' }}>{s.message}</span>
                  </div>
                ))}
              </div>
            )
          },
        }}
      />
    </Modal>
  )
}
