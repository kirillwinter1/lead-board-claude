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

export interface PhaseAggregationInfo {
  hours: number | null
  startDate: string | null
  endDate: string | null
}

export interface PhaseProgressInfo {
  estimateSeconds: number | null
  loggedSeconds: number | null
  completed: boolean
}

export interface EpicTimelineDto {
  epicKey: string
  summary: string
  status: string
  teamName: string | null
  startDate: string | null
  endDate: string | null
  progressPercent: number | null
  isRoughEstimate: boolean
  roughEstimates: Record<string, number> | null
  phaseAggregation: Record<string, PhaseAggregationInfo> | null
  roleProgress: Record<string, PhaseProgressInfo> | null
  flagged: boolean | null
}

export interface ProjectTimelineDto {
  issueKey: string
  summary: string
  status: string
  progressPercent: number
  riceNormalizedScore: number | null
  epics: EpicTimelineDto[]
}

export const projectsApi = {
  list: () =>
    axios.get<ProjectDto[]>('/api/projects').then(r => r.data),

  getDetail: (issueKey: string) =>
    axios.get<ProjectDetailDto>(`/api/projects/${issueKey}`).then(r => r.data),

  getRecommendations: (issueKey: string) =>
    axios.get<ProjectRecommendation[]>(`/api/projects/${issueKey}/recommendations`).then(r => r.data),

  getTimeline: () =>
    axios.get<ProjectTimelineDto[]>('/api/projects/timeline').then(r => r.data),
}
