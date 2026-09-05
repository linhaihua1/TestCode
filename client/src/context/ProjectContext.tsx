/**
 * 全局项目上下文。
 *
 * 职责：在整个应用（接口自动化 / UI 自动化 / 环境配置）中共享「当前选中项目」，
 * 顶部栏项目选择器与各页面通过 useProject 读写，避免散落在 URL 或各组件内部。
 */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api } from '../api/client'
import type { Project } from '../api/types'

interface ProjectContextValue {
  projects: Project[]
  projectId?: string
  setProjectId: (id?: string) => void
}

const ProjectContext = createContext<ProjectContextValue>({
  projects: [],
  projectId: undefined,
  setProjectId: () => {},
})

const STORAGE_KEY = 'wb-project'

export function ProjectProvider({ children }: { children: ReactNode }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [projectId, setProjectIdState] = useState<string | undefined>(() => {
    try {
      return localStorage.getItem(STORAGE_KEY) ?? undefined
    } catch {
      return undefined
    }
  })

  // 拉取项目列表，若尚未选择则默认选第一个
  useEffect(() => {
    api
      .listProjects()
      .then((list) => {
        setProjects(list)
        setProjectIdState((prev) => {
          // 已选中的项目若仍存在则沿用，否则回退到第一个
          if (prev && list.some((p) => p.id === prev)) return prev
          return list[0]?.id
        })
      })
      .catch(() => {})
  }, [])

  const setProjectId = (id?: string) => {
    setProjectIdState(id)
    try {
      if (id) localStorage.setItem(STORAGE_KEY, id)
      else localStorage.removeItem(STORAGE_KEY)
    } catch {
      /* ignore */
    }
  }

  return (
    <ProjectContext.Provider value={{ projects, projectId, setProjectId }}>
      {children}
    </ProjectContext.Provider>
  )
}

/** 读取全局项目上下文 */
export function useProject(): ProjectContextValue {
  return useContext(ProjectContext)
}
