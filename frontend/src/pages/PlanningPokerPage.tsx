import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getConfig } from '../api/config'
import './PlanningPokerPage.css'
import {
  PokerSession,
  EligibleEpic,
  getSessionsByTeam,
  getEligibleEpics,
  createSession,
} from '../api/poker'

export function PlanningPokerPage() {
  const navigate = useNavigate()
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [sessions, setSessions] = useState<PokerSession[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // Create session modal
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [eligibleEpics, setEligibleEpics] = useState<EligibleEpic[]>([])
  const [selectedEpicKey, setSelectedEpicKey] = useState('')
  const [loadingEpics, setLoadingEpics] = useState(false)
  const [creating, setCreating] = useState(false)

  // Join room modal
  const [showJoinModal, setShowJoinModal] = useState(false)
  const [joinRoomCode, setJoinRoomCode] = useState('')

  useEffect(() => {
    Promise.all([
      teamsApi.getAll(),
      getConfig()
    ])
      .then(([teamsData, config]) => {
        const activeTeams = teamsData.filter(t => t.active)
        setTeams(activeTeams)
        setJiraBaseUrl(config.jiraBaseUrl)
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

  const handleOpenCreateModal = async () => {
    if (!selectedTeamId) return

    setShowCreateModal(true)
    setLoadingEpics(true)
    setSelectedEpicKey('')

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

  const handleCreateSession = async () => {
    if (!selectedTeamId || !selectedEpicKey) return

    setCreating(true)
    try {
      const session = await createSession(selectedTeamId, selectedEpicKey)
      setShowCreateModal(false)
      setSelectedEpicKey('')
      navigate(`/poker/room/${session.roomCode}`)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to create session: ' + message)
    } finally {
      setCreating(false)
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
        return <span className="status-badge" style={{ background: '#deebff', color: '#0747a6' }}>Подготовка</span>
      case 'ACTIVE':
        return <span className="status-badge" style={{ background: '#e3fcef', color: '#006644' }}>Активна</span>
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
    <main className="main-content">
      <div className="page-header">
        <h2>Planning Poker</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn btn-secondary" onClick={() => setShowJoinModal(true)}>
            Войти по коду
          </button>
          <button className="btn btn-primary" onClick={handleOpenCreateModal} disabled={!selectedTeamId}>
            + Новая сессия
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="metrics-controls">
        <div className="filter-group">
          <label className="filter-label">Команда</label>
          <select
            className="filter-input"
            value={selectedTeamId ?? ''}
            onChange={e => setSelectedTeamId(Number(e.target.value))}
          >
            <option value="" disabled>Выберите команду...</option>
            {teams.map(team => (
              <option key={team.id} value={team.id}>{team.name}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Sessions List */}
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
                      background: '#f4f5f7',
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

      {/* Create Session Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content poker-create-modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header-icon">🎯</div>
            <h3>Создать сессию Planning Poker</h3>
            <p className="modal-description">
              Выберите эпик для оценки задач командой
            </p>

            <div className="form-group">
              <label className="filter-label">Эпик</label>
              {loadingEpics ? (
                <div className="loading-small">Загрузка эпиков...</div>
              ) : eligibleEpics.length === 0 ? (
                <div className="empty-hint">
                  Нет доступных эпиков в статусах: Планирование, Грязная оценка, В работе
                </div>
              ) : (
                <select
                  className="filter-input"
                  value={selectedEpicKey}
                  onChange={e => setSelectedEpicKey(e.target.value)}
                  autoFocus
                >
                  <option value="">Выберите эпик...</option>
                  {eligibleEpics
                    .filter(e => !e.hasPokerSession)
                    .map(epic => (
                      <option key={epic.epicKey} value={epic.epicKey}>
                        {epic.epicKey} — {epic.summary.length > 50 ? epic.summary.substring(0, 50) + '...' : epic.summary}
                      </option>
                    ))}
                  {eligibleEpics.some(e => e.hasPokerSession) && (
                    <optgroup label="Уже есть сессия">
                      {eligibleEpics
                        .filter(e => e.hasPokerSession)
                        .map(epic => (
                          <option key={epic.epicKey} value={epic.epicKey} disabled>
                            {epic.epicKey} — {epic.summary.length > 50 ? epic.summary.substring(0, 50) + '...' : epic.summary}
                          </option>
                        ))}
                    </optgroup>
                  )}
                </select>
              )}
              {selectedEpicKey && (
                <div className="selected-epic-hint">
                  <a
                    href={`${jiraBaseUrl}${selectedEpicKey}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="issue-key"
                  >
                    Открыть в Jira ↗
                  </a>
                </div>
              )}
            </div>

            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                Отмена
              </button>
              <button
                className="btn btn-primary"
                onClick={handleCreateSession}
                disabled={creating || !selectedEpicKey}
              >
                {creating ? 'Создание...' : 'Создать →'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Join Room Modal */}
      {showJoinModal && (
        <div className="modal-overlay" onClick={() => setShowJoinModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h3>Войти в комнату</h3>
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
          </div>
        </div>
      )}
    </main>
  )
}
