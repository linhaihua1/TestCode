/**
 * UI 用例执行页：收集 UI 用例 → 拖拽排序 → 按顺序执行。
 * 不经过「场景列表」，直接在同一页面完成用例收集与执行。
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
import { useParams } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import type { UiReport, UiTestCase, UiStepResult } from '../api/types'

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
  const [report, setReport] = useState<UiReport | null>(null)
  const [running, setRunning] = useState(false)
  const [loading, setLoading] = useState(false)

  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  const load = async () => {
    setLoading(true)
    try {
      setAllCases(await api.listUiTests(projectId!))
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

  // 拖拽结束：重排执行顺序
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

  // 执行：按当前顺序收集用例 ID，交给后端在同一个浏览器会话中执行
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

  // 尚未被收集的用例（可添加）
  const availableCases = allCases.filter((c) => !selected.some((s) => s.id === c.id))

  const caseResults = (report?.details ?? []) as unknown as CaseResult[]

  return (
    <Card
      title="UI 用例执行"
      extra={
        <Button type="primary" loading={running} onClick={run}>
          执行
        </Button>
      }
    >
      <Spin spinning={loading}>
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
