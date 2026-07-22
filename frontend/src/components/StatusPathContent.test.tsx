import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatusPathContent } from './StatusPathContent'
import type { StatusHistory } from '../api/statusHistory'

// Render StatusBadge as plain text — this test isn't about badge colors, and it
// avoids needing StatusStylesContext (same approach as StatusHistoryTooltip.test.tsx).
vi.mock('./board/StatusBadge', () => ({
  StatusBadge: ({ status }: { status: string }) => <span data-testid="status-badge">{status}</span>,
}))

const history: StatusHistory = {
  issueKey: 'LB-1',
  currentStatus: 'Development',
  totalSeconds: 10 * 86400,
  segments: [
    { status: 'New', durationSeconds: 2 * 86400, current: false },
    { status: 'Development', durationSeconds: 5 * 86400, current: true },
  ],
}

describe('StatusPathContent', () => {
  it('renders both segments, a "now" marker on the current one, Total and Excl rows', () => {
    render(<StatusPathContent history={history} variant="light" />)

    expect(screen.getByText('New')).toBeInTheDocument()
    expect(screen.getByText('Development')).toBeInTheDocument()
    expect(screen.getByText('now')).toBeInTheDocument()
    expect(screen.getByText('Total')).toBeInTheDocument()
    expect(screen.getByText('10d')).toBeInTheDocument()
    expect(screen.getByText('Excl. “New”')).toBeInTheDocument()
    expect(screen.getByText('8d')).toBeInTheDocument()
  })

  it('omits the Excl row for a single-segment history', () => {
    render(
      <StatusPathContent
        history={{ ...history, segments: [history.segments[1]] }}
        variant="dark"
      />
    )

    expect(screen.getByText('Total')).toBeInTheDocument()
    expect(screen.queryByText(/Excl\./)).not.toBeInTheDocument()
  })
})
