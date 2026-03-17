import axios from 'axios'

// ==================== Types ====================

export interface QuarterlyCapacityDto {
  teamId: number
  teamName: string
  teamColor: string | null
  quarter: string
  capacityByRole: Record<string, number>
  totalWorkdays: number
  absenceDays: number
}

export interface EpicDemandDto {
  epicKey: string
  summary: string
  status: string
  manualOrder: number | null
  demandByRole: Record<string, number>
  overCapacity: boolean
  quarterLabel: string
}

export interface ProjectDemandDto {
  projectKey: string
  summary: string
  status: string | null
  priorityScore: number
  riceNormalizedScore: number | null
  manualBoost: number
  totalDemandByRole: Record<string, number>
  epics: EpicDemandDto[]
  fitsInCapacity: boolean
}

export interface QuarterlyDemandDto {
  teamId: number
  teamName: string
  quarter: string
  capacity: QuarterlyCapacityDto
  projects: ProjectDemandDto[]
  unassignedEpics: EpicDemandDto[]
}

export interface TeamQuarterlySnapshotDto {
  teamId: number
  teamName: string
  teamColor: string | null
  capacityByRole: Record<string, number>
  demandByRole: Record<string, number>
  utilizationPctByRole: Record<string, number>
  overloaded: boolean
}

export interface QuarterlySummaryDto {
  quarter: string
  teams: TeamQuarterlySnapshotDto[]
  availableQuarters: string[]
}

export interface TeamAllocationDto {
  teamId: number
  teamName: string
  teamColor: string | null
  epics: EpicDemandDto[]
  teamCapacity: Record<string, number>
  projectDemand: Record<string, number>
  overloaded: boolean
}

export interface ProjectViewDto {
  projectKey: string
  summary: string
  priorityScore: number
  manualBoost: number | null
  quarter: string
  teams: TeamAllocationDto[]
}

// ==================== Projects Overview Types ====================

export interface EpicOverviewDto {
  key: string
  summary: string
  teams: TeamRef[]
  roughEstimated: boolean
  teamMapped: boolean
  blockers: string[]
}

export interface TeamRef {
  id: number
  name: string
  color: string | null
}

export interface QuarterlyProjectOverviewDto {
  projectKey: string
  summary: string
  inQuarter: boolean
  quarterLabel: string | null
  priorityScore: number
  riceNormalizedScore: number
  manualBoost: number
  epicCount: number
  roughEstimateCoverage: number
  teamMappingCoverage: number
  planningStatus: 'ready' | 'partial' | 'blocked' | 'not-added'
  demandDays: number | null
  forecastLabel: string
  risk: 'low' | 'medium' | 'high'
  teams: TeamRef[]
  blockers: string[]
  epics: EpicOverviewDto[]
}

export interface QuarterlyProjectsResponse {
  quarter: string
  inQuarterCount: number
  readyCount: number
  blockedCount: number
  partialCount: number
  teamsInvolved: number
  totalEpics: number
  roughCoveragePct: number
  projects: QuarterlyProjectOverviewDto[]
}

export interface ProjectRefDto {
  key: string
  name: string
  planningStatus: string
}

export interface QuarterlyTeamOverviewDto {
  teamId: number
  teamName: string
  teamColor: string | null
  capacityDays: number
  demandDays: number
  gapDays: number
  utilization: number
  capacityByRole: Record<string, number>
  demandByRole: Record<string, number>
  overloadedEpics: number
  risk: 'low' | 'medium' | 'high'
  impactingProjects: ProjectRefDto[]
}

// ==================== API ====================

export const quarterlyPlanningApi = {
  async getCapacity(teamId: number, quarter: string): Promise<QuarterlyCapacityDto> {
    const res = await axios.get('/api/quarterly-planning/capacity', { params: { teamId, quarter } })
    return res.data
  },

  async getDemand(teamId: number, quarter: string): Promise<QuarterlyDemandDto> {
    const res = await axios.get('/api/quarterly-planning/demand', { params: { teamId, quarter } })
    return res.data
  },

  async getSummary(quarter: string): Promise<QuarterlySummaryDto> {
    const res = await axios.get('/api/quarterly-planning/summary', { params: { quarter } })
    return res.data
  },

  async getProjectView(projectKey: string, quarter: string): Promise<ProjectViewDto> {
    const res = await axios.get('/api/quarterly-planning/project-view', { params: { projectKey, quarter } })
    return res.data
  },

  async getAvailableQuarters(): Promise<string[]> {
    const res = await axios.get('/api/quarterly-planning/quarters')
    return res.data
  },

  async updateProjectBoost(projectKey: string, boost: number): Promise<void> {
    await axios.put(`/api/quarterly-planning/projects/${projectKey}/boost`, { boost })
  },

  async getProjectsOverview(quarter: string): Promise<QuarterlyProjectsResponse> {
    const res = await axios.get('/api/quarterly-planning/projects-overview', { params: { quarter } })
    return res.data
  },

  async getTeamsOverview(quarter: string): Promise<QuarterlyTeamOverviewDto[]> {
    const res = await axios.get('/api/quarterly-planning/teams-overview', { params: { quarter } })
    return res.data
  },
}
