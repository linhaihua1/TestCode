/**
 * 调试记录弹窗：列表 + 筛选 + 详情 + 一键重放 + 对比 + 删除。
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

type CompareData = Awaited<ReturnType<typeof api.compareDebugRecords>>

export default function DebugRecordsModal({ projectId, open, onClose }: Props) {
  const [records, setRecords] = useState<DebugRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<string | undefined>()
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([])
  const [replayingId, setReplayingId] = useState<string | null>(null)
  const [compareData, setCompareData] = useState<CompareData | null>(null)
  const [compareOpen, setCompareOpen] = useState(false)

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

  const replay = async (id: string) => {
    setReplayingId(id)
    try {
      const r = await api.replayDebugRecord(id)
      message.success(`重放完成：${r.status}`)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setReplayingId(null)
    }
  }

  const compare = async () => {
    if (selectedKeys.length !== 2) {
      message.warning('请选择 2 条记录进行对比')
      return
    }
    try {
      const data = await api.compareDebugRecords(String(selectedKeys[0]), String(selectedKeys[1]))
      setCompareData(data)
      setCompareOpen(true)
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
        <Space size={0}>
          <Button size="small" type="link" loading={replayingId === record.id} onClick={() => replay(record.id)}>重放</Button>
          <Popconfirm title="确认删除？" onConfirm={() => remove(record.id)}>
            <Button size="small" type="link" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Modal title="调试记录" open={open} onCancel={onClose} footer={null} width={860}>
      <Space style={{ marginBottom: 12 }} wrap>
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
        <Button onClick={compare} disabled={selectedKeys.length !== 2}>对比选中（{selectedKeys.length}/2）</Button>
      </Space>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        size="small"
        rowSelection={{
          selectedRowKeys: selectedKeys,
          onChange: (keys) => {
            if (keys.length > 2) {
              message.warning('最多选择 2 条记录进行对比')
              return
            }
            setSelectedKeys(keys)
          },
        }}
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

      {/* 对比结果 */}
      <Modal title="调试记录对比" open={compareOpen} onCancel={() => setCompareOpen(false)} footer={null} width={760}>
        {compareData && (
          <div>
            <Space style={{ marginBottom: 16 }}>
              <Tag color={STATUS_COLOR[compareData.a.result]}>A：{compareData.a.result}</Tag>
              <span style={{ color: '#999' }}>{compareData.a.totalDuration}ms</span>
              <span style={{ color: '#999' }}>→</span>
              <Tag color={STATUS_COLOR[compareData.b.result]}>B：{compareData.b.result}</Tag>
              <span style={{ color: '#999' }}>{compareData.b.totalDuration}ms</span>
              <Tag color={compareData.diff.durationDelta >= 0 ? 'orange' : 'green'}>
                耗时差 {compareData.diff.durationDelta >= 0 ? '+' : ''}{compareData.diff.durationDelta}ms
              </Tag>
            </Space>

            <div style={{ fontWeight: 600, marginBottom: 8 }}>步骤对比</div>
            <Table
              rowKey="name"
              size="small"
              pagination={false}
              dataSource={compareData.diff.stepDiffs}
              columns={[
                { title: '步骤', dataIndex: 'name' },
                { title: 'A', dataIndex: 'statusA', render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag> },
                { title: 'B', dataIndex: 'statusB', render: (v: string) => <Tag color={STATUS_COLOR[v]}>{v}</Tag> },
                { title: '变化', dataIndex: 'changed', render: (v: boolean) => (v ? <Tag color="red">已变化</Tag> : <Tag>一致</Tag>) },
              ]}
            />

            <div style={{ fontWeight: 600, margin: '16px 0 8px' }}>变量对比</div>
            <Space direction="vertical" style={{ width: '100%' }} size={4}>
              {compareData.diff.varDiffs.added.map((v) => (
                <div key={v.name}><Tag color="green">新增</Tag>{v.name} = {v.value}</div>
              ))}
              {compareData.diff.varDiffs.removed.map((v) => (
                <div key={v.name}><Tag color="red">移除</Tag>{v.name} = {v.value}</div>
              ))}
              {compareData.diff.varDiffs.changed.map((v) => (
                <div key={v.name}><Tag color="orange">变化</Tag>{v.name}：{v.a} → {v.b}</div>
              ))}
              {compareData.diff.varDiffs.added.length + compareData.diff.varDiffs.removed.length + compareData.diff.varDiffs.changed.length === 0 && (
                <span style={{ color: '#999' }}>变量无变化</span>
              )}
            </Space>
          </div>
        )}
      </Modal>
    </Modal>
  )
}
