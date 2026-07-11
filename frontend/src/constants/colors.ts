// DSR (Delivery Speed Ratio) colors — used in DsrBreakdownChart, ForecastAccuracyChart, AssigneeTable, VelocityChart
export const DSR_GREEN = '#36B37E'
export const DSR_YELLOW = '#FFAB00'
export const DSR_RED = '#FF5630'
export const ESTIMATE_BLUE = '#4C9AFF'
export const FLAGGED_GREY = '#B3BAC5'

// Helper: get DSR color by value
export function getDsrColor(dsr: number | null): string {
  if (dsr === null) return TEXT_MUTED
  if (dsr <= 1.1) return DSR_GREEN
  if (dsr <= 1.5) return DSR_YELLOW
  return DSR_RED
}

// Helper: get accuracy color by ratio
export function getAccuracyColor(ratio: number): string {
  if (ratio >= 0.95 && ratio <= 1.05) return DSR_GREEN
  if (ratio >= 0.8 && ratio <= 1.2) return DSR_YELLOW
  return DSR_RED
}

// Helper: get utilization color by percent.
// Thresholds: <80% green, 80..100% yellow, >100% red.
export function getUtilizationColor(percent: number): string {
  if (percent < 80) return DSR_GREEN
  if (percent <= 100) return DSR_YELLOW
  return DSR_RED
}

// Grade badge colors — member seniority pill (junior/middle/senior), used by GradeBadge.
// Migrated verbatim from the .grade-badge.* rules in TeamsPage.css.
export const GRADE_COLORS: Record<string, { bg: string; text: string }> = {
  junior: { bg: '#fce4ec', text: '#880e4f' },
  middle: { bg: '#e8eaf6', text: '#283593' },
  senior: { bg: '#e0f2f1', text: '#00695c' },
}

// Absence type colors — member vacation/sick/day-off/other pills.
// Used by AbsenceModal, AbsenceTimeline, WorklogTimeline, MyWorklogCalendar, MemberProfilePage, MyWorkPage.
export const ABSENCE_COLORS: Record<string, string> = {
  VACATION: '#4C9AFF',
  SICK_LEAVE: '#FF5630',
  DAY_OFF: '#FF991F',
  OTHER: '#97A0AF',
}

// Severity colors — used in DataQualityPage
export const SEVERITY_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  ERROR: { bg: '#fee2e2', text: '#dc2626', border: '#fca5a5' },
  WARNING: { bg: '#fef3c7', text: '#d97706', border: '#fcd34d' },
  INFO: { bg: '#f3f4f6', text: '#6b7280', border: '#d1d5db' },
}

// Text color hierarchy — Atlassian-based palette
export const TEXT_PRIMARY = '#172b4d'
export const TEXT_SECONDARY = '#42526e'
export const TEXT_MUTED = '#6b778c'
export const TEXT_SUBTLE = '#97a0af'
export const TEXT_DISABLED = '#B3BAC5'

// Chart theme
export const CHART_GRID = '#ebecf0'
export const CHART_AXIS = '#dfe1e6'
export const CHART_TICK = '#6b778c'
export const CHART_TOOLTIP_BG = '#172b4d'

// Throughput chart series colors
export const THROUGHPUT_EPIC = '#6554c0'   // epic throughput line
export const THROUGHPUT_STORY = '#0065ff'  // story throughput line
export const THROUGHPUT_TOTAL = '#0065ff'  // single-type / total throughput line
export const THROUGHPUT_MA = '#ff5630'     // moving-average line

// Progress bar
export const PROGRESS_COMPLETE = '#36B37E'
export const PROGRESS_IN_PROGRESS = '#0065FF'
export const PROGRESS_TRACK = '#DFE1E6'

// Background
export const BG_SUBTLE = '#F4F5F7'

// Border
export const BORDER_DEFAULT = '#DFE1E6'

// Avatar fallback
export const AVATAR_BG = '#0052CC'

// Jira link color
export const LINK_COLOR = '#0052CC'

// Error / danger
export const ERROR_TEXT = '#DE350B'
export const ERROR_BG = '#FFEBE6'
export const ERROR_BORDER = '#FFBDAD'
export const ERROR_DARK_TEXT = '#BF2600'

// Warning / recommendation callout
export const WARNING_BG = '#FFFAE6'
export const WARNING_BORDER = '#FFE380'
export const WARNING_TEXT = '#7B4A00'

// Warning accent orange — RICE mid-band, WARNING-severity text/dots
export const WARNING_ORANGE = '#FF8B00'

// Success (Atlassian green tones)
export const SUCCESS_BG = '#E3FCEF'
export const SUCCESS_TEXT = '#006644'

// Info (Atlassian blue tones)
export const INFO_BG = '#DEEBFF'
export const INFO_TEXT = '#0747A6'
export const INFO_BORDER = '#B3D4FF'

// Page background
export const BG_PAGE = '#ffffff'

// Separator / divider
export const SEPARATOR = '#EBECF0'

// Expanded / detail panel background
export const BG_PANEL = '#FAFBFC'

// Primary light button
export const PRIMARY_LIGHT_BG = '#E9F2FF'
export const PRIMARY_LIGHT_BORDER = '#B3D4FF'

// Tooltip (dark background context)
export const TOOLTIP_BG = 'rgba(23, 43, 77, 0.98)'
export const TOOLTIP_HIGHLIGHT = '#B3D4FF'
export const TOOLTIP_TEXT = '#B3BAC5'
export const TOOLTIP_LABEL = '#8993A4'
export const TOOLTIP_DIVIDER = '#42526e'
export const TOOLTIP_PROGRESS_TRACK = '#42526e'
export const TOOLTIP_SUCCESS = '#22c55e'
export const TOOLTIP_VALUE = '#e5e7eb'
export const TOOLTIP_ACCENT = '#FFD700'

// Timeline (muted Gantt palette) — intentionally dimmer than Board equivalents; см. дизайн-решение F91
export const TIMELINE_PHASE_TINT = 0.65
export const TIMELINE_PHASE_TINT_ROUGH = 0.8
export const TIMELINE_ROLE_BORDER_TINT = 0.5
export const TIMELINE_BAR_TRACK = '#e5e7eb'
export const TIMELINE_FLAGGED_BORDER = '#f97316'
export const TIMELINE_BLOCKED_BORDER = '#ef4444'
export const TIMELINE_ROUGH_BG = '#f0f0f0'
export const TIMELINE_ROUGH_BADGE_BG = '#fef3c7'
export const TIMELINE_ROUGH_BADGE_TEXT = '#92400e'
export const TOOLTIP_DANGER = '#ef4444'

// Eisenhower Matrix quadrant colors (F77) — used in MatrixQuadrant / MatrixUnassigned.
// P1 important+urgent (red), P2 important/not urgent (amber), P3 not important/urgent (purple),
// P4 not important/not urgent (green). Each entry: accent (header/border), bg (zone fill).
export interface QuadrantColor {
  accent: string
  bg: string
}

export const QUADRANT_COLORS: Record<'P1' | 'P2' | 'P3' | 'P4', QuadrantColor> = {
  P1: { accent: '#FF5630', bg: '#FFF1EE' },
  P2: { accent: '#FFAB00', bg: '#FFFAE6' },
  P3: { accent: '#6554C0', bg: '#F3F0FF' },
  P4: { accent: '#36B37E', bg: '#E9FBF2' },
}

// Unassigned zone — neutral grey tone.
export const QUADRANT_UNASSIGNED: QuadrantColor = { accent: '#6b778c', bg: '#F4F5F7' }

// Status age badge colors (F79) — "days in status" pill. Color comes ONLY from the
// backend-decided statusAgeLevel (backlog/done are NORMAL). NORMAL grey, WARNING amber, CRITICAL red.
export const STATUS_AGE_COLORS: Record<'NORMAL' | 'WARNING' | 'CRITICAL', { bg: string; fg: string }> = {
  NORMAL: { bg: '#F4F5F7', fg: '#5e6c84' },
  WARNING: { bg: '#FFF7E6', fg: '#bf4f00' },
  CRITICAL: { bg: '#FFEBE6', fg: '#bf2600' },
}

// Helper: parse a hex color (#RRGGBB) into its RGB components — shared by lightenColor/hexToRgba
function hexToRgb(hex: string): { r: number; g: number; b: number } {
  return {
    r: parseInt(hex.slice(1, 3), 16),
    g: parseInt(hex.slice(3, 5), 16),
    b: parseInt(hex.slice(5, 7), 16),
  }
}

// Helper: lighten a hex color by a factor (0 = original, 1 = white)
export function lightenColor(hex: string, factor: number): string {
  const { r, g, b } = hexToRgb(hex)
  const lr = Math.round(r + (255 - r) * factor)
  const lg = Math.round(g + (255 - g) * factor)
  const lb = Math.round(b + (255 - b) * factor)
  return `#${lr.toString(16).padStart(2, '0')}${lg.toString(16).padStart(2, '0')}${lb.toString(16).padStart(2, '0')}`
}

// Helper: convert a hex color to an rgba() string with the given alpha (0-1)
export function hexToRgba(hex: string, alpha: number): string {
  const { r, g, b } = hexToRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
