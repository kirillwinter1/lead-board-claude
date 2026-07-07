import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { MyWorklogCalendar } from './MyWorklogCalendar'
import type { CalendarDay } from '../../api/myWork'

// Local (not UTC) YYYY-MM-DD — mirrors the component's own todayStr logic so
// tests stay correct regardless of the machine's timezone / time of day.
function localDateStr(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function daysAgo(n: number): string {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return localDateStr(d)
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

describe('MyWorklogCalendar', () => {
  it('renders 28 day cells', () => {
    const days: CalendarDay[] = Array.from({ length: 28 }, (_, i) => makeDay({ date: daysAgo(27 - i) }))

    const { container } = render(<MyWorklogCalendar days={days} />)

    expect(container.querySelectorAll('.mywork-cal-cell')).toHaveLength(28)
  })

  it('marks underlogged workday with low-hours', () => {
    const days: CalendarDay[] = [
      makeDay({ date: daysAgo(3), dayType: 'WORKDAY', loggedH: 1, normH: 6, absenceType: null }),
    ]

    const { container } = render(<MyWorklogCalendar days={days} />)

    const cell = container.querySelector('.mywork-cal-cell')
    expect(cell).not.toBeNull()
    expect(cell!.className).toContain('low-hours')
  })

  it('weekend and absence days are not flagged low', () => {
    const days: CalendarDay[] = [
      makeDay({ date: daysAgo(4), dayType: 'WEEKEND', loggedH: 0, normH: 0, absenceType: null }),
      makeDay({ date: daysAgo(2), dayType: 'WORKDAY', loggedH: 0, normH: 6, absenceType: 'VACATION' }),
    ]

    const { container } = render(<MyWorklogCalendar days={days} />)

    const cells = container.querySelectorAll('.mywork-cal-cell')
    expect(cells).toHaveLength(2)
    cells.forEach(cell => expect(cell.className).not.toContain('low-hours'))
  })
})
