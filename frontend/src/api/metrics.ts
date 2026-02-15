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
  movingAverage: number[]
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
  p85Hours: number
  p99Hours: number
  transitionsCount: number
  sortOrder: number
  color: string | null
}

export interface AssigneeMetrics {
  accountId: string
  displayName: string
  issuesClosed: number
  avgLeadTimeDays: number
  avgCycleTimeDays: number
  personalDsr: number | null
  velocityPercent: number | null
  trend: 'UP' | 'DOWN' | 'STABLE' | null
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

// ==================== DSR (Delivery Speed Ratio) ====================

export interface EpicDsr {
  epicKey: string
  summary: string
  inProgress: boolean
  calendarWorkingDays: number
  flaggedDays: number
  effectiveWorkingDays: number
  estimateDays: number | null
  forecastDays: number | null
  dsrActual: number | null
  dsrForecast: number | null
}

export interface DsrResponse {
  avgDsrActual: number
  avgDsrForecast: number
  totalEpics: number
  onTimeCount: number
  onTimeRate: number
  epics: EpicDsr[]
}

export async function getDsr(
  teamId: number,
  from: string,
  to: string
): Promise<DsrResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  const response = await axios.get(`/api/metrics/dsr?${params}`)
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
  initialEstimateHours: number
  developingEstimateHours: number
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

// ==================== Velocity ====================

export interface WeeklyVelocity {
  weekStart: string
  capacityHours: number
  loggedHours: number
  utilizationPercent: number
}

export interface VelocityResponse {
  teamId: number
  from: string
  to: string
  totalCapacityHours: number
  totalLoggedHours: number
  utilizationPercent: number
  byWeek: WeeklyVelocity[]
}

export async function getVelocity(
  teamId: number,
  from: string,
  to: string
): Promise<VelocityResponse> {
  const params = new URLSearchParams({ teamId: String(teamId), from, to })
  const response = await axios.get(`/api/metrics/velocity?${params}`)
  return response.data
}

// ==================== Epic Burndown ====================

export interface BurndownPoint {
  date: string
  remainingHours: number
}

export interface EpicBurndownResponse {
  epicKey: string
  summary: string
  startDate: string | null
  endDate: string | null
  totalEstimateHours: number
  idealLine: BurndownPoint[]
  actualLine: BurndownPoint[]
}

export interface EpicInfo {
  key: string
  summary: string
  status: string
  completed: boolean
}

export async function getEpicBurndown(epicKey: string): Promise<EpicBurndownResponse> {
  const response = await axios.get(`/api/metrics/epic-burndown?epicKey=${encodeURIComponent(epicKey)}`)
  return response.data
}

export async function getEpicsForBurndown(teamId: number): Promise<EpicInfo[]> {
  const response = await axios.get(`/api/metrics/epics-for-burndown?teamId=${teamId}`)
  return response.data
}
