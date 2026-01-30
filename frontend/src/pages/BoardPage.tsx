import { useEffect, useState, useCallback, useMemo, useRef, Fragment } from 'react'
import axios from 'axios'
import { Reorder, useDragControls } from 'framer-motion'
import { getRoughEstimateConfig, updateRoughEstimate, RoughEstimateConfig, updateEpicOrder, updateStoryOrder } from '../api/epics'
import { getForecast, EpicForecast, ForecastResponse, getUnifiedPlanning, PlannedStory } from '../api/forecast'
import { getScoreBreakdown, ScoreBreakdown } from '../api/board'
import { MultiSelectDropdown } from '../components/MultiSelectDropdown'
import './BoardPage.css'

// Sound effect for drag & drop - Pop sound
const playDropSound = () => {
  const audioContext = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)()
  const oscillator = audioContext.createOscillator()
  const gainNode = audioContext.createGain()

  oscillator.connect(gainNode)
  gainNode.connect(audioContext.destination)

  // Pop: высокая частота с быстрым падением
  oscillator.frequency.setValueAtTime(600, audioContext.currentTime)
  oscillator.frequency.exponentialRampToValueAtTime(150, audioContext.currentTime + 0.08)

  gainNode.gain.setValueAtTime(0.4, audioContext.currentTime)
  gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.08)

  oscillator.start(audioContext.currentTime)
  oscillator.stop(audioContext.currentTime + 0.08)
}

import epicIcon from '../icons/epic.png'
import storyIcon from '../icons/story.png'
import bugIcon from '../icons/bug.png'
import subtaskIcon from '../icons/subtask.png'

const issueTypeIcons: Record<string, string> = {
  'Эпик': epicIcon,
  'Epic': epicIcon,
  'История': storyIcon,
  'Story': storyIcon,
  'Баг': bugIcon,
  'Bug': bugIcon,
  'Подзадача': subtaskIcon,
  'Sub-task': subtaskIcon,
  'Subtask': subtaskIcon,
  'Аналитика': subtaskIcon,
  'Разработка': subtaskIcon,
  'Тестирование': subtaskIcon,
  'Analytics': subtaskIcon,
  'Development': subtaskIcon,
  'Testing': subtaskIcon,
}

function getIssueIcon(issueType: string): string {
  return issueTypeIcons[issueType] || storyIcon
}

interface RoleMetrics {
  estimateSeconds: number
  loggedSeconds: number
  progress: number
  roughEstimateDays: number | null
}

interface RoleProgress {
  analytics: RoleMetrics
  development: RoleMetrics
  testing: RoleMetrics
}

interface DataQualityViolation {
  rule: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  message: string
}

interface BoardNode {
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

interface BoardResponse {
  items: BoardNode[]
  total: number
}

interface SyncStatus {
  syncInProgress: boolean
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  issuesCount: number
  error: string | null
}

// Unified Progress Cell - combines logged, estimate, progress
interface ProgressCellProps {
  loggedSeconds: number | null
  estimateSeconds: number | null
  progress: number | null
}

function ProgressCell({ loggedSeconds, estimateSeconds, progress }: ProgressCellProps) {
  const loggedDays = (loggedSeconds || 0) / 3600 / 8
  const estimateDays = (estimateSeconds || 0) / 3600 / 8
  const remainingDays = Math.max(0, estimateDays - loggedDays)
  const progressValue = progress || 0
  const isComplete = progressValue >= 100

  // Format number compactly
  function formatCompact(n: number): string {
    if (n >= 100) return Math.round(n).toString()
    return n.toFixed(1)
  }

  return (
    <div className="unified-progress-cell">
      <div className="unified-progress-header">
        <span className={`unified-progress-percent ${isComplete ? 'complete' : ''}`}>
          {Math.round(progressValue)}%
        </span>
        <span className="unified-progress-remaining">
          {formatCompact(remainingDays)}d
        </span>
      </div>
      <div className="unified-progress-bar">
        <div
          className={`unified-progress-fill ${isComplete ? 'complete' : ''}`}
          style={{ width: `${Math.min(progressValue, 100)}%` }}
        />
      </div>
      <div className="unified-progress-times">
        <span className="time-logged">{formatCompact(loggedDays)}</span>
        <span className="time-arrow">→</span>
        <span className="time-estimate">{formatCompact(estimateDays)}</span>
      </div>
    </div>
  )
}

function isEpic(issueType: string): boolean {
  return issueType === 'Epic' || issueType === 'Эпик'
}

// Epic Role Chip - for editing rough estimates in TODO status or showing progress in work
interface EpicRoleChipProps {
  label: string
  role: 'sa' | 'dev' | 'qa'
  metrics: RoleMetrics
  epicInTodo: boolean
  epicKey: string
  config: RoughEstimateConfig | null
  onUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
}

function EpicRoleChip({ label, role, metrics, epicInTodo, epicKey, config, onUpdate }: EpicRoleChipProps) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState<string>('')
  const [saving, setSaving] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const roleClass = label.toLowerCase()

  const roughEstimate = metrics.roughEstimateDays
  const hasRoughEstimate = roughEstimate !== null && roughEstimate > 0
  const hasLogged = metrics.loggedSeconds > 0

  // Calculate remaining time
  const estimateDays = metrics.estimateSeconds / 3600 / 8
  const loggedDays = metrics.loggedSeconds / 3600 / 8
  const remainingDays = Math.max(0, estimateDays - loggedDays)

  const handleClick = () => {
    if (!epicInTodo || !config?.enabled) return
    setValue(roughEstimate?.toString() ?? '')
    setEditing(true)
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const days = value.trim() === '' ? null : parseFloat(value)
      if (days !== null && isNaN(days)) {
        throw new Error('Invalid number')
      }
      await onUpdate(epicKey, role, days)
      setEditing(false)
    } catch (err) {
      console.error('Failed to save rough estimate:', err)
      alert('Failed to save: ' + (err instanceof Error ? err.message : 'Unknown error'))
    } finally {
      setSaving(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSave()
    } else if (e.key === 'Escape') {
      setEditing(false)
    }
  }

  const handleBlur = () => {
    if (!saving) {
      handleSave()
    }
  }

  // Epic in TODO - editable chip
  if (epicInTodo) {
    if (editing) {
      return (
        <div className={`epic-role-chip ${roleClass} todo editing`}>
          <span className="epic-role-label">{label}</span>
          <input
            ref={inputRef}
            type="number"
            step={config?.stepDays || 0.5}
            min={config?.minDays || 0}
            max={config?.maxDays || 365}
            value={value}
            onChange={e => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={handleBlur}
            className="epic-role-input"
            disabled={saving}
          />
        </div>
      )
    }

    return (
      <div
        className={`epic-role-chip ${roleClass} todo ${hasRoughEstimate ? '' : 'needs-estimate'}`}
        onClick={handleClick}
        title="Click to set estimate"
      >
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-value">{hasRoughEstimate ? `${roughEstimate}d` : '✎'}</span>
      </div>
    )
  }

  // Epic in progress - show progress bar based on estimate from subtasks
  const hasEstimate = metrics.estimateSeconds > 0
  const progress = hasEstimate ? Math.min(100, (loggedDays / estimateDays) * 100) : 0
  const remainingText = `${formatCompact(remainingDays)}d left`

  // Format number: no decimal for >= 100, one decimal otherwise
  function formatCompact(n: number): string {
    if (n >= 100) return Math.round(n).toString()
    return n.toFixed(1)
  }

  return (
    <div
      className={`epic-role-chip ${roleClass} in-progress ${!hasEstimate ? 'no-estimate' : ''}`}
      title={hasEstimate ? remainingText : undefined}
    >
      <div className="epic-role-header">
        <span className="epic-role-label">{label}</span>
        <span className="epic-role-percent">{hasEstimate ? `${Math.round(progress)}%` : '--'}</span>
      </div>
      {hasEstimate && (
        <>
          <div className="epic-role-progress-bar">
            <div
              className={`epic-role-progress-fill ${progress >= 100 ? 'overburn' : ''}`}
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
          <div className="epic-role-times">
            <span className="time-logged">{formatCompact(loggedDays)}</span>
            <span className="arrow">→</span>
            <span className="time-estimate">{formatCompact(estimateDays)}</span>
          </div>
        </>
      )}
      {!hasEstimate && hasLogged && (
        <div className="epic-role-times warning">
          {formatCompact(loggedDays)}d
        </div>
      )}
    </div>
  )
}

// Standard Role Chip for non-Epic issues
function RoleChip({ label, metrics }: { label: string; metrics: RoleMetrics | null }) {
  const hasEstimate = metrics && metrics.estimateSeconds > 0
  const progress = hasEstimate ? Math.min(metrics.progress, 100) : 0
  const roleClass = label.toLowerCase()

  return (
    <div className={`role-chip ${roleClass} ${hasEstimate ? '' : 'disabled'}`}>
      <div className="role-chip-fill" style={{ width: `${progress}%` }} />
      <span className="role-chip-label">{label}</span>
      {hasEstimate && <span className="role-chip-percent">{metrics.progress}%</span>}
    </div>
  )
}

interface RoleChipsProps {
  node: BoardNode
  config: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
}

function RoleChips({ node, config, onRoughEstimateUpdate }: RoleChipsProps) {
  const roleProgress = node.roleProgress

  // For Epic - use special Epic chips
  if (isEpic(node.issueType) && roleProgress) {
    return (
      <div className="epic-role-chips">
        <EpicRoleChip
          label="SA"
          role="sa"
          metrics={roleProgress.analytics}
          epicInTodo={node.epicInTodo}
          epicKey={node.issueKey}
          config={config}
          onUpdate={onRoughEstimateUpdate}
        />
        <EpicRoleChip
          label="DEV"
          role="dev"
          metrics={roleProgress.development}
          epicInTodo={node.epicInTodo}
          epicKey={node.issueKey}
          config={config}
          onUpdate={onRoughEstimateUpdate}
        />
        <EpicRoleChip
          label="QA"
          role="qa"
          metrics={roleProgress.testing}
          epicInTodo={node.epicInTodo}
          epicKey={node.issueKey}
          config={config}
          onUpdate={onRoughEstimateUpdate}
        />
      </div>
    )
  }

  // For Story/Bug/Subtask - standard chips
  return (
    <div className="role-chips">
      <RoleChip label="SA" metrics={roleProgress?.analytics || null} />
      <RoleChip label="DEV" metrics={roleProgress?.development || null} />
      <RoleChip label="QA" metrics={roleProgress?.testing || null} />
    </div>
  )
}

// Recommendation indicator - shows if manual order differs from autoScore recommendation
function getRecommendationIcon(actualPosition: number, recommendedPosition: number, autoScore: number | null): { icon: string; className: string } {
  if (autoScore === null || actualPosition === recommendedPosition) {
    return { icon: '●', className: 'match' }
  }
  if (actualPosition < recommendedPosition) {
    return { icon: '↓', className: 'suggest-down' }
  }
  return { icon: '↑', className: 'suggest-up' }
}

interface PriorityCellProps {
  node: BoardNode
  recommendedPosition?: number
  actualPosition?: number
}

function PriorityCell({ node, recommendedPosition, actualPosition }: PriorityCellProps) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [breakdown, setBreakdown] = useState<ScoreBreakdown | null>(null)
  const [loading, setLoading] = useState(false)
  const [tooltipPos, setTooltipPos] = useState<{ top: number; left: number; showAbove?: boolean } | null>(null)
  const cellRef = useRef<HTMLDivElement>(null)

  const score = node.autoScore || 0

  // Color based on score
  let color = '#888' // gray for low
  if (score > 80) color = '#36b37e' // green for high
  else if (score >= 40) color = '#ffab00' // yellow for medium
  else if (score < 0) color = '#de350b' // red for negative (blocked)

  // Icons
  const icons: string[] = []
  if (node.issueType?.toLowerCase().includes('bug') || node.issueType?.toLowerCase().includes('баг')) {
    icons.push('🐞')
  }
  if (node.blockedBy && node.blockedBy.length > 0) {
    icons.push('🔒')
  }
  if (node.flagged) {
    icons.push('🚩')
  }
  if (score > 80) {
    icons.push('⚡')
  }

  const hasNoEstimates = node.estimateSeconds === null || node.estimateSeconds === 0
  if (hasNoEstimates && !node.issueType?.toLowerCase().includes('epic') && !node.issueType?.toLowerCase().includes('эпик')) {
    icons.push('⚠️')
  }

  // Load breakdown on hover
  const handleMouseEnter = async () => {
    setShowTooltip(true)

    // Calculate tooltip position
    if (cellRef.current) {
      const rect = cellRef.current.getBoundingClientRect()
      const tooltipWidth = 260
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
      const minSpaceNeeded = 150 // minimum space to show tooltip comfortably

      let top: number
      let left = rect.left + rect.width / 2 - tooltipWidth / 2

      // Decide if tooltip should be above or below
      if (spaceBelow >= minSpaceNeeded) {
        // Show below - enough space
        top = rect.bottom + 8
      } else if (spaceAbove >= minSpaceNeeded) {
        // Show above - not enough space below
        top = rect.top - 8
        // Will use CSS transform to position above
      } else {
        // Very little space - show below and let it scroll
        top = rect.bottom + 8
      }

      // If tooltip would go off right edge, shift left
      if (left + tooltipWidth > window.innerWidth) {
        left = window.innerWidth - tooltipWidth - 16
      }

      // If tooltip would go off left edge, shift right
      if (left < 16) {
        left = 16
      }

      setTooltipPos({
        top,
        left,
        showAbove: spaceBelow < minSpaceNeeded && spaceAbove >= minSpaceNeeded
      })
    }

    if (!breakdown && !loading) {
      setLoading(true)
      try {
        const data = await getScoreBreakdown(node.issueKey)
        setBreakdown(data)
      } catch (err) {
        console.error('Failed to load score breakdown:', err)
      } finally {
        setLoading(false)
      }
    }
  }

  // Factor labels in Russian
  const factorLabels: Record<string, string> = {
    issueType: 'Тип задачи',
    status: 'Статус',
    progress: 'Прогресс',
    priority: 'Приоритет',
    dependency: 'Зависимости',
    dueDate: 'Срок',
    estimateQuality: 'Качество оценки',
    flagged: 'Флаг',
    // Epic factors
    statusWeight: 'Статус',
    storyCompletion: 'Завершение историй',
    dueDateWeight: 'Срок'
  }

  return (
    <div
      ref={cellRef}
      className="priority-cell"
      style={{ color }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => setShowTooltip(false)}
    >
      <span className="priority-score" style={{ fontWeight: 600 }}>
        {score.toFixed(0)}
      </span>
      {actualPosition !== undefined && recommendedPosition !== undefined && (
        <span className={`recommendation-indicator ${getRecommendationIcon(actualPosition, recommendedPosition, node.autoScore).className}`}>
          {getRecommendationIcon(actualPosition, recommendedPosition, node.autoScore).icon}
        </span>
      )}
      {icons.length > 0 && (
        <span className="priority-icons" style={{ marginLeft: '6px' }}>
          {icons.join(' ')}
        </span>
      )}

      {showTooltip && tooltipPos && (
        <div
          className="priority-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="priority-tooltip-header">
            <strong>{node.issueKey}</strong>
            <span className="priority-tooltip-type">{node.issueType}</span>
          </div>
          <div className="priority-tooltip-total">
            Total Score: <strong>{score.toFixed(1)}</strong>
          </div>

          {recommendedPosition !== undefined && actualPosition !== undefined && (
            <div className="priority-tooltip-recommendation">
              Рекомендуемая позиция: <strong>{recommendedPosition}</strong>
              {actualPosition !== recommendedPosition && (
                <span className="current-position"> (сейчас: {actualPosition})</span>
              )}
            </div>
          )}

          {loading && (
            <div className="priority-tooltip-loading">Загрузка...</div>
          )}

          {breakdown && breakdown.breakdown && (
            <div className="priority-tooltip-breakdown">
              <div className="priority-tooltip-title">Breakdown:</div>
              {Object.entries(breakdown.breakdown)
                .filter(([_, value]) => value !== 0)
                .sort(([_a, a], [_b, b]) => Math.abs(b) - Math.abs(a))
                .map(([factor, value]) => (
                  <div key={factor} className="priority-breakdown-item">
                    <span className="factor-name">{factorLabels[factor] || factor}</span>
                    <span className={`factor-value ${value > 0 ? 'positive' : value < 0 ? 'negative' : ''}`}>
                      {value > 0 ? '+' : ''}{value.toFixed(1)}
                    </span>
                  </div>
                ))}
            </div>
          )}

          {icons.length > 0 && (
            <div className="priority-tooltip-indicators">
              <div className="priority-tooltip-title">Индикаторы:</div>
              {icons.map((icon, idx) => {
                let description = ''
                if (icon === '🐞') description = 'Баг'
                else if (icon === '🔒') description = 'Заблокировано другими задачами'
                else if (icon === '🚩') description = 'Отмечено как impediment'
                else if (icon === '⚡') description = 'Высокий приоритет (>80)'
                else if (icon === '⚠️') description = 'Нет оценки времени'

                return (
                  <div key={idx} className="priority-indicator-item">
                    <span className="indicator-icon">{icon}</span>
                    <span className="indicator-description">{description}</span>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const statusClass = status.toLowerCase().replace(/\s+/g, '-')
  return <span className={`status-badge ${statusClass}`}>{status}</span>
}

// Expected Done cell for stories with tooltip
interface StoryExpectedDoneCellProps {
  endDate: string | null
  assignee: string | null
  storyPlanning: PlannedStory | null
}

function StoryExpectedDoneCell({ endDate, assignee, storyPlanning }: StoryExpectedDoneCellProps) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState<{ top: number; left: number; showAbove?: boolean } | null>(null)
  const cellRef = useRef<HTMLDivElement>(null)

  if (!endDate) {
    return <span className="expected-done-empty">--</span>
  }

  const formatDate = (dateStr: string): string => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
  }

  const formatDateRange = (start: string | null | undefined, end: string | null | undefined): string => {
    if (!start && !end) return '—'
    if (!start) return `→ ${formatDate(end!)}`
    if (!end) return `${formatDate(start)} →`
    return `${formatDate(start)} → ${formatDate(end)}`
  }

  const handleMouseEnter = () => {
    setShowTooltip(true)

    if (cellRef.current) {
      const rect = cellRef.current.getBoundingClientRect()
      const tooltipWidth = 280
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
      const minSpaceNeeded = 180

      let top: number
      let left = rect.left + rect.width / 2 - tooltipWidth / 2

      if (spaceBelow >= minSpaceNeeded) {
        top = rect.bottom + 8
      } else if (spaceAbove >= minSpaceNeeded) {
        top = rect.top - 8
      } else {
        top = rect.bottom + 8
      }

      if (left + tooltipWidth > window.innerWidth) {
        left = window.innerWidth - tooltipWidth - 16
      }
      if (left < 16) {
        left = 16
      }

      setTooltipPos({
        top,
        left,
        showAbove: spaceBelow < minSpaceNeeded && spaceAbove >= minSpaceNeeded
      })
    }
  }

  return (
    <div
      ref={cellRef}
      className="expected-done-cell"
      style={{ display: 'flex', flexDirection: 'column', gap: '2px', cursor: storyPlanning ? 'help' : undefined }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => setShowTooltip(false)}
    >
      <span className="expected-done-date">{formatDate(endDate)}</span>
      {assignee && (
        <span style={{ fontSize: '0.75rem', color: '#666', fontStyle: 'italic' }}>
          {assignee}
        </span>
      )}

      {showTooltip && tooltipPos && storyPlanning && (
        <div
          className="forecast-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="forecast-tooltip-header">
            <span><strong>{storyPlanning.storyKey}</strong></span>
            {storyPlanning.autoScore !== null && (
              <span>AutoScore: <strong>{storyPlanning.autoScore.toFixed(1)}</strong></span>
            )}
          </div>

          <div className="forecast-tooltip-section">
            <div className="forecast-tooltip-title">Расписание фаз</div>
            {storyPlanning.phases.sa && (
              <div className="forecast-phase">
                <span className="phase-label sa">SA</span>
                <span className="phase-dates">{formatDateRange(storyPlanning.phases.sa.startDate, storyPlanning.phases.sa.endDate)}</span>
                {storyPlanning.phases.sa.assigneeDisplayName && (
                  <span className="phase-assignee">{storyPlanning.phases.sa.assigneeDisplayName}</span>
                )}
              </div>
            )}
            {storyPlanning.phases.dev && (
              <div className="forecast-phase">
                <span className="phase-label dev">DEV</span>
                <span className="phase-dates">{formatDateRange(storyPlanning.phases.dev.startDate, storyPlanning.phases.dev.endDate)}</span>
                {storyPlanning.phases.dev.assigneeDisplayName && (
                  <span className="phase-assignee">{storyPlanning.phases.dev.assigneeDisplayName}</span>
                )}
              </div>
            )}
            {storyPlanning.phases.qa && (
              <div className="forecast-phase">
                <span className="phase-label qa">QA</span>
                <span className="phase-dates">{formatDateRange(storyPlanning.phases.qa.startDate, storyPlanning.phases.qa.endDate)}</span>
                {storyPlanning.phases.qa.assigneeDisplayName && (
                  <span className="phase-assignee">{storyPlanning.phases.qa.assigneeDisplayName}</span>
                )}
              </div>
            )}
            {!storyPlanning.phases.sa && !storyPlanning.phases.dev && !storyPlanning.phases.qa && (
              <div className="forecast-phase" style={{ color: '#666' }}>Нет данных о фазах</div>
            )}
          </div>

          {storyPlanning.blockedBy && storyPlanning.blockedBy.length > 0 && (
            <div className="forecast-tooltip-footer" style={{ color: '#de350b' }}>
              Заблокировано: {storyPlanning.blockedBy.join(', ')}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// Expected Done cell with confidence indicator and detailed tooltip (for Epics)
interface ExpectedDoneCellProps {
  forecast: EpicForecast | null
}

function ExpectedDoneCell({ forecast }: ExpectedDoneCellProps) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState<{ top: number; left: number; showAbove?: boolean } | null>(null)
  const cellRef = useRef<HTMLDivElement>(null)

  if (!forecast) {
    return <span className="expected-done-empty">--</span>
  }

  const { expectedDone, confidence, dueDateDeltaDays, dueDate, autoScore, remainingByRole, phaseSchedule } = forecast

  // Format date as "15 мар"
  const formatDate = (dateStr: string | null): string => {
    if (!dateStr) return '--'
    const date = new Date(dateStr)
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
  }

  // Format date range
  const formatDateRange = (start: string | null, end: string | null): string => {
    if (!start || !end) return '--'
    return `${formatDate(start)} - ${formatDate(end)}`
  }

  // Confidence colors
  const confidenceClass = confidence.toLowerCase()
  const confidenceLabels = {
    HIGH: 'Высокая',
    MEDIUM: 'Средняя',
    LOW: 'Низкая'
  }

  // Delta indicator
  let deltaClass = ''
  let deltaText = ''
  if (dueDateDeltaDays !== null) {
    if (dueDateDeltaDays > 0) {
      deltaClass = 'delta-late'
      deltaText = `+${dueDateDeltaDays}d`
    } else if (dueDateDeltaDays < 0) {
      deltaClass = 'delta-early'
      deltaText = `${dueDateDeltaDays}d`
    } else {
      deltaClass = 'delta-ontime'
      deltaText = 'on time'
    }
  }

  const handleMouseEnter = () => {
    setShowTooltip(true)

    // Calculate tooltip position
    if (cellRef.current) {
      const rect = cellRef.current.getBoundingClientRect()
      const tooltipWidth = 280
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
      const minSpaceNeeded = 200 // forecast tooltip is typically larger

      let top: number
      let left = rect.left + rect.width / 2 - tooltipWidth / 2

      // Decide if tooltip should be above or below
      if (spaceBelow >= minSpaceNeeded) {
        // Show below - enough space
        top = rect.bottom + 8
      } else if (spaceAbove >= minSpaceNeeded) {
        // Show above - not enough space below
        top = rect.top - 8
      } else {
        // Very little space - show below and let it scroll
        top = rect.bottom + 8
      }

      // If tooltip would go off right edge, shift left
      if (left + tooltipWidth > window.innerWidth) {
        left = window.innerWidth - tooltipWidth - 16
      }

      // If tooltip would go off left edge, shift right
      if (left < 16) {
        left = 16
      }

      setTooltipPos({
        top,
        left,
        showAbove: spaceBelow < minSpaceNeeded && spaceAbove >= minSpaceNeeded
      })
    }
  }

  return (
    <div
      ref={cellRef}
      className="expected-done-cell"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => setShowTooltip(false)}
    >
      <span className={`confidence-dot ${confidenceClass}`} />
      <span className="expected-done-date">{formatDate(expectedDone)}</span>
      {deltaText && (
        <span className={`expected-done-delta ${deltaClass}`}>
          {deltaText}
        </span>
      )}

      {showTooltip && tooltipPos && (
        <div
          className="forecast-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="forecast-tooltip-header">
            <span>AutoScore: <strong>{autoScore.toFixed(1)}</strong></span>
            <span className={`confidence-badge ${confidenceClass}`}>
              {confidenceLabels[confidence]}
            </span>
          </div>

          <div className="forecast-tooltip-section">
            <div className="forecast-tooltip-title">Расписание фаз</div>
            <div className="forecast-phase">
              <span className="phase-label sa">SA</span>
              <span className="phase-dates">{formatDateRange(phaseSchedule?.sa?.startDate, phaseSchedule?.sa?.endDate)}</span>
              <span className="phase-remaining">({remainingByRole?.sa?.days?.toFixed(1) || 0}d)</span>
            </div>
            <div className="forecast-phase">
              <span className="phase-label dev">DEV</span>
              <span className="phase-dates">{formatDateRange(phaseSchedule?.dev?.startDate, phaseSchedule?.dev?.endDate)}</span>
              <span className="phase-remaining">({remainingByRole?.dev?.days?.toFixed(1) || 0}d)</span>
            </div>
            <div className="forecast-phase">
              <span className="phase-label qa">QA</span>
              <span className="phase-dates">{formatDateRange(phaseSchedule?.qa?.startDate, phaseSchedule?.qa?.endDate)}</span>
              <span className="phase-remaining">({remainingByRole?.qa?.days?.toFixed(1) || 0}d)</span>
            </div>
          </div>

          {dueDate && (
            <div className="forecast-tooltip-footer">
              Due date: {formatDate(dueDate)}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function AlertIcon({ node }: { node: BoardNode }) {
  const [showTooltip, setShowTooltip] = useState(false)
  const [tooltipPos, setTooltipPos] = useState<{ top: number; left: number; showAbove?: boolean } | null>(null)
  const iconRef = useRef<HTMLDivElement>(null)
  const alerts = node.alerts || []

  if (alerts.length === 0) {
    return <span className="no-alert">--</span>
  }

  // Find the highest severity
  const hasError = alerts.some(a => a.severity === 'ERROR')
  const hasWarning = alerts.some(a => a.severity === 'WARNING')

  const severityClass = hasError ? 'error' : hasWarning ? 'warning' : 'info'
  const count = alerts.length

  // Severity labels
  const severityLabels: Record<string, string> = {
    ERROR: 'Ошибка',
    WARNING: 'Предупреждение',
    INFO: 'Инфо'
  }

  // Severity icons
  const severityIcons: Record<string, string> = {
    ERROR: '🔴',
    WARNING: '🟡',
    INFO: '🔵'
  }

  const handleMouseEnter = () => {
    setShowTooltip(true)

    // Calculate tooltip position
    if (iconRef.current) {
      const rect = iconRef.current.getBoundingClientRect()
      const tooltipWidth = 320
      const spaceBelow = window.innerHeight - rect.bottom
      const spaceAbove = rect.top
      const minSpaceNeeded = 150 // minimum space to show tooltip comfortably

      let top: number
      let left = rect.left + rect.width / 2 - tooltipWidth / 2

      // Decide if tooltip should be above or below
      if (spaceBelow >= minSpaceNeeded) {
        // Show below - enough space
        top = rect.bottom + 8
      } else if (spaceAbove >= minSpaceNeeded) {
        // Show above - not enough space below
        top = rect.top - 8
      } else {
        // Very little space - show below and let it scroll
        top = rect.bottom + 8
      }

      // If tooltip would go off right edge, shift left
      if (left + tooltipWidth > window.innerWidth) {
        left = window.innerWidth - tooltipWidth - 16
      }

      // If tooltip would go off left edge, shift right
      if (left < 16) {
        left = 16
      }

      setTooltipPos({
        top,
        left,
        showAbove: spaceBelow < minSpaceNeeded && spaceAbove >= minSpaceNeeded
      })
    }
  }

  return (
    <div
      ref={iconRef}
      className="alert-icon-wrapper"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={() => setShowTooltip(false)}
    >
      <span className={`alert-icon alert-${severityClass}`}>
        {count}
      </span>

      {showTooltip && tooltipPos && (
        <div
          className="alert-tooltip"
          style={{
            top: `${tooltipPos.top}px`,
            left: `${tooltipPos.left}px`,
            transform: tooltipPos.showAbove ? 'translateY(-100%)' : 'none'
          }}
        >
          <div className="alert-tooltip-header">
            <strong>Data Quality Issues ({count})</strong>
          </div>
          <div className="alert-tooltip-list">
            {alerts.map((alert, idx) => (
              <div key={idx} className={`alert-tooltip-item alert-${alert.severity.toLowerCase()}`}>
                <div className="alert-item-header">
                  <span className="alert-severity">
                    {severityIcons[alert.severity]} {severityLabels[alert.severity] || alert.severity}
                  </span>
                  <span className="alert-rule">{alert.rule}</span>
                </div>
                <div className="alert-message">
                  {alert.message}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

interface BoardRowProps {
  node: BoardNode
  level: number
  expanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
  forecast: EpicForecast | null
  canReorder: boolean
  isJustDropped: boolean
  // Recommendation indicator
  actualPosition?: number
  recommendedPosition?: number
  // Drag controls for framer-motion
  dragControls?: ReturnType<typeof useDragControls>
  // Story planning data for tooltip
  storyPlanning?: PlannedStory | null
}

function BoardRow({ node, level, expanded, onToggle, hasChildren, roughEstimateConfig, onRoughEstimateUpdate, forecast, canReorder, isJustDropped, actualPosition, recommendedPosition, dragControls, storyPlanning }: BoardRowProps) {
  const isEpicRow = isEpic(node.issueType) && level === 0
  const isStoryRow = (node.issueType === 'Story' || node.issueType === 'История' || node.issueType === 'Bug' || node.issueType === 'Баг') && level === 1

  const justDroppedEffects = isJustDropped ? 'just-dropped' : ''

  return (
    <div className={`board-row level-${level} ${justDroppedEffects}`}>
      <div className="cell cell-expander">
        {hasChildren ? (
          <button
            className={`expander-btn ${expanded ? 'expanded' : ''}`}
            onClick={onToggle}
          >
            <svg className="expander-icon" width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <path d="M6 4l4 4-4 4" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        ) : (
          <span className="expander-placeholder" />
        )}
      </div>
      <div className="cell cell-name">
        <div
          className="name-content"
          style={{
            paddingLeft: `${level * 20}px`,
            cursor: (isEpicRow || isStoryRow) && canReorder && dragControls ? 'grab' : undefined,
            userSelect: (isEpicRow || isStoryRow) && canReorder && dragControls ? 'none' : undefined,
            WebkitUserSelect: (isEpicRow || isStoryRow) && canReorder && dragControls ? 'none' : undefined,
            touchAction: (isEpicRow || isStoryRow) && canReorder && dragControls ? 'none' : undefined
          }}
          onPointerDown={(isEpicRow || isStoryRow) && canReorder && dragControls ? (e) => {
            // Don't start drag if clicking on a link or button
            const target = e.target as HTMLElement
            if (target.tagName === 'A' || target.tagName === 'BUTTON' || target.closest('a') || target.closest('button')) {
              return
            }
            e.preventDefault() // Prevent text selection
            dragControls.start(e)
          } : undefined}
        >
          {(isEpicRow || isStoryRow) && canReorder && dragControls && (
            <span
              className="drag-handle"
              title={isStoryRow ? "Drag to reorder within epic" : "Drag to reorder"}
              onPointerDown={(e) => {
                e.stopPropagation()
                dragControls.start(e)
              }}
              style={{ cursor: 'grab', touchAction: 'none' }}
            >⋮⋮</span>
          )}
          <img src={getIssueIcon(node.issueType)} alt={node.issueType} className="issue-type-icon" />
          <a href={node.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
            {node.issueKey}
          </a>
          <span className="issue-title">{node.title}</span>
        </div>
      </div>
      <div className="cell cell-team">{node.teamName || '--'}</div>
      <div className="cell cell-priority">
        {node.autoScore !== null && node.autoScore !== undefined ? (
          <PriorityCell
            node={node}
            recommendedPosition={recommendedPosition}
            actualPosition={actualPosition}
          />
        ) : (
          <span className="priority-empty">--</span>
        )}
      </div>
      <div className="cell cell-expected-done">
        {isEpic(node.issueType) ? (
          <ExpectedDoneCell forecast={forecast} />
        ) : (
          <StoryExpectedDoneCell endDate={node.expectedDone} assignee={node.assigneeDisplayName} storyPlanning={storyPlanning || null} />
        )}
      </div>
      <div className="cell cell-progress">
        <ProgressCell
          loggedSeconds={node.loggedSeconds}
          estimateSeconds={node.estimateSeconds}
          progress={node.progress}
        />
      </div>
      <div className="cell cell-roles">
        <RoleChips
          node={node}
          config={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
        />
      </div>
      <div className="cell cell-status">
        <StatusBadge status={node.status} />
      </div>
      <div className="cell cell-alerts">
        <AlertIcon node={node} />
      </div>
    </div>
  )
}

interface BoardTableProps {
  items: BoardNode[]
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
  forecastMap: Map<string, EpicForecast>
  storyPlanningMap: Map<string, PlannedStory>
  canReorder: boolean
  onReorder: (epicKey: string, newIndex: number) => Promise<void>
  onStoryReorder: (storyKey: string, parentEpicKey: string, newIndex: number) => Promise<void>
}

// Draggable Epic Row wrapper using framer-motion
function DraggableEpicRow({
  epic,
  isExpanded,
  onToggle,
  hasChildren,
  roughEstimateConfig,
  onRoughEstimateUpdate,
  forecast,
  canReorder,
  actualPosition,
  recommendedPosition,
  onDragEnd,
  children
}: {
  epic: BoardNode
  isExpanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
  forecast: EpicForecast | null
  canReorder: boolean
  actualPosition: number
  recommendedPosition: number | undefined
  onDragEnd?: () => void
  children?: React.ReactNode
}) {
  const dragControls = useDragControls()

  if (!canReorder) {
    return (
      <div>
        <BoardRow
          node={epic}
          level={0}
          expanded={isExpanded}
          onToggle={onToggle}
          hasChildren={hasChildren}
          roughEstimateConfig={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
          forecast={forecast}
          canReorder={false}
          isJustDropped={false}
          actualPosition={actualPosition}
          recommendedPosition={recommendedPosition}
        />
        {children}
      </div>
    )
  }

  return (
    <Reorder.Item
      value={epic}
      dragListener={false}
      dragControls={dragControls}
      layout
      style={{ listStyle: 'none' }}
      initial={{ opacity: 1, scale: 1 }}
      animate={{ opacity: 1, scale: 1 }}
      whileDrag={{
        scale: 1.02,
        boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
        zIndex: 100,
        cursor: 'grabbing'
      }}
      exit={{ opacity: 0 }}
      transition={{
        type: 'spring',
        stiffness: 300,
        damping: 30
      }}
      onDragEnd={onDragEnd}
    >
      <BoardRow
        node={epic}
        level={0}
        expanded={isExpanded}
        onToggle={onToggle}
        hasChildren={hasChildren}
        roughEstimateConfig={roughEstimateConfig}
        onRoughEstimateUpdate={onRoughEstimateUpdate}
        forecast={forecast}
        canReorder={canReorder}
        isJustDropped={false}
        actualPosition={actualPosition}
        recommendedPosition={recommendedPosition}
        dragControls={dragControls}
      />
      {children}
    </Reorder.Item>
  )
}

// Draggable Story Row wrapper using framer-motion
function DraggableStoryRow({
  story,
  isExpanded,
  onToggle,
  hasChildren,
  roughEstimateConfig,
  onRoughEstimateUpdate,
  forecast,
  canReorder,
  actualPosition,
  recommendedPosition,
  onDragEnd,
  storyPlanning,
  children
}: {
  story: BoardNode
  isExpanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
  forecast: EpicForecast | null
  canReorder: boolean
  actualPosition: number | undefined
  recommendedPosition: number | undefined
  onDragEnd?: () => void
  storyPlanning?: PlannedStory | null
  children?: React.ReactNode
}) {
  const dragControls = useDragControls()

  if (!canReorder) {
    return (
      <div>
        <BoardRow
          node={story}
          level={1}
          expanded={isExpanded}
          onToggle={onToggle}
          hasChildren={hasChildren}
          roughEstimateConfig={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
          forecast={forecast}
          canReorder={false}
          isJustDropped={false}
          actualPosition={actualPosition}
          recommendedPosition={recommendedPosition}
          storyPlanning={storyPlanning}
        />
        {children}
      </div>
    )
  }

  return (
    <Reorder.Item
      value={story}
      dragListener={false}
      dragControls={dragControls}
      layout
      style={{ listStyle: 'none' }}
      initial={{ opacity: 1, scale: 1 }}
      animate={{ opacity: 1, scale: 1 }}
      whileDrag={{
        scale: 1.02,
        boxShadow: '0 4px 15px rgba(0,0,0,0.12)',
        zIndex: 100,
        cursor: 'grabbing'
      }}
      exit={{ opacity: 0 }}
      transition={{
        type: 'spring',
        stiffness: 300,
        damping: 30
      }}
      onDragEnd={onDragEnd}
    >
      <BoardRow
        node={story}
        level={1}
        expanded={isExpanded}
        onToggle={onToggle}
        hasChildren={hasChildren}
        roughEstimateConfig={roughEstimateConfig}
        onRoughEstimateUpdate={onRoughEstimateUpdate}
        forecast={forecast}
        canReorder={canReorder}
        isJustDropped={false}
        actualPosition={actualPosition}
        storyPlanning={storyPlanning}
        recommendedPosition={recommendedPosition}
        dragControls={dragControls}
      />
      {children}
    </Reorder.Item>
  )
}

function BoardTable({ items, roughEstimateConfig, onRoughEstimateUpdate, forecastMap, storyPlanningMap, canReorder, onReorder, onStoryReorder }: BoardTableProps) {
  // Load expanded keys from localStorage
  const loadExpandedKeys = (): Set<string> => {
    try {
      const saved = localStorage.getItem('boardExpandedEpics')
      if (saved) {
        const parsed = JSON.parse(saved)
        return new Set(parsed)
      }
    } catch (err) {
      console.error('Failed to load expanded epics from localStorage:', err)
    }
    return new Set()
  }

  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(loadExpandedKeys)
  // Local state for smooth reordering animation
  const [localItems, setLocalItems] = useState<BoardNode[]>(items)
  // Track pending reorder to call API only when drag ends
  const pendingEpicReorderRef = useRef<{ key: string; newIndex: number } | null>(null)
  const pendingStoryReorderRef = useRef<{ key: string; parentKey: string; newIndex: number } | null>(null)

  // Sync local items with props
  useEffect(() => {
    setLocalItems(items)
  }, [items])

  // Save expanded keys to localStorage whenever they change
  useEffect(() => {
    try {
      localStorage.setItem('boardExpandedEpics', JSON.stringify(Array.from(expandedKeys)))
    } catch (err) {
      console.error('Failed to save expanded epics to localStorage:', err)
    }
  }, [expandedKeys])

  const toggleExpand = (key: string) => {
    setExpandedKeys(prev => {
      const next = new Set(prev)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  // Handle reorder from framer-motion - only update local state, defer API call
  const handleReorder = useCallback((newOrder: BoardNode[]) => {
    // Find what changed
    const oldOrder = localItems.map(e => e.issueKey)
    const newOrderKeys = newOrder.map(e => e.issueKey)

    // Find the item that moved
    let movedKey: string | null = null
    let newIndex = -1
    for (let i = 0; i < newOrderKeys.length; i++) {
      if (newOrderKeys[i] !== oldOrder[i]) {
        const keyAtNewPos = newOrderKeys[i]
        const oldPos = oldOrder.indexOf(keyAtNewPos)
        if (oldPos !== i) {
          movedKey = keyAtNewPos
          newIndex = i
          break
        }
      }
    }

    if (movedKey && newIndex >= 0) {
      // Update local state immediately for smooth animation
      setLocalItems(newOrder)
      // Store pending reorder - will be committed on drag end
      pendingEpicReorderRef.current = { key: movedKey, newIndex }
    }
  }, [localItems])

  // Commit epic reorder to API when drag ends
  const commitEpicReorder = useCallback(async () => {
    const pending = pendingEpicReorderRef.current
    if (pending) {
      pendingEpicReorderRef.current = null
      playDropSound()
      await onReorder(pending.key, pending.newIndex)
    }
  }, [onReorder])

  // Handle story reorder within an epic
  const handleStoryReorder = useCallback(async (parentKey: string, newStoryOrder: BoardNode[]) => {
    // Find what changed
    const parent = localItems.find(e => e.issueKey === parentKey)
    if (!parent) return

    const oldStories = parent.children.filter(c =>
      c.issueType === 'Story' || c.issueType === 'История' ||
      c.issueType === 'Bug' || c.issueType === 'Баг'
    )
    const oldOrder = oldStories.map(s => s.issueKey)
    const newOrderKeys = newStoryOrder.map(s => s.issueKey)

    let movedKey: string | null = null
    let newIndex = -1
    for (let i = 0; i < newOrderKeys.length; i++) {
      if (newOrderKeys[i] !== oldOrder[i]) {
        const keyAtNewPos = newOrderKeys[i]
        const oldPos = oldOrder.indexOf(keyAtNewPos)
        if (oldPos !== i) {
          movedKey = keyAtNewPos
          newIndex = i
          break
        }
      }
    }

    if (movedKey && newIndex >= 0) {
      // Update local state
      setLocalItems(prev => prev.map(epic => {
        if (epic.issueKey !== parentKey) return epic
        const subtasks = epic.children.filter(c =>
          c.issueType !== 'Story' && c.issueType !== 'История' &&
          c.issueType !== 'Bug' && c.issueType !== 'Баг'
        )
        return { ...epic, children: [...newStoryOrder, ...subtasks] }
      }))

      // Store pending reorder - will be committed on drag end
      pendingStoryReorderRef.current = { key: movedKey, parentKey, newIndex }
    }
  }, [localItems])

  // Commit story reorder to API when drag ends
  const commitStoryReorder = useCallback(async () => {
    const pending = pendingStoryReorderRef.current
    if (pending) {
      pendingStoryReorderRef.current = null
      playDropSound()
      await onStoryReorder(pending.key, pending.parentKey, pending.newIndex)
    }
  }, [onStoryReorder])

  const [showInfoTooltip, setShowInfoTooltip] = useState(false)

  // Calculate recommended positions based on autoScore (descending)
  const epicRecommendations = useMemo(() => {
    if (!canReorder) {
      return new Map<string, number>()
    }

    const sorted = [...localItems]
      .filter(e => e.autoScore !== null)
      .sort((a, b) => (b.autoScore || 0) - (a.autoScore || 0))

    const recommendations = new Map<string, number>()
    sorted.forEach((epic, idx) => {
      recommendations.set(epic.issueKey, idx + 1)
    })
    return recommendations
  }, [localItems, canReorder])

  // Calculate recommended positions for stories within each epic
  const getStoryRecommendations = useCallback((children: BoardNode[]): Map<string, number> => {
    const stories = children.filter(c =>
      c.issueType === 'Story' || c.issueType === 'История' ||
      c.issueType === 'Bug' || c.issueType === 'Баг'
    )
    const sorted = [...stories]
      .filter(s => s.autoScore !== null)
      .sort((a, b) => (b.autoScore || 0) - (a.autoScore || 0))

    const recommendations = new Map<string, number>()
    sorted.forEach((story, idx) => {
      recommendations.set(story.issueKey, idx + 1)
    })
    return recommendations
  }, [])

  // Render children (stories/subtasks)
  const renderChildren = (children: BoardNode[], parentKey: string, level: number, isExpanded: boolean): JSX.Element => {
    const storyRecommendations = level === 1 ? getStoryRecommendations(children) : new Map<string, number>()

    const stories = children.filter(c =>
      c.issueType === 'Story' || c.issueType === 'История' ||
      c.issueType === 'Bug' || c.issueType === 'Баг'
    )
    const subtasks = children.filter(c =>
      c.issueType !== 'Story' && c.issueType !== 'История' &&
      c.issueType !== 'Bug' && c.issueType !== 'Баг'
    )

    return (
      <div className={`children-wrapper ${isExpanded ? 'expanded' : ''}`}>
        <div className="children-container">
          {level === 1 && canReorder && stories.length > 0 ? (
            <Reorder.Group
              axis="y"
              values={stories}
              onReorder={(newOrder) => handleStoryReorder(parentKey, newOrder)}
              style={{ listStyle: 'none', padding: 0, margin: 0 }}
            >
              {stories.map((story, storyIndex) => {
                const storyIsExpanded = expandedKeys.has(story.issueKey)
                const storyHasChildren = story.children.length > 0
                const storyForecast = forecastMap.get(story.issueKey) || null
                const actualPosition = storyIndex + 1
                const recommendedPosition = storyRecommendations.get(story.issueKey)

                return (
                  <DraggableStoryRow
                    key={story.issueKey}
                    story={story}
                    isExpanded={storyIsExpanded}
                    onToggle={() => toggleExpand(story.issueKey)}
                    hasChildren={storyHasChildren}
                    roughEstimateConfig={roughEstimateConfig}
                    onRoughEstimateUpdate={onRoughEstimateUpdate}
                    forecast={storyForecast}
                    canReorder={canReorder}
                    actualPosition={actualPosition}
                    recommendedPosition={recommendedPosition}
                    onDragEnd={commitStoryReorder}
                    storyPlanning={storyPlanningMap.get(story.issueKey)}
                  >
                    {storyHasChildren && renderChildren(story.children, story.issueKey, level + 1, storyIsExpanded)}
                  </DraggableStoryRow>
                )
              })}
            </Reorder.Group>
          ) : (
            stories.map((story, storyIndex) => {
              const storyIsExpanded = expandedKeys.has(story.issueKey)
              const storyHasChildren = story.children.length > 0
              const storyForecast = forecastMap.get(story.issueKey) || null
              const actualPosition = storyIndex + 1
              const recommendedPosition = storyRecommendations.get(story.issueKey)

              return (
                <Fragment key={story.issueKey}>
                  <BoardRow
                    node={story}
                    level={1}
                    expanded={storyIsExpanded}
                    onToggle={() => toggleExpand(story.issueKey)}
                    hasChildren={storyHasChildren}
                    roughEstimateConfig={roughEstimateConfig}
                    onRoughEstimateUpdate={onRoughEstimateUpdate}
                    forecast={storyForecast}
                    canReorder={false}
                    isJustDropped={false}
                    actualPosition={actualPosition}
                    recommendedPosition={recommendedPosition}
                    storyPlanning={storyPlanningMap.get(story.issueKey)}
                  />
                  {storyHasChildren && renderChildren(story.children, story.issueKey, level + 1, storyIsExpanded)}
                </Fragment>
              )
            })
          )}
          {/* Subtasks (level 2) are not reorderable */}
          {subtasks.map((subtask) => {
            const subtaskIsExpanded = expandedKeys.has(subtask.issueKey)
            const subtaskHasChildren = subtask.children.length > 0
            const subtaskForecast = forecastMap.get(subtask.issueKey) || null

            return (
              <Fragment key={subtask.issueKey}>
                <BoardRow
                  node={subtask}
                  level={level}
                  expanded={subtaskIsExpanded}
                  onToggle={() => toggleExpand(subtask.issueKey)}
                  hasChildren={subtaskHasChildren}
                  roughEstimateConfig={roughEstimateConfig}
                  onRoughEstimateUpdate={onRoughEstimateUpdate}
                  forecast={subtaskForecast}
                  canReorder={false}
                  isJustDropped={false}
                />
                {subtaskHasChildren && renderChildren(subtask.children, subtask.issueKey, level + 1, subtaskIsExpanded)}
              </Fragment>
            )
          })}
        </div>
      </div>
    )
  }

  return (
    <div className="board-table-container">
      <div className="board-grid">
        <div className="board-header">
          <div className="cell th-expander"></div>
          <div className="cell th-name">NAME</div>
          <div className="cell th-team">TEAM</div>
          <div className="cell th-priority">PRIORITY</div>
          <div className="cell th-expected-done">
            <span className="th-with-info">
              EXPECTED DONE
              <span
                className="info-icon"
                onMouseEnter={() => setShowInfoTooltip(true)}
                onMouseLeave={() => setShowInfoTooltip(false)}
              >
                i
                {showInfoTooltip && (
                  <div className="info-tooltip">
                    <div className="info-tooltip-title">Прогноз завершения</div>
                    <p>Дата рассчитывается на основе:</p>
                    <ul>
                      <li>Остатка работы по ролям (SA → DEV → QA)</li>
                      <li>Производительности команды</li>
                      <li>Производственного календаря</li>
                    </ul>
                    <div className="info-tooltip-section">
                      <strong>Уверенность:</strong>
                      <div className="confidence-legend">
                        <span><span className="confidence-dot high"></span> Высокая — есть оценки</span>
                        <span><span className="confidence-dot medium"></span> Средняя — частичные оценки</span>
                        <span><span className="confidence-dot low"></span> Низкая — нет оценок</span>
                      </div>
                    </div>
                    <div className="info-tooltip-section">
                      <strong>Порядок эпиков:</strong>
                      <p>Перетащите эпик для изменения приоритета. Стрелки показывают рекомендации AutoScore.</p>
                    </div>
                  </div>
                )}
              </span>
            </span>
          </div>
          <div className="cell th-progress">PROGRESS</div>
          <div className="cell th-roles">ROLE-BASED PROGRESS</div>
          <div className="cell th-status">STATUS</div>
          <div className="cell th-alerts">ALERTS</div>
        </div>
        <div className="board-body">
          {canReorder ? (
            <Reorder.Group
              axis="y"
              values={localItems}
              onReorder={handleReorder}
              style={{ listStyle: 'none', padding: 0, margin: 0 }}
            >
              {localItems.map((epic, epicIndex) => {
                const isExpanded = expandedKeys.has(epic.issueKey)
                const hasChildren = epic.children.length > 0
                const forecast = forecastMap.get(epic.issueKey) || null
                const actualPosition = epicIndex + 1
                const recommendedPosition = epicRecommendations.get(epic.issueKey)

                return (
                  <DraggableEpicRow
                    key={epic.issueKey}
                    epic={epic}
                    isExpanded={isExpanded}
                    onToggle={() => toggleExpand(epic.issueKey)}
                    hasChildren={hasChildren}
                    roughEstimateConfig={roughEstimateConfig}
                    onRoughEstimateUpdate={onRoughEstimateUpdate}
                    forecast={forecast}
                    canReorder={canReorder}
                    actualPosition={actualPosition}
                    recommendedPosition={recommendedPosition}
                    onDragEnd={commitEpicReorder}
                  >
                    {hasChildren && renderChildren(epic.children, epic.issueKey, 1, isExpanded)}
                  </DraggableEpicRow>
                )
              })}
            </Reorder.Group>
          ) : (
            localItems.map((epic, epicIndex) => {
              const isExpanded = expandedKeys.has(epic.issueKey)
              const hasChildren = epic.children.length > 0
              const forecast = forecastMap.get(epic.issueKey) || null
              const actualPosition = epicIndex + 1
              const recommendedPosition = epicRecommendations.get(epic.issueKey)

              return (
                <Fragment key={epic.issueKey}>
                  <BoardRow
                    node={epic}
                    level={0}
                    expanded={isExpanded}
                    onToggle={() => toggleExpand(epic.issueKey)}
                    hasChildren={hasChildren}
                    roughEstimateConfig={roughEstimateConfig}
                    onRoughEstimateUpdate={onRoughEstimateUpdate}
                    forecast={forecast}
                    canReorder={false}
                    isJustDropped={false}
                    actualPosition={actualPosition}
                    recommendedPosition={recommendedPosition}
                  />
                  {hasChildren && renderChildren(epic.children, epic.issueKey, 1, isExpanded)}
                </Fragment>
              )
            })
          )}
        </div>
      </div>
    </div>
  )
}

interface FilterPanelProps {
  searchKey: string
  onSearchKeyChange: (value: string) => void
  availableStatuses: string[]
  selectedStatuses: Set<string>
  onStatusToggle: (status: string) => void
  availableTeams: string[]
  selectedTeams: Set<string>
  onTeamToggle: (team: string) => void
  onClearFilters: () => void
  syncStatus: SyncStatus | null
  syncing: boolean
  onSync: () => void
}

function FilterPanel({
  searchKey,
  onSearchKeyChange,
  availableStatuses,
  selectedStatuses,
  onStatusToggle,
  availableTeams,
  selectedTeams,
  onTeamToggle,
  onClearFilters,
  syncStatus,
  syncing,
  onSync,
}: FilterPanelProps) {
  const hasActiveFilters = searchKey || selectedStatuses.size > 0 || selectedTeams.size > 0

  const formatSyncTime = (isoString: string | null): string => {
    if (!isoString) return 'Never'
    const date = new Date(isoString)
    return date.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  return (
    <div className="filter-panel">
      <div className="filter-group filter-search">
        <svg
          className="search-icon"
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="none"
        >
          <path
            d="M7 12C9.76142 12 12 9.76142 12 7C12 4.23858 9.76142 2 7 2C4.23858 2 2 4.23858 2 7C2 9.76142 4.23858 12 7 12Z"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M14 14L10.5 10.5"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <input
          type="text"
          placeholder="Search by key..."
          value={searchKey}
          onChange={(e) => onSearchKeyChange(e.target.value)}
          className="filter-input"
        />
      </div>

      <MultiSelectDropdown
        label="Team"
        options={availableTeams}
        selected={selectedTeams}
        onToggle={onTeamToggle}
        placeholder="All teams"
      />

      <MultiSelectDropdown
        label="Status"
        options={availableStatuses}
        selected={selectedStatuses}
        onToggle={onStatusToggle}
        placeholder="All statuses"
      />

      {hasActiveFilters && (
        <button className="btn btn-secondary btn-clear" onClick={onClearFilters}>
          Clear
        </button>
      )}

      <div className="sync-status filter-group-right">
        {syncStatus && (
          <span className="sync-info">
            Last sync: {formatSyncTime(syncStatus.lastSyncCompletedAt)}
          </span>
        )}
        <button
          className={`btn btn-primary btn-refresh ${syncing ? 'syncing' : ''}`}
          onClick={onSync}
          disabled={syncing}
        >
          {syncing ? 'Syncing...' : 'Refresh'}
        </button>
      </div>
    </div>
  )
}

export function BoardPage() {
  const [board, setBoard] = useState<BoardNode[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [roughEstimateConfig, setRoughEstimateConfig] = useState<RoughEstimateConfig | null>(null)

  const [searchKey, setSearchKey] = useState('')
  const [selectedStatuses, setSelectedStatuses] = useState<Set<string>>(new Set())
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(new Set())

  const fetchBoard = useCallback(async () => {
    setLoading(true)
    try {
      const response = await axios.get<BoardResponse>('/api/board')
      setBoard(response.data.items)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load board')
    } finally {
      setLoading(false)
    }
  }, [])

  const fetchRoughEstimateConfig = useCallback(() => {
    getRoughEstimateConfig()
      .then(setRoughEstimateConfig)
      .catch(() => {})
  }, [])

  const fetchSyncStatus = useCallback(() => {
    axios.get<SyncStatus>('/api/sync/status')
      .then(response => {
        setSyncStatus(response.data)
        setSyncing(response.data.syncInProgress)
      })
      .catch(() => {})
  }, [])

  const triggerSync = () => {
    setSyncing(true)
    axios.post<SyncStatus>('/api/sync/trigger')
      .then(() => {
        const pollInterval = setInterval(() => {
          axios.get<SyncStatus>('/api/sync/status')
            .then(response => {
              setSyncStatus(response.data)
              if (!response.data.syncInProgress) {
                setSyncing(false)
                clearInterval(pollInterval)
                fetchBoard()
              }
            })
        }, 2000)
      })
      .catch(err => {
        setSyncing(false)
        alert('Sync failed: ' + err.message)
      })
  }

  useEffect(() => {
    fetchBoard()
    fetchSyncStatus()
    fetchRoughEstimateConfig()
  }, [fetchBoard, fetchSyncStatus, fetchRoughEstimateConfig])

  // Get all unique team IDs from board
  const allTeamIds = useMemo(() => {
    const ids = new Set<number>()
    board.forEach(epic => {
      if (epic.teamId) ids.add(epic.teamId)
    })
    return Array.from(ids)
  }, [board])

  // Load forecasts for all teams
  const [allForecasts, setAllForecasts] = useState<Map<number, ForecastResponse>>(new Map())

  // Story planning data from unified planning (storyKey -> PlannedStory)
  const [storyPlanningMap, setStoryPlanningMap] = useState<Map<string, PlannedStory>>(new Map())

  const loadForecasts = useCallback(() => {
    if (allTeamIds.length === 0) return

    Promise.all(
      allTeamIds.map(teamId =>
        getForecast(teamId)
          .then(data => ({ teamId, data }))
          .catch(() => null)
      )
    ).then(results => {
      const newForecasts = new Map<number, ForecastResponse>()
      results.forEach(result => {
        if (result) {
          newForecasts.set(result.teamId, result.data)
        }
      })
      setAllForecasts(newForecasts)
    })
  }, [allTeamIds])

  useEffect(() => {
    loadForecasts()
  }, [loadForecasts])

  // Load story planning data for tooltips
  const loadStoryPlanning = useCallback(() => {
    if (allTeamIds.length === 0) return

    Promise.all(
      allTeamIds.map(teamId =>
        getUnifiedPlanning(teamId)
          .then(data => ({ teamId, data }))
          .catch(() => null)
      )
    ).then(results => {
      const newMap = new Map<string, PlannedStory>()
      results.forEach(result => {
        if (result) {
          result.data.epics.forEach(epic => {
            epic.stories.forEach(story => {
              newMap.set(story.storyKey, story)
            })
          })
        }
      })
      setStoryPlanningMap(newMap)
    })
  }, [allTeamIds])

  useEffect(() => {
    loadStoryPlanning()
  }, [loadStoryPlanning])

  // Reload story expected done dates and order from unified planning
  const reloadStoryExpectedDone = useCallback(async (teamId: number) => {
    try {
      const planning = await getUnifiedPlanning(teamId)
      // Build maps: storyKey -> endDate, storyKey -> PlannedStory, epicKey -> story order
      const storyEndDates = new Map<string, string | null>()
      const epicStoryOrder = new Map<string, string[]>()
      const newStoryPlanningMap = new Map<string, PlannedStory>()
      planning.epics.forEach(epic => {
        const storyKeys = epic.stories.map(story => {
          storyEndDates.set(story.storyKey, story.endDate)
          newStoryPlanningMap.set(story.storyKey, story)
          return story.storyKey
        })
        epicStoryOrder.set(epic.epicKey, storyKeys)
      })
      // Update story planning map
      setStoryPlanningMap(newStoryPlanningMap)
      // Update board state with new expectedDone and correct story order
      setBoard(prevBoard => prevBoard.map(epic => {
        const storyOrder = epicStoryOrder.get(epic.issueKey)
        if (!storyOrder) return epic

        // Separate stories and subtasks
        const stories = epic.children.filter(c =>
          c.issueType === 'Story' || c.issueType === 'История' ||
          c.issueType === 'Bug' || c.issueType === 'Баг'
        )
        const subtasks = epic.children.filter(c =>
          c.issueType !== 'Story' && c.issueType !== 'История' &&
          c.issueType !== 'Bug' && c.issueType !== 'Баг'
        )

        // Sort stories by the order from unified planning
        const storyMap = new Map(stories.map(s => [s.issueKey, s]))
        const orderedStories = storyOrder
          .map(key => storyMap.get(key))
          .filter((s): s is BoardNode => s !== undefined)
          .map(story => {
            const newEndDate = storyEndDates.get(story.issueKey)
            if (newEndDate !== undefined) {
              return { ...story, expectedDone: newEndDate }
            }
            return story
          })

        // Add any stories not in the order (shouldn't happen, but safety)
        const orderedKeys = new Set(storyOrder)
        const remainingStories = stories
          .filter(s => !orderedKeys.has(s.issueKey))
          .map(story => {
            const newEndDate = storyEndDates.get(story.issueKey)
            if (newEndDate !== undefined) {
              return { ...story, expectedDone: newEndDate }
            }
            return story
          })

        return { ...epic, children: [...orderedStories, ...remainingStories, ...subtasks] }
      }))
    } catch (err) {
      console.error('Failed to reload story expected done:', err)
    }
  }, [])

  // Create forecast map for quick lookup (merged from all teams)
  const forecastMap = useMemo(() => {
    const map = new Map<string, EpicForecast>()
    allForecasts.forEach(forecast => {
      forecast.epics.forEach(f => map.set(f.epicKey, f))
    })
    return map
  }, [allForecasts])

  // Get selected team ID for forecast loading
  const selectedTeamId = useMemo(() => {
    if (selectedTeams.size !== 1) return null
    const teamName = Array.from(selectedTeams)[0]
    const epic = board.find(e => e.teamName === teamName)
    return epic?.teamId || null
  }, [selectedTeams, board])

  // Drag & drop is enabled when exactly one team is selected
  // This ensures we reorder within the same team context
  const canReorder = selectedTeamId !== null

  const handleRoughEstimateUpdate = useCallback(async (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => {
    await updateRoughEstimate(epicKey, role, { days })
    // Refetch board to get updated data
    await fetchBoard()
  }, [fetchBoard])

  // Handle reorder via drag & drop - simple position-based API
  // Note: No fetchBoard() - local state is already updated. Reload forecasts and story dates.
  const handleReorder = useCallback(async (epicKey: string, targetIndex: number) => {
    const newPosition = targetIndex + 1
    try {
      await updateEpicOrder(epicKey, newPosition)
      // Reload forecasts (for epic expected done) and story dates
      loadForecasts()
      if (selectedTeamId) {
        await reloadStoryExpectedDone(selectedTeamId)
      }
    } catch (err) {
      console.error('Failed to reorder epic:', err)
    }
  }, [loadForecasts, selectedTeamId, reloadStoryExpectedDone])

  // Handle story reorder via drag & drop - simple position-based API
  // Note: No fetchBoard() - local state is already updated. Reload story dates from unified planning.
  const handleStoryReorder = useCallback(async (storyKey: string, _parentEpicKey: string, newIndex: number) => {
    const newPosition = newIndex + 1
    try {
      await updateStoryOrder(storyKey, newPosition)
      // Reload forecasts (for epic expected done) and story dates
      loadForecasts()
      if (selectedTeamId) {
        await reloadStoryExpectedDone(selectedTeamId)
      }
    } catch (err) {
      console.error('Failed to reorder story:', err)
    }
  }, [loadForecasts, selectedTeamId, reloadStoryExpectedDone])

  const availableStatuses = useMemo(() => {
    const statuses = new Set<string>()
    board.forEach(epic => statuses.add(epic.status))
    return Array.from(statuses).sort()
  }, [board])

  const availableTeams = useMemo(() => {
    const teams = new Set<string>()
    board.forEach(epic => {
      if (epic.teamName) {
        teams.add(epic.teamName)
      }
    })
    return Array.from(teams).sort()
  }, [board])

  const filteredBoard = useMemo(() => {
    // Board is already sorted by manual_order from backend
    return board.filter(epic => {
      if (searchKey) {
        const keyLower = searchKey.toLowerCase()
        if (!epic.issueKey.toLowerCase().includes(keyLower)) {
          return false
        }
      }
      if (selectedStatuses.size > 0 && !selectedStatuses.has(epic.status)) {
        return false
      }
      if (selectedTeams.size > 0 && (!epic.teamName || !selectedTeams.has(epic.teamName))) {
        return false
      }
      return true
    })
  }, [board, searchKey, selectedStatuses, selectedTeams])

  const handleStatusToggle = (status: string) => {
    setSelectedStatuses(prev => {
      const next = new Set(prev)
      if (next.has(status)) {
        next.delete(status)
      } else {
        next.add(status)
      }
      return next
    })
  }

  const handleTeamToggle = (team: string) => {
    setSelectedTeams(prev => {
      const next = new Set(prev)
      if (next.has(team)) {
        next.delete(team)
      } else {
        next.add(team)
      }
      return next
    })
  }

  const clearFilters = () => {
    setSearchKey('')
    setSelectedStatuses(new Set())
    setSelectedTeams(new Set())
  }

  return (
    <>
      <FilterPanel
        searchKey={searchKey}
        onSearchKeyChange={setSearchKey}
        availableStatuses={availableStatuses}
        selectedStatuses={selectedStatuses}
        onStatusToggle={handleStatusToggle}
        availableTeams={availableTeams}
        selectedTeams={selectedTeams}
        onTeamToggle={handleTeamToggle}
        onClearFilters={clearFilters}
        syncStatus={syncStatus}
        syncing={syncing}
        onSync={triggerSync}
      />

      <main className="main-content">
        {loading && <div className="loading">Loading board...</div>}
        {error && <div className="error">Error: {error}</div>}
        {!loading && !error && filteredBoard.length === 0 && (
          <div className="empty">No epics found</div>
        )}
        {!loading && !error && filteredBoard.length > 0 && (
          <BoardTable
            items={filteredBoard}
            roughEstimateConfig={roughEstimateConfig}
            onRoughEstimateUpdate={handleRoughEstimateUpdate}
            forecastMap={forecastMap}
            storyPlanningMap={storyPlanningMap}
            canReorder={canReorder}
            onReorder={handleReorder}
            onStoryReorder={handleStoryReorder}
          />
        )}
      </main>
    </>
  )
}
