import axios from 'axios'

export interface ScoreBreakdown {
  issueKey: string
  issueType: string
  totalScore: number | null
  breakdown: {
    [factorName: string]: number
  }
}

/**
 * Get AutoScore breakdown for an issue (epic or story).
 * Shows how each factor contributes to the total score.
 */
export async function getScoreBreakdown(issueKey: string): Promise<ScoreBreakdown> {
  const response = await axios.get(`/api/board/${issueKey}/score-breakdown`)
  return response.data
}
