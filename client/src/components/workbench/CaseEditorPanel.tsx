/**
 * 中间编写用例面板（PRD 第 1 期骨架）：用例名称编辑 + 脚本/调试记录双 Tab。
 * 前置/测试/后置步骤、流程控制器、断言提取等能力在后续期次填充。
 */
import { Button, Input, Tabs, Tooltip } from 'antd'
import { useState } from 'react'

export default function CaseEditorPanel({ onCollapse }: { onCollapse: () => void }) {
  const [name, setName] = useState('未命名用例')

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', background: '#fff', minWidth: 0 }}>
      {/* 面板头：折叠按钮 + 用例名称编辑 */}
      <div style={{ height: 40, display: 'flex', alignItems: 'center', gap: 8, padding: '0 12px', borderBottom: '1px solid #f0f0f0' }}>
        <Tooltip title="折叠">
          <Button type="text" size="small" onClick={onCollapse}>»</Button>
        </Tooltip>
        <Input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="用例名称"
          variant="borderless"
          style={{ fontWeight: 600, fontSize: 15 }}
        />
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
                  <Section title="测试步骤" dashed />
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
function Section({ title, dashed }: { title: string; dashed?: boolean }) {
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
      <div style={{ fontWeight: 600, color: '#333', marginBottom: 8 }}>{title}</div>
      步骤区域（后续期次填充：接口请求 / 脚本 / 等待 / 变量赋值 / 流程控制器等）
    </div>
  )
}
