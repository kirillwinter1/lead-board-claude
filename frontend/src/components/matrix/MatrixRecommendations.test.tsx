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
          { issueKey: 'BUG-1', summary: 'b1', issueType: 'Bug', priority: null, estimateHours: 4, assigneeDisplayName: null, status: 'New', quadrant: null, workflowRole: null, daysInStatus: null, statusAgeLevel: 'NORMAL', statusAgeReason: null },
          { issueKey: 'BUG-2', summary: 'b2', issueType: 'Bug', priority: null, estimateHours: null, assigneeDisplayName: null, status: 'New', quadrant: null, workflowRole: null, daysInStatus: null, statusAgeLevel: 'NORMAL', statusAgeReason: null },
        ],
      },
      recommended: [],
      needsEstimation: [],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/Zero Bug Policy — 2 open bugs/)).toBeInTheDocument()
    expect(screen.getByText('BUG-1')).toBeInTheDocument()
    expect(screen.getByText(/No assigned tasks/)).toBeInTheDocument()
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
        daysInStatus: null, statusAgeLevel: 'NORMAL', statusAgeReason: null,
      }],
      needsEstimation: [],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/0 bugs, policy upheld/)).toBeInTheDocument()
    // story shown once, not per role
    expect(screen.getAllByText('PROJ-1')).toHaveLength(1)
    expect(screen.getByText('DEV 16h')).toBeInTheDocument()
    expect(screen.getByText('QA 8h')).toBeInTheDocument()
    expect(screen.getByText('SA 8h')).toBeInTheDocument()
    expect(screen.getByText('Total 32h')).toBeInTheDocument()
  })

  it('renders the needs-estimation warning list', () => {
    const data: RecommendationView = {
      ...emptyView,
      needsEstimation: [
        { issueKey: 'PROJ-9', summary: 'uncut', issueType: 'Story', priority: null, estimateHours: null, assigneeDisplayName: null, status: 'To Do', quadrant: 'P2', workflowRole: null, daysInStatus: null, statusAgeLevel: 'NORMAL', statusAgeReason: null },
      ],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/Needs breakdown/)).toBeInTheDocument()
    expect(screen.getByText('PROJ-9')).toBeInTheDocument()
  })

  it('renders nothing when data is null', () => {
    const { container } = render(<MatrixRecommendations data={null} jiraBaseUrl="https://j/" />)
    expect(container).toBeEmptyDOMElement()
  })
})
