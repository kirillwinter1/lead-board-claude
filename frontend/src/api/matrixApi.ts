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
