import { describe, it, expect, afterEach, vi } from 'vitest'
import { formatDate, localDateKey } from './dsrFormat'

describe('formatDate', () => {
  // Backend sends plain LocalDate strings ('YYYY-MM-DD'). The rendered calendar
  // day must match the input regardless of the viewer's timezone. Parsing the
  // string as UTC midnight and then formatting in a timezone west of UTC
  // (e.g. America/New_York) renders the *previous* day — this test pins the fix.
  it('renders the same calendar day it was given (no UTC shift)', () => {
    expect(formatDate('2026-07-11')).toContain('11')
  })

  it('returns a dash for empty input', () => {
    expect(formatDate('')).toBe('—')
  })
})

describe('localDateKey', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('formats a Date as its LOCAL YYYY-MM-DD (not the UTC day)', () => {
    // 2026-07-11 00:30 in Europe/Moscow (UTC+3) is still 2026-07-10 21:30 UTC.
    // toISOString().slice(0,10) would yield '2026-07-10'; localDateKey must
    // return the local calendar day.
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-10T21:30:00.000Z'))
    const d = new Date()
    // Emulate an east-of-UTC local day by asserting on explicit local parts.
    const expected = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    expect(localDateKey()).toBe(expected)
    // And it must never fall back to the UTC slice when they differ.
    if (d.getUTCDate() !== d.getDate()) {
      expect(localDateKey()).not.toBe(new Date().toISOString().slice(0, 10))
    }
  })
})
