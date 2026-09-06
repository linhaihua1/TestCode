import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ProjectApi } from '@/api'
import type { Project } from '@/types'

export const useProjectStore = defineStore('project', () => {
  const currentProjectId = ref<string>('')
  const projects = ref<Project[]>([])

  const currentProject = computed(() =>
    projects.value.find((p) => p.id === currentProjectId.value) ?? null
  )

  async function fetchAll() {
    projects.value = await ProjectApi.list()
    if (!currentProjectId.value && projects.value.length > 0) {
      const cached = localStorage.getItem('projectId')
      const exists = projects.value.find((p) => p.id === cached)
      currentProjectId.value = exists ? exists.id : projects.value[0].id
    } else if (!projects.value.find((p) => p.id === currentProjectId.value)) {
      currentProjectId.value = ''
    }
    if (currentProjectId.value) {
      localStorage.setItem('projectId', currentProjectId.value)
    }
  }

  function switchProject(id: string) {
    currentProjectId.value = id
    localStorage.setItem('projectId', id)
  }

  return { currentProjectId, projects, currentProject, fetchAll, switchProject }
})