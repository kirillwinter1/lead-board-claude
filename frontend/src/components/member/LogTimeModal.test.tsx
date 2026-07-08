import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { LogTimeModal, type LogTimeTarget } from './LogTimeModal'
import { myWorkApi } from '../../api/myWork'

vi.mock('../../api/myWork', () => ({ myWorkApi: { logTime: vi.fn() } }))

function todayLocal(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const target: LogTimeTarget = { key: 'LB-1', summary: 'Fix the login bug' }

describe('LogTimeModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens with today date and disabled submit until hours set', () => {
    render(<LogTimeModal target={target} onClose={vi.fn()} onLogged={vi.fn()} />)

    expect(screen.getByText('Log time — LB-1')).toBeInTheDocument()
    expect(screen.getByLabelText('Date')).toHaveValue(todayLocal())
    expect(screen.getByRole('button', { name: 'Log time' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Hours'), { target: { value: '2.5' } })

    expect(screen.getByRole('button', { name: 'Log time' })).not.toBeDisabled()
  })

  it('submit posts payload and calls onLogged + onClose', async () => {
    vi.mocked(myWorkApi.logTime).mockResolvedValue({ worklogId: 'wl-1' })
    const onLogged = vi.fn()
    const onClose = vi.fn()
    render(<LogTimeModal target={target} onClose={onClose} onLogged={onLogged} />)

    fireEvent.change(screen.getByLabelText('Hours'), { target: { value: '2.5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log time' }))

    await waitFor(() => {
      expect(myWorkApi.logTime).toHaveBeenCalledWith({
        issueKey: 'LB-1',
        date: todayLocal(),
        hours: 2.5,
        comment: undefined,
      })
    })

    await waitFor(() => {
      expect(onLogged).toHaveBeenCalled()
      expect(onClose).toHaveBeenCalled()
    })
  })

  it('shows server error and keeps values', async () => {
    vi.mocked(myWorkApi.logTime).mockRejectedValue({ response: { data: { error: 'Not your task' } } })
    const onClose = vi.fn()
    render(<LogTimeModal target={target} onClose={onClose} onLogged={vi.fn()} />)

    fireEvent.change(screen.getByLabelText('Hours'), { target: { value: '2.5' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log time' }))

    await waitFor(() => {
      expect(screen.getByText('Not your task')).toBeInTheDocument()
    })

    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Hours')).toHaveValue(2.5)
  })
})
