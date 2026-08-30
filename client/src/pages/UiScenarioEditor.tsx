/**
 * UI 执行编排页：拖拽排序组合 UI 用例，按业务流程执行并展示报告。
 */
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Select, Space, Spin, Tag, message } from 'antd'
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
import { useNavigate, useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { UiReport, UiScenarioStep, UiStepResult, UiTestCase } from '../api/types'

const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

/** 场景报告中单个用例的执行结果 */
interface CaseResult {
  testCaseId: string
  name: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  steps: UiStepResult[]
}

/** 可拖拽的单步骤行（引用一个 UI 用例） */
function SortableStep(props: {
  step: UiScenarioStep
  index: number
  total: number
  caseOptions: { value: string; label: string }[]
  onUpdate: (index: number, patch: Partial<UiScenarioStep>) => void
  onRemove: (index: number) => void
}) {
  const { step, index, total, caseOptions, onUpdate, onRemove } = props
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: step.id,
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
      <Select
        style={{ flex: 1 }}
        placeholder="选择 UI 用例"
        value={step.uiTestCaseId ?? undefined}
        options={caseOptions}
        showSearch
        optionFilterProp="label"
        onChange={(v) => onUpdate(index, { uiTestCaseId: v })}
      />
      <Button size="small" danger onClick={() => onRemove(index)}>
        删除
      </Button>
    </div>
  )
}

export default function UiScenarioEditor() {
  const { projectId, scenarioId } = useParams<{ projectId: string; scenarioId: string }>()
  const navigate = useNavigate()

  const [scenarioName, setScenarioName] = useState('')
  const [steps, setSteps] = useState<UiScenarioStep[]>([])
  const [caseOptions, setCaseOptions] = useState<{ value: string; label: string }[]>([])
  const [report, setReport] = useState<UiReport | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const load = async () => {
    setLoading(true)
    try {
      const scenario = await api.getUiScenario(scenarioId!)
      setScenarioName(scenario.name)
      setSteps(scenario.steps ?? [])
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setLoading(false)
    }
  }

  const loadCaseOptions = async () => {
    try {
      const tests = await api.listUiTests(projectId!)
      setCaseOptions(tests.map((t: UiTestCase) => ({ value: t.id, label: t.name })))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  useEffect(() => {
    load()
    loadCaseOptions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scenarioId])

  const updateStep = (index: number, patch: Partial<UiScenarioStep>) => {
    setSteps((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)))
  }

  const addStep = () => {
    setSteps((prev) => [
      ...prev,
      {
        id: `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
        scenarioId: scenarioId!,
        order: prev.length,
        uiTestCaseId: null,
      },
    ])
  }

  const removeStep = (index: number) => {
    setSteps((prev) => prev.filter((_, i) => i !== index))
  }

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (over && active.id !== over.id) {
      setSteps((prev) => {
        const oldIndex = prev.findIndex((s) => s.id === active.id)
        const newIndex = prev.findIndex((s) => s.id === over.id)
        if (oldIndex < 0 || newIndex < 0) return prev
        return arrayMove(prev, oldIndex, newIndex)
      })
    }
  }

  const save = async () => {
    const payload = steps.map((s, i) => ({ order: i, uiTestCaseId: s.uiTestCaseId ?? null }))
    try {
      await api.updateUiScenarioSteps(scenarioId!, payload)
      message.success('步骤已保存')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const run = async () => {
    await save()
    setRunning(true)
    setReport(null)
    try {
      const r = await api.runUiScenario(scenarioId!)
      setReport(r)
      message.success(r.status === 'PASS' ? '执行通过' : '执行完成，存在失败步骤')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setRunning(false)
    }
  }

  const caseResults = (report?.details ?? []) as unknown as CaseResult[]

  return (
    <Card
      title={`UI 执行编排：${scenarioName || ''}`}
      extra={
        <Space>
          <Button onClick={() => navigate(`/projects/${projectId}/ui-scenarios`)}>返回</Button>
          <Button onClick={save}>保存步骤</Button>
          <Button type="primary" loading={running} onClick={run}>
            执行
          </Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        <div style={{ marginBottom: 16, color: '#999', fontSize: 12 }}>
          拖动左侧 ⠿ 手柄调整 UI 用例执行顺序；所有用例在同一个浏览器会话中按顺序执行
        </div>

        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={steps.map((s) => s.id)} strategy={verticalListSortingStrategy}>
            {steps.map((s, i) => (
              <SortableStep
                key={s.id}
                step={s}
                index={i}
                total={steps.length}
                caseOptions={caseOptions}
                onUpdate={updateStep}
                onRemove={removeStep}
              />
            ))}
          </SortableContext>
        </DndContext>
        <Button type="dashed" block onClick={addStep}>
          添加 UI 用例
        </Button>

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
