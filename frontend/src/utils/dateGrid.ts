// Shared date/grid utilities for Gantt-style timeline views (TimelinePage, ProjectGanttView).

export type ZoomLevel = 'day' | 'week' | 'month'

export interface DateRange {
  start: Date
  end: Date
}

export interface TimelineHeader {
  date: Date
  label: string
}

export interface GroupHeader {
  label: string
  span: number // number of columns this group spans
}

export function toLocalMidnight(date: Date): Date {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

export function daysBetween(start: Date, end: Date): number {
  const s = toLocalMidnight(start)
  const e = toLocalMidnight(end)
  return Math.round((e.getTime() - s.getTime()) / (1000 * 60 * 60 * 24))
}

export function addDays(date: Date, days: number): Date {
  const result = new Date(date)
  result.setDate(result.getDate() + days)
  return result
}

export function startOfWeek(date: Date): Date {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  d.setDate(diff)
  return d
}

export function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

export function formatDateShort(date: Date): string {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

// Get ISO week number
export function getWeekNumber(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  return Math.ceil((((d.getTime() - yearStart.getTime()) / 86400000) + 1) / 7)
}

// Check if a date is a weekend (Saturday or Sunday)
export function isWeekend(date: Date): boolean {
  const day = date.getDay()
  return day === 0 || day === 6
}

// Format seconds to hours
export function formatHours(seconds: number | null): string {
  if (seconds === null || seconds === 0) return '0h'
  return `${Math.round(seconds / 3600)}h`
}

// Computes a DateRange covering [min(startCandidates), max(endCandidates)] (defaulting to
// [today, today+30d] when no candidates are given), optionally clamps how far back the range
// starts, and aligns both ends to week boundaries so the grid matches the header columns.
export function calculateDateRangeFromCandidates(
  startCandidates: Date[],
  endCandidates: Date[],
  clampPastDays: number | null = null,
): DateRange {
  const today = toLocalMidnight(new Date())

  let minDate: Date = today
  let maxDate: Date = addDays(today, 30)

  for (const d of startCandidates) {
    if (d < minDate) minDate = d
  }
  for (const d of endCandidates) {
    if (d > maxDate) maxDate = d
  }

  // Clamp how far back the range starts (e.g. hide work completed long ago).
  if (clampPastDays != null) {
    const clamp = addDays(today, -clampPastDays)
    if (minDate < clamp) minDate = clamp
  }

  // Align to week boundaries so date % matches header grid exactly
  minDate = startOfWeek(addDays(minDate, -3))
  const paddedMax = addDays(maxDate, 7)
  const numWeeks = Math.ceil(daysBetween(minDate, paddedMax) / 7)
  maxDate = addDays(minDate, numWeeks * 7)

  return { start: minDate, end: maxDate }
}

export function generateTimelineHeaders(range: DateRange, zoom: ZoomLevel): TimelineHeader[] {
  const headers: TimelineHeader[] = []
  let current = new Date(range.start)

  if (zoom === 'day') {
    while (current <= range.end) {
      headers.push({ date: new Date(current), label: current.getDate().toString() })
      current = addDays(current, 1)
    }
  } else if (zoom === 'week') {
    current = startOfWeek(current)
    while (current <= range.end) {
      headers.push({ date: new Date(current), label: formatDateShort(current) })
      current = addDays(current, 7)
    }
  } else {
    current = startOfMonth(current)
    while (current <= range.end) {
      headers.push({
        date: new Date(current),
        label: current.toLocaleDateString('en-US', { month: 'short' })
      })
      current = new Date(current.getFullYear(), current.getMonth() + 1, 1)
    }
  }

  return headers
}

// Generate group headers (month for day/week zoom, quarter for month zoom)
export function generateGroupHeaders(headers: TimelineHeader[], zoom: ZoomLevel): GroupHeader[] {
  if (headers.length === 0) return []

  const groups: GroupHeader[] = []

  if (zoom === 'day' || zoom === 'week') {
    // Group by month
    let currentMonth = -1
    let currentYear = -1
    let currentSpan = 0

    for (const header of headers) {
      const month = header.date.getMonth()
      const year = header.date.getFullYear()

      if (month !== currentMonth || year !== currentYear) {
        if (currentSpan > 0) {
          groups.push({
            label: new Date(currentYear, currentMonth, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
            span: currentSpan
          })
        }
        currentMonth = month
        currentYear = year
        currentSpan = 1
      } else {
        currentSpan++
      }
    }

    // Add last group
    if (currentSpan > 0) {
      groups.push({
        label: new Date(currentYear, currentMonth, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' }),
        span: currentSpan
      })
    }
  } else {
    // Group by quarter for month zoom
    let currentQuarter = -1
    let currentYear = -1
    let currentSpan = 0

    for (const header of headers) {
      const quarter = Math.floor(header.date.getMonth() / 3) + 1
      const year = header.date.getFullYear()

      if (quarter !== currentQuarter || year !== currentYear) {
        if (currentSpan > 0) {
          groups.push({
            label: `Q${currentQuarter} ${currentYear}`,
            span: currentSpan
          })
        }
        currentQuarter = quarter
        currentYear = year
        currentSpan = 1
      } else {
        currentSpan++
      }
    }

    // Add last group
    if (currentSpan > 0) {
      groups.push({
        label: `Q${currentQuarter} ${currentYear}`,
        span: currentSpan
      })
    }
  }

  return groups
}

// Generate week headers for day zoom (shows week numbers). `labelFn` lets callers control
// the label format (e.g. "Week 23" vs "W23") without duplicating the grouping logic.
export function generateWeekHeaders(
  headers: TimelineHeader[],
  zoom: ZoomLevel,
  labelFn: (weekNum: number) => string = (n) => `Week ${n}`,
): GroupHeader[] {
  if (zoom !== 'day' || headers.length === 0) return []

  const weeks: GroupHeader[] = []
  let currentWeekStart: Date | null = null
  let currentSpan = 0

  for (const header of headers) {
    const weekStart = startOfWeek(header.date)

    if (!currentWeekStart || weekStart.getTime() !== currentWeekStart.getTime()) {
      if (currentSpan > 0 && currentWeekStart) {
        weeks.push({
          label: labelFn(getWeekNumber(currentWeekStart)),
          span: currentSpan
        })
      }
      currentWeekStart = weekStart
      currentSpan = 1
    } else {
      currentSpan++
    }
  }

  // Add last week
  if (currentSpan > 0 && currentWeekStart) {
    weeks.push({
      label: labelFn(getWeekNumber(currentWeekStart)),
      span: currentSpan
    })
  }

  return weeks
}
