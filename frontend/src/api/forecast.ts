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
}

export interface TeamCapacity {
  saHoursPerDay: number
  devHoursPerDay: number
  qaHoursPerDay: number
}

export interface ForecastResponse {
  calculatedAt: string
  teamId: number
  teamCapacity: TeamCapacity
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
