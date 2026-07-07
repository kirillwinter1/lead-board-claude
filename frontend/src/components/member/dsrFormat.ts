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

export function formatDate(dateStr: string): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return d.toLocaleDateString(undefined, { day: '2-digit', month: '2-digit', year: 'numeric' })
}
