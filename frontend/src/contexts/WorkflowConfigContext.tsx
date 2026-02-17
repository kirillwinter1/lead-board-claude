import { createContext, useContext, useEffect, useState, useCallback, ReactNode } from 'react'
import axios from 'axios'
import { WorkflowRoleDto, JiraIssueTypeMetadata } from '../api/workflowConfig'

interface WorkflowConfig {
  roles: WorkflowRoleDto[]
  issueTypeCategories: Record<string, string>
  issueTypeIcons: Record<string, string>
  loading: boolean
}

interface WorkflowConfigHelpers extends WorkflowConfig {
  isProject: (type: string | null | undefined) => boolean
  isEpic: (type: string | null | undefined) => boolean
  isStory: (type: string | null | undefined) => boolean
  isSubtask: (type: string | null | undefined) => boolean
  getRoleColor: (code: string) => string
  getRoleDisplayName: (code: string) => string
  getRoleCodes: () => string[]
  getIssueTypeIconUrl: (typeName: string | null | undefined) => string | null
  refresh: () => void
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
    issueTypeIcons: {},
    loading: true,
  })

  const loadConfig = useCallback(() => {
    Promise.all([
      axios.get<WorkflowRoleDto[]>('/api/config/workflow/roles').then(r => r.data),
      axios.get<Record<string, string>>('/api/config/workflow/issue-type-categories').then(r => r.data),
      axios.get<JiraIssueTypeMetadata[]>('/api/admin/jira-metadata/issue-types').then(r => r.data).catch(() => [] as JiraIssueTypeMetadata[]),
    ])
      .then(([roles, categories, issueTypes]) => {
        const icons: Record<string, string> = {}
        issueTypes.forEach((t: JiraIssueTypeMetadata) => {
          if (t.iconUrl) icons[t.name] = t.iconUrl
        })
        setConfig({ roles, issueTypeCategories: categories, issueTypeIcons: icons, loading: false })
      })
      .catch(() => {
        setConfig(prev => ({ ...prev, loading: false }))
      })
  }, [])

  useEffect(() => {
    loadConfig()
  }, [loadConfig])

  const helpers: WorkflowConfigHelpers = {
    ...config,
    isProject: (type) => {
      if (!type) return false
      return config.issueTypeCategories[type] === 'PROJECT'
    },
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
    getIssueTypeIconUrl: (typeName) => {
      if (!typeName) return null
      return config.issueTypeIcons[typeName] || null
    },
    refresh: loadConfig,
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
      issueTypeIcons: {},
      loading: false,
      isProject: () => false,
      isEpic: () => false,
      isStory: () => false,
      isSubtask: () => false,
      getRoleColor: (code) => DEFAULT_ROLE_COLORS[code] || '#666',
      getRoleDisplayName: (code) => code,
      getRoleCodes: () => [],
      getIssueTypeIconUrl: () => null,
      refresh: () => {},
    }
  }
  return ctx
}
