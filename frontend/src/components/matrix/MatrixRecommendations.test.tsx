import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MatrixRecommendations } from './MatrixRecommendations'
import type { RecommendationView } from '../../api/matrixApi'

vi.mock('../../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getIssueTypeIconUrl: () => null,
    getIssueTypeCategory: () => null,
    getRoleColor: () => '#206A83',
    getRoleDisplayName: (c: string) => c,
  }),
}))

vi.mock('../board/helpers', () => ({ getIssueIcon: () => '' }))

function card(issueKey: string, extra: Partial<RecommendationView['roles'][0]['ready'][0]> = {}) {
  return {
    issueKey, summary: `S ${issueKey}`, issueType: 'Story', priority: null,
    estimateHours: null, assigneeDisplayName: null, status: 'To Do', quadrant: null,
    workflowRole: null, roleSubtaskKey: null, roleEstimateHours: null,
    cumulativeHours: null, fitsInIdle: null, ...extra,
  }
}

describe('MatrixRecommendations', () => {
  it('renders the Zero Bug Policy section with open bugs', () => {
    const data: RecommendationView = {
      zeroBugPolicy: { openBugCount: 2, bugs: [card('BUG-1'), card('BUG-2')] },
      roles: [],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/Zero Bug Policy — 2 открытых багов/)).toBeInTheDocument()
    expect(screen.getByText('BUG-1')).toBeInTheDocument()
    expect(screen.getByText(/Все роли загружены/)).toBeInTheDocument()
  })

  it('renders the clean state and a role block with both sections', () => {
    const data: RecommendationView = {
      zeroBugPolicy: { openBugCount: 0, bugs: [] },
      roles: [
        { roleCode: 'QA', idleHours: 16, ready: [card('PROJ-1', { quadrant: 'P1', roleEstimateHours: 2, cumulativeHours: 2, fitsInIdle: true })], needsEstimation: [card('PROJ-2', { quadrant: 'P2' })] },
      ],
    }
    render(<MatrixRecommendations data={data} jiraBaseUrl="https://j/" />)
    expect(screen.getByText(/0 багов, политика соблюдается/)).toBeInTheDocument()
    expect(screen.getByText(/простой 16ч/)).toBeInTheDocument()
    expect(screen.getByText('PROJ-1')).toBeInTheDocument()
    expect(screen.getByText('PROJ-2')).toBeInTheDocument()
  })

  it('renders nothing when data is null', () => {
    const { container } = render(<MatrixRecommendations data={null} jiraBaseUrl="https://j/" />)
    expect(container).toBeEmptyDOMElement()
  })
})
