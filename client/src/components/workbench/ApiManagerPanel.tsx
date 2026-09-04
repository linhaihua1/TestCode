/**
 * 右侧接口管理面板（PRD 第 1 期骨架）：接口定义库 + 搜索 + 导入/Mock/同步入口 + 折叠按钮。
 * Swagger 导入、Mock、接口同步、元信息维护等能力在后续期次填充。
 */
import { Button, Input, List, Tooltip } from 'antd'
import { useState } from 'react'

export default function ApiManagerPanel({ onCollapse }: { onCollapse: () => void }) {
  const [keyword, setKeyword] = useState('')

  // 占位接口数据（后续期次接真实数据）
  const apis = [
    { name: '登录', method: 'POST', path: '/login' },
    { name: '查询用户', method: 'GET', path: '/users/{id}' },
  ].filter((a) => a.name.includes(keyword))

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderLeft: '1px solid #eee', background: '#fff', minWidth: 0 }}>
      {/* 面板头 */}
      <div style={{ height: 40, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', borderBottom: '1px solid #f0f0f0', fontWeight: 600 }}>
        <span>接口管理</span>
        <Tooltip title="折叠">
          <Button type="text" size="small" onClick={onCollapse}>»</Button>
        </Tooltip>
      </div>
      {/* 搜索 + 快捷操作 */}
      <div style={{ padding: 8, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Input placeholder="搜索接口" size="small" value={keyword} onChange={(e) => setKeyword(e.target.value)} allowClear />
        <div style={{ display: 'flex', gap: 4 }}>
          <Button size="small" type="text">导入</Button>
          <Button size="small" type="text">Mock</Button>
          <Button size="small" type="text">同步</Button>
        </div>
      </div>
      {/* 接口列表 */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px' }}>
        <List
          size="small"
          dataSource={apis}
          renderItem={(a) => (
            <List.Item style={{ padding: '6px 8px' }}>
              <div>
                <span style={{ color: a.method === 'GET' ? '#52c41a' : '#1677ff', fontWeight: 600, marginRight: 8 }}>
                  {a.method}
                </span>
                <span style={{ color: '#333' }}>{a.name}</span>
                <div style={{ color: '#999', fontSize: 12 }}>{a.path}</div>
              </div>
            </List.Item>
          )}
        />
      </div>
    </div>
  )
}
