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
  manualPriorityBoost: number | null
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

/**
 * Пересчитывает AutoScore.
 */
export async function recalculateAutoScore(teamId?: number): Promise<{ epicsUpdated: number }> {
  const params = teamId ? `?teamId=${teamId}` : ''
  const response = await axios.post<{ status: string; epicsUpdated: number }>(`/api/planning/recalculate${params}`)
  return response.data
}

/**
 * Обновляет ручной boost приоритета.
 */
export async function updateManualBoost(epicKey: string, boost: number): Promise<{ newAutoScore: number }> {
  const response = await axios.patch<{ epicKey: string; boost: number; newAutoScore: number }>(
    `/api/planning/autoscore/epics/${epicKey}/boost`,
    { boost }
  )
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
export type StoryStatusCategory = 'TO_DO' | 'IN_PROGRESS' | 'DONE'
export type StoryPhase = 'SA' | 'DEV' | 'QA'

export interface StoryInfo {
  storyKey: string
  summary: string
  status: string
  issueType: string
  assignee: string | null
  startDate: string | null
  estimateSeconds: number | null
  timeSpentSeconds: number | null
  phase: StoryPhase
}

/**
 * Получает stories (child issues) для эпика.
 */
export async function getEpicStories(epicKey: string): Promise<StoryInfo[]> {
  const response = await axios.get<StoryInfo[]>(`/api/planning/epics/${epicKey}/stories`)
  return response.data
}

/**
 * Определяет категорию статуса сторя.
 */
export function getStoryStatusCategory(status: string): StoryStatusCategory {
  const statusLower = status.toLowerCase()

  if (statusLower.includes('done') || statusLower.includes('closed') ||
      statusLower.includes('resolved') || statusLower.includes('завершен') ||
      statusLower.includes('готов') || statusLower.includes('выполнен')) {
    return 'DONE'
  }
  if (statusLower.includes('progress') || statusLower.includes('work') ||
      statusLower.includes('review') || statusLower.includes('test') ||
      statusLower.includes('в работе') || statusLower.includes('ревью')) {
    return 'IN_PROGRESS'
  }
  return 'TO_DO'
}
