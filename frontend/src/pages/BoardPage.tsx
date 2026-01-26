import { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import axios from 'axios'
import { getRoughEstimateConfig, updateRoughEstimate, RoughEstimateConfig } from '../api/epics'
import { getForecast, EpicForecast, ForecastResponse, updateManualBoost } from '../api/forecast'
import { updateStoryPriority } from '../api/stories'
import { getScoreBreakdown, ScoreBreakdown } from '../api/board'
import { MultiSelectDropdown } from '../components/MultiSelectDropdown'

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
  manualBoost: number | null
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

function PriorityCell({ node }: { node: BoardNode }) {
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
    manual: 'Ручная корректировка',
    // Epic factors
    statusWeight: 'Статус',
    storyCompletion: 'Завершение историй',
    dueDateWeight: 'Срок',
    manualBoost: 'Ручная корректировка'
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
            {node.manualBoost !== null && node.manualBoost !== 0 && (
              <span className="manual-boost-badge">
                {node.manualBoost > 0 ? '+' : ''}{node.manualBoost}
              </span>
            )}
          </div>

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

// Simple Expected Done cell for stories (without forecast data)
function StoryExpectedDoneCell({ endDate, assignee }: { endDate: string | null, assignee: string | null }) {
  if (!endDate) {
    return <span className="expected-done-empty">--</span>
  }

  const formatDate = (dateStr: string): string => {
    const date = new Date(dateStr)
    return date.toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' })
  }

  return (
    <div className="expected-done-cell" style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
      <span className="expected-done-date">{formatDate(endDate)}</span>
      {assignee && (
        <span style={{ fontSize: '0.75rem', color: '#666', fontStyle: 'italic' }}>
          {assignee}
        </span>
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
  index: number
  onDragStart: (e: React.DragEvent, index: number, epicKey: string) => void
  onDragOver: (e: React.DragEvent, index: number) => void
  onDrop: (e: React.DragEvent, index: number) => void
  onDragEnd: () => void
  isDragging: boolean
  isDragOver: boolean
  isJustDropped: boolean
  // Story drag & drop
  parentEpicKey?: string
  onStoryDragStart?: (e: React.DragEvent, storyKey: string, parentEpicKey: string) => void
  onStoryDragOver?: (e: React.DragEvent, storyKey: string, parentEpicKey: string) => void
  onStoryDrop?: (e: React.DragEvent, storyKey: string, parentEpicKey: string) => void
  onStoryDragEnd?: () => void
  isStoryDragging?: boolean
  isStoryDragOver?: boolean
  isStoryJustDropped?: boolean
  // Drag state for preventing accidental clicks
  isAnyDragging?: boolean
  isDragInvalid?: boolean
}

function BoardRow({ node, level, expanded, onToggle, hasChildren, roughEstimateConfig, onRoughEstimateUpdate, forecast, canReorder, index, onDragStart, onDragOver, onDrop, onDragEnd, isDragging, isDragOver, isJustDropped, parentEpicKey, onStoryDragStart, onStoryDragOver, onStoryDrop, onStoryDragEnd, isStoryDragging, isStoryDragOver, isStoryJustDropped, isAnyDragging, isDragInvalid }: BoardRowProps) {
  const isEpicRow = isEpic(node.issueType) && level === 0
  const isStoryRow = (node.issueType === 'Story' || node.issueType === 'История' || node.issueType === 'Bug' || node.issueType === 'Баг') && level === 1

  const dragEffects = isStoryRow && isStoryDragging ? 'dragging' : (isDragging ? 'dragging' : '')
  const dragOverEffects = isStoryRow && isStoryDragOver ? 'drag-over' : (isDragOver ? 'drag-over' : '')
  const dragInvalidEffects = isDragInvalid ? 'drag-over-invalid' : ''
  const justDroppedEffects = isStoryRow && isStoryJustDropped ? 'just-dropped' : (isJustDropped ? 'just-dropped' : '')

  // Prevent expander toggle during drag operations
  const handleExpanderClick = (e: React.MouseEvent) => {
    if (isAnyDragging) {
      e.preventDefault()
      e.stopPropagation()
      return
    }
    onToggle()
  }

  return (
    <tr
      className={`board-row level-${level} ${dragEffects} ${dragOverEffects} ${dragInvalidEffects} ${justDroppedEffects}`}
      draggable={(isEpicRow || isStoryRow) && canReorder}
      onDragStart={
        isEpicRow && canReorder ? (e) => onDragStart(e, index, node.issueKey) :
        isStoryRow && canReorder && onStoryDragStart && parentEpicKey ? (e) => onStoryDragStart(e, node.issueKey, parentEpicKey) :
        undefined
      }
      onDragOver={
        isEpicRow && canReorder ? (e) => onDragOver(e, index) :
        isStoryRow && canReorder && onStoryDragOver && parentEpicKey ? (e) => onStoryDragOver(e, node.issueKey, parentEpicKey) :
        undefined
      }
      onDrop={
        isEpicRow && canReorder ? (e) => onDrop(e, index) :
        isStoryRow && canReorder && onStoryDrop && parentEpicKey ? (e) => onStoryDrop(e, node.issueKey, parentEpicKey) :
        undefined
      }
      onDragEnd={
        isEpicRow && canReorder ? onDragEnd :
        isStoryRow && canReorder && onStoryDragEnd ? onStoryDragEnd :
        undefined
      }
    >
      <td className="cell-expander">
        {hasChildren ? (
          <button
            className={`expander-btn ${isAnyDragging ? 'disabled-during-drag' : ''}`}
            onClick={handleExpanderClick}
            style={isAnyDragging ? { pointerEvents: 'none' } : undefined}
          >
            <span className={`chevron ${expanded ? 'expanded' : ''}`}>›</span>
          </button>
        ) : (
          <span className="expander-placeholder" />
        )}
      </td>
      <td className="cell-name">
        <div className="name-content" style={{ paddingLeft: `${level * 20}px` }}>
          {(isEpicRow || isStoryRow) && canReorder && (
            <span className="drag-handle" title={isStoryRow ? "Drag to reorder within epic" : "Drag to reorder"}>⋮⋮</span>
          )}
          <img src={getIssueIcon(node.issueType)} alt={node.issueType} className="issue-type-icon" />
          <a href={node.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
            {node.issueKey}
          </a>
          <span className="issue-title">{node.title}</span>
        </div>
      </td>
      <td className="cell-team">{node.teamName || '--'}</td>
      <td className="cell-priority">
        {node.autoScore !== null && node.autoScore !== undefined ? (
          <PriorityCell node={node} />
        ) : (
          <span className="priority-empty">--</span>
        )}
      </td>
      <td className="cell-expected-done">
        {isEpic(node.issueType) ? (
          <ExpectedDoneCell forecast={forecast} />
        ) : (
          <StoryExpectedDoneCell endDate={node.expectedDone} assignee={node.assigneeDisplayName} />
        )}
      </td>
      <td className="cell-progress">
        <ProgressCell
          loggedSeconds={node.loggedSeconds}
          estimateSeconds={node.estimateSeconds}
          progress={node.progress}
        />
      </td>
      <td className="cell-roles">
        <RoleChips
          node={node}
          config={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
        />
      </td>
      <td className="cell-status">
        <StatusBadge status={node.status} />
      </td>
      <td className="cell-alerts">
        <AlertIcon node={node} />
      </td>
    </tr>
  )
}

interface BoardTableProps {
  items: BoardNode[]
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
  forecastMap: Map<string, EpicForecast>
  canReorder: boolean
  onReorder: (epicKey: string, newIndex: number) => Promise<void>
  onStoryReorder: (storyKey: string, parentEpicKey: string, newIndex: number) => Promise<void>
}

function BoardTable({ items, roughEstimateConfig, onRoughEstimateUpdate, forecastMap, canReorder, onReorder, onStoryReorder }: BoardTableProps) {
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
  const [draggingIndex, setDraggingIndex] = useState<number | null>(null)
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null)
  const [draggingEpicKey, setDraggingEpicKey] = useState<string | null>(null)
  const [droppedEpicKey, setDroppedEpicKey] = useState<string | null>(null)

  // Story drag & drop state
  const [draggingStoryKey, setDraggingStoryKey] = useState<string | null>(null)
  const [draggingStoryParentKey, setDraggingStoryParentKey] = useState<string | null>(null)
  const [dragOverStoryKey, setDragOverStoryKey] = useState<string | null>(null)
  const [droppedStoryKey, setDroppedStoryKey] = useState<string | null>(null)
  const [dragInvalidStoryKey, setDragInvalidStoryKey] = useState<string | null>(null)

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

  const handleDragStart = (e: React.DragEvent, index: number, epicKey: string) => {
    setDraggingIndex(index)
    setDraggingEpicKey(epicKey)
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', epicKey)
  }

  const handleDragOver = (e: React.DragEvent, index: number) => {
    e.preventDefault()
    e.dataTransfer.dropEffect = 'move'
    setDragOverIndex(index)
  }

  const handleDrop = async (e: React.DragEvent, index: number) => {
    e.preventDefault()
    if (draggingEpicKey && index !== draggingIndex) {
      const epicKey = draggingEpicKey
      await onReorder(epicKey, index)
      playDropSound()
      setDroppedEpicKey(epicKey)
      setTimeout(() => setDroppedEpicKey(null), 400)
    }
    setDraggingIndex(null)
    setDragOverIndex(null)
    setDraggingEpicKey(null)
  }

  const handleDragEnd = () => {
    setDraggingIndex(null)
    setDragOverIndex(null)
    setDraggingEpicKey(null)
  }

  // Handle drop at the end of list
  const handleDropAtEnd = async (e: React.DragEvent) => {
    e.preventDefault()
    const epicCount = items.filter(item => isEpic(item.issueType)).length
    if (draggingEpicKey && draggingIndex !== epicCount - 1) {
      const epicKey = draggingEpicKey
      await onReorder(epicKey, epicCount - 1)
      playDropSound()
      setDroppedEpicKey(epicKey)
      setTimeout(() => setDroppedEpicKey(null), 400)
    }
    setDraggingIndex(null)
    setDragOverIndex(null)
    setDraggingEpicKey(null)
  }

  // Story drag & drop handlers
  const handleStoryDragStart = (e: React.DragEvent, storyKey: string, parentEpicKey: string) => {
    e.stopPropagation()
    setDraggingStoryKey(storyKey)
    setDraggingStoryParentKey(parentEpicKey)
    e.dataTransfer.effectAllowed = 'move'
  }

  const handleStoryDragOver = (e: React.DragEvent, storyKey: string, parentEpicKey: string) => {
    e.preventDefault()
    e.stopPropagation()

    // Auto-scroll near edges
    const threshold = 100
    const scrollSpeed = 10

    if (e.clientY < threshold) {
      window.scrollBy(0, -scrollSpeed)
    } else if (e.clientY > window.innerHeight - threshold) {
      window.scrollBy(0, scrollSpeed)
    }

    // Only allow drop if dragging within the same epic
    if (draggingStoryParentKey === parentEpicKey && draggingStoryKey !== storyKey) {
      e.dataTransfer.dropEffect = 'move'
      setDragOverStoryKey(storyKey)
      setDragInvalidStoryKey(null)
    } else if (draggingStoryParentKey && draggingStoryParentKey !== parentEpicKey) {
      // Invalid drop - different epic
      e.dataTransfer.dropEffect = 'none'
      setDragOverStoryKey(null)
      setDragInvalidStoryKey(storyKey)
    }
  }

  const handleStoryDrop = async (e: React.DragEvent, storyKey: string, parentEpicKey: string) => {
    e.preventDefault()
    e.stopPropagation()

    if (draggingStoryKey && draggingStoryParentKey === parentEpicKey && draggingStoryKey !== storyKey) {
      // Find parent epic and get sorted stories
      const parentEpic = items.find(epic => epic.issueKey === parentEpicKey)
      if (parentEpic) {
        const stories = parentEpic.children.filter(child =>
          child.issueType === 'Story' || child.issueType === 'История' ||
          child.issueType === 'Bug' || child.issueType === 'Баг'
        )
        const newIndex = stories.findIndex(s => s.issueKey === storyKey)

        if (newIndex !== -1) {
          await onStoryReorder(draggingStoryKey, parentEpicKey, newIndex)
          playDropSound()
          setDroppedStoryKey(draggingStoryKey)
          setTimeout(() => setDroppedStoryKey(null), 400)
        }
      }
    }

    setDraggingStoryKey(null)
    setDraggingStoryParentKey(null)
    setDragOverStoryKey(null)
    setDragInvalidStoryKey(null)
  }

  const handleStoryDragEnd = () => {
    setDraggingStoryKey(null)
    setDraggingStoryParentKey(null)
    setDragOverStoryKey(null)
    setDragInvalidStoryKey(null)
  }

  const renderRows = (nodes: BoardNode[], level: number, startIndex: number = 0, parentEpicKey?: string): JSX.Element[] => {
    const rows: JSX.Element[] = []
    let currentIndex = startIndex

    for (const node of nodes) {
      const isExpanded = expandedKeys.has(node.issueKey)
      const hasChildren = node.children.length > 0
      const forecast = forecastMap.get(node.issueKey) || null
      const nodeIndex = currentIndex

      rows.push(
        <BoardRow
          key={node.issueKey}
          node={node}
          level={level}
          expanded={isExpanded}
          onToggle={() => toggleExpand(node.issueKey)}
          hasChildren={hasChildren}
          roughEstimateConfig={roughEstimateConfig}
          onRoughEstimateUpdate={onRoughEstimateUpdate}
          forecast={forecast}
          canReorder={canReorder}
          index={nodeIndex}
          onDragStart={handleDragStart}
          onDragOver={handleDragOver}
          onDrop={handleDrop}
          onDragEnd={handleDragEnd}
          isDragging={draggingIndex === nodeIndex && level === 0}
          isDragOver={dragOverIndex === nodeIndex && level === 0}
          isJustDropped={droppedEpicKey === node.issueKey}
          parentEpicKey={parentEpicKey}
          onStoryDragStart={handleStoryDragStart}
          onStoryDragOver={handleStoryDragOver}
          onStoryDrop={handleStoryDrop}
          onStoryDragEnd={handleStoryDragEnd}
          isStoryDragging={draggingStoryKey === node.issueKey}
          isStoryDragOver={dragOverStoryKey === node.issueKey}
          isStoryJustDropped={droppedStoryKey === node.issueKey}
          isAnyDragging={draggingEpicKey !== null || draggingStoryKey !== null}
          isDragInvalid={dragInvalidStoryKey === node.issueKey}
        />
      )

      if (level === 0) {
        currentIndex++
      }

      if (isExpanded && hasChildren) {
        rows.push(...renderRows(node.children, level + 1, currentIndex, level === 0 ? node.issueKey : parentEpicKey))
      }
    }

    return rows
  }

  const [showInfoTooltip, setShowInfoTooltip] = useState(false)

  return (
    <div className="board-table-container">
      <table className="board-table">
        <thead>
          <tr>
            <th className="th-expander"></th>
            <th className="th-name">NAME</th>
            <th className="th-team">TEAM</th>
            <th className="th-priority">PRIORITY</th>
            <th className="th-expected-done">
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
                        <p>Эпики отсортированы по AutoScore. Перетащите эпик для изменения приоритета.</p>
                      </div>
                    </div>
                  )}
                </span>
              </span>
            </th>
            <th className="th-progress">PROGRESS</th>
            <th className="th-roles">ROLE-BASED PROGRESS</th>
            <th className="th-status">STATUS</th>
            <th className="th-alerts">ALERTS</th>
          </tr>
        </thead>
        <tbody>
          {renderRows(items, 0)}
          {canReorder && draggingIndex !== null && (
            <tr
              className={`board-row drop-zone-row ${dragOverIndex === items.length ? 'drag-over' : ''}`}
              onDragOver={(e) => { e.preventDefault(); setDragOverIndex(items.length) }}
              onDrop={handleDropAtEnd}
            >
              <td colSpan={8} className="drop-zone-cell">
                <span>Переместить в конец списка</span>
              </td>
            </tr>
          )}
        </tbody>
      </table>
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

  useEffect(() => {
    if (allTeamIds.length === 0) return

    // Load forecasts for all teams in parallel
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

  // Create forecast map for quick lookup (merged from all teams)
  const forecastMap = useMemo(() => {
    const map = new Map<string, EpicForecast>()
    allForecasts.forEach(forecast => {
      forecast.epics.forEach(f => map.set(f.epicKey, f))
    })
    return map
  }, [allForecasts])

  // Get selected team ID for reorder operations
  const selectedTeamId = useMemo(() => {
    if (selectedTeams.size !== 1) return null
    const teamName = Array.from(selectedTeams)[0]
    const epic = board.find(e => e.teamName === teamName)
    return epic?.teamId || null
  }, [selectedTeams, board])

  // For drag & drop, we need exactly one team selected
  const canReorder = selectedTeamId !== null

  const handleRoughEstimateUpdate = useCallback(async (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => {
    await updateRoughEstimate(epicKey, role, { days })
    // Refetch board to get updated data
    await fetchBoard()
  }, [fetchBoard])

  // Handle reorder via drag & drop
  const handleReorder = useCallback(async (epicKey: string, newIndex: number) => {
    if (!selectedTeamId) return

    const teamForecast = allForecasts.get(selectedTeamId)
    if (!teamForecast) return

    // Get epics sorted by autoScore (descending)
    const sortedEpics = [...teamForecast.epics].sort((a, b) => b.autoScore - a.autoScore)
    const currentIndex = sortedEpics.findIndex(e => e.epicKey === epicKey)

    if (currentIndex === -1 || currentIndex === newIndex) return

    const currentEpic = sortedEpics[currentIndex]
    const currentBaseScore = currentEpic.autoScore - (currentEpic.manualPriorityBoost || 0)

    let newBoost = 0

    if (newIndex < currentIndex) {
      // Moving UP - need to beat the epic at newIndex
      const targetEpic = sortedEpics[newIndex]
      const targetScore = targetEpic.autoScore
      // Need: baseScore + newBoost > targetScore
      newBoost = Math.ceil(targetScore - currentBaseScore + 0.5)
    } else {
      // Moving DOWN - need to be below the epic at newIndex
      const targetEpic = sortedEpics[newIndex]
      const targetScore = targetEpic.autoScore

      // Need: baseScore + newBoost < targetScore
      // If there's an epic below the target, we need to be above it
      const epicBelow = sortedEpics[newIndex + 1]
      const scoreBelow = epicBelow ? epicBelow.autoScore : 0

      // Target: be between targetScore and scoreBelow
      const targetMiddle = (targetScore + scoreBelow) / 2
      newBoost = Math.floor(targetMiddle - currentBaseScore)
    }

    // No clamping - boost can be any value needed to achieve the desired position

    try {
      await updateManualBoost(epicKey, newBoost)
      // Refetch forecast for this team
      const data = await getForecast(selectedTeamId)
      setAllForecasts(prev => {
        const newMap = new Map(prev)
        newMap.set(selectedTeamId, data)
        return newMap
      })
    } catch (err) {
      console.error('Failed to reorder:', err)
    }
  }, [allForecasts, selectedTeamId])

  // Handle story reorder via drag & drop
  const handleStoryReorder = useCallback(async (storyKey: string, parentEpicKey: string, newIndex: number) => {
    // Find parent epic in board
    const parentEpic = board.find(epic => epic.issueKey === parentEpicKey)
    if (!parentEpic) return

    // Get stories (including bugs) sorted by autoScore (descending)
    const stories = parentEpic.children
      .filter(child =>
        child.issueType === 'Story' || child.issueType === 'История' ||
        child.issueType === 'Bug' || child.issueType === 'Баг'
      )
      .filter(s => s.autoScore !== null)
      .sort((a, b) => (b.autoScore || 0) - (a.autoScore || 0))

    const currentIndex = stories.findIndex(s => s.issueKey === storyKey)
    if (currentIndex === -1 || currentIndex === newIndex) return

    const currentStory = stories[currentIndex]
    const currentBaseScore = (currentStory.autoScore || 0) - (currentStory.manualBoost || 0)

    let newBoost = 0

    if (newIndex < currentIndex) {
      // Moving UP - need to beat the story at newIndex
      // IMPORTANT: Compare BASE scores only (exclude manualBoost) to prevent accumulation
      const targetStory = stories[newIndex]
      const targetBaseScore = (targetStory.autoScore || 0) - (targetStory.manualBoost || 0)

      // We need: currentBaseScore + newBoost > targetBaseScore
      // newBoost > targetBaseScore - currentBaseScore
      newBoost = Math.max(0, Math.ceil(targetBaseScore - currentBaseScore + 0.5))
    } else {
      // Moving DOWN - need to be below the story at newIndex
      // IMPORTANT: Compare BASE scores only (exclude manualBoost) to prevent accumulation
      const targetStory = stories[newIndex]
      const targetBaseScore = (targetStory.autoScore || 0) - (targetStory.manualBoost || 0)

      // If there's a story below the target, we need to be above it
      const storyBelow = stories[newIndex + 1]
      const storyBelowBaseScore = storyBelow
        ? ((storyBelow.autoScore || 0) - (storyBelow.manualBoost || 0))
        : 0

      // Target: be between targetBaseScore and storyBelowBaseScore
      const targetMiddle = (targetBaseScore + storyBelowBaseScore) / 2
      newBoost = Math.floor(targetMiddle - currentBaseScore)
    }

    try {
      await updateStoryPriority(storyKey, newBoost)
      // Refetch board to get updated data
      await fetchBoard()
    } catch (err) {
      console.error('Failed to reorder story:', err)
    }
  }, [board, fetchBoard])

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
    let filtered = board.filter(epic => {
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

    // Sort by AutoScore (descending) when forecasts are available
    if (forecastMap.size > 0) {
      filtered = filtered.sort((a, b) => {
        const forecastA = forecastMap.get(a.issueKey)
        const forecastB = forecastMap.get(b.issueKey)
        const scoreA = forecastA?.autoScore ?? 0
        const scoreB = forecastB?.autoScore ?? 0
        return scoreB - scoreA
      })
    }

    return filtered
  }, [board, searchKey, selectedStatuses, selectedTeams, forecastMap])

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
            canReorder={canReorder}
            onReorder={handleReorder}
            onStoryReorder={handleStoryReorder}
          />
        )}
      </main>
    </>
  )
}
