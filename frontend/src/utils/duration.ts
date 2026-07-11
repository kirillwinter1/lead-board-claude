// Jira-style duration parsing/formatting.
// Units: w = 40h (5 working days), d = 8h, h = 1h, m = 1min.
// In seconds: 1m = 60, 1h = 3600, 1d = 28800, 1w = 144000.

const SEC_PER_MINUTE = 60
const SEC_PER_HOUR = 3600
const SEC_PER_DAY = 8 * SEC_PER_HOUR // 28800
const SEC_PER_WEEK = 5 * SEC_PER_DAY // 144000

const UNIT_SECONDS: Record<string, number> = {
  w: SEC_PER_WEEK,
  d: SEC_PER_DAY,
  h: SEC_PER_HOUR,
  m: SEC_PER_MINUTE,
}

// A single token: a (possibly decimal) number immediately followed by a unit
// letter. A bare number with no unit is NOT a valid token.
const TOKEN_RE = /^(\d+(?:\.\d+)?)([wdhm])$/

/**
 * Parse a Jira duration string ("2w 4d 6h 45m") into seconds.
 * - Requires units on every token; a bare number ("2") is invalid → null.
 * - Allows decimals ("1.5h"), multiple space-separated tokens, any case.
 * - Empty / whitespace-only / any garbage → null.
 */
export function parseDuration(str: string): number | null {
  if (typeof str !== 'string') return null
  const trimmed = str.trim().toLowerCase()
  if (trimmed === '') return null

  const tokens = trimmed.split(/\s+/)
  let total = 0
  for (const token of tokens) {
    const m = TOKEN_RE.exec(token)
    if (!m) return null
    const value = parseFloat(m[1])
    if (!Number.isFinite(value)) return null
    total += value * UNIT_SECONDS[m[2]]
  }
  return Math.round(total)
}

/**
 * Format seconds back into a Jira duration string ("2w 4d 6h 45m").
 * Uses 8h/day and 5d/week, skips zero units, all-zero → "0m".
 */
export function formatDuration(seconds: number): string {
  if (!Number.isFinite(seconds) || seconds <= 0) return '0m'
  let rem = Math.round(seconds)

  const weeks = Math.floor(rem / SEC_PER_WEEK)
  rem -= weeks * SEC_PER_WEEK
  const days = Math.floor(rem / SEC_PER_DAY)
  rem -= days * SEC_PER_DAY
  const hours = Math.floor(rem / SEC_PER_HOUR)
  rem -= hours * SEC_PER_HOUR
  const minutes = Math.floor(rem / SEC_PER_MINUTE)

  const parts: string[] = []
  if (weeks > 0) parts.push(`${weeks}w`)
  if (days > 0) parts.push(`${days}d`)
  if (hours > 0) parts.push(`${hours}h`)
  if (minutes > 0) parts.push(`${minutes}m`)

  return parts.length > 0 ? parts.join(' ') : '0m'
}
