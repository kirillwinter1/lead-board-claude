import axios from 'axios'

export interface WorkflowRoleDto {
  id: number | null
  code: string
  displayName: string
  color: string
  sortOrder: number
  isDefault: boolean
}

export interface IssueTypeMappingDto {
  id: number | null
  jiraTypeName: string
  boardCategory: 'EPIC' | 'STORY' | 'SUBTASK' | 'IGNORE'
  workflowRoleCode: string | null
}

export interface StatusMappingDto {
  id: number | null
  jiraStatusName: string
  issueCategory: 'EPIC' | 'STORY' | 'SUBTASK' | 'IGNORE'
  statusCategory: 'NEW' | 'REQUIREMENTS' | 'PLANNED' | 'IN_PROGRESS' | 'DONE'
  workflowRoleCode: string | null
  sortOrder: number
  scoreWeight: number
  color: string | null
}

export interface StatusIssueCountDto {
  jiraStatusName: string
  issueCategory: string
  count: number
}

export interface LinkTypeMappingDto {
  id: number | null
  jiraLinkTypeName: string
  linkCategory: 'BLOCKS' | 'RELATED' | 'IGNORE'
}

export interface JiraIssueTypeMetadata {
  id: string
  name: string
  subtask: boolean
  description: string | null
}

export interface JiraStatusesByType {
  issueType: string
  statuses: { id: string; name: string; untranslatedName: string; statusCategory: string; statusCategoryName: string }[]
}

export interface JiraWorkflow {
  name: string
  statuses: { id: string; name: string }[]
  transitions: { from: string[]; to: string; name: string; type: string }[]
}

export interface JiraLinkTypeMetadata {
  id: string
  name: string
  inward: string
  outward: string
}

export interface ValidationResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

export interface AutoDetectResult {
  issueTypeCount: number
  roleCount: number
  statusMappingCount: number
  linkTypeCount: number
  warnings: string[]
}

export interface WorkflowConfigResponse {
  configId: number
  configName: string
  roles: WorkflowRoleDto[]
  issueTypes: IssueTypeMappingDto[]
  statuses: StatusMappingDto[]
  linkTypes: LinkTypeMappingDto[]
  statusScoreWeights: Record<string, number>
  planningAllowedCategories: string | null
  timeLoggingAllowedCategories: string | null
}

export const workflowConfigApi = {
  getConfig: () =>
    axios.get<WorkflowConfigResponse>('/api/admin/workflow-config').then(r => r.data),

  updateRoles: (roles: WorkflowRoleDto[]) =>
    axios.put<WorkflowRoleDto[]>('/api/admin/workflow-config/roles', roles).then(r => r.data),

  updateIssueTypes: (types: IssueTypeMappingDto[]) =>
    axios.put<IssueTypeMappingDto[]>('/api/admin/workflow-config/issue-types', types).then(r => r.data),

  updateStatuses: (statuses: StatusMappingDto[]) =>
    axios.put<StatusMappingDto[]>('/api/admin/workflow-config/statuses', statuses).then(r => r.data),

  updateLinkTypes: (links: LinkTypeMappingDto[]) =>
    axios.put<LinkTypeMappingDto[]>('/api/admin/workflow-config/link-types', links).then(r => r.data),

  validate: () =>
    axios.post<ValidationResult>('/api/admin/workflow-config/validate').then(r => r.data),

  getConfigStatus: () =>
    axios.get<{ configured: boolean }>('/api/admin/workflow-config/status').then(r => r.data),

  runAutoDetect: () =>
    axios.post<AutoDetectResult>('/api/admin/workflow-config/auto-detect').then(r => r.data),

  getStatusIssueCounts: () =>
    axios.get<StatusIssueCountDto[]>('/api/admin/workflow-config/status-issue-counts').then(r => r.data),

  fetchJiraIssueTypes: () =>
    axios.get<JiraIssueTypeMetadata[]>('/api/admin/jira-metadata/issue-types').then(r => r.data),

  fetchJiraStatuses: () =>
    axios.get<JiraStatusesByType[]>('/api/admin/jira-metadata/statuses').then(r => r.data),

  fetchJiraLinkTypes: () =>
    axios.get<JiraLinkTypeMetadata[]>('/api/admin/jira-metadata/link-types').then(r => r.data),

  fetchJiraWorkflows: () =>
    axios.get<JiraWorkflow[]>('/api/admin/jira-metadata/workflows').then(r => r.data),
}
