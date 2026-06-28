import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within, act } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { MatrixPage } from './MatrixPage'
import { teamsApi } from '../api/teams'
import * as matrixApi from '../api/matrixApi'
import * as configApi from '../api/config'
import type { MatrixCard, MatrixView } from '../api/matrixApi'

// Capture the DndContext onDragEnd handler so the test can simulate a drop
// without driving real pointer events (unreliable in jsdom).
const dnd = vi.hoisted(() => ({ onDragEnd: null as null | ((e: unknown) => void) }))

vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children, onDragEnd }: { children: React.ReactNode; onDragEnd: (e: unknown) => void }) => {
    dnd.onDragEnd = onDragEnd
    return <>{children}</>
  },
  useDraggable: () => ({ attributes: {}, listeners: {}, setNodeRef: () => {}, transform: null, isDragging: false }),
  useDroppable: () => ({ setNodeRef: () => {}, isOver: false }),
  PointerSensor: class {},
  useSensor: () => ({}),
  useSensors: () => [],
}))

vi.mock('@dnd-kit/utilities', () => ({
  CSS: { Translate: { toString: () => '' }, Transform: { toString: () => '' } },
}))

vi.mock('../api/teams', () => ({
  teamsApi: { getAll: vi.fn() },
}))

vi.mock('../api/matrixApi', () => ({
  getMatrix: vi.fn(),
  triage: vi.fn(),
  getRecommendations: vi.fn().mockResolvedValue({ zeroBugPolicy: { openBugCount: 0, bugs: [] }, roles: [] }),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getIssueTypeIconUrl: () => null,
    getIssueTypeCategory: () => null,
    getPriorityIconUrl: () => null,
  }),
}))

const mockTeams = [
  { id: 1, name: 'Team Alpha', jiraTeamValue: 'alpha', color: '#FF0000', active: true, memberCount: 5 },
  { id: 2, name: 'Team Beta', jiraTeamValue: 'beta', color: '#00FF00', active: true, memberCount: 3 },
]

const card = (issueKey: string, quadrant: MatrixCard['quadrant']): MatrixCard => ({
  issueKey,
  summary: `Summary for ${issueKey}`,
  issueType: 'Task',
  priority: 'High',
  estimateHours: 4,
  assigneeDisplayName: 'Alice',
  status: 'To Do',
  quadrant,
})

const mockView: MatrixView = {
  p1: [card('LB-10', 'P1')],
  p2: [],
  p3: [],
  p4: [],
  unassigned: [card('LB-1', null), card('LB-2', null)],
}

const renderPage = () =>
  render(
    <BrowserRouter>
      <MatrixPage />
    </BrowserRouter>,
  )

describe('MatrixPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.history.pushState({}, '', '/')
    dnd.onDragEnd = null
    vi.mocked(teamsApi.getAll).mockResolvedValue(mockTeams)
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
    vi.mocked(matrixApi.getMatrix).mockResolvedValue(mockView)
    vi.mocked(matrixApi.triage).mockImplementation(async (issueKey, quadrant) => card(issueKey, quadrant))
  })

  it('renders the four quadrants and the unassigned zone', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByTestId('matrix-zone-P1')).toBeInTheDocument()
      expect(screen.getByTestId('matrix-zone-P2')).toBeInTheDocument()
      expect(screen.getByTestId('matrix-zone-P3')).toBeInTheDocument()
      expect(screen.getByTestId('matrix-zone-P4')).toBeInTheDocument()
      expect(screen.getByTestId('matrix-zone-unassigned')).toBeInTheDocument()
    })
  })

  it('loads the matrix for the auto-selected first team', async () => {
    renderPage()
    await waitFor(() => {
      expect(matrixApi.getMatrix).toHaveBeenCalledWith(1)
    })
  })

  it('places cards in the correct zones from the getMatrix response', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByTestId('matrix-zone-unassigned')).toBeInTheDocument()
    })

    const unassigned = screen.getByTestId('matrix-zone-unassigned')
    expect(within(unassigned).getByText('LB-1')).toBeInTheDocument()
    expect(within(unassigned).getByText('LB-2')).toBeInTheDocument()

    const p1 = screen.getByTestId('matrix-zone-P1')
    expect(within(p1).getByText('LB-10')).toBeInTheDocument()
  })

  it('calls triage with the target quadrant and optimistically moves the card', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByTestId('matrix-zone-unassigned')).toBeInTheDocument()
    })

    // Simulate dropping LB-1 from the unassigned zone into P2.
    await act(async () => {
      dnd.onDragEnd?.({ active: { id: 'LB-1' }, over: { id: 'P2' } })
    })

    expect(matrixApi.triage).toHaveBeenCalledWith('LB-1', 'P2')

    const p2 = screen.getByTestId('matrix-zone-P2')
    await waitFor(() => {
      expect(within(p2).getByText('LB-1')).toBeInTheDocument()
    })
    // The card no longer appears in the unassigned zone.
    const unassigned = screen.getByTestId('matrix-zone-unassigned')
    expect(within(unassigned).queryByText('LB-1')).not.toBeInTheDocument()
  })

  it('reverts the optimistic move and shows an error when triage fails', async () => {
    vi.mocked(matrixApi.triage).mockRejectedValue(new Error('boom'))
    renderPage()
    await waitFor(() => {
      expect(screen.getByTestId('matrix-zone-unassigned')).toBeInTheDocument()
    })

    await act(async () => {
      dnd.onDragEnd?.({ active: { id: 'LB-1' }, over: { id: 'P3' } })
    })

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('LB-1')
    })
    // Card is back in the unassigned zone after the revert.
    const unassigned = screen.getByTestId('matrix-zone-unassigned')
    expect(within(unassigned).getByText('LB-1')).toBeInTheDocument()
    const p3 = screen.getByTestId('matrix-zone-P3')
    expect(within(p3).queryByText('LB-1')).not.toBeInTheDocument()
  })

  it('triages a card back to unassigned (null quadrant)', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByTestId('matrix-zone-P1')).toBeInTheDocument()
    })

    await act(async () => {
      dnd.onDragEnd?.({ active: { id: 'LB-10' }, over: { id: 'unassigned' } })
    })

    expect(matrixApi.triage).toHaveBeenCalledWith('LB-10', null)
  })
})
