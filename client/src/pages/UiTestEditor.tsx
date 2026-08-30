/**
 * UI 测试编排页：步骤编排（打开/点击/输入/断言/等待）+ 执行 + 报告展示。
 */
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Collapse, Input, Select, Space, Spin, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { UI_ACTIONS, UI_LOCATORS, type UiReport, type UiStep } from '../api/types'

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

export default function UiTestEditor() {
  const { projectId, testId } = useParams<{ projectId: string; testId: string }>()
  const navigate = useNavigate()

  const [testName, setTestName] = useState('')
  const [steps, setSteps] = useState<UiStep[]>([])
  const [report, setReport] = useState<UiReport | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const testCase = await api.getUiTest(testId!)
      setTestName(testCase.name)
      setSteps(testCase.steps ?? [])
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

  const updateStep = (index: number, patch: Partial<UiStep>) => {
    setSteps((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  }

  const addStep = () => {
    setSteps((prev) => [...prev, { action: 'open', locatorType: 'css', target: '', value: '' }])
  }

  const removeStep = (index: number) => {
    setSteps((prev) => prev.filter((_, i) => i !== index))
  }

  const moveStep = (index: number, dir: -1 | 1) => {
    setSteps((prev) => {
      const target = index + dir
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const save = async () => {
    try {
      await api.updateUiTest(testId!, { steps })
      message.success('步骤已保存')
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const run = async () => {
    await save() // 执行前先保存步骤
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

  const stepColumns: ColumnsType<UiStep> = [
    { title: '#', width: 50, render: (_, __, i) => i + 1 },
    {
      title: '动作',
      width: 150,
      render: (_, record, i) => (
        <Select
          style={{ width: '100%' }}
          value={record.action}
          options={UI_ACTIONS}
          onChange={(v) => updateStep(i, { action: v })}
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
            onChange={(v) => updateStep(i, { locatorType: v })}
          />
        ),
    },
    {
      title: '目标',
      render: (_, record, i) => (
        <Input
          value={record.target}
          placeholder={record.action === 'open' ? 'URL（如 / 或完整地址）' : '选择器'}
          onChange={(e) => updateStep(i, { target: e.target.value })}
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
          onChange={(e) => updateStep(i, { value: e.target.value })}
        />
      ),
    },
    {
      title: '操作',
      width: 180,
      render: (_, __, i) => (
        <Space>
          <Button size="small" onClick={() => moveStep(i, -1)} disabled={i === 0}>
            上移
          </Button>
          <Button size="small" onClick={() => moveStep(i, 1)} disabled={i === steps.length - 1}>
            下移
          </Button>
          <Button size="small" danger onClick={() => removeStep(i)}>
            删除
          </Button>
        </Space>
      ),
    },
  ]

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
      title={`UI 测试编排：${testName || ''}`}
      extra={
        <Space>
          <Button onClick={() => navigate(`/projects/${projectId}/ui-tests`)}>返回</Button>
          <Button onClick={save}>保存步骤</Button>
          <Button type="primary" loading={running} onClick={run}>
            执行测试
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <Table
          rowKey={(_, i) => String(i)}
          columns={stepColumns}
          dataSource={steps}
          pagination={false}
          size="small"
          footer={() => (
            <Button type="dashed" block onClick={addStep}>
              添加步骤
            </Button>
          )}
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
