import { useState, useEffect, useCallback, useRef } from 'react'
import { myWorkApi, type CalendarDay } from '../../api/myWork'
import { ABSENCE_COLORS } from '../../constants/colors'
import type { AbsenceType } from '../../api/teams'

// CSS classes (mywork-cal-*) live in MyWorkPage.css — consuming pages must
// import that stylesheet.

const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

function localDateStr(d: Date): string {
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

// Current calendar month as 'YYYY-MM' — the latest month a user may view.
function currentMonthStr(): string {
  return localDateStr(new Date()).slice(0, 7)
}

// 'YYYY-MM' → human label, e.g. '2026-07' → 'July 2026'.
function monthLabel(month: string): string {
  const [y, m] = month.split('-').map(Number)
  return `${MONTH_NAMES[m - 1]} ${y}`
}

// Shift a 'YYYY-MM' month by delta months (no date libs).
function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number)
  const zero = y * 12 + (m - 1) + delta
  const ny = Math.floor(zero / 12)
  const nm = (zero % 12) + 1
  return `${ny}-${String(nm).padStart(2, '0')}`
}

interface MyWorklogCalendarProps {
  initialDays: CalendarDay[]
  initialMonth: string // 'YYYY-MM' — the month initialDays cover
}

export function MyWorklogCalendar({ initialDays, initialMonth }: MyWorklogCalendarProps) {
  const [month, setMonth] = useState(initialMonth)
  const [days, setDays] = useState<CalendarDay[]>(initialDays)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)
  const [hoveredDate, setHoveredDate] = useState<string | null>(null)

  // Tracks the most recently requested month so a slow in-flight fetch that
  // resolves after the user has navigated elsewhere can't clobber the grid.
  const requestedMonth = useRef(initialMonth)

  // Re-seed from props whenever the parent reloads /api/me/work (e.g. right
  // after logging time). Without this the calendar would keep its stale
  // internal state and never show the freshly logged worklog.
  useEffect(() => {
    setMonth(initialMonth)
    setDays(initialDays)
    setHoveredDate(null)
    setError(false)
    requestedMonth.current = initialMonth
  }, [initialDays, initialMonth])

  const todayStr = localDateStr(new Date())
  const thisMonth = currentMonthStr()
  const isCurrentMonth = month >= thisMonth

  const goToMonth = useCallback((target: string) => {
    if (target > thisMonth) return // never navigate into the future
    setMonth(target)
    setHoveredDate(null)
    setError(false)
    requestedMonth.current = target
    if (target === initialMonth) {
      // The current month is already loaded — reuse it, no round-trip. Clear any
      // in-flight loading state too: a still-pending fetch for another month will
      // no-op in its .finally() guard (requestedMonth.current has moved on), so if
      // we don't reset here the grid stays frozen (opacity 0.5 + pointer-events:none).
      setDays(initialDays)
      setLoading(false)
      return
    }
    setLoading(true)
    myWorkApi.worklogCalendar(target)
      .then(fetched => {
        if (requestedMonth.current === target) setDays(fetched)
      })
      .catch(() => {
        if (requestedMonth.current === target) setError(true)
      })
      .finally(() => {
        if (requestedMonth.current === target) setLoading(false)
      })
  }, [thisMonth, initialMonth, initialDays])

  return (
    <div className="mywork-cal">
      <div className="mywork-cal-nav">
        <button
          type="button"
          className="mywork-cal-nav-btn"
          aria-label="Previous month"
          onClick={() => goToMonth(shiftMonth(month, -1))}
        >
          &lsaquo;
        </button>
        <span className="mywork-cal-nav-label">{monthLabel(month)}</span>
        <button
          type="button"
          className="mywork-cal-nav-btn"
          aria-label="Next month"
          disabled={isCurrentMonth}
          onClick={() => goToMonth(shiftMonth(month, 1))}
        >
          &rsaquo;
        </button>
      </div>

      <div className="mywork-cal-weekdays">
        {WEEKDAY_LABELS.map(label => (
          <span key={label} className="mywork-cal-weekday">{label}</span>
        ))}
      </div>

      {error ? (
        <div className="mywork-cal-error" role="alert">
          <span>Failed to load this month.</span>
          <button type="button" className="mywork-cal-nav-btn" onClick={() => goToMonth(month)}>
            Retry
          </button>
        </div>
      ) : (
      <div className={`mywork-cal-grid${loading ? ' loading' : ''}`}>
        {days.map(d => {
          const isOtherMonth = d.date.slice(0, 7) !== month
          const isFuture = d.date > todayStr
          const isLow = !isOtherMonth && d.dayType === 'WORKDAY' && !d.absenceType && !isFuture && d.loggedH < d.normH * 0.5
          const isLogged = !isOtherMonth && !d.absenceType && !isLow && d.loggedH > 0
          const absenceColor = !isOtherMonth && d.absenceType
            ? ABSENCE_COLORS[d.absenceType as AbsenceType] || ABSENCE_COLORS.OTHER
            : undefined

          return (
            <div
              key={d.date}
              className={[
                'mywork-cal-cell',
                d.dayType.toLowerCase(),
                d.date === todayStr ? 'today' : '',
                isOtherMonth ? 'other-month' : '',
                absenceColor ? 'absence' : '',
                isLow ? 'low-hours' : '',
                isLogged ? 'logged' : '',
                isFuture ? 'future' : '',
              ].filter(Boolean).join(' ')}
              style={absenceColor ? { backgroundColor: absenceColor } : undefined}
              onMouseEnter={() => setHoveredDate(d.date)}
              onMouseLeave={() => setHoveredDate(prev => (prev === d.date ? null : prev))}
            >
              <span className="mywork-cal-daynum">{Number(d.date.slice(8))}</span>
              {!isOtherMonth && d.dayType === 'WORKDAY' && !d.absenceType && !isFuture && (
                <span className="mywork-cal-hours">{d.loggedH}/{d.normH}h</span>
              )}
              {hoveredDate === d.date && (
                <div className="mywork-cal-tip" role="tooltip">
                  <div className="mywork-cal-tip-date">{d.date}</div>
                  {d.byIssue.length > 0 ? (
                    <ul className="mywork-cal-tip-list">
                      {d.byIssue.map(i => (
                        <li key={i.issueKey}>
                          <span className="mywork-cal-tip-key">{i.issueKey}</span>
                          <span className="mywork-cal-tip-hours">{i.hours}h</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <div className="mywork-cal-tip-empty">No worklog</div>
                  )}
                </div>
              )}
            </div>
          )
        })}
      </div>
      )}

      <div className="mywork-cal-legend">
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-logged" />
          Logged
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-low-hours" />
          Low hours
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-weekend" />
          Weekend
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-holiday" />
          Holiday
        </span>
        <span className="mywork-cal-legend-item">
          <span className="mywork-cal-legend-swatch mywork-cal-legend-absence" />
          Absence
        </span>
      </div>
    </div>
  )
}
