/**
 * 左侧用例库面板（PRD 第 2 期）：目录树 + 用例管理。
 * 目录/用例的新增、重命名、删除通过节点上的图标直接操作（无需右键）。
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { Button, Input, Modal, Space, Tree, message } from 'antd'
import type { TreeDataNode } from 'antd'
import { DeleteOutlined, EditOutlined, FileAddOutlined, FolderAddOutlined, PlusOutlined } from '@ant-design/icons'
import { api, getErrorMessage } from '../../api/client'
import ScrollBar from '../ScrollBar'
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
  const treeScrollRef = useRef<HTMLDivElement>(null)

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

  // 删除确认
  const confirmDelete = (key: string, isModule: boolean) => {
    Modal.confirm({
      title: '确认删除？',
      content: isModule ? '删除目录将同步删除其下子目录与用例' : '删除用例',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: () => deleteNode(key),
    })
  }

  // 拖拽调整用例顺序 / 移动到目录
  const handleDrop = (info: {
    node: TreeDataNode
    dragNode: TreeDataNode
    dropPosition: number
    dropToGap: boolean
  }) => {
    const dragKey = String(info.dragNode.key)
    const nodeKey = String(info.node.key)
    if (!dragKey.startsWith('case:')) return // 仅支持用例拖拽

    const dragId = dragKey.slice(5)
    if (nodeKey.startsWith('case:')) {
      // 拖到另一个用例上：同目录下调整顺序
      const targetId = nodeKey.slice(5)
      const dragCase = cases.find((c) => c.id === dragId)
      const targetCase = cases.find((c) => c.id === targetId)
      if (!dragCase || !targetCase || dragCase.moduleId !== targetCase.moduleId) return

      const moduleId = dragCase.moduleId ?? null
      const ordered = cases.filter((c) => (c.moduleId ?? null) === moduleId).map((c) => c.id)
      const fromIndex = ordered.indexOf(dragId)
      if (fromIndex < 0) return
      ordered.splice(fromIndex, 1)
      const targetIndex = ordered.indexOf(targetId)
      if (targetIndex < 0) return
      const insertAt = info.dropPosition < 0 ? targetIndex : targetIndex + 1
      ordered.splice(insertAt, 0, dragId)
      api
        .reorderCases(projectId!, ordered)
        .then(load)
        .catch((e) => message.error(getErrorMessage(e)))
    } else if (nodeKey.startsWith('module:')) {
      // 拖到目录上：移动到该目录末尾
      const moduleId = nodeKey.slice(7)
      api
        .updateCase(dragId, { moduleId })
        .then(load)
        .catch((e) => message.error(getErrorMessage(e)))
    }
  }

  const treeData = buildTree()

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', borderRight: '1px solid #eee', background: '#fff', minWidth: 0 }}>
      <div style={{ height: 40, display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 12px', borderBottom: '1px solid #f0f0f0', fontWeight: 600 }}>
        <span>用例库</span>
        <Button type="text" size="small" onClick={onCollapse}>«</Button>
      </div>

      {/* 工具栏：搜索 + 新建目录（用例入口放到目录节点上） */}
      <div style={{ padding: 8, display: 'flex', gap: 4 }}>
        <Input placeholder="搜索用例" size="small" value={keyword} onChange={(e) => setKeyword(e.target.value)} allowClear style={{ flex: 1 }} />
        <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => openModal('module', '新建目录', null)}>
          新建目录
        </Button>
      </div>

      {/* 目录树 + 滚动拖拽条 */}
      <div style={{ flex: 1, display: 'flex', minHeight: 0 }}>
        <div ref={treeScrollRef} className="scrollbar-hidden" style={{ flex: 1, overflow: 'auto', padding: '0 8px 8px' }}>
          <Tree
          treeData={treeData}
          blockNode
          defaultExpandAll
          draggable={{ icon: false, nodeDraggable: (node) => String(node.key).startsWith('case:') }}
          onDrop={handleDrop}
          selectedKeys={selectedCaseId ? [`case:${selectedCaseId}`] : []}
          onSelect={(keys) => {
            const k = String(keys[0] ?? '')
            if (k.startsWith('case:')) onSelectCase(k.slice(5))
          }}
          titleRender={(node) => {
            const key = String(node.key)
            const isModule = key.startsWith('module:')
            const id = isModule ? key.slice(7) : key.slice(5)
            return (
              <div style={{ display: 'flex', alignItems: 'center', width: '100%', paddingRight: 4 }}>
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {String(node.title)}
                </span>
                <Space size={0} onClick={(e) => e.stopPropagation()}>
                  {isModule && (
                    <>
                      <Button type="text" size="small" icon={<FolderAddOutlined />} title="新建子目录" onClick={() => openModal('module', '新建子目录', id)} />
                      <Button type="text" size="small" icon={<FileAddOutlined />} title="新建用例" onClick={() => openModal('case', '新建用例', id)} />
                    </>
                  )}
                  <Button type="text" size="small" icon={<EditOutlined />} title="重命名" onClick={() => openModal('rename', '重命名', key, String(node.title))} />
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} title="删除" onClick={() => confirmDelete(key, isModule)} />
                </Space>
              </div>
            )
          }}
        />
        </div>
        <ScrollBar containerRef={treeScrollRef} />
      </div>

      {/* 弹窗 */}
      <Modal
        title={modalTitle}
        open={modalOpen}
        onOk={submitModal}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
      >
        <Input placeholder="名称" value={nameInput} onChange={(e) => setNameInput(e.target.value)} onPressEnter={submitModal} />
      </Modal>
    </div>
  )
}
