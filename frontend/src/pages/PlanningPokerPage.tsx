import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getConfig } from '../api/config'
import { Modal } from '../components/Modal'
import { SearchInput } from '../components/SearchInput'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { StatusBadge } from '../components/board/StatusBadge'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { getIssueIcon } from '../components/board/helpers'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import './PlanningPokerPage.css'
import {
  PokerSession,
  EligibleEpic,
  getSessionsByTeam,
  getEligibleEpics,
  createSession,
} from '../api/poker'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { INFO_BG, SUCCESS_BG } from '../constants/colors'

// Two overlapping poker cards — line-art empty-state icon (no emoji)
function PlanningIcon() {
  return (
    <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3.5" y="5" width="10" height="14" rx="1.6" transform="rotate(-8 8.5 12)" />
      <rect x="10.5" y="5" width="10" height="14" rx="1.6" transform="rotate(8 15.5 12)" />
    </svg>
  )
}

function pluralSessions(n: number): string {
  return n === 1 ? 'session' : 'sessions'
}

export function PlanningPokerPage() {
  const navigate = useNavigate()
  const { getIssueTypeIconUrl, getTypeNameByCategory } = useWorkflowConfig()
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [sessions, setSessions] = useState<PokerSession[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // Inline epic picker (create session)
  const [pickingEpic, setPickingEpic] = useState(false)
  const [eligibleEpics, setEligibleEpics] = useState<EligibleEpic[]>([])
  const [epicSearch, setEpicSearch] = useState('')
  const [loadingEpics, setLoadingEpics] = useState(false)
  const [creatingEpicKey, setCreatingEpicKey] = useState<string | null>(null)

  // Join room modal — by epic key (rooms are addressed by epic key, F23)
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [joinEpicKey, setJoinEpicKey] = useState('')

  // Status colors from workflow config — so StatusBadge renders board colors, not grey
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})

  const epicTypeName = getTypeNameByCategory('EPIC')
  const epicIcon = getIssueIcon(epicTypeName || 'Epic', getIssueTypeIconUrl(epicTypeName), 'EPIC')

  useEffect(() => {
    Promise.all([
      teamsApi.getAll(),
      getConfig(),
      getStatusStyles().catch(() => ({}))
    ])
      .then(([teamsData, config, styles]) => {
        const activeTeams = teamsData.filter(t => t.active)
        setTeams(activeTeams)
        setJiraBaseUrl(config.jiraBaseUrl)
        setStatusStyles(styles)
        if (activeTeams.length > 0 && !selectedTeamId) {
          setSelectedTeamId(activeTeams[0].id)
        }
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load data: ' + err.message)
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    if (selectedTeamId) {
      loadSessions()
    }
  }, [selectedTeamId])

  const loadSessions = async () => {
    if (!selectedTeamId) return
    try {
      const data = await getSessionsByTeam(selectedTeamId)
      setSessions(data)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to load sessions: ' + message)
    }
  }

  const handleOpenEpicPicker = async () => {
    if (!selectedTeamId) return

    setPickingEpic(true)
    setEpicSearch('')
    setError(null)
    setLoadingEpics(true)

    try {
      const epics = await getEligibleEpics(selectedTeamId)
      setEligibleEpics(epics)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to load epics: ' + message)
    } finally {
      setLoadingEpics(false)
    }
  }

  const handleClosePicker = () => {
    setPickingEpic(false)
    setEpicSearch('')
  }

  const handleSelectEpic = async (epicKey: string) => {
    if (!selectedTeamId || creatingEpicKey) return

    setCreatingEpicKey(epicKey)
    try {
      const session = await createSession(selectedTeamId, epicKey)
      navigate(`/poker/${session.epicKey}`)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to create session: ' + message)
      setCreatingEpicKey(null)
    }
  }

  const handleJoinRoom = () => {
    if (joinEpicKey.trim()) {
      navigate(`/poker/${joinEpicKey.trim().toUpperCase()}`)
    }
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PREPARING':
        return <span className="status-badge" style={{ background: INFO_BG, color: '#0747a6' }}>Preparing</span>
      case 'ACTIVE':
        return <span className="status-badge" style={{ background: SUCCESS_BG, color: '#006644' }}>Active</span>
      case 'COMPLETED':
        return <span className="status-badge" style={{ background: '#dfe1e6', color: '#42526e' }}>Completed</span>
      default:
        return <span className="status-badge">{status}</span>
    }
  }

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—'
    return new Date(dateStr).toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  if (loading) {
    return <main className="main-content"><div className="loading">Loading...</div></main>
  }

  return (
    <StatusStylesProvider value={statusStyles}>
    <main className="main-content">
      <div className="page-header">
        <h2>Planning Poker</h2>
        {!pickingEpic && (
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-secondary" onClick={() => setShowJoinModal(true)}>
              Join by key
            </button>
            <button className="btn btn-primary" onClick={handleOpenEpicPicker} disabled={!selectedTeamId}>
              New session
            </button>
          </div>
        )}
      </div>

      {error && <div className="error">{error}</div>}

      {!pickingEpic && (
        <div className="poker-toolbar">
          <div className="poker-toolbar-team">
            <label className="filter-label">Team</label>
            <SingleSelectDropdown
              label="Team"
              placeholder="Select team..."
              allowClear={false}
              options={teams.map(t => ({ value: String(t.id), label: t.name, color: t.color ?? undefined }))}
              selected={selectedTeamId != null ? String(selectedTeamId) : null}
              onChange={v => {
                if (v != null) setSelectedTeamId(Number(v))
                handleClosePicker()
              }}
            />
          </div>
          {sessions.length > 0 && (
            <span className="poker-toolbar-count">
              {sessions.length} {pluralSessions(sessions.length)}
            </span>
          )}
        </div>
      )}

      {pickingEpic ? (
        <EpicPicker
          epics={eligibleEpics}
          loading={loadingEpics}
          search={epicSearch}
          onSearch={setEpicSearch}
          onSelect={handleSelectEpic}
          onCancel={handleClosePicker}
          creatingEpicKey={creatingEpicKey}
          epicIcon={epicIcon}
        />
      ) : sessions.length === 0 ? (
        <div className="poker-empty">
          <div className="poker-empty-icon"><PlanningIcon /></div>
          <h3>No sessions yet</h3>
          <p>Create a Planning Poker session for your team to estimate an epic's stories together.</p>
          <div className="poker-empty-actions">
            <button className="btn btn-secondary" onClick={() => setShowJoinModal(true)}>
              Join by key
            </button>
            <button className="btn btn-primary" onClick={handleOpenEpicPicker} disabled={!selectedTeamId}>
              New session
            </button>
          </div>
        </div>
      ) : (
      /* Sessions List */
      <div className="poker-sessions-card">
          <table className="metrics-table">
            <thead>
              <tr>
                <th>Epic</th>
                <th>Status</th>
                <th>Stories</th>
                <th>Created</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map(session => (
                <tr key={session.id}>
                  <td>
                    <span className="poker-epic-cell">
                      <img className="poker-epic-cell-icon" src={epicIcon} alt="" />
                      <a
                        href={`${jiraBaseUrl}${session.epicKey}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="issue-key"
                      >
                        {session.epicKey}
                      </a>
                      {session.epicSummary && (
                        <span className="poker-epic-cell-summary">· {session.epicSummary}</span>
                      )}
                    </span>
                  </td>
                  <td>{getStatusBadge(session.status)}</td>
                  <td>{session.stories.length}</td>
                  <td>{formatDate(session.createdAt)}</td>
                  <td>
                    <button
                      className="btn btn-secondary"
                      onClick={() => navigate(`/poker/${session.epicKey}`)}
                    >
                      Open
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
      </div>
      )}

      {/* Join Room Modal — by epic key */}
      <Modal isOpen={showJoinModal} onClose={() => setShowJoinModal(false)} title="Join by epic key">
        <div className="form-group">
          <label className="filter-label">Epic key</label>
          <input
            type="text"
            className="filter-input"
            placeholder="e.g. LB-203"
            value={joinEpicKey}
            onChange={e => setJoinEpicKey(e.target.value.toUpperCase())}
            onKeyDown={e => { if (e.key === 'Enter' && joinEpicKey.trim()) handleJoinRoom() }}
            autoFocus
            style={{ letterSpacing: '0.14em', textAlign: 'center', fontSize: 18 }}
          />
        </div>
        <div className="modal-actions">
          <button className="btn btn-secondary" onClick={() => setShowJoinModal(false)}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleJoinRoom}
            disabled={!joinEpicKey.trim()}
          >
            Open room
          </button>
        </div>
      </Modal>
    </main>
    </StatusStylesProvider>
  )
}

interface EpicPickerProps {
  epics: EligibleEpic[]
  loading: boolean
  search: string
  onSearch: (v: string) => void
  onSelect: (epicKey: string) => void
  onCancel: () => void
  creatingEpicKey: string | null
  epicIcon: string
}

function EpicPicker({ epics, loading, search, onSearch, onSelect, onCancel, creatingEpicKey, epicIcon }: EpicPickerProps) {
  const q = search.trim().toLowerCase()
  const filtered = q
    ? epics.filter(e => e.epicKey.toLowerCase().includes(q) || e.summary.toLowerCase().includes(q))
    : epics

  return (
    <div className="epic-picker">
      <div className="epic-picker-header">
        <div>
          <h3>Select an epic to estimate</h3>
          <span className="epic-picker-subtitle">Your team will estimate the selected epic's stories</span>
        </div>
        <button className="btn btn-secondary" onClick={onCancel}>Cancel</button>
      </div>

      {!loading && epics.length > 0 && (
        <div className="epic-picker-search">
          <SearchInput value={search} onChange={onSearch} placeholder="Search by key or name..." />
        </div>
      )}

      {loading ? (
        <div className="loading-small">Loading epics...</div>
      ) : epics.length === 0 ? (
        <div className="chart-empty">
          No epics available for estimation
        </div>
      ) : filtered.length === 0 ? (
        <div className="chart-empty">Nothing found for “{search}”</div>
      ) : (
        <div className="epic-picker-list">
          {filtered.map(epic => {
            const busy = creatingEpicKey === epic.epicKey
            const disabled = epic.hasPokerSession || (creatingEpicKey !== null && !busy)
            return (
              <button
                key={epic.epicKey}
                className="epic-picker-row"
                onClick={() => !epic.hasPokerSession && onSelect(epic.epicKey)}
                disabled={disabled}
                title={epic.hasPokerSession ? 'This epic already has a session' : undefined}
              >
                <img className="epic-picker-icon" src={epicIcon} alt="" />
                <span className="epic-picker-key">{epic.epicKey}</span>
                <span className="epic-picker-summary">{epic.summary}</span>
                <span className="epic-picker-meta">
                  {epic.hasPokerSession
                    ? <span className="epic-picker-tag">Has session</span>
                    : <StatusBadge status={epic.status} />}
                </span>
                {busy && <span className="epic-picker-creating">Creating…</span>}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
