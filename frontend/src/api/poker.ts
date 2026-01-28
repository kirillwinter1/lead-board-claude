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
  hasSaSubtask: boolean
  hasDevSubtask: boolean
  hasQaSubtask: boolean
  saEstimate: number | null
  devEstimate: number | null
  qaEstimate: number | null
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
  needsSa: boolean
  needsDev: boolean
  needsQa: boolean
  status: 'PENDING' | 'VOTING' | 'REVEALED' | 'COMPLETED'
  finalSaHours: number | null
  finalDevHours: number | null
  finalQaHours: number | null
  orderIndex: number
  votes: PokerVote[]
}

export interface PokerVote {
  id: number
  voterAccountId: string
  voterDisplayName: string | null
  voterRole: 'SA' | 'DEV' | 'QA'
  voteHours: number | null
  hasVoted: boolean
  votedAt: string | null
}

export interface ParticipantInfo {
  accountId: string
  displayName: string
  role: 'SA' | 'DEV' | 'QA'
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

export async function getSessionsByTeam(teamId: number): Promise<PokerSession[]> {
  const response = await axios.get(`/api/poker/sessions/team/${teamId}`)
  return response.data
}

export async function startSession(sessionId: number): Promise<PokerSession> {
  const response = await axios.post(`/api/poker/sessions/${sessionId}/start`)
  return response.data
}

export async function completeSession(sessionId: number): Promise<PokerSession> {
  const response = await axios.post(`/api/poker/sessions/${sessionId}/complete`)
  return response.data
}

export interface AddStoryRequest {
  title: string
  needsSa: boolean
  needsDev: boolean
  needsQa: boolean
  existingStoryKey?: string
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

export async function deleteStory(storyId: number): Promise<void> {
  await axios.delete(`/api/poker/stories/${storyId}`)
}

export async function getStories(sessionId: number): Promise<PokerStory[]> {
  const response = await axios.get(`/api/poker/sessions/${sessionId}/stories`)
  return response.data
}

export async function revealVotes(storyId: number): Promise<PokerStory> {
  const response = await axios.post(`/api/poker/stories/${storyId}/reveal`)
  return response.data
}

export interface SetFinalRequest {
  saHours: number | null
  devHours: number | null
  qaHours: number | null
}

export async function setFinalEstimate(
  storyId: number,
  request: SetFinalRequest,
  updateJira: boolean = true
): Promise<PokerStory> {
  const response = await axios.post(
    `/api/poker/stories/${storyId}/final?updateJira=${updateJira}`,
    { storyId, ...request }
  )
  return response.data
}

export async function moveToNextStory(sessionId: number): Promise<PokerStory | null> {
  const response = await axios.post(`/api/poker/sessions/${sessionId}/next`)
  return response.status === 204 ? null : response.data
}

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
