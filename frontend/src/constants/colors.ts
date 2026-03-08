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

// Helper: get utilization color by percent
export function getUtilizationColor(percent: number): string {
  if (percent >= 85 && percent <= 110) return DSR_GREEN
  if (percent >= 70 && percent <= 130) return DSR_YELLOW
  return DSR_RED
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
