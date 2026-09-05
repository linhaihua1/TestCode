/**
 * 工作台布局（PRD 第 1 期）：顶部全局配置栏 + 左中右三栏工作台。
 * 三栏支持拖拽调宽、折叠/展开、宽度 localStorage 记忆；支持快捷键。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { Button } from 'antd'
import { useProject } from '../../context/ProjectContext'
import CaseLibraryPanel from './CaseLibraryPanel'
import CaseEditorPanel from './CaseEditorPanel'
import ApiManagerPanel from './ApiManagerPanel'
import GlobalVariablesModal from './GlobalVariablesModal'
import DebugRecordsModal from './DebugRecordsModal'
import RecycleBinModal from './RecycleBinModal'
import TestExecutionModal from './TestExecutionModal'

// 三栏最小宽度（PRD 约束）
const MIN_LEFT = 220
const MIN_MID = 400
const MIN_RIGHT = 280
// 折叠后的窄条宽度
const COLLAPSED_W = 40

function loadWidth(key: string, def: number): number {
  const v = Number(localStorage.getItem(key))
  return v > 0 ? v : def
}

/** 顶部栏入口按钮 */
function TopEntry(props: { label: string; icon?: string; onClick?: () => void }) {
  return (
    <Button type="text" size="small" onClick={props.onClick} style={{ color: '#333' }}>
      {props.icon && <span style={{ marginRight: 4 }}>{props.icon}</span>}
      {props.label}
    </Button>
  )
}

export default function Workbench() {
  // 项目来自全局上下文（顶栏选择器统一维护），选中用例为工作台内部状态
  const { projectId } = useProject()
  const [selectedCaseId, setSelectedCaseId] = useState<string | undefined>()
  // 顶部弹窗状态
  const [gvOpen, setGvOpen] = useState(false)
  const [drOpen, setDrOpen] = useState(false)
  const [rbOpen, setRbOpen] = useState(false)
  const [teOpen, setTeOpen] = useState(false)

  // 项目切换时清空当前选中用例
  useEffect(() => {
    setSelectedCaseId(undefined)
  }, [projectId])

  // 三栏宽度（中间栏 = 总宽 - 左 - 右 - 分隔条）
  const [leftW, setLeftW] = useState(() => loadWidth('wb-left', 260))
  const [rightW, setRightW] = useState(() => loadWidth('wb-right', 320))
  // 折叠状态
  const [leftCollapsed, setLeftCollapsed] = useState(false)
  const [midCollapsed, setMidCollapsed] = useState(false)
  const [rightCollapsed, setRightCollapsed] = useState(false)

  // 拖拽中状态
  const dragRef = useRef<{ type: 'left' | 'right'; startX: number; startW: number; containerW: number } | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  // 宽度变化时记忆
  useEffect(() => {
    localStorage.setItem('wb-left', String(leftW))
  }, [leftW])
  useEffect(() => {
    localStorage.setItem('wb-right', String(rightW))
  }, [rightW])

  // ---------- 拖拽调宽 ----------
  const onResizeMouseDown = (type: 'left' | 'right') => (e: React.MouseEvent) => {
    e.preventDefault()
    const containerW = containerRef.current?.clientWidth ?? window.innerWidth
    dragRef.current = {
      type,
      startX: e.clientX,
      startW: type === 'left' ? leftW : rightW,
      containerW,
    }
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      const d = dragRef.current
      if (!d) return
      const dx = e.clientX - d.startX
      if (d.type === 'left') {
        // 左栏最大宽度需保证中间栏 >= MIN_MID
        const maxLeft = Math.max(MIN_LEFT, d.containerW - MIN_MID - rightW - 10)
        setLeftW(Math.max(MIN_LEFT, Math.min(d.startW + dx, maxLeft)))
      } else {
        // 右栏最大宽度需保证中间栏 >= MIN_MID
        const maxRight = Math.max(MIN_RIGHT, d.containerW - MIN_MID - leftW - 10)
        setRightW(Math.max(MIN_RIGHT, Math.min(d.startW - dx, maxRight)))
      }
    }
    const onUp = () => {
      dragRef.current = null
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
    return () => {
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
  }, [leftW, rightW])

  // ---------- 折叠/展开 ----------
  const toggleLeft = () => setLeftCollapsed((v) => !v)
  const toggleMid = () => setMidCollapsed((v) => !v)
  const toggleRight = () => setRightCollapsed((v) => !v)

  // ---------- 快捷键（PRD：Ctrl+S / Ctrl+Enter / Ctrl+F） ----------
  const onSave = useCallback(() => {
    // 第 1 期占位：后续接实际保存
    window.dispatchEvent(new CustomEvent('workbench:save'))
  }, [])
  const onDebug = useCallback(() => {
    window.dispatchEvent(new CustomEvent('workbench:debug'))
  }, [])
  const onSearch = useCallback(() => {
    window.dispatchEvent(new CustomEvent('workbench:search'))
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey)) return
      const k = e.key.toLowerCase()
      if (k === 's') {
        e.preventDefault()
        onSave()
      } else if (k === 'enter') {
        e.preventDefault()
        onDebug()
      } else if (k === 'f') {
        e.preventDefault()
        onSearch()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onSave, onDebug, onSearch])

  // 实际渲染宽度
  const effLeft = leftCollapsed ? COLLAPSED_W : leftW
  const effRight = rightCollapsed ? COLLAPSED_W : rightW

  return (
    <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
      {/* 顶部全局配置栏 */}
      <div
        style={{
          height: 48,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '0 16px',
          background: '#fff',
          borderBottom: '1px solid #e8e8e8',
        }}
      >
        <TopEntry label="设置全局变量" icon="⚙" onClick={() => setGvOpen(true)} />
        <TopEntry label="测试执行" icon="▶" onClick={() => setTeOpen(true)} />
        <TopEntry label="调试记录" icon="📋" onClick={() => setDrOpen(true)} />
        <TopEntry label="回收站" icon="🗑" onClick={() => setRbOpen(true)} />
        <div style={{ flex: 1 }} />
      </div>

      {/* 水平分割线下方：三栏工作台 */}
      <div ref={containerRef} style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* 左栏：用例库 */}
        <div style={{ width: effLeft, display: 'flex', minWidth: effLeft === COLLAPSED_W ? COLLAPSED_W : MIN_LEFT }}>
          {leftCollapsed ? (
            <div
              style={{ width: COLLAPSED_W, writingMode: 'vertical-rl', textAlign: 'center', cursor: 'pointer', background: '#fafafa', borderRight: '1px solid #eee', fontSize: 13, color: '#666' }}
              onClick={toggleLeft}
            >
              用例库
            </div>
          ) : (
            <CaseLibraryPanel
              projectId={projectId}
              selectedCaseId={selectedCaseId}
              onCollapse={toggleLeft}
              onSelectCase={setSelectedCaseId}
            />
          )}
          <ResizeHandle onMouseDown={onResizeMouseDown('left')} />
        </div>

        {/* 中栏：编写用例 */}
        <div style={{ flex: 1, display: 'flex', minWidth: 0 }}>
          {midCollapsed ? (
            <div
              style={{ width: COLLAPSED_W, flexShrink: 0, writingMode: 'vertical-rl', textAlign: 'center', cursor: 'pointer', background: '#fafafa', fontSize: 13, color: '#666' }}
              onClick={toggleMid}
            >
              编写用例
            </div>
          ) : (
            <div style={{ flex: 1, minWidth: MIN_MID }}>
              <CaseEditorPanel projectId={projectId} caseId={selectedCaseId} onCollapse={toggleMid} />
            </div>
          )}
        </div>

        {/* 右栏：接口管理 */}
        <div style={{ width: effRight, display: 'flex', minWidth: effRight === COLLAPSED_W ? COLLAPSED_W : MIN_RIGHT }}>
          <ResizeHandle onMouseDown={onResizeMouseDown('right')} />
          {rightCollapsed ? (
            <div
              style={{ width: COLLAPSED_W, writingMode: 'vertical-rl', textAlign: 'center', cursor: 'pointer', background: '#fafafa', borderLeft: '1px solid #eee', fontSize: 13, color: '#666' }}
              onClick={toggleRight}
            >
              接口管理
            </div>
          ) : (
            <ApiManagerPanel projectId={projectId} onCollapse={toggleRight} />
          )}
        </div>
      </div>

      {/* 顶部四个弹窗 */}
      <GlobalVariablesModal projectId={projectId} open={gvOpen} onClose={() => setGvOpen(false)} />
      <DebugRecordsModal projectId={projectId} open={drOpen} onClose={() => setDrOpen(false)} />
      <RecycleBinModal projectId={projectId} open={rbOpen} onClose={() => setRbOpen(false)} />
      <TestExecutionModal projectId={projectId} open={teOpen} onClose={() => setTeOpen(false)} />
    </div>
  )
}

/** 拖拽分隔条 */
function ResizeHandle({ onMouseDown }: { onMouseDown: (e: React.MouseEvent) => void }) {
  return (
    <div
      onMouseDown={onMouseDown}
      style={{
        width: 5,
        cursor: 'col-resize',
        background: 'transparent',
        flexShrink: 0,
        transition: 'background 0.2s',
      }}
      onMouseEnter={(e) => (e.currentTarget.style.background = '#d9d9d9')}
      onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
    />
  )
}
