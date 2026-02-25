export const PRIORITY_COLORS: Record<string, string> = {
  Blocker: '#cc0000',
  Critical: '#de350b',
  Highest: '#de350b',
  High: '#ff5630',
  Medium: '#ffab00',
  Low: '#36b37e',
  Lowest: '#97a0af',
}

export function getPriorityColor(priority: string): string {
  return PRIORITY_COLORS[priority] || '#42526e'
}
