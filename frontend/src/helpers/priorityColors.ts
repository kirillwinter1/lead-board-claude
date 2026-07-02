import { ERROR_TEXT } from '../constants/colors'

export const PRIORITY_COLORS: Record<string, string> = {
  Blocker: '#cc0000',
  Critical: ERROR_TEXT,
  Highest: ERROR_TEXT,
  High: '#ff5630',
  Medium: '#ffab00',
  Low: '#36b37e',
  Lowest: '#97a0af',
}

export function getPriorityColor(priority: string): string {
  return PRIORITY_COLORS[priority] || '#42526e'
}
