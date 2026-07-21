// Shared DSR/format helpers extracted from MemberProfilePage — reused by MyWorkPage (F88).

export function getDsrClass(dsr: number | null): string {
  if (dsr == null) return ''
  if (dsr <= 1.0) return 'good'
  if (dsr <= 1.2) return 'warning'
  return 'bad'
}

export function formatHours(h: number | null): string {
  if (h == null) return '—'
  return h % 1 === 0 ? `${h}h` : `${h.toFixed(1)}h`
}

// Local YYYY-MM-DD for a Date — NOT toISOString(), which converts to UTC and
// can shift the calendar day by one in timezones east/west of UTC.
export function localDateKey(d: Date = new Date()): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

export function formatDate(dateStr: string): string {
  if (!dateStr) return '—'
  // Parse 'YYYY-MM-DD' as a LOCAL date. `new Date('2026-07-11')` parses as UTC
  // midnight, which toLocaleDateString then renders in the local timezone —
  // shifting to the previous day west of UTC. Build the Date from its parts so
  // the calendar day is preserved.
  const parts = dateStr.split('-').map(Number)
  const d =
    parts.length === 3 && parts.every(n => Number.isFinite(n))
      ? new Date(parts[0], parts[1] - 1, parts[2])
      : new Date(dateStr)
  return d.toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}
