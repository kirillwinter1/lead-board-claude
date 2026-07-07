import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getConfig } from '../api/config'
import { teamsApi, TeamMember } from '../api/teams'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { useAuth, type AuthUser } from '../contexts/AuthContext'
import { Modal } from '../components/Modal'
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

// Muted line icons for hero/empty states — replaces decorative emoji
function IconStack({ size = 44 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="4" width="18" height="4" rx="1" />
      <rect x="3" y="10" width="18" height="4" rx="1" />
      <rect x="3" y="16" width="18" height="4" rx="1" />
    </svg>
  )
}

function IconCheckCircle({ size = 44 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M8.5 12.5l2.5 2.5 4.5-5" />
    </svg>
  )
}

export function PokerRoomPage() {
  const { roomCode } = useParams<{ roomCode: string }>()
  const navigate = useNavigate()
  const { getRoleCodes, getRoleColor, getRoleDisplayName } = useWorkflowConfig()
  const auth = useAuth()

  const [session, setSession] = useState<PokerSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // Auth state from context
  const authUser: AuthUser | null = auth.user
  const authLoading = auth.loading
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

  // Show error if not authenticated
  useEffect(() => {
    if (!auth.loading && !auth.authenticated) {
      setError('You must be signed in to join the session')
    }
  }, [auth.loading, auth.authenticated])

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

        // Facilitator = session creator; the server independently verifies this
        // from the authenticated account on every WS/REST action
        setIsFacilitator(sessionData.facilitatorAccountId === authUser.accountId)

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
        setError('Failed to load session: ' + err.message)
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
    return <main className="main-content"><div className="loading">Loading room...</div></main>
  }

  if (error && !session) {
    return (
      <main className="main-content">
        <div className="error">{error}</div>
        <button className="btn btn-secondary" onClick={() => navigate('/poker')}>
          Back to list
        </button>
      </main>
    )
  }

  if (!session) {
    return <main className="main-content"><div className="error">Session not found</div></main>
  }

  // Role selector modal
  if (showRoleSelector && !userRole) {
    const roleCodes = getRoleCodes()
    return (
      <main className="main-content">
        <div className="modal-overlay" style={{ position: 'relative', background: 'transparent' }}>
          <div className="modal-content" style={{ marginTop: 80 }}>
            <h3>Select your role</h3>
            <p style={{ color: '#6b778c', marginBottom: 16 }}>
              You're not on this team — pick a role to vote:
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
          <button className="btn btn-secondary" onClick={() => navigate('/poker')}>
            &#8592; Back
          </button>
          <div className="poker-epic-heading">
            <div className="poker-epic-heading-top">
              <a
                href={`${jiraBaseUrl}${session.epicKey}`}
                target="_blank"
                rel="noopener noreferrer"
                className="issue-key"
              >
                {session.epicKey}
              </a>
              <span className="poker-epic-tag">Planning Poker</span>
            </div>
            <h2 className="poker-epic-title">{session.epicSummary || session.epicKey}</h2>
          </div>
        </div>
        <div className="poker-header-right">
          <button
            className={`poker-copy-code-btn ${copied ? 'copied' : ''}`}
            onClick={handleCopyRoomCode}
            title="Copy room code"
          >
            <code>{session.roomCode}</code>
            <span className="copy-icon">{copied ? '\u2713' : '\u2398'}</span>
          </button>
          <div className={`poker-connection-dot ${connected ? 'connected' : 'disconnected'}`} title={connected ? 'Connected' : 'Disconnected'} />
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

      {session.epicDescription && session.epicDescription.trim() && (
        <div className="poker-epic-desc">
          <p title={session.epicDescription}>{session.epicDescription}</p>
        </div>
      )}

      {error && <div className="error" style={{ marginBottom: 16, marginLeft: 24, marginRight: 24 }}>{error}</div>}

      <div className="poker-layout">
        {/* Left sidebar - Stories list */}
        <div className="poker-sidebar">
          <div className="poker-sidebar-header">
            <div>
              <h3>Stories ({stories.length})</h3>
              {stories.length > 0 && (
                <span className="poker-stories-progress">{completedCount}/{stories.length} estimated</span>
              )}
            </div>
            {isFacilitator && session.status === 'PREPARING' && (
              <div style={{ display: 'flex', gap: 4 }}>
                <button
                  className="btn btn-secondary btn-small"
                  onClick={handleOpenImportStories}
                  title="Import from Jira"
                >
                  &#8595; Import
                </button>
                <button className="btn btn-primary btn-small" onClick={() => setShowAddStory(true)} title="Create new">
                  + New
                </button>
              </div>
            )}
          </div>

          <div className="poker-stories-list">
            {stories.length === 0 ? (
              <div className="poker-stories-empty">
                <div className="poker-stories-empty-icon"><IconStack size={28} /></div>
                <p>No stories yet</p>
                {isFacilitator && session.status === 'PREPARING' && (
                  <small>Import from Jira or add manually</small>
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
                          {role}: {story.finalEstimates[role] ?? 0}h
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
              <button
                className="btn btn-primary"
                style={{ width: '100%' }}
                onClick={handleStartSession}
                disabled={!connected}
                title={connected ? undefined : 'No connection to server'}
              >
                Start session
              </button>
            </div>
          )}
        </div>

        {/* Main area */}
        <div className="poker-main">
          {session.status === 'PREPARING' ? (
            <div className="poker-preparing">
              <div className="poker-preparing-icon"><IconStack /></div>
              <h3>Session setup</h3>
              {isFacilitator ? (
                <div className="poker-preparing-steps">
                  <div className="poker-step">
                    <span className="poker-step-num">1</span>
                    <span>Import stories from Jira or add them manually</span>
                  </div>
                  <div className="poker-step">
                    <span className="poker-step-num">2</span>
                    <span>Wait for participants to join</span>
                  </div>
                  <div className="poker-step">
                    <span className="poker-step-num">3</span>
                    <span>Click "Start session"</span>
                  </div>
                  {stories.length === 0 && (
                    <div className="poker-preparing-actions">
                      <button className="btn btn-secondary" onClick={handleOpenImportStories}>
                        &#8595; Import from Jira
                      </button>
                      <button className="btn btn-primary" onClick={() => setShowAddStory(true)}>
                        + New story
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <p>The facilitator is preparing stories. Please wait for the session to start.</p>
              )}
            </div>
          ) : session.status === 'COMPLETED' ? (
            <div className="poker-completed">
              <div className="poker-completed-icon"><IconCheckCircle /></div>
              <h3>Session completed</h3>
              <p>All stories estimated ({completedCount} of {stories.length})</p>
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
                      <p>Your estimate ({userRole}):</p>
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
                      <button
                        className="btn btn-primary"
                        onClick={handleReveal}
                        disabled={!connected}
                        title={connected ? undefined : 'No connection to server'}
                      >
                        Reveal cards
                      </button>
                    </div>
                  )}
                </>
              )}

              {/* Revealed votes */}
              {currentStory.status === 'REVEALED' && (
                <div className="votes-revealed">
                  <h4>Voting results:</h4>
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
                      <h4>Final estimate:</h4>
                      <div className="final-inputs">
                        {currentStory.needsRoles.map(role => (
                          <div key={role} className="final-input-group">
                            <label>{role} (hours)</label>
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
                        <button
                          className="btn btn-primary"
                          onClick={handleSetFinal}
                          disabled={!connected}
                          title={connected ? undefined : 'No connection to server'}
                        >
                          Save and next
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Story completed - next button */}
              {currentStory.status === 'COMPLETED' && isFacilitator && (
                <div className="facilitator-controls">
                  <button
                    className="btn btn-primary"
                    onClick={handleNextStory}
                    disabled={!connected}
                    title={connected ? undefined : 'No connection to server'}
                  >
                    Next story &#8594;
                  </button>
                </div>
              )}
            </>
          ) : (
            <div className="poker-no-story">
              <p>No active story to estimate</p>
            </div>
          )}
        </div>

        {/* Right sidebar - Participants */}
        <div className="poker-participants">
          <h3>Participants ({participants.length})</h3>
          <div className="participants-list">
            {participants.length === 0 ? (
              <div className="poker-participants-empty">
                <p>Waiting for participants</p>
                <div className="poker-participants-share">
                  <small>Share the room code:</small>
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
                    {p.isFacilitator && <span className="participant-facilitator-tag">facilitator</span>}
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
      <Modal isOpen={showAddStory} onClose={() => setShowAddStory(false)} title="Add story">
        <div className="poker-add-story">
          <div className="poker-field">
            <label className="poker-field-label">Story title</label>
            <input
              type="text"
              className="filter-input"
              placeholder="e.g. Login-by-code screen"
              value={newStoryTitle}
              onChange={e => setNewStoryTitle(e.target.value)}
              autoFocus
            />
          </div>
          <div className="poker-field">
            <label className="poker-field-label">Roles to estimate</label>
            <div className="poker-role-toggles">
              {getRoleCodes().map(code => {
                const active = newStoryNeedsRoles.has(code)
                const color = getRoleColor(code)
                return (
                  <button
                    type="button"
                    key={code}
                    className={`poker-role-toggle ${active ? 'active' : ''}`}
                    style={active ? { borderColor: color, background: color + '14', color } : undefined}
                    onClick={() => handleToggleNewStoryRole(code)}
                  >
                    <span className="poker-role-dot" style={{ background: active ? color : '#c1c7d0' }} />
                    <span className="poker-role-toggle-name">{getRoleDisplayName(code)}</span>
                    <span className="poker-role-toggle-code">{code}</span>
                  </button>
                )
              })}
            </div>
          </div>
        </div>
        <div className="modal-actions">
          <button className="btn btn-secondary" onClick={() => setShowAddStory(false)}>
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleAddStory}
            disabled={!newStoryTitle.trim() || newStoryNeedsRoles.size === 0}
          >
            Add
          </button>
        </div>
      </Modal>

      {/* Import Stories Modal */}
      <Modal
        isOpen={showImportStories}
        onClose={() => setShowImportStories(false)}
        title="Import stories from Jira"
        maxWidth={600}
      >
        <p className="modal-description">
          Select existing stories from epic {session?.epicKey}
        </p>

        {loadingExisting ? (
          <div className="loading-small">Loading stories...</div>
        ) : existingStories.length === 0 ? (
          <div className="empty-hint">
            No stories available to import.
            <br />
            <small>All stories are already added or the epic is empty.</small>
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
                        {role} {story.roleEstimates[role] ? `${story.roleEstimates[role]}h` : '\u2014'}
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
            Cancel
          </button>
          <button
            className="btn btn-primary"
            onClick={handleImportSelected}
            disabled={selectedImportKeys.size === 0}
          >
            Import ({selectedImportKeys.size})
          </button>
        </div>
      </Modal>
    </main>
  )
}
