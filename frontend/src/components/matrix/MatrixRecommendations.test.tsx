import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MatrixRecommendations } from './MatrixRecommendations'
import type { RecommendationView } from '../../api/matrixApi'

vi.mock('../../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getIssueTypeIconUrl: () => null,
    getIssueTypeCategory: () => null,
    getRoleColor: () => '#206A83',
  }),
}))

vi.mock('../board/helpers', () => ({ getIssueIcon: () => '' }))

const emptyView: RecommendationView = {
  zeroBugPolicy: { openBugCount: 0, bugs: [] },
  recommended: [],
  needsEstimation: [],
}

describe('MatrixRecommendations', () => {
  it('renders the Zero Bug Policy section with open bugs', () => {
    const data: RecommendationView = {
      zeroBugPolicy: {
        openBugCount: 2,
        bugs: [
          { issueKey: 'BUG-1', summary: 'b1', issueType: 'Bug', priority: null, estimateHours: 4, assigneeDisplayName: null, status: 'New', quadrant: null, workflowRole: null },
          { issueKey: 'BUG-2', summary: 'b2', issueType: 'Bug', priority: null, estimateHours: null, assigneeDisplayName: null, status: 'New', quadrant: null, workflowRole: null },
        ],
      },
      recommended: [],
      needsEstimation: [],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/Zero Bug Policy — 2 открытых багов/)).toBeInTheDocument()
    expect(screen.getByText('BUG-1')).toBeInTheDocument()
    expect(screen.getByText(/Нет распределённых задач/)).toBeInTheDocument()
  })

  it('renders a recommended story once with its role composition and total', () => {
    const data: RecommendationView = {
      zeroBugPolicy: { openBugCount: 0, bugs: [] },
      recommended: [{
        issueKey: 'PROJ-1', summary: 'Story one', issueType: 'Story', priority: null,
        status: 'To Do', quadrant: 'P1',
        roles: [
          { roleCode: 'DEV', subtaskKey: 'PROJ-1-2', hours: 16 },
          { roleCode: 'QA', subtaskKey: 'PROJ-1-3', hours: 8 },
          { roleCode: 'SA', subtaskKey: 'PROJ-1-1', hours: 8 },
        ],
        totalHours: 32,
      }],
      needsEstimation: [],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/0 багов, политика соблюдается/)).toBeInTheDocument()
    // story shown once, not per role
    expect(screen.getAllByText('PROJ-1')).toHaveLength(1)
    expect(screen.getByText('DEV 16ч')).toBeInTheDocument()
    expect(screen.getByText('QA 8ч')).toBeInTheDocument()
    expect(screen.getByText('SA 8ч')).toBeInTheDocument()
    expect(screen.getByText('Всего 32ч')).toBeInTheDocument()
  })

  it('renders the needs-estimation warning list', () => {
    const data: RecommendationView = {
      ...emptyView,
      needsEstimation: [
        { issueKey: 'PROJ-9', summary: 'uncut', issueType: 'Story', priority: null, estimateHours: null, assigneeDisplayName: null, status: 'To Do', quadrant: 'P2', workflowRole: null },
      ],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/Требует нарезки/)).toBeInTheDocument()
    expect(screen.getByText('PROJ-9')).toBeInTheDocument()
  })

  it('renders nothing when data is null', () => {
    const { container } = render(<MatrixRecommendations data={null} jiraBaseUrl="https://j/" />)
    expect(container).toBeEmptyDOMElement()
  })
})
