import axios from 'axios'

export interface PeriodThroughput {
  periodStart: string
  periodEnd: string
  epics: number
  stories: number
  subtasks: number
  total: number
}

export interface ThroughputResponse {
  totalEpics: number
  totalStories: number
  totalSubtasks: number
  total: number
  byPeriod: PeriodThroughput[]
}

export interface LeadTimeResponse {
  avgDays: number
  medianDays: number
  p90Days: number
  minDays: number
  maxDays: number
  sampleSize: number
}

export interface CycleTimeResponse {
  avgDays: number
  medianDays: number
  p90Days: number
  minDays: number
  maxDays: number
  sampleSize: number
}

export interface TimeInStatusResponse {
  status: string
  avgHours: number
  medianHours: number
  transitionsCount: number
}

export interface AssigneeMetrics {
  accountId: string
  displayName: string
  issuesClosed: number
  avgLeadTimeDays: number
  avgCycleTimeDays: number
}

export interface TeamMetricsSummary {
  from: string
  to: string
  teamId: number
  throughput: ThroughputResponse
  leadTime: LeadTimeResponse
  cycleTime: CycleTimeResponse
  timeInStatuses: TimeInStatusResponse[]
  byAssignee: AssigneeMetrics[]
}

export async function getMetricsSummary(
  teamId: number,
  from: string,
  to: string,
  issueType?: string,
  epicKey?: string
): Promise<TeamMetricsSummary> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  if (issueType) params.append('issueType', issueType)
  if (epicKey) params.append('epicKey', epicKey)
  const response = await axios.get(`/api/metrics/summary?${params}`)
  return response.data
}

export async function getThroughput(
  teamId: number,
  from: string,
  to: string,
  issueType?: string,
  epicKey?: string,
  assigneeAccountId?: string
): Promise<ThroughputResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  if (issueType) params.append('issueType', issueType)
  if (epicKey) params.append('epicKey', epicKey)
  if (assigneeAccountId) params.append('assigneeAccountId', assigneeAccountId)
  const response = await axios.get(`/api/metrics/throughput?${params}`)
  return response.data
}

export async function getLeadTime(
  teamId: number,
  from: string,
  to: string,
  issueType?: string,
  epicKey?: string,
  assigneeAccountId?: string
): Promise<LeadTimeResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  if (issueType) params.append('issueType', issueType)
  if (epicKey) params.append('epicKey', epicKey)
  if (assigneeAccountId) params.append('assigneeAccountId', assigneeAccountId)
  const response = await axios.get(`/api/metrics/lead-time?${params}`)
  return response.data
}

export async function getCycleTime(
  teamId: number,
  from: string,
  to: string,
  issueType?: string,
  epicKey?: string,
  assigneeAccountId?: string
): Promise<CycleTimeResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  if (issueType) params.append('issueType', issueType)
  if (epicKey) params.append('epicKey', epicKey)
  if (assigneeAccountId) params.append('assigneeAccountId', assigneeAccountId)
  const response = await axios.get(`/api/metrics/cycle-time?${params}`)
  return response.data
}

export async function getTimeInStatus(
  teamId: number,
  from: string,
  to: string
): Promise<TimeInStatusResponse[]> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  const response = await axios.get(`/api/metrics/time-in-status?${params}`)
  return response.data
}

export async function getByAssignee(
  teamId: number,
  from: string,
  to: string
): Promise<AssigneeMetrics[]> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  const response = await axios.get(`/api/metrics/by-assignee?${params}`)
  return response.data
}

// ==================== LTC (Lead Time to Commit) ====================

export interface EpicLtc {
  epicKey: string
  summary: string
  workingDaysActual: number
  estimateDays: number | null
  forecastDays: number | null
  ltcActual: number | null
  ltcForecast: number | null
}

export interface LtcResponse {
  avgLtcActual: number
  avgLtcForecast: number
  totalEpics: number
  onTimeCount: number
  onTimeRate: number
  epics: EpicLtc[]
}

export async function getLtc(
  teamId: number,
  from: string,
  to: string
): Promise<LtcResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  const response = await axios.get(`/api/metrics/ltc?${params}`)
  return response.data
}

// ==================== Forecast Accuracy ====================

export interface EpicAccuracy {
  epicKey: string
  summary: string
  plannedStart: string | null
  plannedEnd: string | null
  actualStart: string | null
  actualEnd: string | null
  plannedDays: number
  actualDays: number
  accuracyRatio: number
  scheduleVariance: number
  status: 'ON_TIME' | 'EARLY' | 'LATE'
}

export interface ForecastAccuracyResponse {
  teamId: number
  from: string
  to: string
  avgAccuracyRatio: number
  onTimeDeliveryRate: number
  avgScheduleVariance: number
  totalCompleted: number
  onTimeCount: number
  lateCount: number
  earlyCount: number
  epics: EpicAccuracy[]
}

export async function getForecastAccuracy(
  teamId: number,
  from: string,
  to: string
): Promise<ForecastAccuracyResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  const response = await axios.get(`/api/metrics/forecast-accuracy?${params}`)
  return response.data
}
