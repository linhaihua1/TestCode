/**
 * 环境配置页（统一入口）：管理接口自动化与 UI 自动化共用的配置。
 * Tab 1 环境管理：Base URL / 环境变量 / 公共请求头。
 * Tab 2 全局变量：跨项目/场景共享的变量。
 */
import { Tabs } from 'antd'
import EnvironmentPage from './EnvironmentPage'
import GlobalVariablesPanel from '../components/GlobalVariablesPanel'
import { useProject } from '../context/ProjectContext'

export default function EnvironmentConfigPage() {
  const { projectId } = useProject()

  return (
    <Tabs
      items={[
        { key: 'env', label: '环境管理', children: <EnvironmentPage /> },
        { key: 'gv', label: '全局变量', children: <GlobalVariablesPanel projectId={projectId} /> },
      ]}
    />
  )
}
