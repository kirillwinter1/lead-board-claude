import axios from 'axios'

export interface RoleRemaining {
  hours: number
  days: number
}

export interface PhaseInfo {
  startDate: string | null
  endDate: string | null
  workDays: number
  noCapacity?: boolean
}

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW'

export interface RoleWaitInfo {
  waiting: boolean
  waitingUntil: string | null
  queuePosition: number | null
}

export interface EpicForecast {
  epicKey: string
  summary: string
  autoScore: number
  expectedDone: string | null
  confidence: Confidence
  dueDateDeltaDays: number | null
  dueDate: string | null
  remainingByRole: Record<string, RoleRemaining>
  phaseSchedule: Record<string, PhaseInfo>
  queuePosition: number | null
  queuedUntil: string | null
  isWithinWip: boolean
  phaseWaitInfo: Record<string, RoleWaitInfo> | null
}

export interface RoleWipStatus {
  limit: number
  current: number
  exceeded: boolean
}

export interface WipStatus {
  limit: number
  current: number
  exceeded: boolean
  roleWip: Record<string, RoleWipStatus>
}

export interface ForecastResponse {
  calculatedAt: string
  teamId: number
  roleCapacity: Record<string, number>
  wipStatus: WipStatus
  epics: EpicForecast[]
}

/**
 * Получает прогноз для команды.
 */
export async function getForecast(teamId: number, statuses?: string[]): Promise<ForecastResponse> {
  const params = new URLSearchParams({ teamId: String(teamId) })
  if (statuses && statuses.length > 0) {
    statuses.forEach(s => params.append('statuses', s))
  }
  const response = await axios.get<ForecastResponse>(`/api/planning/forecast?${params}`)
  return response.data
}

// WIP History types
export interface WipRoleData {
  limit: number | null
  current: number | null
}

export interface WipDataPoint {
  date: string
  teamLimit: number
  teamCurrent: number
  roleData: Record<string, WipRoleData>
  inQueue: number | null
  totalEpics: number | null
}

export interface WipHistoryResponse {
  teamId: number
  from: string
  to: string
  dataPoints: WipDataPoint[]
}

/**
 * Получает историю WIP для графика.
 */
export async function getWipHistory(teamId: number, days: number = 30): Promise<WipHistoryResponse> {
  const response = await axios.get<WipHistoryResponse>(
    `/api/planning/wip-history?teamId=${teamId}&days=${days}`
  )
  return response.data
}

/**
 * Создаёт снапшот WIP (для ручного запуска).
 */
export async function createWipSnapshot(teamId: number): Promise<{ status: string; date: string }> {
  const response = await axios.post<{ status: string; date: string; teamWip: string }>(
    `/api/planning/wip-snapshot?teamId=${teamId}`
  )
  return response.data
}

// Story types
export interface RoleBreakdown {
  estimateSeconds: number | null
  loggedSeconds: number | null
}

export interface StoryInfo {
  storyKey: string
  summary: string
  status: string
  issueType: string
  assignee: string | null
  startDate: string | null
  endDate: string | null
  estimateSeconds: number | null
  timeSpentSeconds: number | null
  phase: string
  roleBreakdowns: Record<string, RoleBreakdown>
  autoScore: number | null
}

/**
 * Получает stories (child issues) для эпика.
 */
export async function getEpicStories(epicKey: string): Promise<StoryInfo[]> {
  const response = await axios.get<StoryInfo[]>(`/api/planning/epics/${epicKey}/stories`)
  return response.data
}

// ==================== Unified Planning API ====================

/**
 * Phase schedule for a single phase within a story.
 */
export interface UnifiedPhaseSchedule {
  assigneeAccountId: string | null
  assigneeDisplayName: string | null
  startDate: string | null
  endDate: string | null
  hours: number
  noCapacity: boolean
}

/**
 * Warning types for planning issues.
 */
export type WarningType = 'NO_ESTIMATE' | 'NO_CAPACITY' | 'CIRCULAR_DEPENDENCY' | 'FLAGGED'

/**
 * Planning warning.
 */
export interface PlanningWarning {
  issueKey: string
  type: WarningType
  message: string
}

/**
 * Progress info for a single phase.
 */
export interface PhaseProgressInfo {
  estimateSeconds: number
  loggedSeconds: number
  completed: boolean
}

/**
 * Planned story with phase schedules.
 */
export interface PlannedStory {
  storyKey: string
  summary: string
  autoScore: number | null
  status: string
  startDate: string | null
  endDate: string | null
  phases: Record<string, UnifiedPhaseSchedule>
  blockedBy: string[]
  warnings: PlanningWarning[]
  issueType: string | null
  priority: string | null
  flagged: boolean | null
  totalEstimateSeconds: number | null
  totalLoggedSeconds: number | null
  progressPercent: number | null
  roleProgress: Record<string, PhaseProgressInfo> | null
}

/**
 * Aggregated phase data entry for a role.
 */
export interface PhaseAggregationEntry {
  hours: number
  startDate: string | null
  endDate: string | null
}

/**
 * Planned epic with all its stories.
 */
export interface PlannedEpic {
  epicKey: string
  summary: string
  autoScore: number
  startDate: string | null
  endDate: string | null
  stories: PlannedStory[]
  phaseAggregation: Record<string, PhaseAggregationEntry>
  status: string | null
  dueDate: string | null
  totalEstimateSeconds: number | null
  totalLoggedSeconds: number | null
  progressPercent: number | null
  roleProgress: Record<string, PhaseProgressInfo> | null
  storiesTotal: number
  storiesActive: number
  isRoughEstimate: boolean
  roughEstimates: Record<string, number> | null
  flagged: boolean | null
}

/**
 * Assignee utilization statistics.
 */
export interface UnifiedAssigneeUtilization {
  displayName: string
  role: string
  totalHoursAssigned: number
  effectiveHoursPerDay: number
  dailyLoad: Record<string, number>
}

/**
 * Result of unified planning algorithm.
 */
export interface UnifiedPlanningResult {
  teamId: number
  planningDate: string
  epics: PlannedEpic[]
  warnings: PlanningWarning[]
  assigneeUtilization: Record<string, UnifiedAssigneeUtilization>
}

/**
 * Получает unified planning для команды.
 */
export async function getUnifiedPlanning(teamId: number): Promise<UnifiedPlanningResult> {
  const response = await axios.get<UnifiedPlanningResult>(
    `/api/planning/unified?teamId=${teamId}`
  )
  return response.data
}

// ==================== Forecast Snapshots API ====================

export async function getAvailableSnapshotDates(teamId: number): Promise<string[]> {
  const response = await axios.get<string[]>(
    `/api/forecast-snapshots/dates?teamId=${teamId}`
  )
  return response.data
}

export async function getUnifiedPlanningSnapshot(teamId: number, date: string): Promise<UnifiedPlanningResult> {
  const response = await axios.get<UnifiedPlanningResult>(
    `/api/forecast-snapshots/unified?teamId=${teamId}&date=${date}`
  )
  return response.data
}

export async function getForecastSnapshot(teamId: number, date: string): Promise<ForecastResponse> {
  const response = await axios.get<ForecastResponse>(
    `/api/forecast-snapshots/forecast?teamId=${teamId}&date=${date}`
  )
  return response.data
}

// ==================== Role Load API ====================

export type UtilizationStatus = 'OVERLOAD' | 'NORMAL' | 'IDLE' | 'NO_CAPACITY'

export interface RoleLoadInfo {
  memberCount: number
  totalCapacityHours: number
  totalAssignedHours: number
  utilizationPercent: number
  status: UtilizationStatus
}

export type RoleLoadAlertType = 'ROLE_OVERLOAD' | 'ROLE_IDLE' | 'IMBALANCE' | 'NO_CAPACITY'

export interface RoleLoadAlert {
  type: RoleLoadAlertType
  role: string | null
  message: string
}

export interface RoleLoadResponse {
  teamId: number
  planningDate: string
  periodDays: number
  roles: Record<string, RoleLoadInfo>
  alerts: RoleLoadAlert[]
}

export async function getRoleLoad(teamId: number): Promise<RoleLoadResponse> {
  const response = await axios.get<RoleLoadResponse>(
    `/api/planning/role-load?teamId=${teamId}`
  )
  return response.data
}

// ==================== Retrospective Timeline API ====================

export interface RetroPhase {
  roleCode: string
  startDate: string | null
  endDate: string | null
  durationDays: number
  active: boolean
}

export interface RetroStory {
  storyKey: string
  summary: string
  status: string
  completed: boolean
  startDate: string | null
  endDate: string | null
  progressPercent: number | null
  phases: Record<string, RetroPhase>
}

export interface RetroEpic {
  epicKey: string
  summary: string
  status: string | null
  startDate: string | null
  endDate: string | null
  progressPercent: number | null
  stories: RetroStory[]
}

export interface RetrospectiveResult {
  teamId: number
  calculatedAt: string
  epics: RetroEpic[]
}

export async function getRetrospective(teamId: number): Promise<RetrospectiveResult> {
  const response = await axios.get<RetrospectiveResult>(
    `/api/planning/retrospective?teamId=${teamId}`
  )
  return response.data
}
