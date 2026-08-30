import { useEffect, useState } from 'react'
import { Alert, Button, Card, Collapse, Select, Space, Spin, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { Environment, Report, ScenarioStep } from '../api/types'

interface CaseOption {
  value: string
  label: string
}

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

export default function ScenarioEditor() {
  const { projectId, scenarioId } = useParams<{ projectId: string; scenarioId: string }>()
  const navigate = useNavigate()

  const [scenarioName, setScenarioName] = useState('')
  const [steps, setSteps] = useState<ScenarioStep[]>([])
  const [caseOptions, setCaseOptions] = useState<CaseOption[]>([])
  const [envs, setEnvs] = useState<Environment[]>([])
  const [selectedEnv, setSelectedEnv] = useState<string | undefined>()
  const [report, setReport] = useState<Report | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setLoading(true)
    try {
      const [scenario, envList] = await Promise.all([
        api.getScenario(scenarioId!),
        api.listEnvironments(projectId!),
      ])
      setScenarioName(scenario.name)
      setSteps(scenario.steps ?? [])
      setEnvs(envList)
      if (envList.length > 0) setSelectedEnv(envList[0].id)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  const loadCaseOptions = async () => {
    try {
      const apis = await api.listApis(projectId!)
      const options: CaseOption[] = []
      for (const apiDef of apis) {
        const cases = await api.listCases(apiDef.id)
        for (const c of cases) {
          options.push({ value: c.id, label: `${apiDef.name} / ${c.name}` })
        }
      }
      setCaseOptions(options)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  useEffect(() => {
    load()
    loadCaseOptions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioId])

  const updateStep = (index: number, patch: Partial<ScenarioStep>) => {
    setSteps((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  }

  const addStep = () => {
    setSteps((prev) => [
      ...prev,
      {
        id: `tmp-${Date.now()}`,
        scenarioId: scenarioId!,
        order: prev.length,
        apiCaseId: null,
        assertions: [],
        extracts: [],
      },
    ])
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
    const payload = steps.map((s, i) => ({
      order: i,
      apiCaseId: s.apiCaseId ?? null,
      name: s.name ?? null,
    }))
    try {
      await api.updateScenarioSteps(scenarioId!, payload)
      message.success('步骤已保存')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const run = async () => {
    if (!selectedEnv) {
      message.warning('请先选择执行环境')
      return
    }
    setRunning(true)
    setReport(null)
    try {
      const r = await api.runScenario(scenarioId!, selectedEnv)
      setReport(r)
      message.success(r.status === 'PASS' ? '执行通过' : '执行完成，存在失败步骤')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunning(false)
    }
  }

  const stepColumns: ColumnsType<ScenarioStep> = [
    { title: '#', width: 60, render: (_, __, i) => i + 1 },
    {
      title: '接口用例',
      render: (_, record, i) => (
        <Select
          style={{ width: '100%' }}
          placeholder="选择用例"
          value={record.apiCaseId ?? undefined}
          options={caseOptions}
          showSearch
          optionFilterProp="label"
          onChange={(v) => updateStep(i, { apiCaseId: v })}
        />
      ),
    },
    {
      title: '操作',
      width: 200,
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
        <span>步骤 {i + 1}：{d.stepName}</span>
        <Tag color={STATUS_COLOR[d.status]}>{d.status}</Tag>
      </Space>
    ),
    children: (
      <div>
        {d.error && <Alert type="error" message={d.error} style={{ marginBottom: 12 }} />}
        {d.assertions.map((a, j) => (
          <Alert
            key={j}
            type={a.passed ? 'success' : 'error'}
            showIcon
            message={a.message}
            style={{ marginBottom: 8 }}
          />
        ))}
        {d.assertions.length === 0 && !d.error && <span style={{ color: '#999' }}>无断言</span>}
      </div>
    ),
  }))

  return (
    <Card
      title={`场景编排：${scenarioName || ''}`}
      extra={
        <Space>
          <Button onClick={() => navigate(`/projects/${projectId}/scenarios`)}>返回</Button>
          <Button onClick={save}>保存步骤</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <div style={{ marginBottom: 16 }}>
          <Space>
            <span>执行环境：</span>
            <Select
              style={{ width: 240 }}
              placeholder="选择环境"
              value={selectedEnv}
              options={envs.map((e) => ({ value: e.id, label: e.name }))}
              onChange={setSelectedEnv}
            />
            <Button type="primary" loading={running} onClick={run}>
              执行场景
            </Button>
          </Space>
        </div>

        <Table
          rowKey="id"
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
