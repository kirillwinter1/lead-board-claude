import axios from 'axios'

export interface ProjectDto {
  issueKey: string
  summary: string
  status: string
  assigneeDisplayName: string | null
  childEpicCount: number
  completedEpicCount: number
  progressPercent: number
  expectedDone: string | null
  riceScore: number | null
  riceNormalizedScore: number | null
}

export interface ChildEpicDto {
  issueKey: string
  summary: string
  status: string
  teamName: string | null
  estimateSeconds: number | null
  loggedSeconds: number | null
  progressPercent: number | null
  expectedDone: string | null
  dueDate: string | null
  delayDays: number | null
}

export interface ProjectDetailDto {
  issueKey: string
  summary: string
  status: string
  assigneeDisplayName: string | null
  completedEpicCount: number
  progressPercent: number
  expectedDone: string | null
  riceScore: number | null
  riceNormalizedScore: number | null
  epics: ChildEpicDto[]
}

export interface ProjectRecommendation {
  type: 'EPIC_LAGGING' | 'ALL_EPICS_DONE' | 'EPIC_NO_FORECAST' | 'RICE_NOT_FILLED'
  severity: 'WARNING' | 'INFO'
  message: string
  epicKey: string | null
  teamName: string | null
  delayDays: number | null
}

export const projectsApi = {
  list: () =>
    axios.get<ProjectDto[]>('/api/projects').then(r => r.data),

  getDetail: (issueKey: string) =>
    axios.get<ProjectDetailDto>(`/api/projects/${issueKey}`).then(r => r.data),

  getRecommendations: (issueKey: string) =>
    axios.get<ProjectRecommendation[]>(`/api/projects/${issueKey}/recommendations`).then(r => r.data),
}
