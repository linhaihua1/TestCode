/**
 * 回收站弹窗（PRD 2.3）：软删除用例列表 + 还原 + 永久删除。
 */
import { useEffect, useState } from 'react'
import { Button, Modal, Popconfirm, Space, Table, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../../api/client'
import type { CaseInfo } from '../../api/types'

interface Props {
  projectId?: string
  open: boolean
  onClose: () => void
}

export default function RecycleBinModal({ projectId, open, onClose }: Props) {
  const [cases, setCases] = useState<CaseInfo[]>([])
  const [loading, setLoading] = useState(false)

  const load = async () => {
    if (!projectId) return
    setLoading(true)
    try {
      setCases(await api.listRecycleCases(projectId))
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (open) load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, projectId])

  const restore = async (id: string) => {
    try {
      await api.restoreCase(id)
      message.success('已还原')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const permanentDelete = async (id: string) => {
    try {
      await api.permanentDeleteCase(id)
      message.success('已永久删除')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const columns: ColumnsType<CaseInfo> = [
    { title: '用例名称', dataIndex: 'name' },
    {
      title: '删除时间',
      dataIndex: 'deletedAt',
      render: (v: string) => new Date(v).toLocaleString(),
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button size="small" type="link" onClick={() => restore(record.id)}>
            还原
          </Button>
          <Popconfirm title="永久删除不可恢复，确认？" onConfirm={() => permanentDelete(record.id)}>
            <Button size="small" type="link" danger>
              永久删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <Modal title="回收站" open={open} onCancel={onClose} footer={null} width={720}>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={cases} size="small" />
    </Modal>
  )
}
