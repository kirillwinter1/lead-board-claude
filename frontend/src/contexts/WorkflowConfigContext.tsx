import { createContext, useContext, useEffect, useState, ReactNode } from 'react'
import axios from 'axios'
import { WorkflowRoleDto } from '../api/workflowConfig'

interface WorkflowConfig {
  roles: WorkflowRoleDto[]
  issueTypeCategories: Record<string, string>
  loading: boolean
}

interface WorkflowConfigHelpers extends WorkflowConfig {
  isEpic: (type: string | null | undefined) => boolean
  isStory: (type: string | null | undefined) => boolean
  isSubtask: (type: string | null | undefined) => boolean
  getRoleColor: (code: string) => string
  getRoleDisplayName: (code: string) => string
  getRoleCodes: () => string[]
}

const WorkflowConfigContext = createContext<WorkflowConfigHelpers | null>(null)

const DEFAULT_ROLE_COLORS: Record<string, string> = {
  SA: '#1558BC',
  DEV: '#803FA5',
  QA: '#206A83',
}

export function WorkflowConfigProvider({ children }: { children: ReactNode }) {
  const [config, setConfig] = useState<WorkflowConfig>({
    roles: [],
    issueTypeCategories: {},
    loading: true,
  })

  useEffect(() => {
    Promise.all([
      axios.get<WorkflowRoleDto[]>('/api/config/workflow/roles').then(r => r.data),
      axios.get<Record<string, string>>('/api/config/workflow/issue-type-categories').then(r => r.data),
    ])
      .then(([roles, categories]) => {
        setConfig({ roles, issueTypeCategories: categories, loading: false })
      })
      .catch(() => {
        setConfig(prev => ({ ...prev, loading: false }))
      })
  }, [])

  const helpers: WorkflowConfigHelpers = {
    ...config,
    isEpic: (type) => {
      if (!type) return false
      return config.issueTypeCategories[type] === 'EPIC'
    },
    isStory: (type) => {
      if (!type) return false
      return config.issueTypeCategories[type] === 'STORY'
    },
    isSubtask: (type) => {
      if (!type) return false
      return config.issueTypeCategories[type] === 'SUBTASK'
    },
    getRoleColor: (code) => {
      const role = config.roles.find(r => r.code === code)
      return role?.color || DEFAULT_ROLE_COLORS[code] || '#666'
    },
    getRoleDisplayName: (code) => {
      const role = config.roles.find(r => r.code === code)
      return role?.displayName || code
    },
    getRoleCodes: () => config.roles.map(r => r.code),
  }

  return (
    <WorkflowConfigContext.Provider value={helpers}>
      {children}
    </WorkflowConfigContext.Provider>
  )
}

export function useWorkflowConfig(): WorkflowConfigHelpers {
  const ctx = useContext(WorkflowConfigContext)
  if (!ctx) {
    // Fallback for components rendered outside provider (e.g. Landing)
    return {
      roles: [],
      issueTypeCategories: {},
      loading: false,
      isEpic: () => false,
      isStory: () => false,
      isSubtask: () => false,
      getRoleColor: (code) => DEFAULT_ROLE_COLORS[code] || '#666',
      getRoleDisplayName: (code) => code,
      getRoleCodes: () => [],
    }
  }
  return ctx
}
