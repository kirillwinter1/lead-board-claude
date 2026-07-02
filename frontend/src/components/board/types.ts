import { EpicForecast } from '../../api/forecast'
import { PlannedStory } from '../../api/forecast'
import { RoughEstimateConfig } from '../../api/epics'
import { ScoreBreakdown } from '../../api/board'

export interface RoleMetrics {
  estimateSeconds: number
  loggedSeconds: number
  progress: number
  roughEstimateDays: number | null
}

export type RoleProgress = Record<string, RoleMetrics>

export interface DataQualityViolation {
  rule: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  message: string
  /** Human-readable rule name from the backend (F84). Falls back to `rule`. */
  label?: string
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
  teamColor: string | null
  estimateSeconds: number | null
  loggedSeconds: number | null
  progress: number | null
  roleProgress: RoleProgress | null
  epicInTodo: boolean
  epicDone: boolean
  doneAt?: string  // ISO-8601 timestamp when epic transitioned to Done
  roughEstimates: Record<string, number> | null
  // F23: once an epic has poker (final) estimates its badge shows a CLEAN estimate
  // (filled); before poker it's a ROUGH pre-estimate (outlined/muted). Optional so
  // older board payloads default to rough behaviour.
  estimateSource?: 'rough' | 'clean'
  alerts: DataQualityViolation[]
  autoScore: number | null
  manualOrder: number | null
  flagged: boolean | null
  blockedBy: string[] | null
  blocks: string[] | null
  expectedDone: string | null
  assigneeAccountId: string | null
  assigneeDisplayName: string | null
  priority: string | null
  projectKey: string | null
  parentProjectKey: string | null
  parentProjectTitle: string | null
  quarterLabel: string | null
  daysInStatus: number | null
  statusAgeLevel: 'NORMAL' | 'WARNING' | 'CRITICAL'
  statusAgeReason: string | null
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

export type RoughEstimateUpdateFn = (epicKey: string, role: string, days: number | null) => Promise<void>

// Component prop types

export interface ProgressCellProps {
  loggedSeconds: number | null
  estimateSeconds: number | null
  progress: number | null
}

export interface EpicRoleChipProps {
  label: string
  role: string
  metrics: RoleMetrics
  epicInTodo: boolean
  epicKey: string
  config: RoughEstimateConfig | null
  onUpdate: RoughEstimateUpdateFn
  roleColor: string
  // F23: 'clean' once poker estimates exist for the epic, else 'rough'.
  estimateSource?: 'rough' | 'clean'
}

export interface RoleChipsProps {
  node: BoardNode
  config: RoughEstimateConfig | null
  onRoughEstimateUpdate: RoughEstimateUpdateFn
}

export interface PriorityCellProps {
  node: BoardNode
  recommendedPosition?: number
  actualPosition?: number
}

export interface StoryExpectedDoneCellProps {
  endDate: string | null
  assignee: string | null
  storyPlanning: PlannedStory | null
}

export interface ExpectedDoneCellProps {
  forecast: EpicForecast | null
}

export interface BoardRowProps {
  node: BoardNode
  level: number
  expanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: RoughEstimateUpdateFn
  forecast: EpicForecast | null
  canReorder: boolean
  isJustDropped: boolean
  actualPosition?: number
  recommendedPosition?: number
  dragHandleProps?: Record<string, unknown>
  storyPlanning?: PlannedStory | null
}

export interface BoardTableProps {
  items: BoardNode[]
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: RoughEstimateUpdateFn
  forecastMap: Map<string, EpicForecast>
  storyPlanningMap: Map<string, PlannedStory>
  canReorder: boolean
  onReorder: (epicKey: string, newIndex: number) => Promise<void>
  onStoryReorder: (storyKey: string, parentEpicKey: string, newIndex: number) => Promise<void>
}

export type { EpicForecast, RoughEstimateConfig, PlannedStory, ScoreBreakdown }
