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

// ==================== Planning (F69) Types ====================

export interface PlanningEpicDto {
  epicKey: string
  epicSummary: string
  iconUrl: string | null
  typeName: string
  projectKey: string | null
  projectSummary: string | null
  quarterLabel: string | null
  inQuarter: boolean
  riceScore: number
  manualBoost: number
  priorityScore: number
  teams: TeamRef[]
  demandByRole: Record<string, number>
  totalDemandDays: number
  hasEstimate: boolean
  hasTeamMapping: boolean
  overloadedTeams: number[]
  // F70: project-level "desired" quarter (parent project label) — null when standalone
  projectDesiredQuarter: string | null
  // F70: true when epic has no parent project (tech-debt / standalone work)
  isStandalone: boolean
}

export interface QuarterlyEpicsResponse {
  quarter: string
  epics: PlanningEpicDto[]
}

// ==================== F86: Remaining work per epic ====================

export interface EpicRemainingDto {
  epicKey: string
  remainingNowByRole: Record<string, number>
  remainingAtQuarterStartByRole: Record<string, number>
  remainingNowDays: number
  remainingAtQuarterStartDays: number
  hasEstimate: boolean
}

export interface QuarterlyRemainingResponse {
  quarter: string
  teamId: number
  epics: Record<string, EpicRemainingDto>
}

/**
 * F86: an epic "needs planning" for the selected quarter when it is active work
 * that is not in the viewed quarter and is not committed to a future quarter —
 * either uncommitted entirely (quarterLabel == null) or a carryover tail from a
 * past quarter (quarterLabel < quarter). Quarter labels are YYYYQn and sort
 * lexicographically by chronology, so a string comparison is enough.
 *
 * Pure function so it can be reused by the page filter, the card, and tests.
 */
export function needsPlanning(
  epic: Pick<PlanningEpicDto, 'inQuarter' | 'quarterLabel'>,
  quarter: string,
): boolean {
  return !epic.inQuarter && (epic.quarterLabel == null || epic.quarterLabel < quarter)
}

// ==================== F70: Project quarter commitment ====================

export interface TeamCommitmentDto {
  teamId: number // 0 = synthetic "no team mapping" bucket
  teamName: string
  teamColor: string | null
  totalEpics: number
  committedEpics: number    // committed_quarter == project's desired_quarter
  otherQuarterEpics: number // committed_quarter set but != desired
  uncommittedEpics: number  // committed_quarter == null
}

export interface ProjectQuarterCommitmentDto {
  projectKey: string
  projectSummary: string
  desiredQuarter: string | null
  commitmentByTeam: TeamCommitmentDto[]
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

  // ==================== F69: Planning board endpoints ====================

  /**
   * F70: `onlyDesired` is opt-in on the wire. Pass `undefined` to leave the
   * decision to the backend (current default = true). Pass an explicit boolean
   * to override — this also keeps the URL clean in dev tools when not set.
   */
  async getEpicsForQuarter(quarter: string, onlyDesired?: boolean): Promise<QuarterlyEpicsResponse> {
    const params = onlyDesired === undefined ? undefined : { onlyDesired }
    const res = await axios.get(
      `/api/quarterly-planning/quarters/${encodeURIComponent(quarter)}/epics`,
      params ? { params } : undefined,
    )
    return res.data
  },

  /**
   * F86: per-epic remaining work for a single team, split into "now" and
   * "at quarter start". Loaded lazily and independently of the epic list.
   */
  async getRemainingForQuarter(quarter: string, teamId: number): Promise<QuarterlyRemainingResponse> {
    const res = await axios.get('/api/quarterly-planning/remaining', { params: { teamId, quarter } })
    return res.data
  },

  async assignEpicToQuarter(epicKey: string, quarter: string | null): Promise<PlanningEpicDto> {
    const res = await axios.post(`/api/quarterly-planning/epics/${encodeURIComponent(epicKey)}/quarter`, { quarter })
    return res.data
  },

  async setEpicBoost(epicKey: string, boost: number): Promise<PlanningEpicDto> {
    const res = await axios.post(`/api/quarterly-planning/epics/${encodeURIComponent(epicKey)}/boost`, { boost })
    return res.data
  },

  // ==================== F70: Project commitment endpoints ====================

  async setProjectDesiredQuarter(
    projectKey: string,
    quarter: string | null,
  ): Promise<ProjectQuarterCommitmentDto> {
    const res = await axios.post(
      `/api/quarterly-planning/projects/${encodeURIComponent(projectKey)}/desired-quarter`,
      { quarter },
    )
    return res.data
  },

  async getProjectCommitment(projectKey: string): Promise<ProjectQuarterCommitmentDto> {
    const res = await axios.get(
      `/api/quarterly-planning/projects/${encodeURIComponent(projectKey)}/quarter-commitment`,
    )
    return res.data
  },
}
