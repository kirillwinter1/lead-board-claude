import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MyWorklogCalendar } from './MyWorklogCalendar'
import { myWorkApi, type CalendarDay } from '../../api/myWork'

vi.mock('../../api/myWork', () => ({ myWorkApi: { worklogCalendar: vi.fn() } }))

// Local (not UTC) YYYY-MM-DD — mirrors the component's own todayStr logic so
// tests stay correct regardless of the machine's timezone / time of day.
function localDateStr(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const NOW = new Date()
const CURRENT_MONTH = localDateStr(NOW).slice(0, 7) // 'YYYY-MM'

function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const zero = y * 12 + (m - 1) + delta
  const ny = Math.floor(zero / 12)
  const nm = (zero % 12) + 1
  return `${ny}-${String(nm).padStart(2, '0')}`
}

// A day inside the given month, e.g. dayInMonth('2026-07', 15) => '2026-07-15'.
function dayInMonth(month: string, day: number): string {
  return `${month}-${String(day).padStart(2, '0')}`
}

function makeDay(overrides: Partial<CalendarDay> & { date: string }): CalendarDay {
  return {
    dayType: 'WORKDAY',
    loggedH: 8,
    normH: 8,
    absenceType: null,
    byIssue: [],
    ...overrides,
  }
}

// A minimal 3-day month sample: an ordinary logged workday, an underlogged one,
// and a spill-over day from the previous month.
function sampleDays(month: string): CalendarDay[] {
  const prevMonth = shiftMonth(month, -1)
  return [
    makeDay({ date: dayInMonth(prevMonth, 28), loggedH: 8, byIssue: [{ issueKey: 'LB-900', hours: 8 }] }),
    makeDay({ date: dayInMonth(month, 1), loggedH: 8, byIssue: [{ issueKey: 'LB-521', hours: 8 }] }),
    makeDay({ date: dayInMonth(month, 2), loggedH: 1, normH: 6 }),
  ]
}

describe('MyWorklogCalendar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the month label from initialMonth', () => {
    render(<MyWorklogCalendar initialDays={sampleDays('2026-03')} initialMonth="2026-03" />)
    expect(screen.getByText('March 2026')).toBeInTheDocument()
  })

  it('shows prev/next nav buttons; next is disabled on the current month', () => {
    render(<MyWorklogCalendar initialDays={sampleDays(CURRENT_MONTH)} initialMonth={CURRENT_MONTH} />)
    expect(screen.getByLabelText('Previous month')).toBeEnabled()
    expect(screen.getByLabelText('Next month')).toBeDisabled()
  })

  it('clicking prev fetches and renders the previous month', async () => {
    const prevMonth = shiftMonth(CURRENT_MONTH, -1)
    const prevDays = [makeDay({ date: dayInMonth(prevMonth, 10), byIssue: [{ issueKey: 'LB-777', hours: 4 }] })]
    vi.mocked(myWorkApi.worklogCalendar).mockResolvedValue(prevDays)

    const { container } = render(
      <MyWorklogCalendar initialDays={sampleDays(CURRENT_MONTH)} initialMonth={CURRENT_MONTH} />
    )

    fireEvent.click(screen.getByLabelText('Previous month'))

    await waitFor(() => {
      expect(myWorkApi.worklogCalendar).toHaveBeenCalledWith(prevMonth)
    })
    // Grid now holds exactly the fetched previous-month days.
    await waitFor(() => {
      expect(container.querySelectorAll('.mywork-cal-cell')).toHaveLength(1)
    })
  })

  it('a workday with logged hours gets the logged (green) class', () => {
    const { container } = render(
      <MyWorklogCalendar initialDays={sampleDays('2026-03')} initialMonth="2026-03" />
    )
    const logged = container.querySelector('.mywork-cal-cell.logged')
    expect(logged).not.toBeNull()
  })

  it('an underlogged workday gets low-hours, not logged', () => {
    const days = [makeDay({ date: dayInMonth('2026-03', 2), loggedH: 1, normH: 6 })]
    const { container } = render(<MyWorklogCalendar initialDays={days} initialMonth="2026-03" />)
    const cell = container.querySelector('.mywork-cal-cell')
    expect(cell!.className).toContain('low-hours')
    expect(cell!.className).not.toContain('logged')
  })

  it('a day from an adjacent month gets the other-month class', () => {
    const { container } = render(
      <MyWorklogCalendar initialDays={sampleDays('2026-03')} initialMonth="2026-03" />
    )
    const other = container.querySelector('.mywork-cal-cell.other-month')
    expect(other).not.toBeNull()
    // Spill-over days never get the logged highlight even with hours.
    expect(other!.className).not.toContain('logged')
  })

  it('hovering a cell shows a tooltip with the issue breakdown', async () => {
    const days = [makeDay({ date: dayInMonth('2026-03', 1), byIssue: [{ issueKey: 'LB-521', hours: 8 }] })]
    const { container } = render(<MyWorklogCalendar initialDays={days} initialMonth="2026-03" />)

    const cell = container.querySelector('.mywork-cal-cell')!
    fireEvent.mouseEnter(cell)

    await waitFor(() => {
      expect(screen.getByRole('tooltip')).toBeInTheDocument()
    })
    expect(screen.getByText('LB-521')).toBeInTheDocument()
    expect(screen.getByText('8h')).toBeInTheDocument()

    fireEvent.mouseLeave(cell)
    await waitFor(() => {
      expect(screen.queryByRole('tooltip')).toBeNull()
    })
  })

  it('shows "No worklog" in the tooltip for an empty day', async () => {
    const days = [makeDay({ date: dayInMonth('2026-03', 1), loggedH: 0, byIssue: [] })]
    const { container } = render(<MyWorklogCalendar initialDays={days} initialMonth="2026-03" />)

    fireEvent.mouseEnter(container.querySelector('.mywork-cal-cell')!)

    await waitFor(() => {
      expect(screen.getByText('No worklog')).toBeInTheDocument()
    })
  })
})
