/**
 * 中间编写用例面板（PRD 第 2 期）：用例名称 + 前置/测试/后置三段式步骤编辑器。
 * 步骤类型：接口请求（引用接口 + 断言 + 提取）、脚本、等待、变量赋值。
 */
import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Input, InputNumber, Modal, Select, Space, Switch, Tabs, Tag, Tooltip, message } from 'antd'
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
import { api, getErrorMessage } from '../../api/client'
import EnvironmentSelect from '../EnvironmentSelect'
import {
  HTTP_METHODS,
  type ApiDefinition,
  type Assertion,
  type CaseInfo,
  type CaseStep,
  type CaseStepType,
  type ExtractRule,
} from '../../api/types'

interface Props {
  projectId?: string
  caseId?: string
  onCollapse: () => void
}

const STEP_TYPE_LABELS: Record<CaseStepType, string> = {
  request: '接口请求',
  script: '脚本',
  wait: '等待',
  variable: '变量赋值',
  controller: '流程控制器',
}

export default function CaseEditorPanel({ projectId, caseId, onCollapse }: Props) {
  const [caseInfo, setCaseInfo] = useState<CaseInfo | null>(null)
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [steps, setSteps] = useState<CaseStep[]>([])
  const [apis, setApis] = useState<ApiDefinition[]>([])
  // 调试状态
  const [debugOpen, setDebugOpen] = useState(false)
  const [debugEnvId, setDebugEnvId] = useState<string | undefined>()
  const [debugResult, setDebugResult] = useState<{ status: string; duration: number; results: Array<{ id: string; name: string; type: string; status: string; message: string; request?: { method: string; url: string }; response?: { status: number; body: unknown; duration: number }; assertions?: Array<{ passed: boolean; message: string }>; extracted?: Record<string, string>; children?: Array<unknown> }>; variables: Record<string, string> } | null>(null)
  const [debugLoading, setDebugLoading] = useState(false)

  // 加载用例详情 + 项目接口列表
  useEffect(() => {
    if (!caseId) {
      setCaseInfo(null)
      setName('')
      setSteps([])
      return
    }
    api
      .getCase(caseId)
      .then((c) => {
        setCaseInfo(c)
        setName(c.name)
        setSteps((c.steps ?? []) as CaseStep[])
        return api.listApis(c.projectId)
      })
      .then((apiList) => setApis(apiList))
      .catch((e) => message.error(getErrorMessage(e)))
  }, [caseId])

  const setupSteps = useMemo(() => steps.filter((s) => s.phase === 'setup'), [steps])
  const testSteps = useMemo(() => steps.filter((s) => s.phase === 'test'), [steps])
  const teardownSteps = useMemo(() => steps.filter((s) => s.phase === 'teardown'), [steps])

  const save = async () => {
    if (!caseId) return
    setSaving(true)
    try {
      await api.updateCase(caseId, { name, steps })
      message.success('已保存')
      const c = await api.getCase(caseId)
      setCaseInfo(c)
      setName(c.name)
      setSteps((c.steps ?? []) as CaseStep[])
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  // 调试执行
  const runDebug = async () => {
    if (!caseId) return
    await save() // 先保存
    setDebugLoading(true)
    setDebugResult(null)
    try {
      const r = await api.debugCaseInfo(caseId, { environmentId: debugEnvId })
      setDebugResult(r)
      message.success(r.status === 'success' ? '调试通过' : '调试完成，存在失败')
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setDebugLoading(false)
    }
  }

  useEffect(() => {
    const onSave = () => save()
    const onDebug = () => {
      setDebugOpen(true)
    }
    window.addEventListener('workbench:save', onSave)
    window.addEventListener('workbench:debug', onDebug)
    return () => {
      window.removeEventListener('workbench:save', onSave)
      window.removeEventListener('workbench:debug', onDebug)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [caseId, name, steps])

  const updateStep = (phase: CaseStep['phase'], index: number, patch: Partial<CaseStep>) => {
    setSteps((prev) => {
      const phaseList = prev.filter((s) => s.phase === phase)
      const target = phaseList[index]
      if (!target) return prev
      return prev.map((s) => (s.id === target.id ? { ...s, ...patch } : s))
    })
  }

  const addStep = (phase: CaseStep['phase'], type: CaseStepType) => {
    const base: CaseStep = {
      id: `${phase}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      type,
      phase,
      name: STEP_TYPE_LABELS[type],
      enabled: true,
    }
    if (type === 'request') {
      base.method = 'GET'
      base.headers = []
      base.query = []
      base.assertions = []
      base.extracts = []
    } else if (type === 'wait') {
      base.waitMs = 1000
    } else if (type === 'variable') {
      base.varName = ''
      base.varValue = ''
    } else if (type === 'script') {
      base.script = ''
    } else if (type === 'controller') {
      base.controllerType = 'if'
      base.condition = ''
      base.children = []
    }
    setSteps((prev) => [...prev, base])
  }

  const removeStep = (phase: CaseStep['phase'], index: number) => {
    setSteps((prev) => {
      const phaseList = prev.filter((s) => s.phase === phase)
      const target = phaseList[index]
      if (!target) return prev
      return prev.filter((s) => s.id !== target.id)
    })
  }

  const reorderStep = (phase: CaseStep['phase'], reordered: CaseStep[]) => {
    setSteps((prev) => {
      const others = prev.filter((s) => s.phase !== phase)
      return [...others, ...reordered]
    })
  }

  if (!caseId || !caseInfo) {
    return (
      <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#999', background: '#fff' }}>
        <div>
          <Button type="text" size="small" onClick={onCollapse}>»</Button>
          <div style={{ textAlign: 'center' }}>从左侧用例库选择一个用例</div>
        </div>
      </div>
    )
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#fff', minWidth: 0 }}>
      <div style={{ height: 48, display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px', borderBottom: '1px solid #f0f0f0' }}>
        <Button type="text" size="small" onClick={onCollapse}>»</Button>
        <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="用例名称" variant="borderless" style={{ fontWeight: 600, fontSize: 15, flex: 1 }} />
        <Tooltip title="Ctrl+Enter">
          <Button size="small" onClick={() => setDebugOpen(true)}>调试</Button>
        </Tooltip>
        <Tooltip title="Ctrl+S">
          <Button size="small" type="primary" loading={saving} onClick={save}>保存</Button>
        </Tooltip>
        <span style={{ color: '#999', fontSize: 12 }}>v{caseInfo.version}</span>
      </div>

      <div style={{ flex: 1, overflow: 'hidden' }}>
        <Tabs
          style={{ height: '100%' }}
          tabBarStyle={{ margin: 0, padding: '0 12px' }}
          items={[
            {
              key: 'script',
              label: '脚本',
              children: (
                <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
                  <StepSection title="前置步骤" color="#1890ff" phase="setup" steps={setupSteps} apis={apis} onAdd={addStep} onUpdate={updateStep} onRemove={removeStep} onReorder={reorderStep} />
                  <StepSection title="测试步骤" color="#52c41a" phase="test" steps={testSteps} apis={apis} onAdd={addStep} onUpdate={updateStep} onRemove={removeStep} onReorder={reorderStep} />
                  <StepSection title="后置步骤" color="#fa8c16" phase="teardown" steps={teardownSteps} apis={apis} onAdd={addStep} onUpdate={updateStep} onRemove={removeStep} onReorder={reorderStep} />
                </div>
              ),
            },
            {
              key: 'debug',
              label: '调试记录',
              children: <div style={{ padding: 16, color: '#999' }}>调试记录（后续期次）</div>,
            },
          ]}
        />
      </div>

      {/* 调试弹窗 */}
      <Modal
        title={`调试：${name}`}
        open={debugOpen}
        onCancel={() => setDebugOpen(false)}
        footer={null}
        width={760}
      >
        <Space style={{ marginBottom: 16 }}>
          <span>环境：</span>
          <EnvironmentSelect projectId={projectId ?? ''} style={{ width: 220 }} value={debugEnvId} onChange={setDebugEnvId} />
          <Button type="primary" loading={debugLoading} onClick={runDebug}>执行调试</Button>
        </Space>

        {debugResult && (
          <div>
            <div style={{ marginBottom: 12 }}>
              <Tag color={debugResult.status === 'success' ? 'green' : 'red'}>{debugResult.status}</Tag>
              <span style={{ color: '#999' }}>耗时 {debugResult.duration}ms</span>
            </div>
            {debugResult.results.map((r, i) => (
              <Card
                key={i}
                size="small"
                title={
                  <Space>
                    <span>
                      步骤 {i + 1}：{r.name}
                    </span>
                    <Tag color={r.status === 'PASS' ? 'green' : r.status === 'SKIP' ? 'default' : 'red'}>{r.status}</Tag>
                  </Space>
                }
                style={{ marginBottom: 8 }}
              >
                <div style={{ color: '#666', fontSize: 12, marginBottom: 4 }}>{r.message}</div>
                {r.request && <div style={{ color: '#999', fontSize: 12 }}>{r.request.method} {r.request.url}</div>}
                {(r.assertions ?? []).map((a, j) => (
                  <Alert key={j} type={a.passed ? 'success' : 'error'} showIcon message={a.message} style={{ marginBottom: 4 }} />
                ))}
                {r.extracted && Object.keys(r.extracted).length > 0 && (
                  <div style={{ fontSize: 12 }}>
                    提取：{Object.entries(r.extracted).map(([k, v]) => `${k}=${v}`).join('，')}
                  </div>
                )}
              </Card>
            ))}
          </div>
        )}
      </Modal>
    </div>
  )
}

/** 单个阶段的步骤列表（支持拖拽排序） */
function StepSection(props: {
  title: string
  color: string
  phase: CaseStep['phase']
  steps: CaseStep[]
  apis: ApiDefinition[]
  onAdd: (phase: CaseStep['phase'], type: CaseStepType) => void
  onUpdate: (phase: CaseStep['phase'], index: number, patch: Partial<CaseStep>) => void
  onRemove: (phase: CaseStep['phase'], index: number) => void
  onReorder: (phase: CaseStep['phase'], reordered: CaseStep[]) => void
}) {
  const { title, color, phase, steps, apis, onAdd, onUpdate, onRemove, onReorder } = props
  const [addType, setAddType] = useState<CaseStepType>('request')
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (over && active.id !== over.id) {
      const oldIndex = steps.findIndex((s) => s.id === active.id)
      const newIndex = steps.findIndex((s) => s.id === over.id)
      if (oldIndex < 0 || newIndex < 0) return
      onReorder(phase, arrayMove(steps, oldIndex, newIndex))
    }
  }

  return (
    <div style={{ marginBottom: 16, border: '1px solid #e8e8e8', borderRadius: 6, padding: 12 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
        <span style={{ fontWeight: 600, color }}>{title}</span>
        <Space>
          <Select size="small" value={addType} style={{ width: 120 }} onChange={setAddType} options={Object.entries(STEP_TYPE_LABELS).map(([v, l]) => ({ value: v, label: l }))} />
          <Button size="small" type="dashed" onClick={() => onAdd(phase, addType)}>添加步骤</Button>
        </Space>
      </div>

      {steps.length === 0 && <div style={{ color: '#bbb', textAlign: 'center', padding: 16 }}>暂无步骤</div>}

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <SortableContext items={steps.map((s) => s.id)} strategy={verticalListSortingStrategy}>
          {steps.map((s, i) => (
            <StepCard
              key={s.id}
              step={s}
              apis={apis}
              onUpdate={(patch) => onUpdate(phase, i, patch)}
              onRemove={() => onRemove(phase, i)}
            />
          ))}
        </SortableContext>
      </DndContext>
    </div>
  )
}

/** 单个步骤卡片（拖拽手柄排序） */
function StepCard(props: {
  step: CaseStep
  apis: ApiDefinition[]
  onUpdate: (patch: Partial<CaseStep>) => void
  onRemove: () => void
}) {
  const { step, apis, onUpdate, onRemove } = props
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: step.id })

  return (
    <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 }}>
      <Card
        size="small"
        style={{ marginBottom: 8, opacity: step.enabled ? 1 : 0.55 }}
        title={
          <Space>
            <span {...attributes} {...listeners} style={{ cursor: 'grab', color: '#999', userSelect: 'none', touchAction: 'none', fontSize: 16 }} title="拖动排序">⠿</span>
            <Tag color="blue">{STEP_TYPE_LABELS[step.type]}</Tag>
            <span style={{ fontSize: 13 }}>{step.name}</span>
          </Space>
        }
        extra={
          <Space size={4}>
            <Switch size="small" checked={step.enabled} onChange={(v) => onUpdate({ enabled: v })} />
            <Button size="small" type="text" danger onClick={onRemove}>删除</Button>
          </Space>
        }
      >
        <StepEditor step={step} apis={apis} onUpdate={onUpdate} />
      </Card>
    </div>
  )
}

/** 步骤编辑表单（根据类型） */
function StepEditor(props: { step: CaseStep; apis: ApiDefinition[]; onUpdate: (patch: Partial<CaseStep>) => void }) {
  const { step, apis, onUpdate } = props

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={8}>
      <Input size="small" value={step.name} placeholder="步骤名称" onChange={(e) => onUpdate({ name: e.target.value })} style={{ width: 240 }} />

      {step.type === 'request' && (
        <>
          <Select
            size="small"
            style={{ width: '100%' }}
            placeholder="引用接口（可选，选择后自动填充方法/URL/请求体）"
            allowClear
            value={step.apiId}
            options={apis.map((a) => ({ value: a.id, label: `${a.method} ${a.name} ${a.path}` }))}
            showSearch
            optionFilterProp="label"
            onChange={(v) => {
              const a = apis.find((x) => x.id === v)
              if (a) onUpdate({ apiId: a.id, method: a.method, url: a.path, headers: a.headers, query: a.query, body: a.body ?? '' })
              else onUpdate({ apiId: undefined })
            }}
          />
          <Space size={8}>
            <Select size="small" style={{ width: 110 }} value={step.method} options={HTTP_METHODS.map((m) => ({ value: m, label: m }))} onChange={(v) => onUpdate({ method: v })} />
            <Input size="small" style={{ width: 300 }} value={step.url} placeholder="URL（支持 ${变量}）" onChange={(e) => onUpdate({ url: e.target.value })} />
          </Space>
          <Input size="small" value={step.body} placeholder="请求体（JSON，支持 ${变量}）" onChange={(e) => onUpdate({ body: e.target.value })} />
          <AssertionMiniEditor value={step.assertions ?? []} onChange={(assertions) => onUpdate({ assertions })} />
          <ExtractMiniEditor value={step.extracts ?? []} onChange={(extracts) => onUpdate({ extracts })} />
        </>
      )}

      {step.type === 'script' && (
        <Input.TextArea rows={4} value={step.script} placeholder="JS 脚本，支持 ${变量}" onChange={(e) => onUpdate({ script: e.target.value })} />
      )}

      {step.type === 'wait' && (
        <Space>
          <span>等待</span>
          <InputNumber size="small" value={step.waitMs} onChange={(v) => onUpdate({ waitMs: v ?? 0 })} />
          <span>毫秒</span>
        </Space>
      )}

      {step.type === 'variable' && (
        <Space>
          <Input size="small" style={{ width: 160 }} value={step.varName} placeholder="变量名" onChange={(e) => onUpdate({ varName: e.target.value })} />
          <span>=</span>
          <Input size="small" style={{ width: 200 }} value={step.varValue} placeholder="变量值" onChange={(e) => onUpdate({ varValue: e.target.value })} />
        </Space>
      )}

      {step.type === 'controller' && (
        <>
          <Select
            size="small"
            style={{ width: 140 }}
            value={step.controllerType}
            options={[
              { value: 'if', label: 'IF-ELSE 条件' },
              { value: 'for', label: 'FOR 循环' },
              { value: 'while', label: 'WHILE 循环' },
            ]}
            onChange={(v) => onUpdate({ controllerType: v })}
          />
          {step.controllerType === 'if' && (
            <Input size="small" value={step.condition} placeholder='条件表达式，如 ${status} == "ok"' onChange={(e) => onUpdate({ condition: e.target.value })} />
          )}
          {step.controllerType === 'for' && (
            <Space>
              <span>循环变量</span>
              <Input size="small" style={{ width: 100 }} value={step.loopVar} placeholder="如 i" onChange={(e) => onUpdate({ loopVar: e.target.value })} />
              <span>次数</span>
              <InputNumber size="small" value={step.loopCount} onChange={(v) => onUpdate({ loopCount: v ?? 1 })} />
            </Space>
          )}
          {step.controllerType === 'while' && (
            <Space>
              <Input size="small" style={{ width: 200 }} value={step.condition} placeholder="条件表达式" onChange={(e) => onUpdate({ condition: e.target.value })} />
              <span>最大循环</span>
              <InputNumber size="small" value={step.maxLoops} onChange={(v) => onUpdate({ maxLoops: v ?? 100 })} />
            </Space>
          )}
          <div style={{ borderLeft: '2px solid #e0e0e0', paddingLeft: 8 }}>
            <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>子步骤（{step.children?.length ?? 0}）</div>
            {(step.children ?? []).map((child, ci) => (
              <StepCard
                key={child.id}
                step={child}
                apis={apis}
                onUpdate={(patch) => {
                  const children = [...(step.children ?? [])]
                  children[ci] = { ...children[ci], ...patch }
                  onUpdate({ children })
                }}
                onRemove={() => {
                  const children = (step.children ?? []).filter((_, j) => j !== ci)
                  onUpdate({ children })
                }}
              />
            ))}
            <Button
              size="small"
              type="dashed"
              block
              onClick={() => {
                const child: CaseStep = {
                  id: `child-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
                  type: 'request',
                  phase: step.phase,
                  name: '子步骤',
                  enabled: true,
                  method: 'GET',
                  headers: [],
                  query: [],
                  assertions: [],
                  extracts: [],
                }
                onUpdate({ children: [...(step.children ?? []), child] })
              }}
            >
              添加子步骤
            </Button>
          </div>
        </>
      )}
    </Space>
  )
}

/** 简化的断言编辑器（state 版） */
function AssertionMiniEditor(props: { value: Assertion[]; onChange: (v: Assertion[]) => void }) {
  const { value, onChange } = props
  return (
    <div>
      <div style={{ fontWeight: 500, fontSize: 12, marginBottom: 4 }}>断言（{value.length}）</div>
      {value.map((a, i) => (
        <Space key={i} size={4} style={{ marginBottom: 4, display: 'flex' }}>
          <Select size="small" style={{ width: 110 }} value={a.type} options={[{ value: 'statusCode', label: '状态码' }, { value: 'jsonPath', label: 'JSONPath' }, { value: 'header', label: '响应头' }, { value: 'regex', label: '正则' }]} onChange={(v) => onChange(value.map((x, j) => (j === i ? { ...x, type: v as Assertion['type'] } : x)))} />
          <Input size="small" style={{ width: 160 }} value={a.expression} placeholder="表达式" onChange={(e) => onChange(value.map((x, j) => (j === i ? { ...x, expression: e.target.value } : x)))} />
          <Input size="small" style={{ width: 120 }} value={a.expected} placeholder="期望值" onChange={(e) => onChange(value.map((x, j) => (j === i ? { ...x, expected: e.target.value } : x)))} />
          <Button size="small" type="text" danger onClick={() => onChange(value.filter((_, j) => j !== i))}>删</Button>
        </Space>
      ))}
      <Button size="small" type="dashed" block onClick={() => onChange([...value, { type: 'statusCode', expression: '', expected: '200', operator: 'eq' }])}>添加断言</Button>
    </div>
  )
}

/** 简化的提取编辑器（state 版） */
function ExtractMiniEditor(props: { value: ExtractRule[]; onChange: (v: ExtractRule[]) => void }) {
  const { value, onChange } = props
  return (
    <div>
      <div style={{ fontWeight: 500, fontSize: 12, marginBottom: 4 }}>提取（{value.length}）</div>
      {value.map((e, i) => (
        <Space key={i} size={4} style={{ marginBottom: 4, display: 'flex' }}>
          <Input size="small" style={{ width: 120 }} value={e.name} placeholder="变量名" onChange={(ev) => onChange(value.map((x, j) => (j === i ? { ...x, name: ev.target.value } : x)))} />
          <Select size="small" style={{ width: 110 }} value={e.type} options={[{ value: 'jsonPath', label: 'JSONPath' }, { value: 'header', label: '响应头' }, { value: 'regex', label: '正则' }]} onChange={(v) => onChange(value.map((x, j) => (j === i ? { ...x, type: v as ExtractRule['type'] } : x)))} />
          <Input size="small" style={{ width: 160 }} value={e.expression} placeholder="表达式" onChange={(ev) => onChange(value.map((x, j) => (j === i ? { ...x, expression: ev.target.value } : x)))} />
          <Button size="small" type="text" danger onClick={() => onChange(value.filter((_, j) => j !== i))}>删</Button>
        </Space>
      ))}
      <Button size="small" type="dashed" block onClick={() => onChange([...value, { name: '', type: 'jsonPath', expression: '' }])}>添加提取</Button>
    </div>
  )
}
