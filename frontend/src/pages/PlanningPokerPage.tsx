import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getConfig } from '../api/config'
import { Modal } from '../components/Modal'
import { SearchInput } from '../components/SearchInput'
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
import { BG_SUBTLE, INFO_BG, SUCCESS_BG } from '../constants/colors'

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

  // Join room modal
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [joinRoomCode, setJoinRoomCode] = useState('')

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
      navigate(`/poker/room/${session.roomCode}`)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to create session: ' + message)
      setCreatingEpicKey(null)
    }
  }

  const handleJoinRoom = () => {
    if (joinRoomCode.trim()) {
      navigate(`/poker/room/${joinRoomCode.trim().toUpperCase()}`)
    }
  }

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PREPARING':
        return <span className="status-badge" style={{ background: INFO_BG, color: '#0747a6' }}>Подготовка</span>
      case 'ACTIVE':
        return <span className="status-badge" style={{ background: SUCCESS_BG, color: '#006644' }}>Активна</span>
      case 'COMPLETED':
        return <span className="status-badge" style={{ background: '#dfe1e6', color: '#42526e' }}>Завершена</span>
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
    return <main className="main-content"><div className="loading">Загрузка...</div></main>
  }

  return (
    <StatusStylesProvider value={statusStyles}>
    <main className="main-content">
      <div className="page-header">
        <h2>Planning Poker</h2>
        {!pickingEpic && (
          <div style={{ display: 'flex', gap: 8 }}>
            <button className="btn btn-secondary" onClick={() => setShowJoinModal(true)}>
              Войти по коду
            </button>
            <button className="btn btn-primary" onClick={handleOpenEpicPicker} disabled={!selectedTeamId}>
              Новая сессия
            </button>
          </div>
        )}
      </div>

      {error && <div className="error">{error}</div>}

      <div className="metrics-controls">
        <div className="filter-group">
          <label className="filter-label">Команда</label>
          <select
            className="filter-input"
            style={{ minWidth: 240 }}
            value={selectedTeamId ?? ''}
            onChange={e => {
              setSelectedTeamId(Number(e.target.value))
              handleClosePicker()
            }}
          >
            <option value="" disabled>Выберите команду...</option>
            {teams.map(team => (
              <option key={team.id} value={team.id}>{team.name}</option>
            ))}
          </select>
        </div>
      </div>

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
      ) : (
      /* Sessions List */
      <div className="poker-sessions-list">
        {sessions.length === 0 ? (
          <div className="chart-empty">
            Нет сессий Planning Poker.
            <br />
            <small>Создайте новую сессию для оценки эпика.</small>
          </div>
        ) : (
          <table className="metrics-table">
            <thead>
              <tr>
                <th>Эпик</th>
                <th>Код комнаты</th>
                <th>Статус</th>
                <th>Сторей</th>
                <th>Создана</th>
                <th>Действия</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map(session => (
                <tr key={session.id}>
                  <td>
                    <a
                      href={`${jiraBaseUrl}${session.epicKey}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="issue-key"
                    >
                      {session.epicKey}
                    </a>
                  </td>
                  <td>
                    <code style={{
                      background: BG_SUBTLE,
                      padding: '2px 8px',
                      borderRadius: 4,
                      fontWeight: 600,
                      letterSpacing: '0.1em'
                    }}>
                      {session.roomCode}
                    </code>
                  </td>
                  <td>{getStatusBadge(session.status)}</td>
                  <td>{session.stories.length}</td>
                  <td>{formatDate(session.createdAt)}</td>
                  <td>
                    <button
                      className="btn btn-secondary"
                      onClick={() => navigate(`/poker/room/${session.roomCode}`)}
                    >
                      {session.status === 'COMPLETED' ? 'Просмотр' : 'Войти'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      )}

      {/* Join Room Modal */}
      <Modal isOpen={showJoinModal} onClose={() => setShowJoinModal(false)} title="Войти в комнату">
        <div className="form-group">
          <label className="filter-label">Код комнаты</label>
          <input
            type="text"
            className="filter-input"
            placeholder="Например: ABC123"
            value={joinRoomCode}
            onChange={e => setJoinRoomCode(e.target.value.toUpperCase())}
            autoFocus
            maxLength={6}
            style={{ letterSpacing: '0.2em', textAlign: 'center', fontSize: 18 }}
          />
        </div>
        <div className="modal-actions">
          <button className="btn btn-secondary" onClick={() => setShowJoinModal(false)}>
            Отмена
          </button>
          <button
            className="btn btn-primary"
            onClick={handleJoinRoom}
            disabled={joinRoomCode.trim().length !== 6}
          >
            Войти
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
          <h3>Выберите эпик для оценки</h3>
          <span className="epic-picker-subtitle">Команда оценит задачи выбранного эпика</span>
        </div>
        <button className="btn btn-secondary" onClick={onCancel}>Отмена</button>
      </div>

      {!loading && epics.length > 0 && (
        <div className="epic-picker-search">
          <SearchInput value={search} onChange={onSearch} placeholder="Поиск по ключу или названию..." />
        </div>
      )}

      {loading ? (
        <div className="loading-small">Загрузка эпиков...</div>
      ) : epics.length === 0 ? (
        <div className="chart-empty">
          У команды нет незавершённых эпиков для оценки
        </div>
      ) : filtered.length === 0 ? (
        <div className="chart-empty">Ничего не найдено по запросу «{search}»</div>
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
                title={epic.hasPokerSession ? 'Для этого эпика уже есть сессия' : undefined}
              >
                <img className="epic-picker-icon" src={epicIcon} alt="" />
                <span className="epic-picker-key">{epic.epicKey}</span>
                <span className="epic-picker-summary">{epic.summary}</span>
                <span className="epic-picker-meta">
                  {epic.hasPokerSession
                    ? <span className="epic-picker-tag">Есть сессия</span>
                    : <StatusBadge status={epic.status} />}
                </span>
                {busy && <span className="epic-picker-creating">Создание…</span>}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
