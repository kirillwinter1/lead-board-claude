import epicIcon from '../../icons/epic.png'
import storyIcon from '../../icons/story.png'
import bugIcon from '../../icons/bug.png'
import subtaskIcon from '../../icons/subtask.png'

// Sound effect for drag & drop - Pop sound
export const playDropSound = () => {
  const audioContext = new (window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext)()
  const oscillator = audioContext.createOscillator()
  const gainNode = audioContext.createGain()

  oscillator.connect(gainNode)
  gainNode.connect(audioContext.destination)

  // Pop: высокая частота с быстрым падением
  oscillator.frequency.setValueAtTime(600, audioContext.currentTime)
  oscillator.frequency.exponentialRampToValueAtTime(150, audioContext.currentTime + 0.08)

  gainNode.gain.setValueAtTime(0.4, audioContext.currentTime)
  gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.08)

  oscillator.start(audioContext.currentTime)
  oscillator.stop(audioContext.currentTime + 0.08)
}

export const issueTypeIcons: Record<string, string> = {
  'Эпик': epicIcon,
  'Epic': epicIcon,
  'История': storyIcon,
  'Story': storyIcon,
  'Баг': bugIcon,
  'Bug': bugIcon,
  'Подзадача': subtaskIcon,
  'Sub-task': subtaskIcon,
  'Subtask': subtaskIcon,
  'Аналитика': subtaskIcon,
  'Разработка': subtaskIcon,
  'Тестирование': subtaskIcon,
  'Analytics': subtaskIcon,
  'Development': subtaskIcon,
  'Testing': subtaskIcon,
}

export function getIssueIcon(issueType: string): string {
  return issueTypeIcons[issueType] || storyIcon
}

export function isEpic(issueType: string): boolean {
  return issueType === 'Epic' || issueType === 'Эпик'
}

// Format number compactly: no decimal for >= 100, one decimal otherwise
export function formatCompact(n: number): string {
  if (n >= 100) return Math.round(n).toString()
  return n.toFixed(1)
}

// Recommendation indicator - shows if manual order differs from autoScore recommendation
export function getRecommendationIcon(actualPosition: number, recommendedPosition: number, autoScore: number | null): { icon: string; className: string } {
  if (autoScore === null || actualPosition === recommendedPosition) {
    return { icon: '●', className: 'match' }
  }
  if (actualPosition < recommendedPosition) {
    return { icon: '↓', className: 'suggest-down' }
  }
  return { icon: '↑', className: 'suggest-up' }
}
