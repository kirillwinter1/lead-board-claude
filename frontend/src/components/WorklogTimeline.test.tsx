import { describe, it, expect, vi } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { WorklogTimeline } from './WorklogTimeline'
import { teamsApi } from '../api/teams'

vi.mock('../api/teams', () => ({ teamsApi: { getWorklogTimeline: vi.fn() } }))
vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getRoleColor: () => '#888888',
    getRoleDisplayName: (r: string) => r,
  }),
}))

// Local (not UTC) YYYY-MM-DD — the calendar day the user actually sees.
function localDateStr(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

describe('WorklogTimeline default range', () => {
  it('fetches a 30-day range ending today in LOCAL time', async () => {
    // Bug reproduction: the default range is built with toISOString().slice(0, 10)
    // on a date pinned to LOCAL midnight. In any TZ east of UTC (the project runs
    // in UTC+3) local midnight is still "yesterday" in UTC, so `to` is always the
    // previous day: the today column never exists and today's worklogs are hidden.
    // The component's own comment states the contract: "30 days including today".
    // Run under TZ=Europe/Moscow for a deterministic red.
    vi.mocked(teamsApi.getWorklogTimeline).mockResolvedValue({
      from: '',
      to: '',
      days: [],
      members: [],
    })

    render(
      <MemoryRouter>
        <WorklogTimeline teamId={1} />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(teamsApi.getWorklogTimeline).toHaveBeenCalled()
    })

    const [, , to] = vi.mocked(teamsApi.getWorklogTimeline).mock.calls[0]
    expect(to).toBe(localDateStr(new Date()))
  })
})
