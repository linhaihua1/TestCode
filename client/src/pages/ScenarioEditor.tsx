/**
 * 场景编排与执行页（用例执行）
 *
 * 职责：编排场景步骤（选择接口用例并【拖拽】调整顺序），
 * 选择执行环境后运行整个场景，并实时展示执行报告。
 */
import { useEffect, useState } from 'react'
import { Alert, Button, Card, Collapse, Select, Space, Spin, Tag, message } from 'antd'
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
import type { Report, ScenarioStep } from '../api/types'
import EnvironmentSelect from '../components/EnvironmentSelect'

// 用例下拉选项结构（value 为用例 ID，label 为「接口名 / 用例名」）
interface CaseOption {
  value: string
  label: string
}

// 执行状态与标签颜色的映射
const STATUS_COLOR: Record<string, string> = { PASS: 'green', FAIL: 'red', ERROR: 'orange' }

/** 可拖拽的单步骤行组件 */
function SortableStep(props: {
  step: ScenarioStep
  index: number
  total: number
  caseOptions: CaseOption[]
  onUpdate: (index: number, patch: Partial<ScenarioStep>) => void
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
      {/* 拖拽手柄 */}
      <span
        {...attributes}
        {...listeners}
        style={{ cursor: 'grab', fontSize: 18, color: '#999', userSelect: 'none', touchAction: 'none' }}
        title="拖动排序"
      >
        ⠿
      </span>
      {/* 序号（起止标注） */}
      <span style={{ width: 90, color: '#999', fontSize: 12 }}>
        {index === 0 ? '① 起始步骤' : index === total - 1 ? '② 结束步骤' : `步骤 ${index + 1}`}
      </span>
      {/* 用例选择 */}
      <Select
        style={{ flex: 1 }}
        placeholder="选择用例"
        value={step.apiCaseId ?? undefined}
        options={caseOptions}
        showSearch
        optionFilterProp="label"
        onChange={(v) => onUpdate(index, { apiCaseId: v })}
      />
      {/* 删除 */}
      <Button size="small" danger onClick={() => onRemove(index)}>
        删除
      </Button>
    </div>
  )
}

export default function ScenarioEditor() {
  const { projectId, scenarioId } = useParams<{ projectId: string; scenarioId: string }>()
  const navigate = useNavigate()

  const [scenarioName, setScenarioName] = useState('')
  const [steps, setSteps] = useState<ScenarioStep[]>([])
  const [caseOptions, setCaseOptions] = useState<CaseOption[]>([])
  const [selectedEnv, setSelectedEnv] = useState<string | undefined>()
  const [report, setReport] = useState<Report | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  // 拖拽传感器：移动超过 5px 才触发拖拽，避免误触
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  )

  const load = async () => {
    setLoading(true)
    try {
      const scenario = await api.getScenario(scenarioId!)
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
        id: `tmp-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
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

  // 拖拽结束：根据新旧位置重排步骤
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
      title={`用例执行：${scenarioName || ''}`}
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
            <EnvironmentSelect
              projectId={projectId!}
              style={{ width: 240 }}
              value={selectedEnv}
              onChange={setSelectedEnv}
            />
            <Button type="primary" loading={running} onClick={run}>
              执行
            </Button>
          </Space>
          <span style={{ marginLeft: 16, color: '#999', fontSize: 12 }}>
            拖动左侧 ⠿ 手柄调整用例执行顺序
          </span>
        </div>

        {/* 可拖拽排序的步骤列表 */}
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
          添加步骤
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
            <Collapse items={reportItems} defaultActiveKey={reportItems.map((i) => i.key)} />
          </Card>
        )}
      </Spin>
    </Card>
  )
}
