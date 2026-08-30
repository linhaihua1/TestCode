/**
 * 场景编排与执行页
 *
 * 职责：本平台的核心页面，用于编排场景步骤（选择接口用例并调整顺序），
 * 选择执行环境后运行整个场景，并实时展示执行报告。
 */
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Collapse, Select, Space, Spin, Table, Tag, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { Environment, Report, ScenarioStep } from '../api/types'

// 用例下拉选项结构（value 为用例 ID，label 为「接口名 / 用例名」）
interface CaseOption {
  value: string
  label: string
}

// 执行状态与标签颜色的映射
const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

export default function ScenarioEditor() {
  const { projectId, scenarioId } = useParams<{ projectId: string; scenarioId: string }>() // 当前项目与场景 ID
  const navigate = useNavigate() // 路由跳转

  const [scenarioName, setScenarioName] = useState('') // 场景名称
  const [steps, setSteps] = useState<ScenarioStep[]>([]) // 场景步骤列表
  const [caseOptions, setCaseOptions] = useState<CaseOption[]>([]) // 可选用例下拉选项
  const [envs, setEnvs] = useState<Environment[]>([]) // 可用环境列表
  const [selectedEnv, setSelectedEnv] = useState<string | undefined>() // 当前选中的执行环境
  const [report, setReport] = useState<Report | null>(null) // 本次执行报告
  const [running, setRunning] = useState(false) // 是否正在执行
  const [loading, setLoading] = useState(false) // 页面加载状态

  // 加载场景详情与环境列表
  const load = async () => {
    setLoading(true)
    try {
      // 并行获取场景详情和环境列表
      const [scenario, envList] = await Promise.all([
        api.getScenario(scenarioId!),
        api.listEnvironments(projectId!),
      ])
      setScenarioName(scenario.name)
      setSteps(scenario.steps ?? [])
      setEnvs(envList)
      // 默认选中第一个环境
      if (envList.length > 0) setSelectedEnv(envList[0].id)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  // 加载所有接口及其用例，组装为步骤可选的用例下拉选项
  const loadCaseOptions = async () => {
    try {
      const apis = await api.listApis(projectId!)
      const options: CaseOption[] = []
      // 遍历接口与用例，生成「接口名 / 用例名」形式的标签
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

  // 场景切换时初始化页面数据
  useEffect(() => {
    load()
    loadCaseOptions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioId])

  // 更新指定步骤的局部字段（如选择的用例）
  const updateStep = (index: number, patch: Partial<ScenarioStep>) => {
    setSteps((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  }

  // 新增一个空步骤（使用临时 ID，待保存时由后端分配）
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

  // 删除指定步骤
  const removeStep = (index: number) => {
    setSteps((prev) => prev.filter((_, i) => i !== index))
  }

  // 上移 / 下移步骤：dir 为 -1 表示上移、1 表示下移
  const moveStep = (index: number, dir: -1 | 1) => {
    setSteps((prev) => {
      const target = index + dir
      // 越界时不移动
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      // 交换相邻两步的位置
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  // 保存步骤顺序与用例关联
  const save = async () => {
    // 组装提交负载：以数组下标作为步骤顺序
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

  // 执行场景
  const run = async () => {
    // 未选择环境时给出提示并中止
    if (!selectedEnv) {
      message.warning('请先选择执行环境')
      return
    }
    setRunning(true)
    setReport(null) // 清空上一次报告
    try {
      const r = await api.runScenario(scenarioId!, selectedEnv)
      setReport(r)
      // 根据整体状态给出不同提示
      message.success(r.status === 'PASS' ? '执行通过' : '执行完成，存在失败步骤')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunning(false)
    }
  }

  // 步骤表格列定义
  const stepColumns: ColumnsType<ScenarioStep> = [
    // 序号列
    { title: '#', width: 60, render: (_, __, i) => i + 1 },
    {
      title: '接口用例',
      // 用例下拉选择，变更后更新对应步骤
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
          {/* 上移：第一步禁用 */}
          <Button size="small" onClick={() => moveStep(i, -1)} disabled={i === 0}>
            上移
          </Button>
          {/* 下移：最后一步禁用 */}
          <Button size="small" onClick={() => moveStep(i, 1)} disabled={i === steps.length - 1}>
            下移
          </Button>
          {/* 删除步骤 */}
          <Button size="small" danger onClick={() => removeStep(i)}>
            删除
          </Button>
        </Space>
      ),
    },
  ]

  // 将报告明细转换为 Collapse（折叠面板）的 item 结构
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
        {/* 步骤出错时展示错误信息 */}
        {d.error && <Alert type="error" message={d.error} style={{ marginBottom: 12 }} />}
        {/* 逐条展示断言结果 */}
        {d.assertions.map((a, j) => (
          <Alert
            key={j}
            type={a.passed ? 'success' : 'error'}
            showIcon
            message={a.message}
            style={{ marginBottom: 8 }}
          />
        ))}
        {/* 无错误且无断言时给出占位提示 */}
        {d.assertions.length === 0 && !d.error && <span style={{ color: '#999' }}>无断言</span>}
      </div>
    ),
  }))

  return (
    <Card
      title={`场景编排：${scenarioName || ''}`}
      extra={
        <Space>
          {/* 返回场景列表 */}
          <Button onClick={() => navigate(`/projects/${projectId}/scenarios`)}>返回</Button>
          {/* 保存当前步骤顺序与用例关联 */}
          <Button onClick={save}>保存步骤</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {/* 执行环境选择与执行按钮 */}
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
            {/* 执行中显示 loading */}
            <Button type="primary" loading={running} onClick={run}>
              执行场景
            </Button>
          </Space>
        </div>

        {/* 步骤编辑表格 */}
        <Table
          rowKey="id"
          columns={stepColumns}
          dataSource={steps}
          pagination={false}
          size="small"
          footer={() => (
            // 表格底部添加步骤按钮
            <Button type="dashed" block onClick={addStep}>
              添加步骤
            </Button>
          )}
        />

        {/* 有执行报告时展示结果 */}
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
            {/* 默认展开所有步骤明细 */}
            <Collapse items={reportItems} defaultActiveKey={reportItems.map((i) => i.key)} />
          </Card>
        )}
      </Spin>
    </Card>
  )
}
