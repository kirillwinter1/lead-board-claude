import { describe, it, expect } from 'vitest'
import { parseDuration, formatDuration } from './duration'

describe('parseDuration', () => {
  it('parses a single hour token', () => {
    expect(parseDuration('6h')).toBe(21600)
  })

  it('parses days + hours', () => {
    expect(parseDuration('1d 4h')).toBe(43200)
  })

  it('parses weeks + days', () => {
    // 2w = 288000, 3d = 86400
    expect(parseDuration('2w 3d')).toBe(374400)
  })

  it('parses minutes', () => {
    expect(parseDuration('45m')).toBe(2700)
  })

  it('parses a full mixed string', () => {
    // 2w=288000, 4d=115200, 6h=21600, 45m=2700
    expect(parseDuration('2w 4d 6h 45m')).toBe(427500)
  })

  it('allows decimals', () => {
    expect(parseDuration('1.5h')).toBe(5400)
  })

  it('is case-insensitive', () => {
    expect(parseDuration('1D 4H')).toBe(43200)
  })

  it('tolerates extra whitespace', () => {
    expect(parseDuration('  1d   4h  ')).toBe(43200)
  })

  it('rejects a bare number (no unit)', () => {
    expect(parseDuration('2')).toBeNull()
  })

  it('rejects an empty string', () => {
    expect(parseDuration('')).toBeNull()
    expect(parseDuration('   ')).toBeNull()
  })

  it('rejects garbage', () => {
    expect(parseDuration('abc')).toBeNull()
    expect(parseDuration('6x')).toBeNull()
    expect(parseDuration('6h junk')).toBeNull()
  })
})

describe('formatDuration', () => {
  it('formats a single hour value', () => {
    expect(formatDuration(21600)).toBe('6h')
  })

  it('normalizes hours into days (8h/day)', () => {
    expect(formatDuration(43200)).toBe('1d 4h')
  })

  it('normalizes days into weeks (5d/week)', () => {
    expect(formatDuration(374400)).toBe('2w 3d')
  })

  it('includes minutes', () => {
    expect(formatDuration(2700)).toBe('45m')
    expect(formatDuration(427500)).toBe('2w 4d 6h 45m')
  })

  it('skips zero units', () => {
    // 1w + 6h, no days/minutes
    expect(formatDuration(144000 + 21600)).toBe('1w 6h')
  })

  it('renders zero as "0m"', () => {
    expect(formatDuration(0)).toBe('0m')
    expect(formatDuration(-100)).toBe('0m')
  })

  it('round-trips with parseDuration', () => {
    expect(formatDuration(parseDuration('2w 4d 6h')!)).toBe('2w 4d 6h')
  })
})
