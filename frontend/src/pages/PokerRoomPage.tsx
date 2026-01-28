import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getConfig } from '../api/config'
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

export function PokerRoomPage() {
  const { roomCode } = useParams<{ roomCode: string }>()
  const navigate = useNavigate()

  const [session, setSession] = useState<PokerSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // User info (in production, get from OAuth context)
  const [userAccountId] = useState('user-' + Math.random().toString(36).substr(2, 9))
  const [userDisplayName] = useState('User')
  const [userRole] = useState<'SA' | 'DEV' | 'QA'>('DEV')
  const [isFacilitator] = useState(true) // TODO: determine from OAuth

  // Room state
  const [participants, setParticipants] = useState<ParticipantInfo[]>([])
  const [currentStoryId, setCurrentStoryId] = useState<number | null>(null)
  const [stories, setStories] = useState<PokerStory[]>([])
  const [myVote, setMyVote] = useState<number | null>(null)

  // Add story modal
  const [showAddStory, setShowAddStory] = useState(false)
  const [newStoryTitle, setNewStoryTitle] = useState('')
  const [newStoryNeedsSa, setNewStoryNeedsSa] = useState(true)
  const [newStoryNeedsDev, setNewStoryNeedsDev] = useState(true)
  const [newStoryNeedsQa, setNewStoryNeedsQa] = useState(true)

  // Import existing stories modal
  const [showImportStories, setShowImportStories] = useState(false)
  const [existingStories, setExistingStories] = useState<EpicStory[]>([])
  const [loadingExisting, setLoadingExisting] = useState(false)
  const [selectedImportKeys, setSelectedImportKeys] = useState<Set<string>>(new Set())

  // Final estimate inputs
  const [finalSa, setFinalSa] = useState<string>('')
  const [finalDev, setFinalDev] = useState<string>('')
  const [finalQa, setFinalQa] = useState<string>('')

  // Load initial session
  useEffect(() => {
    if (!roomCode) return

    Promise.all([
      getSessionByRoomCode(roomCode),
      getConfig()
    ])
      .then(([sessionData, config]) => {
        setSession(sessionData)
        setStories(sessionData.stories)
        setCurrentStoryId(sessionData.currentStoryId)
        setJiraBaseUrl(config.jiraBaseUrl)
        // TODO: Check if current user is facilitator via OAuth
        // setIsFacilitator(sessionData.facilitatorAccountId === userAccountId)
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load session: ' + err.message)
        setLoading(false)
      })
  }, [roomCode, userAccountId])

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
          voterRole: role as 'SA' | 'DEV' | 'QA',
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

  const handleStoryCompleted = useCallback((storyId: number, saHours: number, devHours: number, qaHours: number) => {
    setStories(prev => prev.map(story =>
      story.id === storyId
        ? {
            ...story,
            status: 'COMPLETED' as const,
            finalSaHours: saHours,
            finalDevHours: devHours,
            finalQaHours: qaHours
          }
        : story
    ))
    setMyVote(null)
    setFinalSa('')
    setFinalDev('')
    setFinalQa('')
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

  // WebSocket connection
  const {
    connected,
    sendVote,
    sendReveal,
    sendSetFinal,
    sendNextStory,
    sendStartSession,
  } = usePokerWebSocket({
    roomCode: roomCode || '',
    accountId: userAccountId,
    displayName: userDisplayName,
    role: userRole,
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
    sendSetFinal(
      currentStoryId,
      finalSa ? parseInt(finalSa) : null,
      finalDev ? parseInt(finalDev) : null,
      finalQa ? parseInt(finalQa) : null
    )
  }

  const handleNextStory = () => {
    sendNextStory()
  }

  const handleStartSession = () => {
    sendStartSession()
  }

  const handleAddStory = async () => {
    if (!session || !newStoryTitle.trim()) return

    const request: AddStoryRequest = {
      title: newStoryTitle.trim(),
      needsSa: newStoryNeedsSa,
      needsDev: newStoryNeedsDev,
      needsQa: newStoryNeedsQa,
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
            needsSa: epicStory.hasSaSubtask,
            needsDev: epicStory.hasDevSubtask,
            needsQa: epicStory.hasQaSubtask,
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

  // Current story
  const currentStory = stories.find(s => s.id === currentStoryId)

  // Check if user can vote on current story
  const canVote = currentStory &&
    currentStory.status === 'VOTING' &&
    ((userRole === 'SA' && currentStory.needsSa) ||
     (userRole === 'DEV' && currentStory.needsDev) ||
     (userRole === 'QA' && currentStory.needsQa))

  // Get votes for display
  const getVotesDisplay = (story: PokerStory) => {
    if (story.status === 'VOTING') {
      // Show who has voted but not the values
      const saVoted = story.votes.filter(v => v.voterRole === 'SA' && v.hasVoted).length
      const devVoted = story.votes.filter(v => v.voterRole === 'DEV' && v.hasVoted).length
      const qaVoted = story.votes.filter(v => v.voterRole === 'QA' && v.hasVoted).length
      return { saVoted, devVoted, qaVoted, revealed: false }
    }
    if (story.status === 'REVEALED' || story.status === 'COMPLETED') {
      return {
        saVotes: story.votes.filter(v => v.voterRole === 'SA'),
        devVotes: story.votes.filter(v => v.voterRole === 'DEV'),
        qaVotes: story.votes.filter(v => v.voterRole === 'QA'),
        revealed: true
      }
    }
    return null
  }

  if (loading) {
    return <main className="main-content"><div className="loading">Загрузка комнаты...</div></main>
  }

  if (error && !session) {
    return (
      <main className="main-content">
        <div className="error">{error}</div>
        <button className="btn btn-secondary" onClick={() => navigate('/poker')}>
          Назад к списку
        </button>
      </main>
    )
  }

  if (!session) {
    return <main className="main-content"><div className="error">Сессия не найдена</div></main>
  }

  return (
    <main className="main-content poker-room">
      {/* Header */}
      <div className="poker-header">
        <div className="poker-header-left">
          <button className="btn btn-secondary" onClick={() => navigate('/poker')}>
            ← Назад
          </button>
          <h2>
            Planning Poker
            <a
              href={`${jiraBaseUrl}${session.epicKey}`}
              target="_blank"
              rel="noopener noreferrer"
              className="issue-key"
              style={{ marginLeft: 12 }}
            >
              {session.epicKey}
            </a>
          </h2>
        </div>
        <div className="poker-header-right">
          <div className="room-code">
            Код: <code>{session.roomCode}</code>
          </div>
          <div className={`connection-status ${connected ? 'connected' : 'disconnected'}`}>
            {connected ? '● Подключено' : '○ Отключено'}
          </div>
        </div>
      </div>

      {error && <div className="error" style={{ marginBottom: 16 }}>{error}</div>}

      <div className="poker-layout">
        {/* Left sidebar - Stories list */}
        <div className="poker-sidebar">
          <div className="poker-sidebar-header">
            <h3>Стори ({stories.length})</h3>
            {session.status === 'PREPARING' && (
              <div style={{ display: 'flex', gap: 4 }}>
                <button
                  className="btn btn-secondary btn-small"
                  onClick={handleOpenImportStories}
                  title="Импорт из Jira"
                >
                  ↓
                </button>
                <button className="btn btn-primary btn-small" onClick={() => setShowAddStory(true)} title="Создать новую">
                  +
                </button>
              </div>
            )}
          </div>

          <div className="poker-stories-list">
            {stories.map(story => (
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
                    {story.status === 'PENDING' && '○'}
                    {story.status === 'VOTING' && '●'}
                    {story.status === 'REVEALED' && '◐'}
                    {story.status === 'COMPLETED' && '✓'}
                  </span>
                </div>
                <div className="poker-story-title">{story.title}</div>
                {story.status === 'COMPLETED' && (
                  <div className="poker-story-estimates">
                    {story.needsSa && <span className="estimate-badge sa">SA: {story.finalSaHours}ч</span>}
                    {story.needsDev && <span className="estimate-badge dev">DEV: {story.finalDevHours}ч</span>}
                    {story.needsQa && <span className="estimate-badge qa">QA: {story.finalQaHours}ч</span>}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Session controls */}
          {session.status === 'PREPARING' && stories.length > 0 && (
            <button className="btn btn-primary" style={{ width: '100%', marginTop: 16 }} onClick={handleStartSession}>
              Начать сессию
            </button>
          )}
        </div>

        {/* Main area */}
        <div className="poker-main">
          {session.status === 'PREPARING' ? (
            <div className="poker-preparing">
              <h3>Подготовка сессии</h3>
              <p>Добавьте стори для оценки и нажмите "Начать сессию"</p>
            </div>
          ) : session.status === 'COMPLETED' ? (
            <div className="poker-completed">
              <h3>Сессия завершена</h3>
              <p>Все стори оценены</p>
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
                  {currentStory.needsSa && <span className="role-badge sa">SA</span>}
                  {currentStory.needsDev && <span className="role-badge dev">DEV</span>}
                  {currentStory.needsQa && <span className="role-badge qa">QA</span>}
                </div>
              </div>

              {/* Voting area */}
              {currentStory.status === 'VOTING' && (
                <>
                  {/* Show who has voted */}
                  <div className="votes-status">
                    {(() => {
                      const display = getVotesDisplay(currentStory)
                      if (!display || display.revealed) return null
                      const { saVoted, devVoted, qaVoted } = display as { saVoted: number; devVoted: number; qaVoted: number; revealed: boolean }
                      return (
                        <div className="votes-pending">
                          {currentStory.needsSa && (
                            <span className={`vote-indicator sa ${saVoted > 0 ? 'voted' : ''}`}>
                              SA {saVoted > 0 ? '✓' : '...'}
                            </span>
                          )}
                          {currentStory.needsDev && (
                            <span className={`vote-indicator dev ${devVoted > 0 ? 'voted' : ''}`}>
                              DEV {devVoted > 0 ? '✓' : '...'}
                            </span>
                          )}
                          {currentStory.needsQa && (
                            <span className={`vote-indicator qa ${qaVoted > 0 ? 'voted' : ''}`}>
                              QA {qaVoted > 0 ? '✓' : '...'}
                            </span>
                          )}
                        </div>
                      )
                    })()}
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
                    {currentStory.needsSa && (
                      <div className="vote-column">
                        <h5>SA</h5>
                        {currentStory.votes.filter(v => v.voterRole === 'SA').map(vote => (
                          <div key={vote.id} className="vote-result">
                            <span>{vote.voterDisplayName || vote.voterAccountId}</span>
                            <span className="vote-value">{vote.voteHours === -1 ? '?' : vote.voteHours}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    {currentStory.needsDev && (
                      <div className="vote-column">
                        <h5>DEV</h5>
                        {currentStory.votes.filter(v => v.voterRole === 'DEV').map(vote => (
                          <div key={vote.id} className="vote-result">
                            <span>{vote.voterDisplayName || vote.voterAccountId}</span>
                            <span className="vote-value">{vote.voteHours === -1 ? '?' : vote.voteHours}</span>
                          </div>
                        ))}
                      </div>
                    )}
                    {currentStory.needsQa && (
                      <div className="vote-column">
                        <h5>QA</h5>
                        {currentStory.votes.filter(v => v.voterRole === 'QA').map(vote => (
                          <div key={vote.id} className="vote-result">
                            <span>{vote.voterDisplayName || vote.voterAccountId}</span>
                            <span className="vote-value">{vote.voteHours === -1 ? '?' : vote.voteHours}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Facilitator: Set final estimate */}
                  {isFacilitator && (
                    <div className="final-estimate-form">
                      <h4>Финальная оценка:</h4>
                      <div className="final-inputs">
                        {currentStory.needsSa && (
                          <div className="final-input-group">
                            <label>SA (часы)</label>
                            <input
                              type="number"
                              value={finalSa}
                              onChange={e => setFinalSa(e.target.value)}
                              min="0"
                              max="100"
                            />
                          </div>
                        )}
                        {currentStory.needsDev && (
                          <div className="final-input-group">
                            <label>DEV (часы)</label>
                            <input
                              type="number"
                              value={finalDev}
                              onChange={e => setFinalDev(e.target.value)}
                              min="0"
                              max="100"
                            />
                          </div>
                        )}
                        {currentStory.needsQa && (
                          <div className="final-input-group">
                            <label>QA (часы)</label>
                            <input
                              type="number"
                              value={finalQa}
                              onChange={e => setFinalQa(e.target.value)}
                              min="0"
                              max="100"
                            />
                          </div>
                        )}
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
                    Следующая стори →
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
            {participants.map(p => (
              <div key={p.accountId} className={`participant-item ${p.isOnline ? 'online' : 'offline'}`}>
                <span className="participant-name">
                  {p.displayName}
                  {p.isFacilitator && ' 👑'}
                </span>
                <span className={`participant-role role-${p.role.toLowerCase()}`}>
                  {p.role}
                </span>
              </div>
            ))}
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
                <label>
                  <input
                    type="checkbox"
                    checked={newStoryNeedsSa}
                    onChange={e => setNewStoryNeedsSa(e.target.checked)}
                  />
                  Аналитика (SA)
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={newStoryNeedsDev}
                    onChange={e => setNewStoryNeedsDev(e.target.checked)}
                  />
                  Разработка (DEV)
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={newStoryNeedsQa}
                    onChange={e => setNewStoryNeedsQa(e.target.checked)}
                  />
                  Тестирование (QA)
                </label>
              </div>
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setShowAddStory(false)}>
                Отмена
              </button>
              <button
                className="btn btn-primary"
                onClick={handleAddStory}
                disabled={!newStoryTitle.trim() || (!newStoryNeedsSa && !newStoryNeedsDev && !newStoryNeedsQa)}
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
                        {story.hasSaSubtask && (
                          <span className="subtask-badge sa">
                            SA {story.saEstimate ? `${story.saEstimate}ч` : '—'}
                          </span>
                        )}
                        {story.hasDevSubtask && (
                          <span className="subtask-badge dev">
                            DEV {story.devEstimate ? `${story.devEstimate}ч` : '—'}
                          </span>
                        )}
                        {story.hasQaSubtask && (
                          <span className="subtask-badge qa">
                            QA {story.qaEstimate ? `${story.qaEstimate}ч` : '—'}
                          </span>
                        )}
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
