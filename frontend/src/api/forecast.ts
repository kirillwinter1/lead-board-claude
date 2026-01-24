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
