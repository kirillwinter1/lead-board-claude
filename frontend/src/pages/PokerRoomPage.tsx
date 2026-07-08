import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getConfig } from '../api/config'
import { teamsApi, TeamMember } from '../api/teams'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { useAuth, type AuthUser } from '../contexts/AuthContext'
import { Modal } from '../components/Modal'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { getIssueIcon } from '../components/board/helpers'
import { hexToRgba } from '../constants/colors'
import './PlanningPokerPage.css'
import {
  PokerSession,
  PokerStory,
  PokerVote,
  ParticipantInfo,
  SessionState,
  EpicStory,
  JiraComponent,
  SessionSummary,
  getSessionByEpicKey,
  getEpicStories,
  getProjectComponents,
  getSessionSummary,
  publishSession,
  addStory,
  AddStoryRequest,
  formatDays,
  formatDayValue,
  formatDeltaDayValue,
  apiError,
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

function IconLink({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9.5 13.5a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 0 0-5.7-5.7l-1.2 1.2" />
      <path d="M14.5 10.5a4 4 0 0 0-5.7 0L6.5 12.8a4 4 0 0 0 5.7 5.7l1.2-1.2" />
    </svg>
  )
}

// ---- stats helpers ----

interface VoteStats {
  avg: number
  median: number
  min: number
  max: number
  count: number
}

function computeStats(hours: number[]): VoteStats | null {
  const valid = hours.filter(h => h !== null && h >= 0) // drop null and "?" (-1)
  if (valid.length === 0) return null
  const sorted = [...valid].sort((a, b) => a - b)
  const sum = sorted.reduce((a, b) => a + b, 0)
  const avg = sum / sorted.length
  const mid = Math.floor(sorted.length / 2)
  const median = sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2
  return { avg, median, min: sorted[0], max: sorted[sorted.length - 1], count: sorted.length }
}

function fmtNum(n: number): string {
  const r = Math.round(n * 10) / 10
  return String(r)
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}

function shortName(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length <= 1) return name
  return `${parts[0]} ${parts[parts.length - 1][0]}.`
}

export function PokerRoomPage() {
  const { epicKey } = useParams<{ epicKey: string }>()
  const navigate = useNavigate()
  const {
    getRoleCodes, getRoleColor, getRoleDisplayName,
    getTypeNameByCategory, getIssueTypeIconUrl,
  } = useWorkflowConfig()
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

  // Copy link
  const [copied, setCopied] = useState(false)

  // Add story modal
  const [showAddStory, setShowAddStory] = useState(false)
  const [newStoryTitle, setNewStoryTitle] = useState('')
  const [newStoryDescription, setNewStoryDescription] = useState('')
  const [newStoryComponent, setNewStoryComponent] = useState<string | null>(null)
  const [newStoryNeedsRoles, setNewStoryNeedsRoles] = useState<Set<string>>(new Set(getRoleCodes()))
  const [components, setComponents] = useState<JiraComponent[]>([])
  const [addingStory, setAddingStory] = useState(false)

  // Import existing stories modal
  const [showImportStories, setShowImportStories] = useState(false)
  const [existingStories, setExistingStories] = useState<EpicStory[]>([])
  const [loadingExisting, setLoadingExisting] = useState(false)
  const [selectedImportKeys, setSelectedImportKeys] = useState<Set<string>>(new Set())

  // Final estimate inputs - dynamic map
  const [finalEstimateInputs, setFinalEstimateInputs] = useState<Record<string, string>>({})

  // Completed-screen summary (rough → poker → Δ)
  const [summary, setSummary] = useState<SessionSummary | null>(null)
  const [publishing, setPublishing] = useState(false)
  const [publishDone, setPublishDone] = useState(false)

  // Issue-type icons (from workflow config)
  const epicTypeName = getTypeNameByCategory('EPIC')
  const storyTypeName = getTypeNameByCategory('STORY')
  const epicIcon = getIssueIcon(epicTypeName || 'Epic', getIssueTypeIconUrl(epicTypeName), 'EPIC')
  const storyIcon = getIssueIcon(storyTypeName || 'Story', getIssueTypeIconUrl(storyTypeName), 'STORY')

  const projectKey = useMemo(() => (epicKey ? epicKey.split('-')[0] : ''), [epicKey])

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

  // Load session by epic key + determine role once auth is ready
  useEffect(() => {
    if (!epicKey || !authUser) return

    Promise.all([
      getSessionByEpicKey(epicKey),
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
            setShowRoleSelector(true)
          }
        } catch {
          setShowRoleSelector(true)
        }

        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load session: ' + err.message)
        setLoading(false)
      })
  }, [epicKey, authUser])

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
    setFinalEstimateInputs({})
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

  // WebSocket connection -- only connect when we have auth + role.
  // Rooms are addressed by epic key in the URL, but the WS channel still uses the
  // server-issued room code carried on the session object.
  const wsReady = !!authUser && !!userRole && !!session
  const {
    connected,
    sendVote,
    sendReveal,
    sendSetFinal,
    sendNextStory,
    sendStartSession,
  } = usePokerWebSocket({
    roomCode: wsReady ? (session?.roomCode || '') : '',
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

  // Current story
  const currentStory = stories.find(s => s.id === currentStoryId)

  // Per-role vote stats for the current story (used by Revealed columns + median prefill)
  const roleStats = useMemo(() => {
    const map: Record<string, VoteStats | null> = {}
    if (currentStory) {
      for (const role of currentStory.needsRoles) {
        const hours = currentStory.votes
          .filter(v => v.voterRole === role && v.voteHours !== null)
          .map(v => v.voteHours as number)
        map[role] = computeStats(hours)
      }
    }
    return map
  }, [currentStory])

  // Prefill final-estimate inputs with the median when votes are revealed
  useEffect(() => {
    if (currentStory && currentStory.status === 'REVEALED') {
      setFinalEstimateInputs(prev => {
        // don't clobber values the facilitator already edited
        if (Object.keys(prev).length > 0) return prev
        const next: Record<string, string> = {}
        for (const role of currentStory.needsRoles) {
          const st = roleStats[role]
          next[role] = st ? fmtNum(st.median) : ''
        }
        return next
      })
    }
  }, [currentStory, roleStats])

  // Load the rough → poker comparison once the session is completed
  useEffect(() => {
    if (session && session.status === 'COMPLETED') {
      getSessionSummary(session.id)
        .then(setSummary)
        .catch(() => setSummary(null)) // endpoint may not be wired yet — degrade gracefully
    }
  }, [session?.status, session?.id])

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

  const handleCopyLink = () => {
    navigator.clipboard.writeText(window.location.href).then(() => {
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

  const handleOpenAddStory = async () => {
    setShowAddStory(true)
    setNewStoryTitle('')
    setNewStoryDescription('')
    setNewStoryComponent(null)
    if (projectKey && components.length === 0) {
      try {
        const comps = await getProjectComponents(projectKey)
        setComponents(comps)
      } catch {
        // Components are optional — the selector just stays empty if unavailable
        setComponents([])
      }
    }
  }

  const handleAddStory = async () => {
    if (!session || !newStoryTitle.trim()) return

    const request: AddStoryRequest = {
      title: newStoryTitle.trim(),
      needsRoles: Array.from(newStoryNeedsRoles),
      description: newStoryDescription.trim() || undefined,
      component: newStoryComponent || undefined,
    }

    setAddingStory(true)
    try {
      // A new story is created in Jira together with its subtasks (F23), so it
      // gets a key immediately. Final estimates are written on publish.
      const story = await addStory(session.id, request, true)
      setStories(prev => [...prev, story])
      setShowAddStory(false)
      setNewStoryTitle('')
      setNewStoryDescription('')
      setNewStoryComponent(null)
    } catch (err: unknown) {
      const message = apiError(err)
      setError('Failed to add story: ' + message)
    } finally {
      setAddingStory(false)
    }
  }

  const handleOpenImportStories = async () => {
    if (!session) return

    setShowImportStories(true)
    setLoadingExisting(true)
    setSelectedImportKeys(new Set())

    try {
      const epicStories = await getEpicStories(session.epicKey)
      const existingKeys = new Set(stories.map(s => s.storyKey))
      const availableStories = epicStories.filter(s => !existingKeys.has(s.storyKey))
      setExistingStories(availableStories)
    } catch (err: unknown) {
      const message = apiError(err)
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
      const message = apiError(err)
      setError('Failed to import stories: ' + message)
    }
  }

  const handlePublish = async () => {
    if (!session) return
    setPublishing(true)
    try {
      const result = await publishSession(session.id)
      const failed = result.stories.filter(r => r.status === 'error')
      if (failed.length > 0) {
        setError(`Published with issues: ${failed.map(f => f.storyKey || f.title || 'story').join(', ')}`)
      } else {
        setPublishDone(true)
      }
    } catch (err: unknown) {
      const message = apiError(err)
      setError('Failed to publish: ' + message)
    } finally {
      setPublishing(false)
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
      background: hexToRgba(color, 0.12),
      color: color,
    }
  }

  // Progress stats
  const completedCount = stories.filter(s => s.status === 'COMPLETED').length

  // Check if user can vote on current story
  const canVote = currentStory &&
    currentStory.status === 'VOTING' &&
    userRole &&
    currentStory.needsRoles.includes(userRole)

  // Vote counts per role (for the voting-status chips)
  const getVoteCountByRole = (story: PokerStory, role: string) =>
    story.votes.filter(v => v.voterRole === role && v.hasVoted).length
  const getRoleParticipantCount = (role: string) =>
    Math.max(1, participants.filter(p => p.role === role).length)

  // Consensus indicator for the current story
  const spreadRoles = currentStory
    ? currentStory.needsRoles.filter(role => {
        const st = roleStats[role]
        return st && st.max > st.min
      })
    : []

  // ---- Completed screen data (client-side, from stories) ----
  const completedRoleCodes = useMemo(() => {
    const present = new Set<string>()
    stories.forEach(s => s.needsRoles.forEach(r => present.add(r)))
    return getRoleCodes().filter(r => present.has(r))
  }, [stories, getRoleCodes])

  const storyRows = useMemo(() => {
    return stories.map(s => {
      const roleHours: Record<string, number> = {}
      let total = 0
      for (const role of completedRoleCodes) {
        const h = s.finalEstimates[role] ?? 0
        roleHours[role] = h
        total += h
      }
      return { key: s.storyKey, title: s.title, roleHours, total }
    })
  }, [stories, completedRoleCodes])

  const roleTotals = useMemo(() => {
    const totals: Record<string, number> = {}
    let grand = 0
    for (const role of completedRoleCodes) {
      const sum = storyRows.reduce((a, r) => a + (r.roleHours[role] ?? 0), 0)
      totals[role] = sum
      grand += sum
    }
    return { totals, grand }
  }, [storyRows, completedRoleCodes])

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

  // Role selector screen
  if (showRoleSelector && !userRole) {
    const roleCodes = getRoleCodes()
    return (
      <main className="main-content">
        <div className="poker-role-select-card">
          <h3>Select your role</h3>
          <p>You're not on this team — pick a role to vote</p>
          <div className="poker-role-select-row">
            {roleCodes.map(code => (
              <button
                key={code}
                className="poker-role-select-btn"
                style={{ background: getRoleColor(code), borderColor: getRoleColor(code) }}
                onClick={() => handleSelectRole(code)}
              >
                {getRoleDisplayName(code)}
              </button>
            ))}
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
              <img className="poker-epic-heading-icon" src={epicIcon} alt="" />
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
            className={`poker-copy-link-btn ${copied ? 'copied' : ''}`}
            onClick={handleCopyLink}
            title="Copy room link"
          >
            <IconLink />
            <span>{copied ? 'Copied' : 'Copy link'}</span>
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
            {isFacilitator && session.status !== 'COMPLETED' && (
              <div style={{ display: 'flex', gap: 4 }}>
                <button
                  className="btn btn-secondary btn-small"
                  onClick={handleOpenImportStories}
                  title="Import from Jira"
                >
                  &#8595; Import
                </button>
                <button className="btn btn-primary btn-small" onClick={handleOpenAddStory} title="Create new">
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
                    <span className="poker-story-key">
                      <img className="poker-story-icon" src={storyIcon} alt="" />
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
                    </span>
                    <span className={`story-status story-status-${story.status.toLowerCase()}`}>
                      {story.status === 'PENDING' && '○'}
                      {story.status === 'VOTING' && '◐'}
                      {story.status === 'REVEALED' && '◑'}
                      {story.status === 'COMPLETED' && '✓'}
                    </span>
                  </div>
                  <div className="poker-story-title">{story.title}</div>
                  {story.description && story.description.trim() && (
                    <div className="poker-story-sub">{story.description}</div>
                  )}
                  {story.status === 'COMPLETED' && (
                    <div className="poker-story-estimates">
                      {story.needsRoles.map(role => (
                        <span key={role} className="estimate-badge" style={roleLightStyle(role)}>
                          {role}: {formatDays(story.finalEstimates[role] ?? 0)}
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
                    <span>Start the session when everyone's ready</span>
                  </div>
                  {stories.length === 0 && (
                    <div className="poker-preparing-actions">
                      <button className="btn btn-secondary" onClick={handleOpenImportStories}>
                        &#8595; Import from Jira
                      </button>
                      <button className="btn btn-primary" onClick={handleOpenAddStory}>
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
            <CompletedPanel
              storyRows={storyRows}
              roleCodes={completedRoleCodes}
              roleTotals={roleTotals}
              storyCount={stories.length}
              summary={summary}
              getRoleColor={getRoleColor}
              getRoleDisplayName={getRoleDisplayName}
              storyIcon={storyIcon}
              isFacilitator={isFacilitator}
              publishing={publishing}
              publishDone={publishDone}
              onPublish={handlePublish}
            />
          ) : currentStory ? (
            <>
              {/* Current story */}
              <div className="poker-current-story">
                <div className="cur-head">
                  <img className="cur-story-icon" src={storyIcon} alt="" />
                  {currentStory.storyKey && (
                    <a
                      href={`${jiraBaseUrl}${currentStory.storyKey}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="issue-key"
                    >
                      {currentStory.storyKey}
                    </a>
                  )}
                  <h3 className="cur-story-title">{currentStory.title}</h3>
                </div>

                {currentStory.description && currentStory.description.trim() && (
                  <div className="cur-desc">
                    <span className="cur-desc-k">Description</span>
                    {currentStory.description}
                  </div>
                )}

                <div className="need-roles">
                  {currentStory.needsRoles.map(role => (
                    <span key={role} className="role-badge" style={roleBadgeStyle(role)}>{role}</span>
                  ))}
                </div>
              </div>

              {/* Voting area */}
              {currentStory.status === 'VOTING' && (
                <>
                  {/* Who has voted */}
                  <div className="voted-row">
                    {currentStory.needsRoles.map(role => {
                      const voted = getVoteCountByRole(currentStory, role)
                      const total = getRoleParticipantCount(role)
                      const color = getRoleColor(role)
                      const done = voted > 0
                      return (
                        <span
                          key={role}
                          className={`vchip ${done ? '' : 'wait'}`}
                          style={done ? { borderColor: color, color } : undefined}
                        >
                          <span className="vchip-tick" style={done ? { background: color, color: '#fff' } : undefined}>
                            {done ? '✓' : '···'}
                          </span>
                          {role} <span className="vcount">{voted}/{total}</span>
                        </span>
                      )
                    })}
                  </div>

                  {/* Voting cards */}
                  {canVote && (
                    <div className="voting-cards">
                      <p className="cards-label">Your estimate ({userRole})</p>
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
                  <div className="results-head">
                    <h4>Voting results</h4>
                    {spreadRoles.length > 0 ? (
                      <span className="consensus spread">Spread in {spreadRoles.join(', ')}</span>
                    ) : (
                      <span className="consensus ok">Consensus</span>
                    )}
                  </div>

                  <div className="role-cols">
                    {currentStory.needsRoles.map(role => {
                      const color = getRoleColor(role)
                      const st = roleStats[role]
                      const votes = currentStory.votes.filter(v => v.voterRole === role)
                      return (
                        <div key={role} className="role-col">
                          <div className="rc-head" style={{ background: hexToRgba(color, 0.12) }}>
                            <span className="rc-name" style={{ color }}>
                              <span className="rc-dot" style={{ background: color }} />
                              {getRoleDisplayName(role)}
                            </span>
                            {st && <span className="rc-median">med {fmtNum(st.median)}h</span>}
                          </div>
                          <div className="rc-body">
                            {votes.length === 0 ? (
                              <div className="rc-empty">No votes</div>
                            ) : votes.map(vote => (
                              <div key={`${vote.voterAccountId}-${vote.id}`} className="vote">
                                <span className="voter">
                                  <span className="vinit">{initials(vote.voterDisplayName || vote.voterAccountId)}</span>
                                  {shortName(vote.voterDisplayName || vote.voterAccountId)}
                                </span>
                                <span className="vval">{vote.voteHours === -1 ? '?' : vote.voteHours ?? '—'}</span>
                              </div>
                            ))}
                          </div>
                          {st && (
                            <div className="rc-stats">
                              <div className="stat"><span className="stat-k">Avg</span><span className="stat-v">{fmtNum(st.avg)}</span></div>
                              <div className="stat"><span className="stat-k">Median</span><span className="stat-v">{fmtNum(st.median)}</span></div>
                              <div className="stat"><span className="stat-k">Range</span><span className="stat-v">{st.min}{'–'}{st.max}</span></div>
                            </div>
                          )}
                        </div>
                      )
                    })}
                  </div>

                  {/* Facilitator: Final estimate (prefilled with medians) */}
                  {isFacilitator && (
                    <div className="final">
                      <h4>Final estimate</h4>
                      <div className="final-grid">
                        {currentStory.needsRoles.map(role => {
                          const color = getRoleColor(role)
                          const st = roleStats[role]
                          const spread = st && st.max > st.min
                          return (
                            <div key={role} className="fin-field">
                              <label>{role} · hours</label>
                              <input
                                className="fin-input"
                                style={{ borderLeft: `3px solid ${color}` }}
                                type="number"
                                value={finalEstimateInputs[role] || ''}
                                onChange={e => setFinalEstimateInputs(prev => ({ ...prev, [role]: e.target.value }))}
                                min="0"
                                max="1000"
                              />
                              {st && (
                                <div className="fin-hint">median {fmtNum(st.median)}h{spread ? ' · spread' : ''}</div>
                              )}
                            </div>
                          )
                        })}
                      </div>
                      <div className="final-actions">
                        <button
                          className="btn btn-secondary"
                          onClick={handleReveal}
                          disabled={!connected}
                          title="Re-open voting"
                        >
                          Re-vote
                        </button>
                        <button
                          className="btn btn-primary"
                          onClick={handleSetFinal}
                          disabled={!connected}
                          title={connected ? undefined : 'No connection to server'}
                        >
                          Save &amp; next story &#8594;
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
                  <small>Share the room link:</small>
                  <button className="poker-share-code-btn" onClick={handleCopyLink}>
                    <IconLink />
                    <span>{copied ? 'Copied' : 'Copy link'}</span>
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
            <label className="poker-field-label">Description</label>
            <textarea
              className="filter-input poker-textarea"
              placeholder="What needs to be done…"
              value={newStoryDescription}
              onChange={e => setNewStoryDescription(e.target.value)}
              rows={3}
            />
          </div>
          <div className="poker-field">
            <label className="poker-field-label">Component</label>
            <SingleSelectDropdown
              label="Component"
              placeholder={components.length ? 'Select component…' : 'No components available'}
              allowClear
              options={components.map(c => ({ value: c.name, label: c.name }))}
              selected={newStoryComponent}
              onChange={v => setNewStoryComponent(v)}
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
                    style={active ? { borderColor: color, background: hexToRgba(color, 0.08), color } : undefined}
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
            disabled={!newStoryTitle.trim() || newStoryNeedsRoles.size === 0 || addingStory}
          >
            {addingStory ? 'Adding…' : 'Add story'}
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
                    <img className="poker-story-icon" src={storyIcon} alt="" />
                    <span className="issue-key">{story.storyKey}</span>
                    <span className="import-story-status">{story.status}</span>
                  </div>
                  <span className="import-story-summary">{story.summary}</span>
                  <div className="import-story-subtasks">
                    {story.subtaskRoles.map(role => (
                      <span key={role} className="subtask-badge" style={roleLightStyle(role)}>
                        {role} {story.roleEstimates[role] ? `${story.roleEstimates[role]}h` : '—'}
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

// ============ Completed panel ============

interface CompletedStoryRow {
  key: string | null
  title: string
  roleHours: Record<string, number>
  total: number
}

interface CompletedPanelProps {
  storyRows: CompletedStoryRow[]
  roleCodes: string[]
  roleTotals: { totals: Record<string, number>; grand: number }
  storyCount: number
  summary: SessionSummary | null
  getRoleColor: (code: string) => string
  getRoleDisplayName: (code: string) => string
  storyIcon: string
  isFacilitator: boolean
  publishing: boolean
  publishDone: boolean
  onPublish: () => void
}

function CompletedPanel({
  storyRows, roleCodes, roleTotals, storyCount, summary,
  getRoleColor, getRoleDisplayName, storyIcon,
  isFacilitator, publishing, publishDone, onPublish,
}: CompletedPanelProps) {
  return (
    <div className="poker-completed-panel">
      <div className="done-hero"><IconCheckCircle size={28} /></div>
      <h3 className="done-title">Session completed</h3>
      <p className="done-sub">
        All {storyCount} stories estimated · {publishDone ? 'published to Jira' : 'not published yet'}
      </p>

      <div className="poker-summary-panel">
        {/* Stories & final estimates (person-days) */}
        <div className="panel-title">
          Stories &amp; final estimates <span className="panel-title-note">· person-days, 1 d = 8 h</span>
        </div>
        <div className="poker-summary-card">
          <table className="sum-table">
            <thead>
              <tr>
                <th>Story</th>
                {roleCodes.map(r => <th key={r} className="num-cell">{r}</th>)}
                <th className="num-cell">Total</th>
              </tr>
            </thead>
            <tbody>
              {storyRows.map((row, i) => (
                <tr key={row.key || i}>
                  <td>
                    <span className="story-cell">
                      <img className="poker-story-icon" src={storyIcon} alt="" />
                      {row.key && <span className="issue-key">{row.key}</span>}
                      <span className="story-cell-title">{row.key ? '· ' : ''}{row.title}</span>
                    </span>
                  </td>
                  {roleCodes.map(r => (
                    <td key={r} className="num-cell">{formatDays(row.roleHours[r] ?? 0)}</td>
                  ))}
                  <td className="num-cell sum-total">{formatDays(row.total)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td>Total · {storyCount} stories</td>
                {roleCodes.map(r => (
                  <td key={r} className="num-cell">{formatDays(roleTotals.totals[r] ?? 0)}</td>
                ))}
                <td className="num-cell">{formatDays(roleTotals.grand)}</td>
              </tr>
            </tfoot>
          </table>
        </div>

        {/* Rough → Poker comparison */}
        {summary && summary.comparison.length > 0 && (
          <>
            <div className="panel-title">
              Rough estimate (pre-planning) → Poker result <span className="panel-title-note">· person-days</span>
            </div>
            <div className="poker-summary-card">
              <table className="sum-table">
                <thead>
                  <tr>
                    <th>Role</th>
                    <th className="num-cell">Rough</th>
                    <th className="num-cell">Poker</th>
                    <th className="num-cell">{'Δ'}</th>
                  </tr>
                </thead>
                <tbody>
                  {summary.comparison.map(row => {
                    const cls = row.deltaDays > 0 ? 'over' : row.deltaDays < 0 ? 'under' : 'eq'
                    return (
                      <tr key={row.role}>
                        <td>
                          <span className="story-cell">
                            <span className="role-dot" style={{ background: getRoleColor(row.role) }} />
                            {getRoleDisplayName(row.role)}
                          </span>
                        </td>
                        <td className="num-cell">{formatDayValue(row.roughDays)}</td>
                        <td className="num-cell sum-total">{formatDayValue(row.pokerDays)}</td>
                        <td className="num-cell"><span className={`delta ${cls}`}>{formatDeltaDayValue(row.deltaDays)}</span></td>
                      </tr>
                    )
                  })}
                </tbody>
                <tfoot>
                  <tr>
                    <td>Total</td>
                    <td className="num-cell">{formatDayValue(summary.roughTotalDays)}</td>
                    <td className="num-cell">{formatDayValue(summary.pokerTotalDays)}</td>
                    <td className="num-cell">
                      <span className="delta over">{formatDayValue(summary.errorDays)}</span>
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </>
        )}

        {/* Metrics */}
        <div className="accuracy">
          <div className="acc-metric">
            <div className="acc-v acc-ink">{formatDays(roleTotals.grand).replace('d', '')}<span className="acc-pct">d</span></div>
            <div className="acc-k">Total · person-days</div>
          </div>
          {summary && (
            <div className="acc-metric last">
              <div className="acc-v">{fmtNum(summary.errorPercent)}<span className="acc-pct">%</span></div>
              <div className="acc-k">Planning error</div>
            </div>
          )}
        </div>

        {/* Publish */}
        {isFacilitator && (
          <div className="publish-foot">
            <button
              className="btn btn-primary poker-publish-btn"
              onClick={onPublish}
              disabled={publishing || publishDone}
            >
              {publishDone ? 'Published ✓' : publishing ? 'Publishing…' : 'Publish to Jira →'}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
