import { useEffect, useState, useCallback, useMemo } from 'react'
import axios from 'axios'
import './App.css'

import epicIcon from './icons/epic.png'
import storyIcon from './icons/story.png'
import subtaskIcon from './icons/subtask.png'

const issueTypeIcons: Record<string, string> = {
  'Эпик': epicIcon,
  'Epic': epicIcon,
  'История': storyIcon,
  'Story': storyIcon,
  'Подзадача': subtaskIcon,
  'Sub-task': subtaskIcon,
  'Subtask': subtaskIcon,
  // Custom subtask types
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
  estimateSeconds: number | null
  loggedSeconds: number | null
  progress: number | null
  roleProgress: RoleProgress | null
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
  return `${days.toFixed(days % 1 === 0 ? 0 : 2)} d`
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

function RoleChips({ roleProgress }: { roleProgress: RoleProgress | null }) {
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
  // Check for data quality issues
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
}

function BoardRow({ node, level, expanded, onToggle, hasChildren }: BoardRowProps) {
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
      <td className="cell-team">--</td>
      <td className="cell-estimate">{formatDays(node.estimateSeconds)}</td>
      <td className="cell-logged">{formatDays(node.loggedSeconds)}</td>
      <td className="cell-progress">
        <ProgressBar progress={node.progress || 0} />
      </td>
      <td className="cell-roles">
        <RoleChips roleProgress={node.roleProgress} />
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

function BoardTable({ items }: { items: BoardNode[] }) {
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
            <th className="th-estimate">ESTIMATE</th>
            <th className="th-logged">LOGGED TIME</th>
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
}: FilterPanelProps) {
  const hasActiveFilters = searchKey || selectedStatuses.size > 0 || selectedTeams.size > 0

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
    </div>
  )
}

function formatSyncTime(isoString: string | null): string {
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

function App() {
  const [board, setBoard] = useState<BoardNode[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null)
  const [syncing, setSyncing] = useState(false)

  // Filter state
  const [searchKey, setSearchKey] = useState('')
  const [selectedStatuses, setSelectedStatuses] = useState<Set<string>>(new Set())
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(new Set())

  const fetchBoard = useCallback(() => {
    setLoading(true)
    axios.get<BoardResponse>('/api/board')
      .then(response => {
        setBoard(response.data.items)
        setLoading(false)
      })
      .catch(err => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  const fetchSyncStatus = useCallback(() => {
    axios.get<SyncStatus>('/api/sync/status')
      .then(response => {
        setSyncStatus(response.data)
        setSyncing(response.data.syncInProgress)
      })
      .catch(() => {
        // Ignore sync status errors
      })
  }, [])

  const triggerSync = () => {
    setSyncing(true)
    axios.post<SyncStatus>('/api/sync/trigger')
      .then(() => {
        // Poll for sync completion
        const pollInterval = setInterval(() => {
          axios.get<SyncStatus>('/api/sync/status')
            .then(response => {
              setSyncStatus(response.data)
              if (!response.data.syncInProgress) {
                setSyncing(false)
                clearInterval(pollInterval)
                fetchBoard() // Refresh board after sync
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
  }, [fetchBoard, fetchSyncStatus])

  // Extract unique statuses from epics
  const availableStatuses = useMemo(() => {
    const statuses = new Set<string>()
    board.forEach(epic => statuses.add(epic.status))
    return Array.from(statuses).sort()
  }, [board])

  // Extract unique teams from epics (placeholder for now)
  const availableTeams = useMemo(() => {
    const teams = new Set<string>()
    // When team field is added to backend, extract from board
    // board.forEach(epic => { if (epic.team) teams.add(epic.team) })
    return Array.from(teams).sort()
  }, [board])

  // Filter epics
  const filteredBoard = useMemo(() => {
    return board.filter(epic => {
      // Filter by key
      if (searchKey) {
        const keyLower = searchKey.toLowerCase()
        if (!epic.issueKey.toLowerCase().includes(keyLower)) {
          return false
        }
      }

      // Filter by status
      if (selectedStatuses.size > 0 && !selectedStatuses.has(epic.status)) {
        return false
      }

      // Filter by team (when implemented)
      // if (selectedTeams.size > 0 && epic.team && !selectedTeams.has(epic.team)) {
      //   return false
      // }

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
    <div className="app">
      <header className="header">
        <div className="header-left">
          <h1>Lead Board</h1>
        </div>
        <div className="header-right">
          <div className="sync-status">
            {syncStatus && (
              <span className="sync-info">
                Last sync: {formatSyncTime(syncStatus.lastSyncCompletedAt)}
                {syncStatus.issuesCount > 0 && ` (${syncStatus.issuesCount} issues)`}
              </span>
            )}
            <button
              className={`btn btn-primary btn-refresh ${syncing ? 'syncing' : ''}`}
              onClick={triggerSync}
              disabled={syncing}
            >
              {syncing ? 'Syncing...' : 'Refresh'}
            </button>
          </div>
        </div>
      </header>

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
      />

      <main className="main-content">
        {loading && <div className="loading">Loading board...</div>}
        {error && <div className="error">Error: {error}</div>}
        {!loading && !error && filteredBoard.length === 0 && (
          <div className="empty">No epics found</div>
        )}
        {!loading && !error && filteredBoard.length > 0 && (
          <BoardTable items={filteredBoard} />
        )}
      </main>
    </div>
  )
}

export default App
