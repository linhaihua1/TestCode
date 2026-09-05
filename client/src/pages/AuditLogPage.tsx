/**
 * 审计日志页：查看谁在何时对什么实体做了什么操作（含变更前后快照）。
 */
import { useEffect, useState } from 'react'
import { Button, Card, Select, Space, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { api, getErrorMessage } from '../api/client'
import type { AuditLog } from '../api/types'

const ACTION_LABEL: Record<string, string> = {
  create: '新增', update: '修改', delete: '删除', run: '执行', rollback: '回退',
  'import:swagger': 'Swagger 导入', 'import:excel': 'Excel 导入',
  'review:submit': '提交评审', 'review:approve': '评审通过', 'review:reject': '评审驳回',
}

const ENTITY_LABEL: Record<string, string> = {
  project: '项目', environment: '环境', api: '接口', case: '用例', module: '模块',
  task: '任务', user: '用户', globalVariable: '全局变量',
}

function pretty(v: unknown): string {
  if (v === undefined || v === null) return '-'
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v)
  }
}

export default function AuditLogPage() {
  const [logs, setLogs] = useState<AuditLog[]>([])
  const [loading, setLoading] = useState(false)
  const [actions, setActions] = useState<string[]>([])
  const [entityTypes, setEntityTypes] = useState<string[]>([])
  const [action, setAction] = useState<string | undefined>()
  const [entityType, setEntityType] = useState<string | undefined>()

  const loadMeta = async () => {
    try {
      const meta = await api.getAuditLogMeta()
      setActions(meta.actions)
      setEntityTypes(meta.entityTypes)
    } catch {
      /* ignore */
    }
  }

  const load = async () => {
    setLoading(true)
    try {
      const params: Record<string, string> = {}
      if (action) params.action = action
      if (entityType) params.entityType = entityType
      const res = await api.listAuditLogs(params)
      setLogs(res.logs)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadMeta()
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [action, entityType])

  const columns: ColumnsType<AuditLog> = [
    {
      title: '时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (v: string) => new Date(v).toLocaleString(),
    },
    { title: '操作人', dataIndex: 'username', width: 120, render: (v?: string | null) => v ?? '-' },
    {
      title: '动作',
      dataIndex: 'action',
      width: 130,
      render: (v: string) => <Tag color="blue">{ACTION_LABEL[v] ?? v}</Tag>,
    },
    {
      title: '对象类型',
      dataIndex: 'entityType',
      width: 110,
      render: (v: string) => <Tag>{ENTITY_LABEL[v] ?? v}</Tag>,
    },
    { title: '对象 ID', dataIndex: 'entityId', width: 200, ellipsis: true },
  ]

  return (
    <Card
      title="审计日志"
      extra={
        <Space>
          <Select
            allowClear
            placeholder="动作"
            style={{ width: 150 }}
            value={action}
            onChange={setAction}
            options={actions.map((a) => ({ value: a, label: ACTION_LABEL[a] ?? a }))}
          />
          <Select
            allowClear
            placeholder="对象类型"
            style={{ width: 130 }}
            value={entityType}
            onChange={setEntityType}
            options={entityTypes.map((t) => ({ value: t, label: ENTITY_LABEL[t] ?? t }))}
          />
          <Button onClick={load}>刷新</Button>
        </Space>
      }
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={logs}
        size="small"
        pagination={{ pageSize: 20, showSizeChanger: false }}
        expandable={{
          expandedRowRender: (record) => (
            <div style={{ fontSize: 12, fontFamily: 'monospace' }}>
              <div>
                <b>变更前：</b>
                <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{pretty(record.before)}</pre>
              </div>
              <div>
                <b>变更后：</b>
                <pre style={{ margin: 0, whiteSpace: 'pre-wrap' }}>{pretty(record.after)}</pre>
              </div>
            </div>
          ),
        }}
      />
    </Card>
  )
}
