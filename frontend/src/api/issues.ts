import axios from 'axios'

// F85 — per-issue detail for board hover tooltips (any issue type).
export interface IssueDetail {
  issueKey: string
  issueType: string
  summary: string
  description: string | null
}

export async function getIssueDetail(issueKey: string, signal?: AbortSignal): Promise<IssueDetail> {
  const response = await axios.get<IssueDetail>(`/api/issues/${issueKey}/detail`, { signal })
  return response.data
}
