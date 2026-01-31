import axios from 'axios'

export interface RoleRemaining {
  hours: number
  days: number
}

export interface RemainingByRole {
  sa: RoleRemaining
  dev: RoleRemaining
  qa: RoleRemaining
}

export interface PhaseInfo {
  startDate: string | null
  endDate: string | null
  workDays: number
  noCapacity?: boolean  // true если нет ресурсов для этой роли
}

export interface PhaseSchedule {
  sa: PhaseInfo
  dev: PhaseInfo
  qa: PhaseInfo
}

export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW'

export interface RoleWaitInfo {
  waiting: boolean              // Ожидает ли вход в фазу
  waitingUntil: string | null   // До какой даты ждёт
  queuePosition: number | null  // Позиция в очереди на эту фазу
}

export interface PhaseWaitInfo {
  sa: RoleWaitInfo
  dev: RoleWaitInfo
  qa: RoleWaitInfo
}

export interface EpicForecast {
  epicKey: string
  summary: string
  autoScore: number
  expectedDone: string | null
  confidence: Confidence
  dueDateDeltaDays: number | null
  dueDate: string | null
  remainingByRole: RemainingByRole
  phaseSchedule: PhaseSchedule
  // WIP fields (team-level)
  queuePosition: number | null     // Позиция в очереди (null если в WIP)
  queuedUntil: string | null       // До какой даты в очереди
  isWithinWip: boolean             // Входит ли в активный WIP
  // WIP fields (role-level)
  phaseWaitInfo: PhaseWaitInfo | null  // Информация об ожидании по фазам
}

export interface TeamCapacity {
  saHoursPerDay: number
  devHoursPerDay: number
  qaHoursPerDay: number
}

export interface RoleWipStatus {
  limit: number      // Лимит для роли
  current: number    // Текущее количество эпиков на этой фазе
  exceeded: boolean  // Превышен ли лимит
}

export interface WipStatus {
  limit: number      // WIP лимит команды
  current: number    // Текущее количество активных эпиков
  exceeded: boolean  // Превышен ли лимит
  sa: RoleWipStatus | null   // WIP статус для SA
  dev: RoleWipStatus | null  // WIP статус для DEV
  qa: RoleWipStatus | null   // WIP статус для QA
}

export interface ForecastResponse {
  calculatedAt: string
  teamId: number
  teamCapacity: TeamCapacity
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
export interface WipDataPoint {
  date: string
  teamLimit: number
  teamCurrent: number
  saLimit: number | null
  saCurrent: number | null
  devLimit: number | null
  devCurrent: number | null
  qaLimit: number | null
  qaCurrent: number | null
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
export type StoryPhase = 'SA' | 'DEV' | 'QA'

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
  phase: StoryPhase
  saBreakdown: RoleBreakdown | null
  devBreakdown: RoleBreakdown | null
  qaBreakdown: RoleBreakdown | null
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
 * Phase schedules for a story (SA -> DEV -> QA pipeline).
 */
export interface PlannedPhases {
  sa: UnifiedPhaseSchedule | null
  dev: UnifiedPhaseSchedule | null
  qa: UnifiedPhaseSchedule | null
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
 * Progress info for a single phase (SA/DEV/QA).
 */
export interface PhaseProgressInfo {
  estimateSeconds: number
  loggedSeconds: number
  completed: boolean
}

/**
 * Role progress info for a story.
 */
export interface RoleProgressInfo {
  sa: PhaseProgressInfo | null
  dev: PhaseProgressInfo | null
  qa: PhaseProgressInfo | null
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
  phases: PlannedPhases
  blockedBy: string[]
  warnings: PlanningWarning[]
  // Additional fields for tooltip
  issueType: string | null
  priority: string | null
  flagged: boolean | null
  // Aggregated progress from subtasks
  totalEstimateSeconds: number | null
  totalLoggedSeconds: number | null
  progressPercent: number | null
  roleProgress: RoleProgressInfo | null
}

/**
 * Aggregated phase data for epic.
 */
export interface PhaseAggregation {
  saHours: number
  devHours: number
  qaHours: number
  saStartDate: string | null
  saEndDate: string | null
  devStartDate: string | null
  devEndDate: string | null
  qaStartDate: string | null
  qaEndDate: string | null
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
  phaseAggregation: PhaseAggregation
  // Additional fields for epic card/tooltip
  status: string | null
  dueDate: string | null
  totalEstimateSeconds: number | null
  totalLoggedSeconds: number | null
  progressPercent: number | null
  roleProgress: RoleProgressInfo | null
  storiesTotal: number
  storiesActive: number
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

/**
 * Получает доступные даты снэпшотов для команды.
 */
export async function getAvailableSnapshotDates(teamId: number): Promise<string[]> {
  const response = await axios.get<string[]>(
    `/api/forecast-snapshots/dates?teamId=${teamId}`
  )
  return response.data
}

/**
 * Получает unified planning из исторического снэпшота.
 */
export async function getUnifiedPlanningSnapshot(teamId: number, date: string): Promise<UnifiedPlanningResult> {
  const response = await axios.get<UnifiedPlanningResult>(
    `/api/forecast-snapshots/unified?teamId=${teamId}&date=${date}`
  )
  return response.data
}

/**
 * Получает forecast из исторического снэпшота.
 */
export async function getForecastSnapshot(teamId: number, date: string): Promise<ForecastResponse> {
  const response = await axios.get<ForecastResponse>(
    `/api/forecast-snapshots/forecast?teamId=${teamId}&date=${date}`
  )
  return response.data
}

