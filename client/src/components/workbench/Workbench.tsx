/**
 * 工作台布局（PRD 第 1 期）：顶部全局配置栏 + 左中右三栏工作台。
 * 三栏支持拖拽调宽、折叠/展开、宽度 localStorage 记忆；支持快捷键。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Dropdown } from 'antd'
import type { MenuProps } from 'antd'
import { clearAuth, getCurrentUser } from '../../api/auth'
import CaseLibraryPanel from './CaseLibraryPanel'
import CaseEditorPanel from './CaseEditorPanel'
import ApiManagerPanel from './ApiManagerPanel'

// 三栏最小宽度（PRD 约束）
const MIN_LEFT = 220
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
  // 三栏宽度（中间栏 = 总宽 - 左 - 右 - 分隔条）
  const [leftW, setLeftW] = useState(() => loadWidth('wb-left', 260))
  const [rightW, setRightW] = useState(() => loadWidth('wb-right', 320))
  // 折叠状态
  const [leftCollapsed, setLeftCollapsed] = useState(false)
  const [midCollapsed, setMidCollapsed] = useState(false)
  const [rightCollapsed, setRightCollapsed] = useState(false)

  // 拖拽中状态
  const dragRef = useRef<{ type: 'left' | 'right'; startX: number; startW: number } | null>(null)

  // 宽度变化时记忆
  useEffect(() => {
    localStorage.setItem('wb-left', String(leftW))
  }, [leftW])
  useEffect(() => {
    localStorage.setItem('wb-right', String(rightW))
  }, [rightW])

  const user = getCurrentUser()

  // ---------- 拖拽调宽 ----------
  const onResizeMouseDown = (type: 'left' | 'right') => (e: React.MouseEvent) => {
    e.preventDefault()
    dragRef.current = {
      type,
      startX: e.clientX,
      startW: type === 'left' ? leftW : rightW,
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
        setLeftW(Math.max(MIN_LEFT, Math.min(d.startW + dx, 800)))
      } else {
        setRightW(Math.max(MIN_RIGHT, Math.min(d.startW - dx, 800)))
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
  }, [])

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

  // 右上角用户下拉
  const userMenu: MenuProps['items'] = [
    { key: 'logout', label: '退出登录' },
  ]
  const onUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') {
      clearAuth()
      window.location.href = '/login'
    }
  }

  // 实际渲染宽度
  const effLeft = leftCollapsed ? COLLAPSED_W : leftW
  const effRight = rightCollapsed ? COLLAPSED_W : rightW

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh' }}>
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
        <TopEntry label="设置全局变量" icon="⚙" />
        <TopEntry label="调试记录" icon="📋" />
        <TopEntry label="回收站" icon="🗑" />
        <div style={{ flex: 1 }} />
        <span style={{ color: '#666', fontSize: 13 }}>当前项目</span>
        <Dropdown menu={{ items: userMenu, onClick: onUserMenuClick }}>
          <span style={{ cursor: 'pointer', color: '#333' }} title={user?.role}>
            {user?.username ?? ''}
          </span>
        </Dropdown>
      </div>

      {/* 水平分割线下方：三栏工作台 */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
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
            <CaseLibraryPanel onCollapse={toggleLeft} />
          )}
          <ResizeHandle onMouseDown={onResizeMouseDown('left')} />
        </div>

        {/* 中栏：编写用例 */}
        <div style={{ flex: 1, display: 'flex', minWidth: 0 }}>
          {midCollapsed ? (
            <div
              style={{ width: '100%', writingMode: 'vertical-rl', textAlign: 'center', cursor: 'pointer', background: '#fafafa', fontSize: 13, color: '#666' }}
              onClick={toggleMid}
            >
              编写用例
            </div>
          ) : (
            <div style={{ flex: 1, minWidth: 0 }}>
              <CaseEditorPanel onCollapse={toggleMid} />
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
            <ApiManagerPanel onCollapse={toggleRight} />
          )}
        </div>
      </div>
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
