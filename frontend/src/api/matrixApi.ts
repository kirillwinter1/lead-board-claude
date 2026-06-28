import axios from 'axios'

// Eisenhower quadrant codes. `null` means the card is unassigned (no quadrant).
export type Quadrant = 'P1' | 'P2' | 'P3' | 'P4'

export interface MatrixCard {
  issueKey: string
  summary: string
  issueType: string
  priority: string | null
  estimateHours: number | null
  assigneeDisplayName: string | null
  status: string
  quadrant: Quadrant | null
  daysInStatus: number | null
  statusAgeLevel: 'NORMAL' | 'WARNING' | 'CRITICAL'
  statusAgeReason: string | null
}

export interface MatrixView {
  p1: MatrixCard[]
  p2: MatrixCard[]
  p3: MatrixCard[]
  p4: MatrixCard[]
  unassigned: MatrixCard[]
}

// Orphan tasks for a team, grouped by Eisenhower quadrant.
export async function getMatrix(teamId: number): Promise<MatrixView> {
  const response = await axios.get<MatrixView>('/api/matrix', { params: { teamId } })
  return response.data
}

// Assign (P1..P4) or clear (null) a card's quadrant. Returns the updated card.
export async function triage(issueKey: string, quadrant: Quadrant | null): Promise<MatrixCard> {
  const response = await axios.put<MatrixCard>('/api/matrix/triage', { issueKey, quadrant })
  return response.data
}

// ---- F78: autoplanner recommendations ----

// A recommendation card (superset of MatrixCard). For bugs, quadrant is null and
// A simple card — used for Zero Bug Policy bugs and the "needs estimation" list.
export interface RecCard {
  issueKey: string
  summary: string
  issueType: string
  priority: string | null
  estimateHours: number | null
  assigneeDisplayName: string | null
  status: string
  quadrant: Quadrant | null
  workflowRole: string | null
  daysInStatus: number | null
  statusAgeLevel: 'NORMAL' | 'WARNING' | 'CRITICAL'
  statusAgeReason: string | null
}

export interface ZeroBugPolicy {
  openBugCount: number
  bugs: RecCard[]
}

// One role's share of a story (its subtask + estimate).
export interface RoleSlice {
  roleCode: string
  subtaskKey: string
  hours: number
}

// A recommended (triaged, fully-estimated) orphan story with its role composition.
export interface StoryRec {
  issueKey: string
  summary: string
  issueType: string
  priority: string | null
  status: string
  quadrant: Quadrant | null
  roles: RoleSlice[]
  totalHours: number
  daysInStatus: number | null
  statusAgeLevel: 'NORMAL' | 'WARNING' | 'CRITICAL'
  statusAgeReason: string | null
}

export interface RecommendationView {
  zeroBugPolicy: ZeroBugPolicy
  recommended: StoryRec[]
  needsEstimation: RecCard[]
}

// Prioritised tech-debt stories + Zero Bug Policy for a team.
export async function getRecommendations(teamId: number): Promise<RecommendationView> {
  const response = await axios.get<RecommendationView>('/api/matrix/recommendations', { params: { teamId } })
  return response.data
}
