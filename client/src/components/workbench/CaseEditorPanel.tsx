/**
 * 中间编写用例面板（PRD 第 2 期）：用例名称 + 前置/测试/后置三段式步骤编辑器。
 * 步骤类型：接口请求（引用接口 + 断言 + 提取）、脚本、等待、变量赋值。
 */
import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Button, Card, Dropdown, Input, InputNumber, Modal, Radio, Select, Space, Switch, Tabs, Tag, Tooltip, message } from 'antd'
import type { MenuProps } from 'antd'
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
import ScrollBar from '../ScrollBar'
import {
  HTTP_METHODS,
  type ApiDefinition,
  type Assertion,
  type CaseInfo,
  type CaseReview,
  type CaseStep,
  type CaseStepType,
  type DebugRecord,
  type ExtractRule,
  type KeyValue,
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

const REVIEW_ACTION: Record<string, { label: string; color: string }> = {
  submit: { label: '提交评审', color: 'blue' },
  approve: { label: '通过', color: 'green' },
  reject: { label: '驳回', color: 'red' },
}

const JS_TEMPLATE = `// 脚本上下文：
// context.get('变量名')  获取变量
// context.set('变量名', 值)  设置变量（作为提取参数提交到用例）
const token = context.get('token') || ''
console.log('token =', token)`

const PY_TEMPLATE = `# context.get('变量名')  获取变量
# context.set('变量名', 值)  设置变量（作为提取参数提交到用例）
token = context.get('token') or ''
print('token =', token)`

const JAVA_TEMPLATE = `// context.get("变量名")  获取变量
// context.set("变量名", 值)  设置变量（作为提取参数提交到用例）
// System.out.println(...)  输出日志
String token = context.get("token");
System.out.println("token = " + token);`

const IF_JS_TEMPLATE = `// IF 自定义代码：通过 context.set('__condition__', 'true'/'false') 返回判断结果
// 其它 context.set 也会作为提取参数提交到用例
context.set('__condition__', 'true')`

const IF_PY_TEMPLATE = `# IF 自定义代码：通过 context.set('__condition__', 'true'/'false') 返回判断结果
# 其它 context.set 也会作为提取参数提交到用例
context.set('__condition__', 'true')`

const IF_JAVA_TEMPLATE = `// IF 自定义代码：通过 context.set("__condition__", "true"/"false") 返回判断结果
// 其它 context.set 也会作为提取参数提交到用例
context.set("__condition__", "true");`

const SCRIPT_LANG_OPTIONS = [
  { value: 'javascript', label: 'JavaScript' },
  { value: 'python', label: 'Python' },
  { value: 'java', label: 'Java' },
]

function scriptTemplate(lang: string | undefined): string {
  if (lang === 'python') return PY_TEMPLATE
  if (lang === 'java') return JAVA_TEMPLATE
  return JS_TEMPLATE
}

function ifTemplate(lang: string | undefined): string {
  if (lang === 'python') return IF_PY_TEMPLATE
  if (lang === 'java') return IF_JAVA_TEMPLATE
  return IF_JS_TEMPLATE
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
  // 调试临时变量 + 执行方式/模式
  const [debugVars, setDebugVars] = useState<Array<{ key: string; value: string }>>([])
  const [debugVarsOpen, setDebugVarsOpen] = useState(false)
  const [runMode, setRunMode] = useState<'full' | 'step' | 'breakpoint'>('full')
  const [execMethod, setExecMethod] = useState<'server' | 'local'>('server')
  // 状态/优先级/版本
  const [status, setStatus] = useState('draft')
  const [priority, setPriority] = useState('P2')
  const [tags, setTags] = useState<string[]>([])
  const [versionOpen, setVersionOpen] = useState(false)
  // 评审状态
  const [reviews, setReviews] = useState<CaseReview[]>([])
  const [reviewComment, setReviewComment] = useState('')
  const [reviewLoading, setReviewLoading] = useState(false)
  // 步骤复制/粘贴 + 本用例调试记录
  const [copiedStep, setCopiedStep] = useState<CaseStep | null>(null)
  const [caseDebugRecords, setCaseDebugRecords] = useState<DebugRecord[]>([])
  const scriptScrollRef = useRef<HTMLDivElement>(null)
  const skipAutoSaveRef = useRef(true)

  const loadReviews = async (id: string) => {
    try {
      setReviews(await api.listCaseReviews(id))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  const loadCaseDebugRecords = async (id: string) => {
    try {
      setCaseDebugRecords(await api.listDebugRecords(projectId ?? '', { caseId: id }))
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

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
        setStatus(c.status)
        setPriority(c.priority)
        setTags((c.tags ?? []) as string[])
        setSteps((c.steps ?? []) as CaseStep[])
        return api.listApis(c.projectId)
      })
      .then((apiList) => setApis(apiList))
      .catch((e) => message.error(getErrorMessage(e)))
    loadReviews(caseId)
    loadCaseDebugRecords(caseId)
    skipAutoSaveRef.current = true
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [caseId])

  const setupSteps = useMemo(() => steps.filter((s) => s.phase === 'setup'), [steps])
  const testSteps = useMemo(() => steps.filter((s) => s.phase === 'test'), [steps])
  const teardownSteps = useMemo(() => steps.filter((s) => s.phase === 'teardown'), [steps])

  const persist = async (silent = false) => {
    if (!caseId) return
    setSaving(true)
    try {
      await api.updateCase(caseId, { name, steps, status, priority, tags })
      if (!silent) {
        message.success('已保存')
        const c = await api.getCase(caseId)
        setCaseInfo(c)
        setName(c.name)
        setSteps((c.steps ?? []) as CaseStep[])
      }
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  const save = () => persist(false)

  // 步骤/名称等变化后自动保存（首次加载跳过，防抖 800ms）
  useEffect(() => {
    if (!caseId) return
    if (skipAutoSaveRef.current) {
      skipAutoSaveRef.current = false
      return
    }
    const timer = setTimeout(() => {
      void persist(true)
    }, 800)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [steps, name, status, priority, tags])

  // 评审动作：提交/通过/驳回
  const doReview = async (action: string) => {
    if (!caseId) return
    setReviewLoading(true)
    try {
      await api.reviewCase(caseId, { action, comment: reviewComment })
      message.success(`${REVIEW_ACTION[action]?.label ?? action}成功`)
      setReviewComment('')
      await loadReviews(caseId)
      const c = await api.getCase(caseId)
      setStatus(c.status)
      setCaseInfo(c)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setReviewLoading(false)
    }
  }

  // 调试执行
  const runDebug = async () => {
    if (!caseId) return
    await save() // 先保存
    setDebugLoading(true)
    setDebugResult(null)
    try {
      const debugVarsRecord: Record<string, string> = {}
      for (const v of debugVars) {
        if (v.key.trim()) debugVarsRecord[v.key.trim()] = v.value
      }
      const r = await api.debugCaseInfo(caseId, { environmentId: debugEnvId, debugVars: debugVarsRecord })
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

  const buildStep = (phase: CaseStep['phase'], type: CaseStepType): CaseStep => {
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
      base.scriptLang = 'javascript'
    } else if (type === 'controller') {
      base.controllerType = 'if'
      base.condMode = 'expression'
      base.condition = ''
      base.children = []
    }
    return base
  }

  // 在指定阶段的指定位置插入步骤（index 省略则追加到末尾）
  const addStepAt = (phase: CaseStep['phase'], type: CaseStepType, index?: number) => {
    const base = buildStep(phase, type)
    setSteps((prev) => {
      const phaseList = prev.filter((s) => s.phase === phase)
      const insertAt = index === undefined ? phaseList.length : Math.max(0, Math.min(index, phaseList.length))
      phaseList.splice(insertAt, 0, base)
      return [...prev.filter((s) => s.phase !== phase), ...phaseList]
    })
  }

  const addStep = (phase: CaseStep['phase'], type: CaseStepType) => addStepAt(phase, type)

  // 拖拽到某个步骤前面/后面插入
  const insertStepRelative = (phase: CaseStep['phase'], type: CaseStepType, refId: string, before: boolean) => {
    const base = buildStep(phase, type)
    setSteps((prev) => {
      const phaseList = prev.filter((s) => s.phase === phase)
      const idx = phaseList.findIndex((s) => s.id === refId)
      const insertAt = idx < 0 ? phaseList.length : before ? idx : idx + 1
      phaseList.splice(insertAt, 0, base)
      return [...prev.filter((s) => s.phase !== phase), ...phaseList]
    })
  }

  // 从右侧接口管理拖拽接口到步骤区，生成一条接口请求步骤
  const addApiStep = (phase: CaseStep['phase'], apiId: string) => {
    const a = apis.find((x) => x.id === apiId)
    if (!a) return
    const step: CaseStep = {
      id: `${phase}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      type: 'request',
      phase,
      name: a.name,
      enabled: true,
      apiId: a.id,
      method: a.method,
      url: a.path,
      headers: a.headers ?? [],
      query: a.query ?? [],
      body: a.body ?? '',
      assertions: [{ type: 'statusCode', expression: '', expected: '200', operator: 'eq', failStrategy: 'continue' }],
      extracts: [],
    }
    setSteps((prev) => [...prev, step])
    message.success(`已添加接口步骤「${a.name}」`)
  }

  // 复制步骤到剪贴板（深拷贝，避免引用）
  const copyStep = (step: CaseStep) => {
    setCopiedStep(JSON.parse(JSON.stringify(step)))
    message.success('已复制步骤')
  }

  // 粘贴步骤：插入到指定阶段的指定位置之后
  const pasteStep = (phase: CaseStep['phase'], afterId: string) => {
    if (!copiedStep) {
      message.warning('请先复制一个步骤')
      return
    }
    const clone: CaseStep = {
      ...JSON.parse(JSON.stringify(copiedStep)),
      id: `${phase}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      phase,
    }
    setSteps((prev) => {
      const phaseList = prev.filter((s) => s.phase === phase)
      const idx = phaseList.findIndex((s) => s.id === afterId)
      const insertAt = idx >= 0 ? idx + 1 : phaseList.length
      phaseList.splice(insertAt, 0, clone)
      return [...prev.filter((s) => s.phase !== phase), ...phaseList]
    })
    message.success('已粘贴步骤')
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
        <Select
          size="small"
          style={{ width: 90 }}
          value={status}
          options={[
            { value: 'draft', label: '草稿' },
            { value: 'pending', label: '待评审' },
            { value: 'passed', label: '已通过' },
            { value: 'rejected', label: '已驳回' },
            { value: 'completed', label: '已完成' },
            { value: 'deprecated', label: '已废弃' },
          ]}
          onChange={setStatus}
        />
        <Select
          size="small"
          style={{ width: 70 }}
          value={priority}
          options={['P0', 'P1', 'P2', 'P3'].map((p) => ({ value: p, label: p }))}
          onChange={setPriority}
        />
        <Tooltip title="版本历史">
          <Button size="small" type="text" onClick={() => setVersionOpen(true)}>v{caseInfo.version}</Button>
        </Tooltip>
      </div>

      <div style={{ flex: 1, display: 'flex', minHeight: 0, overflow: 'hidden' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
        <Tabs
          className="case-editor-tabs"
          style={{ height: '100%' }}
          tabBarStyle={{ margin: 0, padding: '0 12px' }}
          tabBarExtraContent={
            <Select
              mode="tags"
              size="small"
              style={{ minWidth: 120, marginRight: 12 }}
              placeholder="标签"
              value={tags}
              onChange={(v) => setTags(v as string[])}
            />
          }
          items={[
            {
              key: 'script',
              label: '脚本',
              children: (
                <div ref={scriptScrollRef} className="scrollbar-hidden" style={{ padding: 16, height: '100%', overflow: 'auto' }}>
                  <StepSection title="前置步骤" color="#1890ff" phase="setup" steps={setupSteps} apis={apis} onAdd={addStep} onInsertRelative={insertStepRelative} onUpdate={updateStep} onRemove={removeStep} onReorder={reorderStep} onDropApi={addApiStep} onCopy={copyStep} onPaste={pasteStep} hasCopied={!!copiedStep} />
                  <StepSection title="测试步骤" color="#52c41a" phase="test" steps={testSteps} apis={apis} onAdd={addStep} onInsertRelative={insertStepRelative} onUpdate={updateStep} onRemove={removeStep} onReorder={reorderStep} onDropApi={addApiStep} onCopy={copyStep} onPaste={pasteStep} hasCopied={!!copiedStep} />
                  <StepSection title="后置步骤" color="#fa8c16" phase="teardown" steps={teardownSteps} apis={apis} onAdd={addStep} onInsertRelative={insertStepRelative} onUpdate={updateStep} onRemove={removeStep} onReorder={reorderStep} onDropApi={addApiStep} onCopy={copyStep} onPaste={pasteStep} hasCopied={!!copiedStep} />

                  {/* 底部快捷添加：拖拽到任意阶段的任意位置 */}
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginTop: 4 }}>
                    <span style={{ color: '#999', fontSize: 12 }}>拖拽添加步骤：</span>
                    <QuickAddButton type="wait" label="等待时间" onAdd={addStep} />
                    <QuickAddButton type="script" label="自定义代码" onAdd={addStep} />
                    <QuickAddButton type="controller" label="IF 判断" onAdd={addStep} />
                  </div>
                </div>
              ),
            },
            {
              key: 'debug',
              label: '调试记录',
              children: (
                <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
                  {caseDebugRecords.length === 0 ? (
                    <div style={{ color: '#999', textAlign: 'center', padding: 24 }}>暂无调试记录</div>
                  ) : (
                    caseDebugRecords.map((r) => {
                      const steps = (r.stepResults ?? []) as Array<{ name: string; type: string; status: string; message: string }>
                      return (
                        <Card
                          key={r.id}
                          size="small"
                          style={{ marginBottom: 8 }}
                          title={
                            <Space>
                              <Tag color={r.result === 'success' ? 'green' : r.result === 'error' ? 'orange' : 'red'}>{r.result}</Tag>
                              <span style={{ color: '#999', fontSize: 12 }}>{new Date(r.createdAt).toLocaleString()}</span>
                              <span style={{ color: '#999', fontSize: 12 }}>耗时 {r.totalDuration}ms</span>
                            </Space>
                          }
                        >
                          {Array.isArray(steps) &&
                            steps.map((s, i) => (
                              <div key={i} style={{ padding: '3px 0', fontSize: 12 }}>
                                <Tag color={s.status === 'PASS' ? 'green' : s.status === 'SKIP' ? 'default' : 'red'}>{s.status}</Tag>
                                <span style={{ marginRight: 8 }}>{s.name}</span>
                                <span style={{ color: '#999' }}>{s.message}</span>
                              </div>
                            ))}
                        </Card>
                      )
                    })
                  )}
                </div>
              ),
            },
            {
              key: 'review',
              label: '评审',
              children: (
                <div style={{ padding: 16, height: '100%', overflow: 'auto' }}>
                  <Card size="small" title="评审操作" style={{ marginBottom: 16 }}>
                    <Input.TextArea
                      rows={2}
                      placeholder="评审意见（可选）"
                      value={reviewComment}
                      onChange={(e) => setReviewComment(e.target.value)}
                      style={{ marginBottom: 12 }}
                    />
                    <Space>
                      <Button size="small" type="primary" loading={reviewLoading} onClick={() => doReview('submit')}>提交评审</Button>
                      <Button size="small" loading={reviewLoading} onClick={() => doReview('approve')}>通过</Button>
                      <Button size="small" danger loading={reviewLoading} onClick={() => doReview('reject')}>驳回</Button>
                    </Space>
                  </Card>
                  <div style={{ fontWeight: 600, marginBottom: 8 }}>评审记录</div>
                  {reviews.length === 0 ? (
                    <div style={{ color: '#999' }}>暂无评审记录</div>
                  ) : (
                    reviews.map((r) => (
                      <Card key={r.id} size="small" style={{ marginBottom: 8 }}>
                        <Space>
                          <Tag color={REVIEW_ACTION[r.action]?.color}>{REVIEW_ACTION[r.action]?.label ?? r.action}</Tag>
                          <span>{r.reviewerName ?? '-'}</span>
                          <span style={{ color: '#999', fontSize: 12 }}>{new Date(r.createdAt).toLocaleString()}</span>
                        </Space>
                        {r.comment && <div style={{ marginTop: 8 }}>{r.comment}</div>}
                        {(r.fromStatus || r.toStatus) && (
                          <div style={{ color: '#999', fontSize: 12, marginTop: 4 }}>
                            状态：{r.fromStatus ?? '-'} → {r.toStatus ?? '-'}
                          </div>
                        )}
                      </Card>
                    ))
                  )}
                </div>
              ),
            },
          ]}
        />
        </div>
        <ScrollBar containerRef={scriptScrollRef} />
      </div>

      {/* 调试弹窗 */}
      <Modal
        title={`调试：${name}`}
        open={debugOpen}
        onCancel={() => setDebugOpen(false)}
        footer={null}
        width={900}
      >
        {/* 顶部：环境 + 临时变量 + 执行方式 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12, flexWrap: 'wrap' }}>
          <Space>
            <span style={{ color: '#999' }}>环境：</span>
            <EnvironmentSelect projectId={projectId ?? ''} style={{ width: 200 }} value={debugEnvId} onChange={setDebugEnvId} />
          </Space>
          <Button size="small" onClick={() => setDebugVarsOpen(true)}>临时变量（{debugVars.length}）</Button>
          <Space>
            <span style={{ color: '#999' }}>执行方式：</span>
            <Select
              size="small"
              style={{ width: 110 }}
              value={execMethod}
              onChange={setExecMethod}
              options={[
                { value: 'server', label: '服务端' },
                { value: 'local', label: '本地', disabled: true },
              ]}
            />
          </Space>
        </div>

        {/* 中部：变量预览 / 执行日志 */}
        <Card size="small" title={debugResult ? '执行结果' : '变量预览'} style={{ marginBottom: 12 }}>
          {debugResult ? (
            <div style={{ maxHeight: 320, overflow: 'auto' }}>
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
                      <span>步骤 {i + 1}：{r.name}</span>
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
          ) : (
            <div>
              {debugVars.length === 0 ? (
                <div style={{ color: '#999', padding: 12, textAlign: 'center' }}>
                  环境变量与全局变量在运行时按优先级合并，临时变量优先级最高
                </div>
              ) : (
                debugVars.map((v, i) => (
                  <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 4 }}>
                    <Tag>{v.key || '(未命名)'}</Tag>
                    <span style={{ fontFamily: 'monospace' }}>{v.value}</span>
                  </div>
                ))
              )}
            </div>
          )}
        </Card>

        {/* 底部：执行模式 + 开始 */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <Radio.Group value={runMode} onChange={(e) => setRunMode(e.target.value)} optionType="button" buttonStyle="solid">
            <Radio.Button value="full">全速运行</Radio.Button>
            <Radio.Button value="step" disabled>单步调试</Radio.Button>
            <Radio.Button value="breakpoint" disabled>断点调试</Radio.Button>
          </Radio.Group>
          <div style={{ flex: 1 }} />
          <Button type="primary" loading={debugLoading} onClick={runDebug}>开始</Button>
        </div>
      </Modal>

      {/* 临时变量编辑 */}
      <Modal
        title="调试临时变量"
        open={debugVarsOpen}
        onCancel={() => setDebugVarsOpen(false)}
        onOk={() => setDebugVarsOpen(false)}
        width={480}
      >
        {debugVars.map((v, i) => (
          <Space key={i} style={{ display: 'flex', marginBottom: 8 }}>
            <Input
              size="small"
              style={{ width: 160 }}
              placeholder="变量名"
              value={v.key}
              onChange={(e) => setDebugVars((prev) => prev.map((x, j) => (j === i ? { ...x, key: e.target.value } : x)))}
            />
            <Input
              size="small"
              style={{ width: 200 }}
              placeholder="变量值"
              value={v.value}
              onChange={(e) => setDebugVars((prev) => prev.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
            />
            <Button size="small" type="text" danger onClick={() => setDebugVars((prev) => prev.filter((_, j) => j !== i))}>删</Button>
          </Space>
        ))}
        <Button size="small" type="dashed" block onClick={() => setDebugVars((prev) => [...prev, { key: '', value: '' }])}>
          添加变量
        </Button>
      </Modal>

      {/* 版本历史 */}
      <Modal title="版本历史" open={versionOpen} onCancel={() => setVersionOpen(false)} footer={null} width={560}>
        {(caseInfo.versions ?? []).map((v) => (
          <div key={v.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid #f0f0f0' }}>
            <span>
              v{v.version} <span style={{ color: '#999', fontSize: 12 }}>{new Date(v.createdAt).toLocaleString()}</span>
            </span>
            <Button
              size="small"
              onClick={async () => {
                try {
                  await api.rollbackCase(caseId, v.id)
                  message.success('已回退')
                  setVersionOpen(false)
                  const c = await api.getCase(caseId)
                  setCaseInfo(c)
                  setName(c.name)
                  setStatus(c.status)
                  setPriority(c.priority)
                  setSteps((c.steps ?? []) as CaseStep[])
                } catch (e) {
                  message.error(getErrorMessage(e))
                }
              }}
            >
              回退
            </Button>
          </div>
        ))}
        {(caseInfo.versions ?? []).length === 0 && <div style={{ color: '#999', textAlign: 'center', padding: 16 }}>暂无版本</div>}
      </Modal>
    </div>
  )
}

/** 底部快捷添加按钮（可拖拽到任意阶段、任意步骤前/后） */
function QuickAddButton(props: { type: CaseStepType; label: string; onAdd: (phase: CaseStep['phase'], type: CaseStepType) => void }) {
  const { type, label, onAdd } = props
  return (
    <Button
      size="small"
      draggable
      title={`拖拽到步骤区插入，或点击添加到测试步骤`}
      onDragStart={(e) => {
        e.dataTransfer.setData('application/step-type', type)
        e.dataTransfer.effectAllowed = 'copy'
      }}
      onClick={() => onAdd('test', type)}
    >
      {label}
    </Button>
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
  onInsertRelative: (phase: CaseStep['phase'], type: CaseStepType, refId: string, before: boolean) => void
  onUpdate: (phase: CaseStep['phase'], index: number, patch: Partial<CaseStep>) => void
  onRemove: (phase: CaseStep['phase'], index: number) => void
  onReorder: (phase: CaseStep['phase'], reordered: CaseStep[]) => void
  onDropApi: (phase: CaseStep['phase'], apiId: string) => void
  onCopy: (step: CaseStep) => void
  onPaste: (phase: CaseStep['phase'], afterId: string) => void
  hasCopied: boolean
}) {
  const { title, color, phase, steps, apis, onAdd, onInsertRelative, onUpdate, onRemove, onReorder, onDropApi, onCopy, onPaste, hasCopied } = props
  const [addType, setAddType] = useState<CaseStepType>('request')
  const [dragOver, setDragOver] = useState(false)
  const [collapsed, setCollapsed] = useState(false)
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
    <div
      style={{
        marginBottom: 16,
        border: `1px dashed ${dragOver ? '#1677ff' : '#e8e8e8'}`,
        borderRadius: 6,
        padding: 12,
        background: dragOver ? '#f0f7ff' : undefined,
        transition: 'all 0.2s',
      }}
      onDragOver={(e) => {
        if (e.dataTransfer.types.includes('application/api-id') || e.dataTransfer.types.includes('application/step-type')) {
          e.preventDefault()
          e.dataTransfer.dropEffect = 'copy'
          setDragOver(true)
        }
      }}
      onDragLeave={() => setDragOver(false)}
      onDrop={(e) => {
        e.preventDefault()
        setDragOver(false)
        const apiId = e.dataTransfer.getData('application/api-id')
        if (apiId) {
          onDropApi(phase, apiId)
          return
        }
        const stepType = e.dataTransfer.getData('application/step-type')
        if (stepType) onAdd(phase, stepType as CaseStepType)
      }}
    >
      <div
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: collapsed ? 0 : 12, cursor: 'pointer', userSelect: 'none' }}
        onClick={() => setCollapsed((v) => !v)}
      >
        <span style={{ fontWeight: 600, color }}>
          <span style={{ display: 'inline-block', width: 14, color: '#999' }}>{collapsed ? '▶' : '▼'}</span>
          {title}
          <span style={{ color: '#999', fontWeight: 400, marginLeft: 6 }}>（{steps.length}）</span>
        </span>
        <Space onClick={(e) => e.stopPropagation()}>
          {!collapsed && (
            <>
              <Select size="small" value={addType} style={{ width: 120 }} onChange={setAddType} options={Object.entries(STEP_TYPE_LABELS).map(([v, l]) => ({ value: v, label: l }))} />
              <Button size="small" type="dashed" onClick={() => onAdd(phase, addType)}>添加步骤</Button>
            </>
          )}
        </Space>
      </div>

      {!collapsed && (
        <>
          {steps.length === 0 && <div style={{ color: '#bbb', textAlign: 'center', padding: 16 }}>暂无步骤，可从右侧接口管理拖拽接口、或拖拽底部步骤到此添加</div>}

          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
            <SortableContext items={steps.map((s) => s.id)} strategy={verticalListSortingStrategy}>
              {steps.map((s, i) => (
                <StepCard
                  key={s.id}
                  step={s}
                  apis={apis}
                  onUpdate={(patch) => onUpdate(phase, i, patch)}
                  onRemove={() => onRemove(phase, i)}
                  onCopy={() => onCopy(s)}
                  onPaste={() => onPaste(phase, s.id)}
                  hasCopied={hasCopied}
                  onInsertRelative={(type, before) => onInsertRelative(phase, type, s.id, before)}
                />
              ))}
            </SortableContext>
          </DndContext>
        </>
      )}
    </div>
  )
}

/** 单个步骤卡片（拖拽手柄排序 + 拖入步骤前/后插入） */
function StepCard(props: {
  step: CaseStep
  apis: ApiDefinition[]
  onUpdate: (patch: Partial<CaseStep>) => void
  onRemove: () => void
  onCopy?: () => void
  onPaste?: () => void
  hasCopied?: boolean
  onInsertRelative?: (type: CaseStepType, before: boolean) => void
}) {
  const { step, apis, onUpdate, onRemove, onCopy, onPaste, hasCopied = false, onInsertRelative } = props
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: step.id })
  const [dropPos, setDropPos] = useState<'before' | 'after' | null>(null)

  const contextMenu: MenuProps = {
    items: [
      { key: 'copy', label: '复制步骤', disabled: !onCopy },
      { key: 'paste', label: '粘贴步骤', disabled: !hasCopied || !onPaste },
    ],
    onClick: ({ key }) => {
      if (key === 'copy') onCopy?.()
      if (key === 'paste') onPaste?.()
    },
  }

  return (
    <div ref={setNodeRef} style={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 }}>
      <div
        onDragOver={(e) => {
          if (!e.dataTransfer.types.includes('application/step-type')) return
          e.preventDefault()
          e.stopPropagation()
          e.dataTransfer.dropEffect = 'copy'
          const rect = e.currentTarget.getBoundingClientRect()
          setDropPos(e.clientY < rect.top + rect.height / 2 ? 'before' : 'after')
        }}
        onDragLeave={() => setDropPos(null)}
        onDrop={(e) => {
          if (!e.dataTransfer.types.includes('application/step-type')) return
          e.preventDefault()
          e.stopPropagation()
          const type = e.dataTransfer.getData('application/step-type') as CaseStepType
          const rect = e.currentTarget.getBoundingClientRect()
          const before = e.clientY < rect.top + rect.height / 2
          setDropPos(null)
          if (type && onInsertRelative) onInsertRelative(type, before)
        }}
        style={{
          borderTop: dropPos === 'before' ? '2px solid #1677ff' : '2px solid transparent',
          borderBottom: dropPos === 'after' ? '2px solid #1677ff' : '2px solid transparent',
          borderRadius: 4,
          transition: 'border-color 0.15s',
        }}
      >
        <Dropdown menu={contextMenu} trigger={['contextMenu']}>
          <Card
            size="small"
            style={{ marginBottom: 8, opacity: step.enabled ? 1 : 0.55 }}
            title={
              <Space>
                <span {...attributes} {...listeners} style={{ cursor: 'grab', color: '#999', userSelect: 'none', touchAction: 'none', fontSize: 16 }} title="拖动排序">⠿</span>
                <Switch size="small" checked={step.enabled} onChange={(v) => onUpdate({ enabled: v })} title="启用/禁用" />
                <Tag color="blue">{STEP_TYPE_LABELS[step.type]}</Tag>
                <span style={{ fontSize: 13 }}>{step.name}</span>
              </Space>
            }
            extra={
              <Space size={4}>
                <Button size="small" type="text" danger onClick={onRemove}>删除</Button>
              </Space>
            }
          >
            <StepEditor step={step} apis={apis} onUpdate={onUpdate} />
          </Card>
        </Dropdown>
      </div>
    </div>
  )
}

/** 控制器子步骤区域（THEN/ELSE 复用） */
function ChildList(props: {
  label: string
  phase: CaseStep['phase']
  children: CaseStep[]
  apis: ApiDefinition[]
  onChange: (children: CaseStep[]) => void
}) {
  const { label, phase, children, apis, onChange } = props
  const addChild = () => {
    const child: CaseStep = {
      id: `child-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      type: 'request',
      phase,
      name: '子步骤',
      enabled: true,
      method: 'GET',
      headers: [],
      query: [],
      assertions: [],
      extracts: [],
    }
    onChange([...children, child])
  }
  return (
    <div style={{ borderLeft: '2px solid #e0e0e0', paddingLeft: 8, marginTop: 8 }}>
      <div style={{ fontSize: 12, color: '#999', marginBottom: 4 }}>{label}（{children.length}）</div>
      {children.map((child, ci) => (
        <StepCard
          key={child.id}
          step={child}
          apis={apis}
          onUpdate={(patch) => onChange(children.map((x, j) => (j === ci ? { ...x, ...patch } : x)))}
          onRemove={() => onChange(children.filter((_, j) => j !== ci))}
        />
      ))}
      <Button size="small" type="dashed" block onClick={addChild}>添加子步骤</Button>
    </div>
  )
}

/** 步骤编辑表单（根据类型） */
function StepEditor(props: { step: CaseStep; apis: ApiDefinition[]; onUpdate: (patch: Partial<CaseStep>) => void }) {
  const { step, apis, onUpdate } = props

  return (
    <Space orientation="vertical" style={{ width: '100%' }} size={8}>
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
          <div style={{ display: 'flex', gap: 8, width: '100%' }}>
            <Select size="small" style={{ width: 110, flexShrink: 0 }} value={step.method} options={HTTP_METHODS.map((m) => ({ value: m, label: m }))} onChange={(v) => onUpdate({ method: v })} />
            <Input size="small" style={{ flex: 1, minWidth: 0 }} value={step.url} placeholder="URL（支持 ${变量}）" onChange={(e) => onUpdate({ url: e.target.value })} />
          </div>
          <Input size="small" value={step.body} placeholder="请求体（JSON，支持 ${变量}）" onChange={(e) => onUpdate({ body: e.target.value })} />
          <KeyValueMiniEditor label="请求头" value={step.headers ?? []} onChange={(headers) => onUpdate({ headers })} />
          <KeyValueMiniEditor label="查询参数" value={step.query ?? []} onChange={(query) => onUpdate({ query })} />
          <AssertionMiniEditor value={step.assertions ?? []} onChange={(assertions) => onUpdate({ assertions })} />
          <ExtractMiniEditor value={step.extracts ?? []} onChange={(extracts) => onUpdate({ extracts })} />
        </>
      )}

      {step.type === 'script' && (
        <>
          <Space size={8} style={{ width: '100%' }}>
            <Select
              size="small"
              style={{ width: 130 }}
              value={step.scriptLang ?? 'javascript'}
              options={SCRIPT_LANG_OPTIONS}
              onChange={(v) => onUpdate({ scriptLang: v })}
            />
            <Button size="small" onClick={() => onUpdate({ script: scriptTemplate(step.scriptLang) })}>
              插入模板
            </Button>
          </Space>
          <Input.TextArea
            rows={5}
            value={step.script}
            placeholder={scriptTemplate(step.scriptLang)}
            onChange={(e) => onUpdate({ script: e.target.value })}
            style={{ fontFamily: 'monospace' }}
          />
        </>
      )}

      {step.type === 'wait' && (
        <Space orientation="vertical" style={{ width: '100%' }} size={8}>
          <Select
            size="small"
            style={{ width: 160 }}
            value={step.waitMode ?? 'fixed'}
            options={[
              { value: 'fixed', label: '固定等待' },
              { value: 'condition', label: '条件等待' },
            ]}
            onChange={(v) => onUpdate({ waitMode: v })}
          />
          {(step.waitMode ?? 'fixed') === 'fixed' ? (
            <Space>
              <span>等待时间</span>
              <InputNumber size="small" min={0} value={step.waitMs} onChange={(v) => onUpdate({ waitMs: v ?? 0 })} />
              <span>毫秒</span>
            </Space>
          ) : (
            <Space wrap>
              <Input size="small" style={{ width: 220 }} value={step.waitCondition} placeholder='条件表达式，如 ${status} == "ok"' onChange={(e) => onUpdate({ waitCondition: e.target.value })} />
              <span>最大等待</span>
              <InputNumber size="small" value={step.waitTimeout ?? 10000} onChange={(v) => onUpdate({ waitTimeout: v ?? 10000 })} />
              <span>ms</span>
              <span>轮询间隔</span>
              <InputNumber size="small" value={step.waitInterval ?? 100} onChange={(v) => onUpdate({ waitInterval: v ?? 100 })} />
              <span>ms</span>
            </Space>
          )}
        </Space>
      )}

      {step.type === 'variable' && (
        <Space wrap>
          <Input size="small" style={{ width: 160 }} value={step.varName} placeholder="变量名" onChange={(e) => onUpdate({ varName: e.target.value })} />
          <span>=</span>
          <Input size="small" style={{ width: 200 }} value={step.varValue} placeholder="变量值（支持 ${变量}）" onChange={(e) => onUpdate({ varValue: e.target.value })} />
          <Select
            size="small"
            style={{ width: 110 }}
            value={step.varMode ?? 'direct'}
            options={[
              { value: 'direct', label: '直接赋值' },
              { value: 'expression', label: '表达式计算' },
            ]}
            onChange={(v) => onUpdate({ varMode: v })}
          />
        </Space>
      )}

      {step.type === 'controller' && (
        <>
          <Select
            size="small"
            style={{ width: 150 }}
            value={step.controllerType}
            options={[
              { value: 'if', label: 'IF-ELSE 条件' },
              { value: 'for', label: 'FOR 循环' },
              { value: 'while', label: 'WHILE 循环' },
            ]}
            onChange={(v) => onUpdate({ controllerType: v })}
          />
          {step.controllerType === 'if' && (
            <>
              <Space size={8} style={{ width: '100%' }}>
                <span style={{ color: '#999', fontSize: 12 }}>判断方式：</span>
                <Select
                  size="small"
                  style={{ width: 120 }}
                  value={step.condMode ?? 'expression'}
                  options={[
                    { value: 'expression', label: '表达式' },
                    { value: 'script', label: '自定义代码' },
                  ]}
                  onChange={(v) => onUpdate({ condMode: v })}
                />
              </Space>
              {(step.condMode ?? 'expression') === 'expression' ? (
                <Input size="small" value={step.condition} placeholder='条件表达式，如 ${status} == "ok"' onChange={(e) => onUpdate({ condition: e.target.value })} />
              ) : (
                <>
                  <Space size={8} style={{ width: '100%' }}>
                    <Select
                      size="small"
                      style={{ width: 130 }}
                      value={step.scriptLang ?? 'javascript'}
                      options={SCRIPT_LANG_OPTIONS}
                      onChange={(v) => onUpdate({ scriptLang: v })}
                    />
                    <Button size="small" onClick={() => onUpdate({ script: ifTemplate(step.scriptLang) })}>插入模板</Button>
                  </Space>
                  <Input.TextArea
                    rows={4}
                    value={step.script}
                    placeholder={ifTemplate(step.scriptLang)}
                    onChange={(e) => onUpdate({ script: e.target.value })}
                    style={{ fontFamily: 'monospace' }}
                  />
                </>
              )}
              <ChildList label="THEN 区域" phase={step.phase} children={step.children ?? []} apis={apis} onChange={(children) => onUpdate({ children })} />
              <ChildList label="ELSE 区域" phase={step.phase} children={step.elseChildren ?? []} apis={apis} onChange={(elseChildren) => onUpdate({ elseChildren })} />
            </>
          )}
          {step.controllerType === 'for' && (
            <>
              <Space>
                <span>循环变量</span>
                <Input size="small" style={{ width: 100 }} value={step.loopVar} placeholder="如 i" onChange={(e) => onUpdate({ loopVar: e.target.value })} />
                <span>次数</span>
                <InputNumber size="small" value={step.loopCount} onChange={(v) => onUpdate({ loopCount: v ?? 1 })} />
              </Space>
              <ChildList label="循环体" phase={step.phase} children={step.children ?? []} apis={apis} onChange={(children) => onUpdate({ children })} />
            </>
          )}
          {step.controllerType === 'while' && (
            <>
              <Space wrap>
                <Input size="small" style={{ width: 200 }} value={step.condition} placeholder="条件表达式" onChange={(e) => onUpdate({ condition: e.target.value })} />
                <span>最大循环（必填）</span>
                <InputNumber size="small" value={step.maxLoops} onChange={(v) => onUpdate({ maxLoops: v ?? 100 })} />
              </Space>
              <ChildList label="循环体" phase={step.phase} children={step.children ?? []} apis={apis} onChange={(children) => onUpdate({ children })} />
            </>
          )}
        </>
      )}
    </Space>
  )
}

/** 简化的键值对编辑器（state 版，用于请求头/查询参数） */
function KeyValueMiniEditor(props: { label: string; value: KeyValue[]; onChange: (v: KeyValue[]) => void }) {
  const { label, value, onChange } = props
  const upd = (i: number, patch: Partial<KeyValue>) => onChange(value.map((x, j) => (j === i ? { ...x, ...patch } : x)))
  return (
    <div>
      <div style={{ fontWeight: 500, fontSize: 12, marginBottom: 4 }}>{label}（{value.length}）</div>
      {value.map((kv, i) => (
        <Space key={i} size={4} wrap style={{ marginBottom: 4, display: 'flex' }}>
          <Input size="small" style={{ width: 150 }} value={kv.key} placeholder="键" onChange={(e) => upd(i, { key: e.target.value })} />
          <Input size="small" style={{ width: 200 }} value={kv.value} placeholder="值（支持 ${变量}）" onChange={(e) => upd(i, { value: e.target.value })} />
          <Button size="small" type="text" danger onClick={() => onChange(value.filter((_, j) => j !== i))}>删</Button>
        </Space>
      ))}
      <Button size="small" type="dashed" block onClick={() => onChange([...value, { key: '', value: '' }])}>添加{label}</Button>
    </div>
  )
}

/** 简化的断言编辑器（state 版） */
function AssertionMiniEditor(props: { value: Assertion[]; onChange: (v: Assertion[]) => void }) {
  const { value, onChange } = props
  const upd = (i: number, patch: Partial<Assertion>) => onChange(value.map((x, j) => (j === i ? { ...x, ...patch } : x)))
  return (
    <div>
      <div style={{ fontWeight: 500, fontSize: 12, marginBottom: 4 }}>断言（{value.length}）</div>
      {value.map((a, i) => (
        <div key={i} style={{ border: '1px solid #f0f0f0', borderRadius: 4, padding: 8, marginBottom: 8 }}>
          <Space size={4} wrap style={{ marginBottom: 4 }}>
            <Select size="small" style={{ width: 100 }} value={a.type} options={[{ value: 'statusCode', label: '状态码' }, { value: 'jsonPath', label: 'JSONPath' }, { value: 'header', label: '响应头' }, { value: 'regex', label: '正则' }]} onChange={(v) => upd(i, { type: v as Assertion['type'] })} />
            <Select size="small" style={{ width: 92 }} value={a.operator ?? 'eq'} options={[{ value: 'eq', label: '等于' }, { value: 'ne', label: '不等于' }, { value: 'contains', label: '包含' }, { value: 'notContains', label: '不包含' }, { value: 'regex', label: '正则匹配' }, { value: 'gt', label: '大于' }, { value: 'lt', label: '小于' }]} onChange={(v) => upd(i, { operator: v as Assertion['operator'] })} />
            <Input size="small" style={{ width: 140 }} value={a.expression} placeholder="表达式" onChange={(e) => upd(i, { expression: e.target.value })} />
            <Input size="small" style={{ width: 120 }} value={a.expected} placeholder="期望值" onChange={(e) => upd(i, { expected: e.target.value })} />
            <Button size="small" type="text" danger onClick={() => onChange(value.filter((_, j) => j !== i))}>删</Button>
          </Space>
          <Space size={4} wrap>
            <Input size="small" style={{ width: 200 }} value={a.failMessage} placeholder="失败提示（可选）" onChange={(e) => upd(i, { failMessage: e.target.value })} />
            <Select size="small" style={{ width: 110 }} value={a.failStrategy ?? 'continue'} options={[{ value: 'continue', label: '失败继续' }, { value: 'stop', label: '停止用例' }]} onChange={(v) => upd(i, { failStrategy: v as Assertion['failStrategy'] })} />
          </Space>
        </div>
      ))}
      <Button size="small" type="dashed" block onClick={() => onChange([...value, { type: 'statusCode', expression: '', expected: '200', operator: 'eq', failStrategy: 'continue' }])}>添加断言</Button>
    </div>
  )
}

/** 简化的提取编辑器（state 版） */
function ExtractMiniEditor(props: { value: ExtractRule[]; onChange: (v: ExtractRule[]) => void }) {
  const { value, onChange } = props
  const upd = (i: number, patch: Partial<ExtractRule>) => onChange(value.map((x, j) => (j === i ? { ...x, ...patch } : x)))
  return (
    <div>
      <div style={{ fontWeight: 500, fontSize: 12, marginBottom: 4 }}>提取（{value.length}）</div>
      {value.map((e, i) => (
        <Space key={i} size={4} wrap style={{ marginBottom: 4, display: 'flex' }}>
          <Input size="small" style={{ width: 110 }} value={e.name} placeholder="变量名" onChange={(ev) => upd(i, { name: ev.target.value })} />
          <Select size="small" style={{ width: 100 }} value={e.type} options={[{ value: 'jsonPath', label: 'JSONPath' }, { value: 'header', label: '响应头' }, { value: 'regex', label: '正则' }]} onChange={(v) => upd(i, { type: v as ExtractRule['type'] })} />
          <Input size="small" style={{ width: 150 }} value={e.expression} placeholder="表达式" onChange={(ev) => upd(i, { expression: ev.target.value })} />
          <Input size="small" style={{ width: 110 }} value={e.defaultValue} placeholder="默认值" onChange={(ev) => upd(i, { defaultValue: ev.target.value })} />
          <Button size="small" type="text" danger onClick={() => onChange(value.filter((_, j) => j !== i))}>删</Button>
        </Space>
      ))}
      <Button size="small" type="dashed" block onClick={() => onChange([...value, { name: '', type: 'jsonPath', expression: '' }])}>添加提取</Button>
    </div>
  )
}
