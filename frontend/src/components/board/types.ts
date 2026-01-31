import { EpicForecast } from '../../api/forecast'
import { RoughEstimateConfig } from '../../api/epics'

export interface RoleMetrics {
  estimateSeconds: number
  loggedSeconds: number
  progress: number
  roughEstimateDays: number | null
}

export interface RoleProgress {
  analytics: RoleMetrics
  development: RoleMetrics
  testing: RoleMetrics
}

export interface DataQualityViolation {
  rule: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  message: string
}

export interface BoardNode {
  issueKey: string
  title: string
  status: string
  issueType: string
  jiraUrl: string
  role: string | null
  teamId: number | null
  teamName: string | null
  estimateSeconds: number | null
  loggedSeconds: number | null
  progress: number | null
  roleProgress: RoleProgress | null
  epicInTodo: boolean
  roughEstimateSaDays: number | null
  roughEstimateDevDays: number | null
  roughEstimateQaDays: number | null
  alerts: DataQualityViolation[]
  autoScore: number | null
  manualOrder: number | null
  flagged: boolean | null
  blockedBy: string[] | null
  blocks: string[] | null
  expectedDone: string | null
  assigneeAccountId: string | null
  assigneeDisplayName: string | null
  children: BoardNode[]
}

export interface BoardResponse {
  items: BoardNode[]
  total: number
}

export interface SyncStatus {
  syncInProgress: boolean
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  issuesCount: number
  error: string | null
}

export interface DragHandleProps {
  [key: string]: unknown
}

export type RoughEstimateUpdateFn = (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>

export type { EpicForecast, RoughEstimateConfig }
