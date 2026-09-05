/**
 * 全局变量管理弹窗（快捷入口）：包一层 Modal，复用 GlobalVariablesPanel。
 */
import { Modal } from 'antd'
import GlobalVariablesPanel from '../GlobalVariablesPanel'

interface Props {
  projectId?: string
  open: boolean
  onClose: () => void
}

export default function GlobalVariablesModal({ projectId, open, onClose }: Props) {
  return (
    <Modal title="全局变量" open={open} onCancel={onClose} footer={null} width={760}>
      <GlobalVariablesPanel projectId={projectId} />
    </Modal>
  )
}
