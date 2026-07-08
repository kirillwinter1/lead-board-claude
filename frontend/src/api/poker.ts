import axios from 'axios'

// ===== Eligible Epics =====

export interface EligibleEpic {
  epicKey: string
  summary: string
  status: string
  hasPokerSession: boolean
}

export async function getEligibleEpics(teamId: number): Promise<EligibleEpic[]> {
  const response = await axios.get(`/api/poker/eligible-epics/${teamId}`)
  return response.data
}

export interface EpicStory {
  storyKey: string
  summary: string
  status: string
  subtaskRoles: string[]
  roleEstimates: Record<string, number | null>
}

export async function getEpicStories(epicKey: string): Promise<EpicStory[]> {
  const response = await axios.get(`/api/poker/epic-stories/${epicKey}`)
  return response.data
}

// ===== Types =====

export interface PokerSession {
  id: number
  teamId: number
  epicKey: string
  epicSummary: string | null
  epicDescription: string | null
  facilitatorAccountId: string
  status: 'PREPARING' | 'ACTIVE' | 'COMPLETED'
  roomCode: string
  createdAt: string
  startedAt: string | null
  completedAt: string | null
  stories: PokerStory[]
  currentStoryId: number | null
}

export interface PokerStory {
  id: number
  storyKey: string | null
  title: string
  description: string | null
  needsRoles: string[]
  status: 'PENDING' | 'VOTING' | 'REVEALED' | 'COMPLETED'
  finalEstimates: Record<string, number | null>
  orderIndex: number
  votes: PokerVote[]
}

export interface PokerVote {
  id: number
  voterAccountId: string
  voterDisplayName: string | null
  voterRole: string
  voteHours: number | null
  hasVoted: boolean
  votedAt: string | null
}

export interface ParticipantInfo {
  accountId: string
  displayName: string
  role: string
  isFacilitator: boolean
  isOnline: boolean
}

export interface SessionState {
  sessionId: number
  teamId: number
  epicKey: string
  status: string
  roomCode: string
  facilitatorAccountId: string
  stories: PokerStory[]
  currentStoryId: number | null
  participants: ParticipantInfo[]
}

// ===== API =====

export async function createSession(teamId: number, epicKey: string): Promise<PokerSession> {
  const response = await axios.post('/api/poker/sessions', { teamId, epicKey })
  return response.data
}

export async function getSession(id: number): Promise<PokerSession> {
  const response = await axios.get(`/api/poker/sessions/${id}`)
  return response.data
}

export async function getSessionByRoomCode(roomCode: string): Promise<PokerSession> {
  const response = await axios.get(`/api/poker/sessions/room/${roomCode}`)
  return response.data
}

// Room is addressed by epic key (F23). Returns the active (PREPARING/ACTIVE) session
// for the epic, or the most recent one. 404 if the epic has no session.
export async function getSessionByEpicKey(epicKey: string): Promise<PokerSession> {
  const response = await axios.get(`/api/poker/sessions/epic/${epicKey}`)
  return response.data
}

// ===== Jira project components (for the Add-story selector) =====

export interface JiraComponent {
  id: string
  name: string
}

// TODO(F23 backend): GET /api/poker/projects/{projectKey}/components — pulls Jira
// project components via JiraClient + JiraConfigResolver. Returns [] until wired.
export async function getProjectComponents(projectKey: string): Promise<JiraComponent[]> {
  const response = await axios.get(`/api/poker/projects/${projectKey}/components`)
  return response.data
}

export async function getSessionsByTeam(teamId: number): Promise<PokerSession[]> {
  const response = await axios.get(`/api/poker/sessions/team/${teamId}`)
  return response.data
}

// Note: startSession/completeSession are done via WebSocket

export interface AddStoryRequest {
  title: string
  needsRoles: string[]
  existingStoryKey?: string
  // F23: a new story is created in Jira together with its subtasks; description
  // and component are collected in the Add-story form.
  description?: string
  component?: string
}

export async function addStory(
  sessionId: number,
  request: AddStoryRequest,
  createInJira: boolean = true
): Promise<PokerStory> {
  const response = await axios.post(
    `/api/poker/sessions/${sessionId}/stories?createInJira=${createInJira}`,
    request
  )
  return response.data
}

// ===== Session summary (Completed screen: rough → poker → Δ) =====

export interface SummaryStoryRow {
  storyKey: string | null
  title: string
  // role code -> final estimate in hours
  finalEstimates: Record<string, number>
  totalHours: number
}

// Rough vs poker comparison per role — server-computed in person-days (1 d = 8 h).
export interface SummaryRoleComparison {
  role: string
  roughDays: number
  pokerDays: number
  deltaDays: number // pokerDays − roughDays (signed)
}

export interface SessionSummary {
  sessionId: number
  epicKey: string
  stories: SummaryStoryRow[]
  totalPokerDays: number
  comparison: SummaryRoleComparison[]
  roughTotalDays: number
  pokerTotalDays: number
  errorDays: number // Σ|pokerDays_role − roughDays_role| (under- and over-estimate both count)
  errorPercent: number // errorDays / roughTotalDays * 100
}

// GET /api/poker/sessions/{id}/summary — rough vs poker per role (in days), Δ and
// planning error. The Completed screen shows the comparison only when rough exists.
export async function getSessionSummary(sessionId: number): Promise<SessionSummary> {
  const response = await axios.get(`/api/poker/sessions/${sessionId}/summary`)
  return response.data
}

// ===== Publish to Jira (final estimates → subtask Original Estimate) =====

export interface PublishStoryResult {
  storyId: number
  storyKey: string | null
  title: string
  status: 'ok' | 'error'
  message: string | null
  subtaskKeys: Record<string, string> // role code -> subtask key
}

export interface PublishResult {
  sessionId: number
  stories: PublishStoryResult[]
}

// POST /api/poker/sessions/{id}/publish — writes each story's final role estimate to
// the matching subtask's Original Estimate (creating missing subtasks). Idempotent,
// facilitator-only.
export async function publishSession(sessionId: number): Promise<PublishResult> {
  const response = await axios.post(`/api/poker/sessions/${sessionId}/publish`)
  return response.data
}

// ===== Day helpers (1 d = 8 h) =====

export const HOURS_PER_DAY = 8

// Format hours as compact person-days: 12h -> "1.5d", 24h -> "3d", 0 -> "0d".
export function formatDays(hours: number): string {
  const days = hours / HOURS_PER_DAY
  const rounded = Math.round(days * 100) / 100
  const text = Number.isInteger(rounded) ? String(rounded) : String(rounded)
  return `${text}d`
}

// Signed delta in days for the rough → poker comparison: "+0.5d", "−0.5d", "0".
export function formatDeltaDays(deltaHours: number): string {
  const days = Math.round((deltaHours / HOURS_PER_DAY) * 100) / 100
  if (days === 0) return '0'
  const sign = days > 0 ? '+' : '−' // minus sign
  return `${sign}${Math.abs(days)}d`
}

// Format a value ALREADY in days (server comparison block): 1.5 -> "1.5d".
export function formatDayValue(days: number): string {
  return `${Math.round(days * 100) / 100}d`
}

// Signed days already in days: 0.5 -> "+0.5d", -0.5 -> "−0.5d", 0 -> "0".
export function formatDeltaDayValue(days: number): string {
  const r = Math.round(days * 100) / 100
  if (r === 0) return '0'
  return `${r > 0 ? '+' : '−'}${Math.abs(r)}d`
}

// Note: revealVotes, setFinalEstimate, moveToNextStory are done via WebSocket.
// Story deletion is not exposed in the UI yet (REST DELETE /api/poker/stories/{id} exists, facilitator-only).

export async function getVotes(storyId: number): Promise<PokerVote[]> {
  const response = await axios.get(`/api/poker/stories/${storyId}/votes`)
  return response.data
}

// ===== WebSocket Message Types =====

export type PokerMessageType =
  | 'JOIN'
  | 'VOTE'
  | 'REVEAL'
  | 'SET_FINAL'
  | 'NEXT_STORY'
  | 'START_SESSION'
  | 'COMPLETE_SESSION'
  | 'STATE'
  | 'PARTICIPANT_JOINED'
  | 'PARTICIPANT_LEFT'
  | 'VOTE_CAST'
  | 'VOTES_REVEALED'
  | 'STORY_COMPLETED'
  | 'CURRENT_STORY_CHANGED'
  | 'SESSION_STARTED'
  | 'SESSION_COMPLETED'
  | 'STORY_ADDED'
  | 'ERROR'

export interface PokerMessage {
  type: PokerMessageType
  payload: Record<string, unknown>
}
