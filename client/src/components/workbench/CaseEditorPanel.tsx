/**
 * 中间编写用例面板（PRD 第 2 期）：加载选中用例详情 + 名称编辑 + 保存 + 双 Tab。
 * 前置/测试/后置步骤、断言提取等能力在后续期次填充。
 */
import { useEffect, useState } from 'react'
import { Button, Input, Tabs, Tooltip, message } from 'antd'
import { api, getErrorMessage } from '../../api/client'
import type { CaseInfo } from '../../api/types'

interface Props {
  caseId?: string
  onCollapse: () => void
}

export default function CaseEditorPanel({ caseId, onCollapse }: Props) {
  const [caseInfo, setCaseInfo] = useState<CaseInfo | null>(null)
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)

  // 加载用例详情
  useEffect(() => {
    if (!caseId) {
      setCaseInfo(null)
      setName('')
      return
    }
    api
      .getCase(caseId)
      .then((c) => {
        setCaseInfo(c)
        setName(c.name)
      })
      .catch((e) => message.error(getErrorMessage(e)))
  }, [caseId])

  // 保存用例（触发后端版本快照）
  const save = async () => {
    if (!caseId) return
    setSaving(true)
    try {
      await api.updateCase(caseId, { name })
      message.success('已保存')
      // 重新加载，获取最新版本号
      const c = await api.getCase(caseId)
      setCaseInfo(c)
      setName(c.name)
    } catch (e) {
      message.error(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  // 监听 Ctrl+S 快捷键（Workbench 派发 workbench:save 事件）
  useEffect(() => {
    const onSaveEvent = () => save()
    window.addEventListener('workbench:save', onSaveEvent)
    return () => window.removeEventListener('workbench:save', onSaveEvent)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [caseId, name])

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

  const stepCount = (caseInfo.steps ?? []).length

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#fff', minWidth: 0 }}>
      {/* 面板头：折叠 + 名称 + 保存 */}
      <div style={{ height: 48, display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px', borderBottom: '1px solid #f0f0f0' }}>
        <Button type="text" size="small" onClick={onCollapse}>»</Button>
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="用例名称"
          variant="borderless"
          style={{ fontWeight: 600, fontSize: 15, flex: 1 }}
        />
        <Tooltip title="Ctrl+S">
          <Button size="small" type="primary" loading={saving} onClick={save}>
            保存
          </Button>
        </Tooltip>
        <span style={{ color: '#999', fontSize: 12 }}>v{caseInfo.version}</span>
      </div>

      {/* 双 Tab */}
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
                  <Section title="前置步骤" />
                  <Section title="测试步骤" count={stepCount} dashed />
                  <Section title="后置步骤" />
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
    </div>
  )
}

/** 步骤区域占位 */
function Section({ title, count, dashed }: { title: string; count?: number; dashed?: boolean }) {
  return (
    <div
      style={{
        minHeight: 120,
        marginBottom: 12,
        padding: 12,
        border: `1px ${dashed ? 'dashed' : 'solid'} #e0e0e0`,
        borderRadius: 6,
        color: '#999',
        fontSize: 13,
      }}
    >
      <div style={{ fontWeight: 600, color: '#333', marginBottom: 8 }}>
        {title}
        {count !== undefined && <span style={{ color: '#999', fontWeight: 400, marginLeft: 8 }}>{count} 步</span>}
      </div>
      步骤区域（后续期次填充：接口请求 / 脚本 / 等待 / 变量赋值 / 流程控制器等）
    </div>
  )
}
