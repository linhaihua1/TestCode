/**
 * 左侧用例库面板（PRD 第 2 期）：目录树 + 用例管理。
 * 对接后端 modules + cases 路由，支持新建目录/用例、重命名、删除、搜索、点击加载用例。
 */
import { useCallback, useEffect, useState } from 'react'
import { Button, Dropdown, Input, Modal, Tree, message } from 'antd'
import type { TreeDataNode } from 'antd'
import { api, getErrorMessage } from '../../api/client'
import type { CaseInfo, Module } from '../../api/types'

interface Props {
  projectId?: string
  selectedCaseId?: string
  onCollapse: () => void
  onSelectCase: (caseId: string) => void
}

export default function CaseLibraryPanel({ projectId, selectedCaseId, onCollapse, onSelectCase }: Props) {
  const [modules, setModules] = useState<Module[]>([])
  const [cases, setCases] = useState<CaseInfo[]>([])
  const [keyword, setKeyword] = useState('')

  // 弹窗状态
  const [modalOpen, setModalOpen] = useState(false)
  const [modalType, setModalType] = useState<'module' | 'case' | 'rename'>('module')
  const [modalTitle, setModalTitle] = useState('')
  const [nameInput, setNameInput] = useState('')
  const [targetParentId, setTargetParentId] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!projectId) return
    try {
      const [modList, caseList] = await Promise.all([
        api.listModules(projectId),
        api.listCaseLibraryCases(projectId),
      ])
      // 用例库只展示「用例」类型的目录，接口导入产生的 api 类型目录由接口管理面板负责
      setModules(modList.filter((m) => m.type !== 'api'))
      setCases(caseList)
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }, [projectId])

  useEffect(() => {
    load()
  }, [load])

  // 构建目录树
  const buildTree = (): TreeDataNode[] => {
    const filteredCases = keyword ? cases.filter((c) => c.name.includes(keyword)) : cases
    const moduleMap = new Map<string, Module[]>()
    const rootModules: Module[] = []

    for (const m of modules) {
      if (m.parentId) {
        const list = moduleMap.get(m.parentId) ?? []
        list.push(m)
        moduleMap.set(m.parentId, list)
      } else {
        rootModules.push(m)
      }
    }

    const casesByModule = new Map<string, CaseInfo[]>()
    const orphanCases: CaseInfo[] = []
    for (const c of filteredCases) {
      if (c.moduleId) {
        const list = casesByModule.get(c.moduleId) ?? []
        list.push(c)
        casesByModule.set(c.moduleId, list)
      } else {
        orphanCases.push(c)
      }
    }

    const buildModuleNode = (m: Module): TreeDataNode => {
      const children: TreeDataNode[] = [
        ...(moduleMap.get(m.id) ?? []).map(buildModuleNode),
        ...(casesByModule.get(m.id) ?? []).map((c) => ({
          key: `case:${c.id}`,
          title: c.name,
          isLeaf: true,
        })),
      ]
      return { key: `module:${m.id}`, title: m.name, children }
    }

    const nodes: TreeDataNode[] = rootModules.map(buildModuleNode)
    orphanCases.forEach((c) => {
      nodes.push({ key: `case:${c.id}`, title: c.name, isLeaf: true })
    })
    return nodes
  }

  // 打开弹窗
  const openModal = (type: 'module' | 'case' | 'rename', title: string, parentId: string | null, initName = '') => {
    setModalType(type)
    setModalTitle(title)
    setTargetParentId(parentId)
    setNameInput(initName)
    setModalOpen(true)
  }

  // 提交弹窗
  const submitModal = async () => {
    if (!projectId || !nameInput.trim()) {
      message.warning('请输入名称')
      return
    }
    try {
      if (modalType === 'module') {
        await api.createModule(projectId, { name: nameInput.trim(), parentId: targetParentId })
      } else if (modalType === 'case') {
        await api.createCase(projectId, { name: nameInput.trim(), moduleId: targetParentId })
      } else if (modalType === 'rename') {
        const key = targetParentId // 复用 targetParentId 存节点 key
        if (key?.startsWith('module:')) {
          await api.updateModule(key.slice(7), { name: nameInput.trim() })
        } else if (key?.startsWith('case:')) {
          await api.updateCase(key.slice(5), { name: nameInput.trim() })
        }
      }
      message.success('操作成功')
      setModalOpen(false)
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 删除节点
  const deleteNode = async (key: string) => {
    try {
      if (key.startsWith('module:')) {
        await api.deleteModule(key.slice(7))
      } else if (key.startsWith('case:')) {
        await api.deleteCase(key.slice(5))
      }
      message.success('删除成功')
      load()
    } catch (e) {
      message.error(getErrorMessage(e))
    }
  }

  // 右键菜单
  const renderContextMenu = (key: string, isModule: boolean) => ({
    items: [
      isModule
        ? { key: 'new-module', label: '新建子目录' }
        : null,
      isModule
        ? { key: 'new-case', label: '新建用例' }
        : null,
      { key: 'rename', label: '重命名' },
      { key: 'delete', label: '删除', danger: true },
    ].filter(Boolean) as { key: string; label: string; danger?: boolean }[],
    onClick: ({ key: action }: { key: string }) => {
      if (action === 'new-module') openModal('module', '新建子目录', isModule ? key.slice(7) : null)
      if (action === 'new-case') openModal('case', '新建用例', isModule ? key.slice(7) : null)
      if (action === 'rename') openModal('rename', '重命名', key)
      if (action === 'delete') {
        Modal.confirm({
          title: '确认删除？',
          content: isModule ? '删除目录将同步删除其下用例' : '删除用例',
          onOk: () => deleteNode(key),
        })
      }
    },
  })

  const treeData = buildTree()

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderRight: '1px solid #eee', background: '#fff', minWidth: 0 }}>
      <div style={{ height: 40, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', borderBottom: '1px solid #f0f0f0', fontWeight: 600 }}>
        <span>用例库</span>
        <Button type="text" size="small" onClick={onCollapse}>«</Button>
      </div>

      {/* 工具栏 */}
      <div style={{ padding: 8, display: 'flex', gap: 4 }}>
        <Input placeholder="搜索用例" size="small" value={keyword} onChange={(e) => setKeyword(e.target.value)} allowClear style={{ flex: 1 }} />
        <Button size="small" onClick={() => openModal('module', '新建目录', null)}>目录</Button>
        <Button size="small" type="primary" onClick={() => openModal('case', '新建用例', null)}>用例</Button>
      </div>

      {/* 目录树 */}
      <div style={{ flex: 1, overflow: 'auto', padding: '0 8px 8px' }}>
        <Tree
          treeData={treeData}
          blockNode
          defaultExpandAll
          selectedKeys={selectedCaseId ? [`case:${selectedCaseId}`] : []}
          onSelect={(keys) => {
            const k = String(keys[0] ?? '')
            if (k.startsWith('case:')) onSelectCase(k.slice(5))
          }}
          titleRender={(node) => {
            const key = String(node.key)
            const isModule = key.startsWith('module:')
            return (
              <Dropdown menu={renderContextMenu(key, isModule)} trigger={['contextMenu']}>
                <span style={{ display: 'inline-block', width: '100%' }}>{String(node.title)}</span>
              </Dropdown>
            )
          }}
        />
      </div>

      {/* 弹窗 */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={submitModal}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Input placeholder="名称" value={nameInput} onChange={(e) => setNameInput(e.target.value)} onPressEnter={submitModal} />
      </Modal>
    </div>
  )
}
