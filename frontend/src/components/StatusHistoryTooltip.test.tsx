import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { StatusHistoryTooltip } from './StatusHistoryTooltip'
import * as api from '../api/statusHistory'

// Render StatusBadge as plain text to avoid needing StatusStylesContext.
vi.mock('./board/StatusBadge', () => ({
  StatusBadge: ({ status }: { status: string }) => <span data-testid="status-badge">{status}</span>,
}))

vi.mock('../api/statusHistory', async (importOriginal) => {
  const actual = await importOriginal<typeof api>()
  return { ...actual, getStatusHistory: vi.fn() }
})

describe('formatDuration', () => {
  it('formats days, hours, and sub-hour durations', () => {
    expect(api.formatDuration(3 * 86400)).toBe('3d')
    expect(api.formatDuration(5 * 3600)).toBe('5h')
    expect(api.formatDuration(600)).toBe('<1h')
  })
})

describe('StatusHistoryTooltip', () => {
  beforeEach(() => {
    vi.mocked(api.getStatusHistory).mockReset()
  })

  const hoverTrigger = () => {
    const wrapper = screen.getByText('badge').parentElement as HTMLElement
    fireEvent.mouseEnter(wrapper)
    return wrapper
  }

  it('lazily loads and renders the status journey on hover', async () => {
    vi.mocked(api.getStatusHistory).mockResolvedValue({
      issueKey: 'LB-1',
      currentStatus: 'Development',
      totalSeconds: 9 * 86400,
      segments: [
        { status: 'New', durationSeconds: 2 * 86400, current: false },
        { status: 'Analysis', durationSeconds: 3 * 86400, current: false },
        { status: 'Development', durationSeconds: 4 * 86400, current: true },
      ],
    })

    render(<StatusHistoryTooltip issueKey="LB-1"><span>badge</span></StatusHistoryTooltip>)
    hoverTrigger()

    await waitFor(() => expect(api.getStatusHistory).toHaveBeenCalledWith('LB-1', expect.anything()))
    expect(await screen.findByText('New')).toBeInTheDocument()
    expect(screen.getByText('Development')).toBeInTheDocument()
    // current status highlighted with a "сейчас" marker
    expect(screen.getByText('now')).toBeInTheDocument()
    // total + "without New" total (9д total − 2д New = 7д)
    expect(screen.getByText('Total')).toBeInTheDocument()
    expect(screen.getByText('9d')).toBeInTheDocument() // total
    expect(screen.getByText('Excl. “New”')).toBeInTheDocument()
    expect(screen.getByText('7d')).toBeInTheDocument() // total without New
  })

  it('does not fetch again on a second hover (cached)', async () => {
    vi.mocked(api.getStatusHistory).mockResolvedValue({
      issueKey: 'LB-2',
      currentStatus: 'New',
      totalSeconds: 4 * 86400,
      segments: [{ status: 'New', durationSeconds: 4 * 86400, current: true }],
    })

    render(<StatusHistoryTooltip issueKey="LB-2"><span>badge</span></StatusHistoryTooltip>)
    const wrapper = hoverTrigger()
    await waitFor(() => expect(api.getStatusHistory).toHaveBeenCalledTimes(1))

    fireEvent.mouseLeave(wrapper)
    fireEvent.mouseEnter(wrapper)
    // still only one call — the result is cached in component state
    expect(api.getStatusHistory).toHaveBeenCalledTimes(1)
  })
})
