/**
 * UI 测试用例编排页（Pytest 三段式）：
 * 前置步骤(setup) → 测试步骤(test) → 后置步骤(teardown)，执行时按此顺序串联。
 */
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Collapse, Input, Select, Space, Spin, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { UI_LOCATORS, type UiReport, type UiStep } from '../api/types'
const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

/** 单个阶段的步骤编辑表格（前置/测试/后置共用） */
function StepTable(props: {
  title: string
  color: string
  steps: UiStep[]
  onUpdate: (index: number, patch: Partial<UiStep>) => void
  onAdd: () => void
  onRemove: (index: number) => void
  onMove: (index: number, dir: -1 | 1) => void
}) {
  const { title, color, steps, onUpdate, onAdd, onRemove, onMove } = props

  const columns: ColumnsType<UiStep> = [
    { title: '#', width: 50, render: (_, __, i) => i + 1 },
    {
      title: '动作',
      width: 180,
      render: (_, record, i) => (
        <Input
          value={record.action}
          placeholder="如 open / click / type / assertText / wait"
          onChange={(e) => onUpdate(i, { action: e.target.value })}
        />
      ),
    },
    {
      title: '定位方式',
      width: 130,
      render: (_, record, i) =>
        record.action === 'open' || record.action === 'assertTitle' ? (
          <span style={{ color: '#999' }}>—</span>
        ) : (
          <Select
            style={{ width: '100%' }}
            value={record.locatorType ?? 'css'}
            options={UI_LOCATORS}
            onChange={(v) => onUpdate(i, { locatorType: v })}
          />
        ),
    },
    {
      title: '目标',
      render: (_, record, i) => (
        <Input
          value={record.target}
          placeholder={record.action === 'open' ? 'URL（如 / 或完整地址）' : '选择器'}
          onChange={(e) => onUpdate(i, { target: e.target.value })}
        />
      ),
    },
    {
      title: '值',
      width: 220,
      render: (_, record, i) => (
        <Input
          value={record.value}
          placeholder={
            record.action === 'type'
              ? '输入内容'
              : record.action === 'assertText' || record.action === 'assertTitle'
                ? '期望值'
                : record.action === 'wait'
                  ? '超时秒数'
                  : '（可选）'
          }
          onChange={(e) => onUpdate(i, { value: e.target.value })}
        />
      ),
    },
    {
      title: '操作',
      width: 180,
      render: (_, __, i) => (
        <Space>
          <Button size="small" onClick={() => onMove(i, -1)} disabled={i === 0}>
            上移
          </Button>
          <Button size="small" onClick={() => onMove(i, 1)} disabled={i === steps.length - 1}>
            下移
          </Button>
          <Button size="small" danger onClick={() => onRemove(i)}>
            删除
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontWeight: 600, marginBottom: 8, color }}>{title}</div>
      <Table
        rowKey={(_, i) => String(i)}
        columns={columns}
        dataSource={steps}
        pagination={false}
        size="small"
        footer={() => (
          <Button type="dashed" block onClick={onAdd}>
            添加步骤
          </Button>
        )}
      />
    </div>
  )
}

export default function UiTestEditor() {
  const { testId } = useParams<{ testId: string }>()
  const navigate = useNavigate()

  const [testName, setTestName] = useState('')
  const [setupSteps, setSetupSteps] = useState<UiStep[]>([])
  const [steps, setSteps] = useState<UiStep[]>([])
  const [teardownSteps, setTeardownSteps] = useState<UiStep[]>([])
  const [report, setReport] = useState<UiReport | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const testCase = await api.getUiTest(testId!)
      setTestName(testCase.name)
      setSetupSteps(testCase.setupSteps ?? [])
      setSteps(testCase.steps ?? [])
      setTeardownSteps(testCase.teardownSteps ?? [])
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [testId])

  // 通用步骤操作（针对某个阶段数组）
  const updateIn = (setter: React.Dispatch<React.SetStateAction<UiStep[]>>, index: number, patch: Partial<UiStep>) =>
    setter((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  const addIn = (setter: React.Dispatch<React.SetStateAction<UiStep[]>>) =>
    setter((prev) => [...prev, { action: 'open', locatorType: 'css', target: '', value: '' }])
  const removeIn = (setter: React.Dispatch<React.SetStateAction<UiStep[]>>, index: number) =>
    setter((prev) => prev.filter((_, i) => i !== index))
  const moveIn = (setter: React.Dispatch<React.SetStateAction<UiStep[]>>, index: number, dir: -1 | 1) =>
    setter((prev) => {
      const target = index + dir
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })

  const save = async () => {
    try {
      await api.updateUiTest(testId!, { setupSteps, steps, teardownSteps })
      message.success('步骤已保存')
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const run = async () => {
    await save()
    setRunning(true)
    setReport(null)
    try {
      const r = await api.runUiTest(testId!)
      setReport(r)
      message.success(r.status === 'PASS' ? '执行通过' : '执行完成，存在失败步骤')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunning(false)
    }
  }

  const reportItems = (report?.details ?? []).map((d, i) => ({
    key: String(i),
    label: (
      <Space>
        <span>
          步骤 {i + 1}：{d.action}
          {d.target ? `（${d.target}）` : ''}
        </span>
        <Tag color={STATUS_COLOR[d.status]}>{d.status}</Tag>
      </Space>
    ),
    children: (
      <div>
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
      </div>
    ),
  }))

  return (
    <Card
      title={`UI 测试用例编排：${testName || ''}`}
      extra={
        <Space>
          <Button onClick={() => navigate('/ui-tests')}>返回</Button>
          <Button onClick={save}>保存步骤</Button>
          <Button type="primary" loading={running} onClick={run}>
            执行测试
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <StepTable
          title="① 前置步骤（setup，如打开页面/登录）"
          color="#1890ff"
          steps={setupSteps}
          onUpdate={(i, p) => updateIn(setSetupSteps, i, p)}
          onAdd={() => addIn(setSetupSteps)}
          onRemove={(i) => removeIn(setSetupSteps, i)}
          onMove={(i, d) => moveIn(setSetupSteps, i, d)}
        />
        <StepTable
          title="② 测试步骤（test，核心操作 + 断言）"
          color="#52c41a"
          steps={steps}
          onUpdate={(i, p) => updateIn(setSteps, i, p)}
          onAdd={() => addIn(setSteps)}
          onRemove={(i) => removeIn(setSteps, i)}
          onMove={(i, d) => moveIn(setSteps, i, d)}
        />
        <StepTable
          title="③ 后置步骤（teardown，如清理/关闭）"
          color="#fa8c16"
          steps={teardownSteps}
          onUpdate={(i, p) => updateIn(setTeardownSteps, i, p)}
          onAdd={() => addIn(setTeardownSteps)}
          onRemove={(i) => removeIn(setTeardownSteps, i)}
          onMove={(i, d) => moveIn(setTeardownSteps, i, d)}
        />

        {report && (
          <Card
            size="small"
            title={
              <Space>
                <span>执行报告</span>
                <Tag color={STATUS_COLOR[report.status]}>{report.status}</Tag>
                <span style={{ color: '#999' }}>耗时 {report.duration}ms</span>
              </Space>
            }
            style={{ marginTop: 24 }}
          >
            <Collapse items={reportItems} defaultActiveKey={reportItems.map((i) => i.key)} />
          </Card>
        )}
      </Spin>
    </Card>
  )
}
