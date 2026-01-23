import { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import axios from 'axios'
import { getRoughEstimateConfig, updateRoughEstimate, RoughEstimateConfig } from '../api/epics'

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

function formatDays(seconds: number | null): string {
  if (!seconds) return '0 d'
  const days = seconds / 3600 / 8
  return `${days.toFixed(days % 1 === 0 ? 0 : 1)} d`
}

function ProgressBar({ progress }: { progress: number }) {
  return (
    <div className="progress-cell">
      <div className="progress-bar">
        <div
          className={`progress-fill ${progress >= 100 ? 'complete' : ''}`}
          style={{ width: `${Math.min(progress, 100)}%` }}
        />
      </div>
      <span className="progress-percent">{progress.toFixed(2)}%</span>
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

function StatusBadge({ status }: { status: string }) {
  const statusClass = status.toLowerCase().replace(/\s+/g, '-')
  return <span className={`status-badge ${statusClass}`}>{status}</span>
}

function AlertIcon({ node }: { node: BoardNode }) {
  const hasAlert =
    (node.loggedSeconds && node.loggedSeconds > 0 && (!node.estimateSeconds || node.estimateSeconds === 0)) ||
    (node.estimateSeconds && node.loggedSeconds && node.loggedSeconds > node.estimateSeconds)

  if (hasAlert) {
    return <span className="alert-icon" title="Data quality issue">!</span>
  }
  return <span className="no-alert">--</span>
}

interface BoardRowProps {
  node: BoardNode
  level: number
  expanded: boolean
  onToggle: () => void
  hasChildren: boolean
  roughEstimateConfig: RoughEstimateConfig | null
  onRoughEstimateUpdate: (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => Promise<void>
}

function BoardRow({ node, level, expanded, onToggle, hasChildren, roughEstimateConfig, onRoughEstimateUpdate }: BoardRowProps) {
  return (
    <tr className={`board-row level-${level}`}>
      <td className="cell-expander">
        {hasChildren ? (
          <button className="expander-btn" onClick={onToggle}>
            <span className={`chevron ${expanded ? 'expanded' : ''}`}>›</span>
          </button>
        ) : (
          <span className="expander-placeholder" />
        )}
      </td>
      <td className="cell-name">
        <div className="name-content" style={{ paddingLeft: `${level * 20}px` }}>
          <img src={getIssueIcon(node.issueType)} alt={node.issueType} className="issue-type-icon" />
          <a href={node.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
            {node.issueKey}
          </a>
          <span className="issue-title">{node.title}</span>
        </div>
      </td>
      <td className="cell-team">{node.teamName || '--'}</td>
      <td className="cell-logged">{formatDays(node.loggedSeconds)}</td>
      <td className="cell-estimate">{formatDays(node.estimateSeconds)}</td>
      <td className="cell-progress">
        <ProgressBar progress={node.progress || 0} />
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
}

function BoardTable({ items, roughEstimateConfig, onRoughEstimateUpdate }: BoardTableProps) {
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set())

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

  const renderRows = (nodes: BoardNode[], level: number): JSX.Element[] => {
    const rows: JSX.Element[] = []

    for (const node of nodes) {
      const isExpanded = expandedKeys.has(node.issueKey)
      const hasChildren = node.children.length > 0

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
        />
      )

      if (isExpanded && hasChildren) {
        rows.push(...renderRows(node.children, level + 1))
      }
    }

    return rows
  }

  return (
    <div className="board-table-container">
      <table className="board-table">
        <thead>
          <tr>
            <th className="th-expander"></th>
            <th className="th-name">NAME</th>
            <th className="th-team">TEAM</th>
            <th className="th-logged">LOGGED TIME</th>
            <th className="th-estimate">ESTIMATE</th>
            <th className="th-progress">OVERALL PROGRESS</th>
            <th className="th-roles">ROLE-BASED PROGRESS</th>
            <th className="th-status">STATUS</th>
            <th className="th-alerts">ALERTS</th>
          </tr>
        </thead>
        <tbody>
          {renderRows(items, 0)}
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
      <div className="filter-group">
        <label className="filter-label">Search by key</label>
        <input
          type="text"
          placeholder="e.g. LB-1"
          value={searchKey}
          onChange={(e) => onSearchKeyChange(e.target.value)}
          className="filter-input"
        />
      </div>

      <div className="filter-group">
        <label className="filter-label">Team</label>
        <div className="filter-checkboxes">
          {availableTeams.length === 0 ? (
            <span className="filter-empty">No teams</span>
          ) : (
            availableTeams.map(team => (
              <label key={team} className="filter-checkbox">
                <input
                  type="checkbox"
                  checked={selectedTeams.has(team)}
                  onChange={() => onTeamToggle(team)}
                />
                <span>{team}</span>
              </label>
            ))
          )}
        </div>
      </div>

      <div className="filter-group">
        <label className="filter-label">Status</label>
        <div className="filter-checkboxes">
          {availableStatuses.map(status => (
            <label key={status} className="filter-checkbox">
              <input
                type="checkbox"
                checked={selectedStatuses.has(status)}
                onChange={() => onStatusToggle(status)}
              />
              <span>{status}</span>
            </label>
          ))}
        </div>
      </div>

      {hasActiveFilters && (
        <button className="btn btn-secondary btn-clear" onClick={onClearFilters}>
          Clear filters
        </button>
      )}

      <div className="sync-status filter-group-right">
        {syncStatus && (
          <span className="sync-info">
            Last sync: {formatSyncTime(syncStatus.lastSyncCompletedAt)}
            {syncStatus.issuesCount > 0 && ` (${syncStatus.issuesCount} issues)`}
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

  const handleRoughEstimateUpdate = useCallback(async (epicKey: string, role: 'sa' | 'dev' | 'qa', days: number | null) => {
    await updateRoughEstimate(epicKey, role, { days })
    // Refetch board to get updated data
    await fetchBoard()
  }, [fetchBoard])

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
          />
        )}
      </main>
    </>
  )
}
