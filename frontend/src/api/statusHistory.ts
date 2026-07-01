import axios from 'axios'

// F81 — chronological status journey for the status-age tooltip.
export interface StatusSegment {
  status: string
  durationSeconds: number
  current: boolean
}

export interface StatusHistory {
  issueKey: string
  currentStatus: string
  totalSeconds: number
  segments: StatusSegment[]
}

export async function getStatusHistory(issueKey: string, signal?: AbortSignal): Promise<StatusHistory> {
  const response = await axios.get<StatusHistory>(`/api/issues/${issueKey}/status-history`, { signal })
  return response.data
}

// Compact duration: >= 1 day -> "Nд", >= 1 hour -> "Nч", else "<1ч".
export function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400)
  if (days >= 1) return `${days}д`
  const hours = Math.floor(seconds / 3600)
  if (hours >= 1) return `${hours}ч`
  return '<1ч'
}
