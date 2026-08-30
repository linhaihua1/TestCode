/**
 * 执行环境选择器（公共组件）
 * 封装「加载项目环境列表 + 默认选中第一个 + 选择环境」的通用逻辑。
 * 供接口自动化的用例执行、用例调试/运行等场景复用。
 */
import { useEffect, useState } from 'react'
import { Select, message } from 'antd'
import { api, getErrorMessage } from '../api/client'
import type { Environment } from '../api/types'

interface EnvironmentSelectProps {
  projectId: string
  value?: string
  onChange?: (value: string) => void
  style?: React.CSSProperties
  placeholder?: string
}

export default function EnvironmentSelect({
  projectId,
  value,
  onChange,
  style,
  placeholder = '选择环境',
}: EnvironmentSelectProps) {
  const [envs, setEnvs] = useState<Environment[]>([])

  useEffect(() => {
    let mounted = true
    api
      .listEnvironments(projectId)
      .then((list) => {
        if (!mounted) return
        setEnvs(list)
        // 默认选中第一个环境
        if (list.length > 0 && onChange && !value) {
          onChange(list[0].id)
        }
      })
      .catch((e) => message.error(getErrorMessage(e)))
    return () => {
      mounted = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId])

  return (
    <Select
      style={style}
      placeholder={placeholder}
      value={value}
      options={envs.map((e) => ({ value: e.id, label: e.name }))}
      onChange={onChange}
    />
  )
}
