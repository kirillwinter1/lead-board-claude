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
}

export interface ProjectDetailDto {
  issueKey: string
  summary: string
  status: string
  assigneeDisplayName: string | null
  completedEpicCount: number
  progressPercent: number
  expectedDone: string | null
  epics: ChildEpicDto[]
}

export const projectsApi = {
  list: () =>
    axios.get<ProjectDto[]>('/api/projects').then(r => r.data),

  getDetail: (issueKey: string) =>
    axios.get<ProjectDetailDto>(`/api/projects/${issueKey}`).then(r => r.data),
}
