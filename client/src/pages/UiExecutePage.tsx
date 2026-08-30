/**
 * UI 用例执行页：收集 UI 用例 → 拖拽排序 → 保存为执行计划 → 执行。
 * 支持保存/加载执行计划，记录收集的用例与顺序。
 */
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Input, Select, Space, Spin, Tag, message } from 'antd'
import {
  DndContext,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { UiReport, UiScenario, UiTestCase, UiStepResult } from '../api/types'

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

/** 报告中单个用例的执行结果 */
interface CaseResult {
  testCaseId: string
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  steps: UiStepResult[]
}

/** 可拖拽的单个用例行 */
function SortableCase(props: {
  testCase: UiTestCase
  index: number
  total: number
  onRemove: (index: number) => void
}) {
  const { testCase, index, total, onRemove } = props
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: testCase.id,
  })

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.4 : 1,
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '8px 12px',
    marginBottom: 8,
    background: '#fff',
    border: '1px solid #f0f0f0',
    borderRadius: 6,
  }

  return (
    <div ref={setNodeRef} style={style}>
      <span
        {...attributes}
        {...listeners}
        style={{ cursor: 'grab', fontSize: 18, color: '#999', userSelect: 'none', touchAction: 'none' }}
        title="拖动排序"
      >
        ⠿
      </span>
      <span style={{ width: 90, color: '#999', fontSize: 12 }}>
        {index === 0 ? '① 起始步骤' : index === total - 1 ? '② 结束步骤' : `步骤 ${index + 1}`}
      </span>
      <span style={{ flex: 1 }}>{testCase.name}</span>
      <Button size="small" danger onClick={() => onRemove(index)}>
        移除
      </Button>
    </div>
  )
}

export default function UiExecutePage() {
  const { projectId } = useParams<{ projectId: string }>()
  const [allCases, setAllCases] = useState<UiTestCase[]>([]) // 项目所有 UI 用例
  const [selected, setSelected] = useState<UiTestCase[]>([]) // 已收集的执行列表
  const [plans, setPlans] = useState<UiScenario[]>([]) // 已有执行计划
  const [planId, setPlanId] = useState<string | undefined>() // 当前选中的计划
  const [planName, setPlanName] = useState('') // 计划名称输入
  const [report, setReport] = useState<UiReport | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const load = async () => {
    setLoading(true)
    try {
      const [cases, scenarioList] = await Promise.all([
        api.listUiTests(projectId!),
        api.listUiScenarios(projectId!),
      ])
      setAllCases(cases)
      setPlans(scenarioList)
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

  // 添加用例到执行列表
  const addCase = (id: string) => {
    const tc = allCases.find((c) => c.id === id)
    if (tc && !selected.some((c) => c.id === id)) {
      setSelected((prev) => [...prev, tc])
    }
  }

  const removeCase = (index: number) => {
    setSelected((prev) => prev.filter((_, i) => i !== index))
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (over && active.id !== over.id) {
      setSelected((prev) => {
        const oldIndex = prev.findIndex((c) => c.id === active.id)
        const newIndex = prev.findIndex((c) => c.id === over.id)
        if (oldIndex < 0 || newIndex < 0) return prev
        return arrayMove(prev, oldIndex, newIndex)
      })
    }
  }

  // 加载已有计划：回填计划名称与用例列表
  const loadPlan = async (id: string) => {
    setPlanId(id)
    try {
      const scn = await api.getUiScenario(id)
      setPlanName(scn.name)
      const cases = (scn.steps ?? [])
        .filter((s) => s.uiTestCase)
        .map((s) => s.uiTestCase!)
      setSelected(cases)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 保存计划：有 planId 则更新，否则创建新计划
  const savePlan = async () => {
    if (!planName.trim()) {
      message.warning('请输入计划名称')
      return
    }
    const stepPayload = selected.map((c, i) => ({ order: i, uiTestCaseId: c.id }))
    try {
      if (planId) {
        await api.updateUiScenario(planId, { name: planName })
        await api.updateUiScenarioSteps(planId, stepPayload)
      } else {
        const scn = await api.createUiScenario(projectId!, { name: planName })
        await api.updateUiScenarioSteps(scn.id, stepPayload)
        setPlanId(scn.id)
      }
      message.success('计划已保存')
      const scenarioList = await api.listUiScenarios(projectId!)
      setPlans(scenarioList)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 新建计划：清空当前计划与用例列表
  const newPlan = () => {
    setPlanId(undefined)
    setPlanName('')
    setSelected([])
    setReport(null)
  }

  const run = async () => {
    if (selected.length === 0) {
      message.warning('请先添加 UI 用例')
      return
    }
    setRunning(true)
    setReport(null)
    try {
      const r = await api.executeUiCases(projectId!, selected.map((c) => c.id))
      setReport(r)
      message.success(r.status === 'PASS' ? '执行通过' : '执行完成，存在失败步骤')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunning(false)
    }
  }

  const availableCases = allCases.filter((c) => !selected.some((s) => s.id === c.id))
  const caseResults = (report?.details ?? []) as unknown as CaseResult[]

  return (
    <Card title="UI 用例执行">
      <Spin spinning={loading}>
        {/* 计划管理与执行工具栏 */}
        <div style={{ marginBottom: 16 }}>
          <Space wrap>
            <span>执行计划：</span>
            <Select
              style={{ width: 220 }}
              placeholder="选择已有计划"
              value={planId}
              options={plans.map((p) => ({ value: p.id, label: p.name }))}
              onChange={loadPlan}
              allowClear
              onClear={newPlan}
            />
            <Input
              style={{ width: 200 }}
              placeholder="计划名称"
              value={planName}
              onChange={(e) => setPlanName(e.target.value)}
            />
            <Button onClick={savePlan}>保存计划</Button>
            <Button onClick={newPlan}>新建</Button>
            <Button type="primary" loading={running} onClick={run}>
              执行
            </Button>
          </Space>
        </div>

        {/* 收集用例 */}
        <div style={{ marginBottom: 16 }}>
          <Space>
            <span>添加用例：</span>
            <Select
              style={{ width: 360 }}
              placeholder="选择 UI 用例添加到执行列表"
              value={undefined}
              options={availableCases.map((c) => ({ value: c.id, label: c.name }))}
              showSearch
              optionFilterProp="label"
              onChange={addCase}
            />
            <span style={{ color: '#999', fontSize: 12 }}>
              已选 {selected.length} 个，拖动左侧 ⠿ 手柄调整执行顺序
            </span>
          </Space>
        </div>

        {/* 拖拽排序列表 */}
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={selected.map((c) => c.id)} strategy={verticalListSortingStrategy}>
            {selected.map((c, i) => (
              <SortableCase key={c.id} testCase={c} index={i} total={selected.length} onRemove={removeCase} />
            ))}
          </SortableContext>
        </DndContext>

        {selected.length === 0 && (
          <div style={{ color: '#999', textAlign: 'center', padding: 24 }}>
            暂无用例，请从上方下拉添加 UI 用例
          </div>
        )}

        {/* 执行报告 */}
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
            {caseResults.map((cr, i) => (
              <Card
                key={i}
                size="small"
                title={
                  <Space>
                    <span>
                      用例 {i + 1}：{cr.name}
                    </span>
                    <Tag color={STATUS_COLOR[cr.status]}>{cr.status}</Tag>
                  </Space>
                }
                style={{ marginBottom: 8 }}
              >
                {cr.steps.map((st, j) => (
                  <div key={j} style={{ marginBottom: 8 }}>
                    <Alert
                      type={st.status === 'PASS' ? 'success' : 'error'}
                      showIcon
                      message={`${st.action}${st.target ? `（${st.target}）` : ''}：${st.message}`}
                      style={{ marginBottom: 4 }}
                    />
                    {st.screenshot && (
                      <img
                        src={`data:image/png;base64,${st.screenshot}`}
                        alt="失败截图"
                        style={{ maxWidth: '100%', border: '1px solid #eee' }}
                      />
                    )}
                  </div>
                ))}
              </Card>
            ))}
          </Card>
        )}
      </Spin>
    </Card>
  )
}
