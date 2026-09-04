/**
 * 左侧用例库面板（PRD 第 1 期骨架）：目录树 + 搜索 + 折叠按钮。
 * 目录树右键菜单、版本管理、批量操作、标签等能力在后续期次填充。
 */
import { Button, Input, Tree, Tooltip } from 'antd'
import type { TreeDataNode } from 'antd'
import { useState } from 'react'

export default function CaseLibraryPanel({ onCollapse }: { onCollapse: () => void }) {
  const [keyword, setKeyword] = useState('')

  // 占位目录树数据（后续期次接真实数据）
  const treeData: TreeDataNode[] = [
    {
      title: '默认模块',
      key: 'root',
      children: [
        { title: '登录用例', key: 'case-1' },
        { title: '查询用例', key: 'case-2' },
      ],
    },
  ]

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderRight: '1px solid #eee', background: '#fff', minWidth: 0 }}>
      {/* 面板头：标题 + 折叠 */}
      <div style={{ height: 40, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', borderBottom: '1px solid #f0f0f0', fontWeight: 600 }}>
        <span>用例库</span>
        <Tooltip title="折叠">
          <Button type="text" size="small" onClick={onCollapse}>«</Button>
        </Tooltip>
      </div>
      {/* 搜索框 */}
      <div style={{ padding: 8 }}>
        <Input
          placeholder="搜索用例"
          size="small"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          allowClear
        />
      </div>
      {/* 目录树 */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px 8px' }}>
        <Tree defaultExpandAll treeData={treeData} blockNode />
      </div>
      {/* 底部工具栏占位 */}
      <div style={{ height: 36, borderTop: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', padding: '0 8px', gap: 8, color: '#999', fontSize: 12 }}>
        <span>批量操作</span>
        <span>版本管理</span>
      </div>
    </div>
  )
}
