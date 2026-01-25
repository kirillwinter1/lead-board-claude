import axios from 'axios'

export interface ScoreBreakdown {
  issueType: number
  status: number
  progress: number
  priority: number
  dependency: number
  dueDate: number
  estimateQuality: number
  flagged: number
  manual: number
}

export interface SubtaskInfo {
  subtaskKey: string
  summary: string
  issueType: string
  status: string
  assignee: string | null
  estimateSeconds: number | null
  timeSpentSeconds: number | null
}

export interface StoryWithScore {
  storyKey: string
  summary: string
  issueType: string
  status: string
  priority: string | null
  flagged: boolean | null
  autoScore: number
  manualBoost: number | null
  blockedBy: string[] | null
  blocks: string[] | null
  canStart: boolean
  estimateSeconds: number | null
  timeSpentSeconds: number | null
  progress: number
  scoreBreakdown: ScoreBreakdown
  subtasks: SubtaskInfo[]
}

export interface DependencyEdge {
  from: string
  to: string
  type: string
}

export interface DependencyGraph {
  nodes: string[]
  edges: DependencyEdge[]
}

export interface StoriesResponse {
  stories: StoryWithScore[]
  dependencyGraph: DependencyGraph
}

export interface UpdatePriorityRequest {
  boost: number
}

/**
 * Get stories for an epic sorted by AutoScore
 */
export async function getStoriesWithScore(epicKey: string): Promise<StoriesResponse> {
  const response = await axios.get(`/api/epics/${epicKey}/stories`)
  return response.data
}

/**
 * Update manual priority boost for a story
 */
export async function updateStoryPriority(storyKey: string, boost: number): Promise<StoryWithScore> {
  const response = await axios.patch(`/api/stories/${storyKey}/priority`, { boost })
  return response.data
}

/**
 * Recalculate AutoScore for stories
 */
export async function recalculateStories(epicKey?: string): Promise<{ recalculated: number; timestamp: string }> {
  const params = epicKey ? { epicKey } : {}
  const response = await axios.post('/api/planning/recalculate-stories', null, { params })
  return response.data
}
