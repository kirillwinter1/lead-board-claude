import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { LogTimeModal, type LogTimeTarget } from './LogTimeModal'
import { myWorkApi } from '../../api/myWork'

vi.mock('../../api/myWork', () => ({ myWorkApi: { logTime: vi.fn() } }))

function todayLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const target: LogTimeTarget = {
  key: 'LB-1',
  summary: 'Fix the login bug',
  originalEstimateH: 8,
  spentH: 2,
  remainingH: 6,
}

describe('LogTimeModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('opens with today (local) date, prefilled remaining, and disabled submit until valid spent', () => {
    // Freeze the clock at 01:30 local on Jul 8. In any timezone ahead of UTC the
    // UTC instant is still Jul 7 22:30 — a regression to toISOString().slice(0,10)
    // would render '2026-07-07' and fail this assertion.
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 6, 8, 1, 30))

    render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    expect(screen.getByText('Log time — LB-1')).toBeInTheDocument()
    expect(screen.getByLabelText('Date')).toHaveValue('2026-07-08')

    // Remaining prefilled from remainingH (6h)
    expect(screen.getByLabelText('Remaining')).toHaveValue('6h')
    // Time spent starts empty
    expect(screen.getByLabelText('Time spent')).toHaveValue('')

    // Original estimate line (8h = 1d with 8h/day)
    expect(screen.getByText('Original estimate — 1d')).toBeInTheDocument()

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })

    expect(screen.getByRole('button', { name: 'Save' })).not.toBeDisabled()
  })

  it('renders a live progress bar: logged = current spent + entered spent', () => {
    render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    // Initially 2h logged (current spent), 6h remaining
    expect(screen.getByText('2h logged')).toBeInTheDocument()
    expect(screen.getByText('Remaining: 6h')).toBeInTheDocument()

    // Enter 2h spent → 4h logged; drop remaining to 4h
    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })
    fireEvent.change(screen.getByLabelText('Remaining'), { target: { value: '4h' } })

    expect(screen.getByText('4h logged')).toBeInTheDocument()
    expect(screen.getByText('Remaining: 4h')).toBeInTheDocument()

    // filled 4h / total 8h → 50%
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '50')
  })

  it('auto-decrements Remaining as Time spent is typed (Jira auto mode)', () => {
    render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    // Starts at the current remaining (6h)
    expect(screen.getByLabelText('Remaining')).toHaveValue('6h')

    // Enter 2h spent → remaining auto-drops to 4h
    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })
    expect(screen.getByLabelText('Remaining')).toHaveValue('4h')

    // Overshoot: 8h spent against 6h remaining → clamps at 0m and bar fills
    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '8h' } })
    expect(screen.getByLabelText('Remaining')).toHaveValue('0m')
    expect(screen.getByText('Remaining: 0m')).toBeInTheDocument()

    // Clearing / invalid Time spent reverts Remaining to the current estimate
    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '' } })
    expect(screen.getByLabelText('Remaining')).toHaveValue('6h')
  })

  it('manual edit of Remaining disables the auto-recompute', () => {
    render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    // User overrides Remaining by hand
    fireEvent.change(screen.getByLabelText('Remaining'), { target: { value: '10h' } })
    expect(screen.getByLabelText('Remaining')).toHaveValue('10h')

    // Typing Time spent no longer touches the manually-set Remaining
    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })
    expect(screen.getByLabelText('Remaining')).toHaveValue('10h')
  })

  it('keeps submit disabled while spent has an invalid format', () => {
    render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    // Bare number without a unit is invalid
    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2' } })
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })
    expect(screen.getByRole('button', { name: 'Save' })).not.toBeDisabled()
  })

  it('resets fields (spent, remaining, comment, error, date) when the target changes', async () => {
    vi.mocked(myWorkApi.logTime).mockRejectedValue({ response: { data: { error: 'Boom' } } })
    const { rerender } = render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '4h' } })
    fireEvent.change(screen.getByLabelText('Comment'), { target: { value: 'stale note' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(screen.getByText('Boom')).toBeInTheDocument()
    })

    const other: LogTimeTarget = {
      key: 'LB-99', summary: 'A different task',
      originalEstimateH: 4, spentH: 0, remainingH: 4,
    }
    rerender(<LogTimeModal target={other} onClose={vi.fn()} onLogged={vi.fn()} />)

    expect(screen.getByText('Log time — LB-99')).toBeInTheDocument()
    expect(screen.getByLabelText('Time spent')).toHaveValue('')
    expect(screen.getByLabelText('Remaining')).toHaveValue('4h')
    expect(screen.getByLabelText('Comment')).toHaveValue('')
    expect(screen.queryByText('Boom')).toBeNull()
    expect(screen.getByLabelText('Date')).toHaveValue(todayLocal())
  })

  it('submit posts seconds payload and calls onLogged + onClose', async () => {
    vi.mocked(myWorkApi.logTime).mockResolvedValue({ worklogId: 'wl-1' })
    const onLogged = vi.fn()
    const onClose = vi.fn()
    render(<LogTimeModal target={target} onClose={onClose} onLogged={onLogged} />)

    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h 30m' } })
    fireEvent.change(screen.getByLabelText('Remaining'), { target: { value: '4h' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(myWorkApi.logTime).toHaveBeenCalledWith({
        issueKey: 'LB-1',
        date: todayLocal(),
        timeSpentSeconds: 9000, // 2h30m
        remainingEstimateSeconds: 14400, // 4h
        comment: undefined,
      })
    })

    await waitFor(() => {
      expect(onLogged).toHaveBeenCalled()
      expect(onClose).toHaveBeenCalled()
    })
  })

  it('handles a null remaining estimate by defaulting the field to 0m', () => {
    const noRemaining: LogTimeTarget = {
      key: 'LB-7', summary: 'No estimate',
      originalEstimateH: null, spentH: null, remainingH: null,
    }
    render(<LogTimeModal target={noRemaining} onClose={vi.fn()} onLogged={vi.fn()} />)

    expect(screen.getByLabelText('Remaining')).toHaveValue('0m')
    // No original estimate line when originalEstimateH is null
    expect(screen.queryByText(/Original estimate/)).toBeNull()
  })

  it('shows server error and keeps values', async () => {
    vi.mocked(myWorkApi.logTime).mockRejectedValue({ response: { data: { error: 'Not your task' } } })
    const onClose = vi.fn()
    render(<LogTimeModal target={target} onClose={onClose} onLogged={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(screen.getByText('Not your task')).toBeInTheDocument()
    })

    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Time spent')).toHaveValue('2h')
  })
})
