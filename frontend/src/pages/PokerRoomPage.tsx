import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import axios from 'axios'
import { getConfig } from '../api/config'
import { teamsApi, TeamMember } from '../api/teams'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import './PlanningPokerPage.css'
import {
  PokerSession,
  PokerStory,
  PokerVote,
  ParticipantInfo,
  SessionState,
  EpicStory,
  getSessionByRoomCode,
  getEpicStories,
  addStory,
  AddStoryRequest,
} from '../api/poker'
import { usePokerWebSocket } from '../hooks/usePokerWebSocket'

const VOTE_OPTIONS = [2, 4, 8, 12, 16, 24, 32, 40, -1] // -1 = "?"

interface AuthUser {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: string
  permissions: string[]
}

interface AuthStatus {
  authenticated: boolean
  user: AuthUser | null
}

export function PokerRoomPage() {
  const { roomCode } = useParams<{ roomCode: string }>()
  const navigate = useNavigate()
  const { getRoleCodes, getRoleColor, getRoleDisplayName } = useWorkflowConfig()

  const [session, setSession] = useState<PokerSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // Auth state
  const [authUser, setAuthUser] = useState<AuthUser | null>(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [userRole, setUserRole] = useState<string | null>(null)
  const [showRoleSelector, setShowRoleSelector] = useState(false)
  const [isFacilitator, setIsFacilitator] = useState(false)

  // Room state
  const [participants, setParticipants] = useState<ParticipantInfo[]>([])
  const [currentStoryId, setCurrentStoryId] = useState<number | null>(null)
  const [stories, setStories] = useState<PokerStory[]>([])
  const [myVote, setMyVote] = useState<number | null>(null)

  // Copy room code
  const [copied, setCopied] = useState(false)

  // Add story modal
  const [showAddStory, setShowAddStory] = useState(false)
  const [newStoryTitle, setNewStoryTitle] = useState('')
  const [newStoryNeedsRoles, setNewStoryNeedsRoles] = useState<Set<string>>(new Set(getRoleCodes()))

  // Import existing stories modal
  const [showImportStories, setShowImportStories] = useState(false)
  const [existingStories, setExistingStories] = useState<EpicStory[]>([])
  const [loadingExisting, setLoadingExisting] = useState(false)
  const [selectedImportKeys, setSelectedImportKeys] = useState<Set<string>>(new Set())

  // Final estimate inputs - dynamic map
  const [finalEstimateInputs, setFinalEstimateInputs] = useState<Record<string, string>>({})

  // Fetch auth status
  useEffect(() => {
    axios.get<AuthStatus>('/oauth/atlassian/status')
      .then(response => {
        if (response.data.authenticated && response.data.user) {
          setAuthUser(response.data.user)
        } else {
          setError('Необходимо авторизоваться для участия в сессии')
        }
        setAuthLoading(false)
      })
      .catch(() => {
        setError('Не удалось проверить авторизацию')
        setAuthLoading(false)
      })
  }, [])

  // Initialize newStoryNeedsRoles when roles load
  useEffect(() => {
    const codes = getRoleCodes()
    if (codes.length > 0) {
      setNewStoryNeedsRoles(new Set(codes))
    }
  }, [getRoleCodes])

  // Load session + determine role once auth is ready
  useEffect(() => {
    if (!roomCode || !authUser) return

    Promise.all([
      getSessionByRoomCode(roomCode),
      getConfig()
    ])
      .then(async ([sessionData, config]) => {
        setSession(sessionData)
        setStories(sessionData.stories)
        setCurrentStoryId(sessionData.currentStoryId)
        setJiraBaseUrl(config.jiraBaseUrl)

        // Determine facilitator (fallback: "system" means legacy session -- treat creator as facilitator)
        const isFac = sessionData.facilitatorAccountId === authUser.accountId
          || sessionData.facilitatorAccountId === 'system'
        setIsFacilitator(isFac)

        // Determine team role
        try {
          const members: TeamMember[] = await teamsApi.getMembers(sessionData.teamId)
          const me = members.find(m => m.jiraAccountId === authUser.accountId)
          if (me) {
            setUserRole(me.role)
          } else {
            // User not in team - show role selector
            setShowRoleSelector(true)
          }
        } catch {
          // Fallback: show role selector
          setShowRoleSelector(true)
        }

        setLoading(false)
      })
      .catch(err => {
        setError('Не удалось загрузить сессию: ' + err.message)
        setLoading(false)
      })
  }, [roomCode, authUser])

  // WebSocket callbacks
  const handleStateUpdate = useCallback((state: SessionState) => {
    setStories(state.stories)
    setCurrentStoryId(state.currentStoryId)
    setParticipants(state.participants)
    setSession(prev => prev ? {
      ...prev,
      status: state.status as PokerSession['status'],
      stories: state.stories,
      currentStoryId: state.currentStoryId
    } : null)
  }, [])

  const handleParticipantJoined = useCallback((participant: ParticipantInfo) => {
    setParticipants(prev => [...prev.filter(p => p.accountId !== participant.accountId), participant])
  }, [])

  const handleParticipantLeft = useCallback((accountId: string) => {
    setParticipants(prev => prev.filter(p => p.accountId !== accountId))
  }, [])

  const handleVoteCast = useCallback((storyId: number, voterAccountId: string, role: string) => {
    setStories(prev => prev.map(story => {
      if (story.id !== storyId) return story
      const existingVote = story.votes.find(v => v.voterAccountId === voterAccountId && v.voterRole === role)
      if (existingVote) {
        return {
          ...story,
          votes: story.votes.map(v =>
            v.voterAccountId === voterAccountId && v.voterRole === role
              ? { ...v, hasVoted: true }
              : v
          )
        }
      }
      return {
        ...story,
        votes: [...story.votes, {
          id: 0,
          voterAccountId,
          voterDisplayName: null,
          voterRole: role,
          voteHours: null,
          hasVoted: true,
          votedAt: new Date().toISOString()
        }]
      }
    }))
  }, [])

  const handleVotesRevealed = useCallback((storyId: number, votes: PokerVote[]) => {
    setStories(prev => prev.map(story =>
      story.id === storyId
        ? { ...story, status: 'REVEALED' as const, votes }
        : story
    ))
  }, [])

  const handleStoryCompleted = useCallback((storyId: number, finalEstimates: Record<string, number>) => {
    setStories(prev => prev.map(story =>
      story.id === storyId
        ? {
            ...story,
            status: 'COMPLETED' as const,
            finalEstimates
          }
        : story
    ))
    setMyVote(null)
    setFinalEstimateInputs({})
  }, [])

  const handleCurrentStoryChanged = useCallback((storyId: number) => {
    setCurrentStoryId(storyId)
    setMyVote(null)
    setStories(prev => prev.map(story =>
      story.id === storyId
        ? { ...story, status: 'VOTING' as const }
        : story
    ))
  }, [])

  const handleSessionCompleted = useCallback(() => {
    setSession(prev => prev ? { ...prev, status: 'COMPLETED' } : null)
  }, [])

  const handleError = useCallback((message: string) => {
    setError(message)
  }, [])

  // WebSocket connection -- only connect when we have auth + role
  const wsReady = !!authUser && !!userRole
  const {
    connected,
    sendVote,
    sendReveal,
    sendSetFinal,
    sendNextStory,
    sendStartSession,
  } = usePokerWebSocket({
    roomCode: wsReady ? (roomCode || '') : '',
    accountId: authUser?.accountId || '',
    displayName: authUser?.displayName || '',
    role: userRole || 'DEV',
    isFacilitator,
    onStateUpdate: handleStateUpdate,
    onParticipantJoined: handleParticipantJoined,
    onParticipantLeft: handleParticipantLeft,
    onVoteCast: handleVoteCast,
    onVotesRevealed: handleVotesRevealed,
    onStoryCompleted: handleStoryCompleted,
    onCurrentStoryChanged: handleCurrentStoryChanged,
    onSessionCompleted: handleSessionCompleted,
    onError: handleError,
  })

  // Handlers
  const handleVote = (hours: number) => {
    if (!currentStoryId) return
    setMyVote(hours)
    sendVote(currentStoryId, hours)
  }

  const handleReveal = () => {
    if (!currentStoryId) return
    sendReveal(currentStoryId)
  }

  const handleSetFinal = () => {
    if (!currentStoryId) return
    const estimates: Record<string, number | null> = {}
    for (const [role, value] of Object.entries(finalEstimateInputs)) {
      estimates[role] = value ? parseInt(value) : null
    }
    sendSetFinal(currentStoryId, estimates)
  }

  const handleNextStory = () => {
    sendNextStory()
  }

  const handleStartSession = () => {
    sendStartSession()
  }

  const handleCopyRoomCode = () => {
    if (!session) return
    navigator.clipboard.writeText(session.roomCode).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  const handleSelectRole = (role: string) => {
    setUserRole(role)
    setShowRoleSelector(false)
  }

  const handleToggleNewStoryRole = (roleCode: string) => {
    setNewStoryNeedsRoles(prev => {
      const next = new Set(prev)
      if (next.has(roleCode)) {
        next.delete(roleCode)
      } else {
        next.add(roleCode)
      }
      return next
    })
  }

  const handleAddStory = async () => {
    if (!session || !newStoryTitle.trim()) return

    const request: AddStoryRequest = {
      title: newStoryTitle.trim(),
      needsRoles: Array.from(newStoryNeedsRoles),
    }

    try {
      const story = await addStory(session.id, request, true)
      setStories(prev => [...prev, story])
      setShowAddStory(false)
      setNewStoryTitle('')
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to add story: ' + message)
    }
  }

  const handleOpenImportStories = async () => {
    if (!session) return

    setShowImportStories(true)
    setLoadingExisting(true)
    setSelectedImportKeys(new Set())

    try {
      const epicStories = await getEpicStories(session.epicKey)
      // Filter out stories already in poker session
      const existingKeys = new Set(stories.map(s => s.storyKey))
      const availableStories = epicStories.filter(s => !existingKeys.has(s.storyKey))
      setExistingStories(availableStories)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to load existing stories: ' + message)
    } finally {
      setLoadingExisting(false)
    }
  }

  const handleToggleImportStory = (storyKey: string) => {
    setSelectedImportKeys(prev => {
      const newSet = new Set(prev)
      if (newSet.has(storyKey)) {
        newSet.delete(storyKey)
      } else {
        newSet.add(storyKey)
      }
      return newSet
    })
  }

  const handleImportSelected = async () => {
    if (!session || selectedImportKeys.size === 0) return

    try {
      for (const storyKey of selectedImportKeys) {
        const epicStory = existingStories.find(s => s.storyKey === storyKey)
        if (epicStory) {
          const request: AddStoryRequest = {
            title: epicStory.summary,
            needsRoles: epicStory.subtaskRoles,
            existingStoryKey: epicStory.storyKey,
          }
          const story = await addStory(session.id, request, false)
          setStories(prev => [...prev, story])
        }
      }
      setShowImportStories(false)
      setSelectedImportKeys(new Set())
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to import stories: ' + message)
    }
  }

  // Helpers
  const roleBadgeStyle = (roleCode: string) => ({
    background: getRoleColor(roleCode),
    color: '#fff',
  })

  const roleLightStyle = (roleCode: string) => {
    const color = getRoleColor(roleCode)
    return {
      background: color + '18',
      color: color,
    }
  }

  // Current story
  const currentStory = stories.find(s => s.id === currentStoryId)

  // Progress stats
  const completedCount = stories.filter(s => s.status === 'COMPLETED').length

  // Check if user can vote on current story
  const canVote = currentStory &&
    currentStory.status === 'VOTING' &&
    userRole &&
    currentStory.needsRoles.includes(userRole)

  // Get vote counts per role (for voting status display)
  const getVoteCountByRole = (story: PokerStory, role: string) =>
    story.votes.filter(v => v.voterRole === role && v.hasVoted).length

  // Loading states
  if (authLoading || loading) {
    return <main className="main-content"><div className="loading">Загрузка комнаты...</div></main>
  }

  if (error && !session) {
    return (
      <main className="main-content">
        <div className="error">{error}</div>
        <button className="btn btn-secondary" onClick={() => navigate('/board/poker')}>
          Назад к списку
        </button>
      </main>
    )
  }

  if (!session) {
    return <main className="main-content"><div className="error">Сессия не найдена</div></main>
  }

  // Role selector modal
  if (showRoleSelector && !userRole) {
    const roleCodes = getRoleCodes()
    return (
      <main className="main-content">
        <div className="modal-overlay" style={{ position: 'relative', background: 'transparent' }}>
          <div className="modal-content" style={{ marginTop: 80 }}>
            <h3>Выберите роль</h3>
            <p style={{ color: '#6b778c', marginBottom: 16 }}>
              Вы не привязаны к команде. Выберите роль для голосования:
            </p>
            <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
              {roleCodes.map(code => (
                <button
                  key={code}
                  className="btn btn-primary poker-role-select-btn"
                  style={{ background: getRoleColor(code), borderColor: getRoleColor(code) }}
                  onClick={() => handleSelectRole(code)}
                >
                  {getRoleDisplayName(code)}
                </button>
              ))}
            </div>
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className="main-content poker-room">
      {/* Header */}
      <div className="poker-header">
        <div className="poker-header-left">
          <button className="btn btn-secondary" onClick={() => navigate('/board/poker')}>
            &#8592; Назад
          </button>
          <h2>
            <a
              href={`${jiraBaseUrl}${session.epicKey}`}
              target="_blank"
              rel="noopener noreferrer"
              className="issue-key"
            >
              {session.epicKey}
            </a>
            <span className="poker-header-separator">/</span>
            <span className="poker-header-title">Planning Poker</span>
          </h2>
        </div>
        <div className="poker-header-right">
          <button
            className={`poker-copy-code-btn ${copied ? 'copied' : ''}`}
            onClick={handleCopyRoomCode}
            title="Скопировать код комнаты"
          >
            <code>{session.roomCode}</code>
            <span className="copy-icon">{copied ? '\u2713' : '\u2398'}</span>
          </button>
          <div className={`poker-connection-dot ${connected ? 'connected' : 'disconnected'}`} title={connected ? 'Подключено' : 'Отключено'} />
          {authUser && (
            <div className="poker-user-badge">
              <span className="poker-user-name">{authUser.displayName}</span>
              {userRole && (
                <span className="participant-role" style={roleLightStyle(userRole)}>{userRole}</span>
              )}
            </div>
          )}
        </div>
      </div>

      {error && <div className="error" style={{ marginBottom: 16, marginLeft: 24, marginRight: 24 }}>{error}</div>}

      <div className="poker-layout">
        {/* Left sidebar - Stories list */}
        <div className="poker-sidebar">
          <div className="poker-sidebar-header">
            <div>
              <h3>Стори ({stories.length})</h3>
              {stories.length > 0 && (
                <span className="poker-stories-progress">{completedCount}/{stories.length} оценено</span>
              )}
            </div>
            {isFacilitator && session.status === 'PREPARING' && (
              <div style={{ display: 'flex', gap: 4 }}>
                <button
                  className="btn btn-secondary btn-small"
                  onClick={handleOpenImportStories}
                  title="Импорт из Jira"
                >
                  &#8595; Импорт
                </button>
                <button className="btn btn-primary btn-small" onClick={() => setShowAddStory(true)} title="Создать новую">
                  + Новая
                </button>
              </div>
            )}
          </div>

          <div className="poker-stories-list">
            {stories.length === 0 ? (
              <div className="poker-stories-empty">
                <div className="poker-stories-empty-icon">&#x1F4CB;</div>
                <p>Пока нет сторей</p>
                {isFacilitator && session.status === 'PREPARING' && (
                  <small>Импортируйте из Jira или создайте вручную</small>
                )}
              </div>
            ) : (
              stories.map(story => (
                <div
                  key={story.id}
                  className={`poker-story-item ${story.id === currentStoryId ? 'active' : ''} ${story.status.toLowerCase()}`}
                >
                  <div className="poker-story-header">
                    {story.storyKey && (
                      <a
                        href={`${jiraBaseUrl}${story.storyKey}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="issue-key"
                      >
                        {story.storyKey}
                      </a>
                    )}
                    <span className={`story-status story-status-${story.status.toLowerCase()}`}>
                      {story.status === 'PENDING' && '\u25CB'}
                      {story.status === 'VOTING' && '\u25CF'}
                      {story.status === 'REVEALED' && '\u25D0'}
                      {story.status === 'COMPLETED' && '\u2713'}
                    </span>
                  </div>
                  <div className="poker-story-title">{story.title}</div>
                  {story.status === 'COMPLETED' && (
                    <div className="poker-story-estimates">
                      {story.needsRoles.map(role => (
                        <span key={role} className="estimate-badge" style={roleLightStyle(role)}>
                          {role}: {story.finalEstimates[role] ?? 0}\u0447
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))
            )}
          </div>

          {/* Session controls */}
          {isFacilitator && session.status === 'PREPARING' && stories.length > 0 && (
            <div className="poker-sidebar-footer">
              <button className="btn btn-primary" style={{ width: '100%' }} onClick={handleStartSession}>
                Начать сессию
              </button>
            </div>
          )}
        </div>

        {/* Main area */}
        <div className="poker-main">
          {session.status === 'PREPARING' ? (
            <div className="poker-preparing">
              <div className="poker-preparing-icon">&#x1F0CF;</div>
              <h3>Подготовка сессии</h3>
              {isFacilitator ? (
                <div className="poker-preparing-steps">
                  <div className="poker-step">
                    <span className="poker-step-num">1</span>
                    <span>Импортируйте стори из Jira или добавьте вручную</span>
                  </div>
                  <div className="poker-step">
                    <span className="poker-step-num">2</span>
                    <span>Дождитесь подключения участников</span>
                  </div>
                  <div className="poker-step">
                    <span className="poker-step-num">3</span>
                    <span>Нажмите "Начать сессию"</span>
                  </div>
                  {stories.length === 0 && (
                    <div className="poker-preparing-actions">
                      <button className="btn btn-secondary" onClick={handleOpenImportStories}>
                        &#8595; Импорт из Jira
                      </button>
                      <button className="btn btn-primary" onClick={() => setShowAddStory(true)}>
                        + Новая стори
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <p>Ведущий готовит стори для оценки. Подождите начала сессии.</p>
              )}
            </div>
          ) : session.status === 'COMPLETED' ? (
            <div className="poker-completed">
              <div className="poker-completed-icon">&#x2705;</div>
              <h3>Сессия завершена</h3>
              <p>Все стори оценены ({completedCount} из {stories.length})</p>
            </div>
          ) : currentStory ? (
            <>
              {/* Current story */}
              <div className="poker-current-story">
                <div className="current-story-header">
                  {currentStory.storyKey && (
                    <a
                      href={`${jiraBaseUrl}${currentStory.storyKey}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="issue-key"
                      style={{ fontSize: 18 }}
                    >
                      {currentStory.storyKey}
                    </a>
                  )}
                  <h3>{currentStory.title}</h3>
                </div>

                <div className="needed-roles">
                  {currentStory.needsRoles.map(role => (
                    <span key={role} className="role-badge" style={roleBadgeStyle(role)}>{role}</span>
                  ))}
                </div>
              </div>

              {/* Voting area */}
              {currentStory.status === 'VOTING' && (
                <>
                  {/* Show who has voted */}
                  <div className="votes-status">
                    <div className="votes-pending">
                      {currentStory.needsRoles.map(role => {
                        const voted = getVoteCountByRole(currentStory, role)
                        return (
                          <span
                            key={role}
                            className={`vote-indicator ${voted > 0 ? 'voted' : ''}`}
                            style={voted > 0 ? roleLightStyle(role) : undefined}
                          >
                            {role} {voted > 0 ? '\u2713' : '...'}
                          </span>
                        )
                      })}
                    </div>
                  </div>

                  {/* Voting cards */}
                  {canVote && (
                    <div className="voting-cards">
                      <p>Ваша оценка ({userRole}):</p>
                      <div className="cards-row">
                        {VOTE_OPTIONS.map(hours => (
                          <button
                            key={hours}
                            className={`poker-card ${myVote === hours ? 'selected' : ''}`}
                            onClick={() => handleVote(hours)}
                          >
                            {hours === -1 ? '?' : hours}
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Facilitator: Reveal button */}
                  {isFacilitator && (
                    <div className="facilitator-controls">
                      <button className="btn btn-primary" onClick={handleReveal}>
                        Открыть карты
                      </button>
                    </div>
                  )}
                </>
              )}

              {/* Revealed votes */}
              {currentStory.status === 'REVEALED' && (
                <div className="votes-revealed">
                  <h4>Результаты голосования:</h4>
                  <div className="votes-grid">
                    {currentStory.needsRoles.map(role => (
                      <div key={role} className="vote-column">
                        <h5>{role}</h5>
                        {currentStory.votes.filter(v => v.voterRole === role).map(vote => (
                          <div key={vote.id} className="vote-result">
                            <span>{vote.voterDisplayName || vote.voterAccountId}</span>
                            <span className="vote-value">{vote.voteHours === -1 ? '?' : vote.voteHours}</span>
                          </div>
                        ))}
                      </div>
                    ))}
                  </div>

                  {/* Facilitator: Set final estimate */}
                  {isFacilitator && (
                    <div className="final-estimate-form">
                      <h4>Финальная оценка:</h4>
                      <div className="final-inputs">
                        {currentStory.needsRoles.map(role => (
                          <div key={role} className="final-input-group">
                            <label>{role} (часы)</label>
                            <input
                              type="number"
                              value={finalEstimateInputs[role] || ''}
                              onChange={e => setFinalEstimateInputs(prev => ({ ...prev, [role]: e.target.value }))}
                              min="0"
                              max="100"
                            />
                          </div>
                        ))}
                      </div>
                      <div className="final-actions">
                        <button className="btn btn-primary" onClick={handleSetFinal}>
                          Сохранить и далее
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Story completed - next button */}
              {currentStory.status === 'COMPLETED' && isFacilitator && (
                <div className="facilitator-controls">
                  <button className="btn btn-primary" onClick={handleNextStory}>
                    Следующая стори &#8594;
                  </button>
                </div>
              )}
            </>
          ) : (
            <div className="poker-no-story">
              <p>Нет активной стори для оценки</p>
            </div>
          )}
        </div>

        {/* Right sidebar - Participants */}
        <div className="poker-participants">
          <h3>Участники ({participants.length})</h3>
          <div className="participants-list">
            {participants.length === 0 ? (
              <div className="poker-participants-empty">
                <p>Ожидание участников</p>
                <div className="poker-participants-share">
                  <small>Поделитесь кодом комнаты:</small>
                  <button className="poker-share-code-btn" onClick={handleCopyRoomCode}>
                    <code>{session.roomCode}</code>
                    <span>{copied ? '\u2713' : '\u2398'}</span>
                  </button>
                </div>
              </div>
            ) : (
              participants.map(p => (
                <div key={p.accountId} className={`participant-item ${p.isOnline ? 'online' : 'offline'}`}>
                  <span className="participant-name">
                    {p.displayName}
                    {p.isFacilitator && ' \uD83D\uDC51'}
                  </span>
                  <span className="participant-role" style={roleLightStyle(p.role)}>
                    {p.role}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Add Story Modal */}
      {showAddStory && (
        <div className="modal-overlay" onClick={() => setShowAddStory(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h3>Добавить стори</h3>
            <div className="form-group">
              <label className="filter-label">Название</label>
              <input
                type="text"
                className="filter-input"
                placeholder="Описание стори..."
                value={newStoryTitle}
                onChange={e => setNewStoryTitle(e.target.value)}
                autoFocus
              />
            </div>
            <div className="form-group">
              <label className="filter-label">Роли</label>
              <div className="checkbox-group">
                {getRoleCodes().map(code => (
                  <label key={code}>
                    <input
                      type="checkbox"
                      checked={newStoryNeedsRoles.has(code)}
                      onChange={() => handleToggleNewStoryRole(code)}
                    />
                    {getRoleDisplayName(code)} ({code})
                  </label>
                ))}
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setShowAddStory(false)}>
                Отмена
              </button>
              <button
                className="btn btn-primary"
                onClick={handleAddStory}
                disabled={!newStoryTitle.trim() || newStoryNeedsRoles.size === 0}
              >
                Добавить
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Import Stories Modal */}
      {showImportStories && (
        <div className="modal-overlay" onClick={() => setShowImportStories(false)}>
          <div className="modal-content import-stories-modal" onClick={e => e.stopPropagation()}>
            <h3>Импорт сторей из Jira</h3>
            <p className="modal-description">
              Выберите существующие стори из эпика {session?.epicKey}
            </p>

            {loadingExisting ? (
              <div className="loading-small">Загрузка сторей...</div>
            ) : existingStories.length === 0 ? (
              <div className="empty-hint">
                Нет доступных сторей для импорта.
                <br />
                <small>Все стори уже добавлены или эпик пустой.</small>
              </div>
            ) : (
              <div className="import-stories-list">
                {existingStories.map(story => (
                  <label key={story.storyKey} className="import-story-item">
                    <input
                      type="checkbox"
                      checked={selectedImportKeys.has(story.storyKey)}
                      onChange={() => handleToggleImportStory(story.storyKey)}
                    />
                    <div className="import-story-info">
                      <div className="import-story-header">
                        <span className="issue-key">{story.storyKey}</span>
                        <span className="import-story-status">{story.status}</span>
                      </div>
                      <span className="import-story-summary">{story.summary}</span>
                      <div className="import-story-subtasks">
                        {story.subtaskRoles.map(role => (
                          <span key={role} className="subtask-badge" style={roleLightStyle(role)}>
                            {role} {story.roleEstimates[role] ? `${story.roleEstimates[role]}\u0447` : '\u2014'}
                          </span>
                        ))}
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            )}

            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setShowImportStories(false)}>
                Отмена
              </button>
              <button
                className="btn btn-primary"
                onClick={handleImportSelected}
                disabled={selectedImportKeys.size === 0}
              >
                Импортировать ({selectedImportKeys.size})
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
