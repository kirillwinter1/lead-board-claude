import axios from 'axios'

export interface Team {
  id: number
  name: string
  jiraTeamValue: string | null
  active: boolean
  memberCount: number
  createdAt: string
  updatedAt: string
}

export interface TeamMember {
  id: number
  teamId: number
  jiraAccountId: string
  displayName: string | null
  role: string
  grade: 'JUNIOR' | 'MIDDLE' | 'SENIOR'
  hoursPerDay: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateTeamRequest {
  name: string
  jiraTeamValue?: string
}

export interface UpdateTeamRequest {
  name?: string
  jiraTeamValue?: string
}

export interface CreateTeamMemberRequest {
  jiraAccountId: string
  displayName?: string
  role?: string
  grade?: 'JUNIOR' | 'MIDDLE' | 'SENIOR'
  hoursPerDay?: number
}

export interface UpdateTeamMemberRequest {
  displayName?: string
  role?: string
  grade?: 'JUNIOR' | 'MIDDLE' | 'SENIOR'
  hoursPerDay?: number
}

export interface TeamsConfig {
  manualTeamManagement: boolean
  organizationId: string
}

export interface TeamSyncStatus {
  syncInProgress: boolean
  lastSyncTime: string | null
  error: string | null
}

export interface GradeCoefficients {
  senior: number
  middle: number
  junior: number
}

export interface WipLimits {
  team: number
  roleLimits: Record<string, number>
}

export interface StoryDuration {
  roleDurations: Record<string, number>
}

export interface PlanningConfig {
  gradeCoefficients: GradeCoefficients
  riskBuffer: number
  wipLimits: WipLimits
  storyDuration: StoryDuration
}

export const teamsApi = {
  getConfig: () => axios.get<TeamsConfig>('/api/teams/config').then(r => r.data),

  getSyncStatus: () => axios.get<TeamSyncStatus>('/api/teams/sync/status').then(r => r.data),

  triggerSync: () => axios.post<TeamSyncStatus>('/api/teams/sync/trigger').then(r => r.data),

  getAll: () => axios.get<Team[]>('/api/teams').then(r => r.data),

  getById: (id: number) => axios.get<Team>(`/api/teams/${id}`).then(r => r.data),

  create: (data: CreateTeamRequest) =>
    axios.post<Team>('/api/teams', data).then(r => r.data),

  update: (id: number, data: UpdateTeamRequest) =>
    axios.put<Team>(`/api/teams/${id}`, data).then(r => r.data),

  delete: (id: number) => axios.delete(`/api/teams/${id}`),

  getMembers: (teamId: number) =>
    axios.get<TeamMember[]>(`/api/teams/${teamId}/members`).then(r => r.data),

  addMember: (teamId: number, data: CreateTeamMemberRequest) =>
    axios.post<TeamMember>(`/api/teams/${teamId}/members`, data).then(r => r.data),

  updateMember: (teamId: number, memberId: number, data: UpdateTeamMemberRequest) =>
    axios.put<TeamMember>(`/api/teams/${teamId}/members/${memberId}`, data).then(r => r.data),

  deactivateMember: (teamId: number, memberId: number) =>
    axios.post(`/api/teams/${teamId}/members/${memberId}/deactivate`),

  getPlanningConfig: (teamId: number) =>
    axios.get<PlanningConfig>(`/api/teams/${teamId}/planning-config`).then(r => r.data),

  updatePlanningConfig: (teamId: number, config: PlanningConfig) =>
    axios.put<PlanningConfig>(`/api/teams/${teamId}/planning-config`, config).then(r => r.data),
}
