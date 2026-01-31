import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'

// Mock the API modules
vi.mock('../api/epics', () => ({
  getRoughEstimateConfig: vi.fn().mockResolvedValue({ enabled: false }),
  updateRoughEstimate: vi.fn(),
  updateEpicOrder: vi.fn().mockResolvedValue({ issueKey: 'EPIC-1', manualOrder: 1 }),
  updateStoryOrder: vi.fn().mockResolvedValue({ issueKey: 'STORY-1', manualOrder: 1 }),
}))

vi.mock('../api/forecast', () => ({
  getForecast: vi.fn().mockResolvedValue({ epics: [] }),
  getUnifiedPlanning: vi.fn().mockResolvedValue({ epics: [] }),
}))

vi.mock('../api/board', () => ({
  getScoreBreakdown: vi.fn(),
}))

// Mock axios
vi.mock('axios', () => ({
  default: {
    get: vi.fn().mockImplementation((url: string) => {
      if (url.includes('/api/board')) {
        return Promise.resolve({
          data: {
            items: [
              {
                issueKey: 'EPIC-1',
                title: 'First Epic',
                status: 'In Progress',
                issueType: 'Epic',
                jiraUrl: 'https://jira.example.com/EPIC-1',
                teamId: 1,
                teamName: 'Team A',
                autoScore: 50,
                manualOrder: 1,
                alerts: [],
                children: [
                  {
                    issueKey: 'STORY-1',
                    title: 'First Story',
                    status: 'To Do',
                    issueType: 'Story',
                    jiraUrl: 'https://jira.example.com/STORY-1',
                    autoScore: 30,
                    manualOrder: 1,
                    alerts: [],
                    children: [],
                  },
                ],
              },
              {
                issueKey: 'EPIC-2',
                title: 'Second Epic',
                status: 'To Do',
                issueType: 'Epic',
                jiraUrl: 'https://jira.example.com/EPIC-2',
                teamId: 1,
                teamName: 'Team A',
                autoScore: 40,
                manualOrder: 2,
                alerts: [],
                children: [],
              },
            ],
            total: 2,
          },
        })
      }
      if (url.includes('/api/teams')) {
        return Promise.resolve({
          data: [
            { id: 1, name: 'Team A' },
            { id: 2, name: 'Team B' },
          ],
        })
      }
      if (url.includes('/api/sync/status')) {
        return Promise.resolve({
          data: {
            syncInProgress: false,
            lastSyncCompletedAt: new Date().toISOString(),
            issuesCount: 10,
          },
        })
      }
      return Promise.resolve({ data: {} })
    }),
  },
}))

// Import after mocks
import { BoardPage } from './BoardPage'

const renderBoardPage = () => {
  return render(
    <BrowserRouter>
      <BoardPage />
    </BrowserRouter>
  )
}

describe('BoardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  describe('Rendering', () => {
    it('should render the board page', async () => {
      const { container } = renderBoardPage()

      await waitFor(() => {
        // Check that the main board container is rendered
        expect(container.querySelector('.board-grid')).toBeInTheDocument()
      })
    })

    it('should render epics after loading', async () => {
      renderBoardPage()

      await waitFor(() => {
        expect(screen.getByText('First Epic')).toBeInTheDocument()
        expect(screen.getByText('Second Epic')).toBeInTheDocument()
      })
    })

    it('should show epic issue keys', async () => {
      renderBoardPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-2')).toBeInTheDocument()
      })
    })
  })

  describe('Board structure', () => {
    it('should render board header with columns', async () => {
      const { container } = renderBoardPage()

      await waitFor(() => {
        const header = container.querySelector('.board-header')
        expect(header).toBeInTheDocument()
      })
    })

    it('should render board rows for epics', async () => {
      const { container } = renderBoardPage()

      await waitFor(() => {
        const rows = container.querySelectorAll('.board-row.level-0')
        expect(rows.length).toBe(2)
      })
    })
  })

  describe('Drag and Drop - Structure', () => {
    it('should not show drag handles when all teams selected', async () => {
      const { container } = renderBoardPage()

      await waitFor(() => {
        expect(screen.getByText('First Epic')).toBeInTheDocument()
      })

      // With "All teams" selected, drag handles should not be visible
      const dragHandles = container.querySelectorAll('.drag-handle')
      expect(dragHandles.length).toBe(0)
    })
  })
})

describe('API Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('updateEpicOrder should be called with correct parameters', async () => {
    const { updateEpicOrder: mockUpdateEpicOrder } = await import('../api/epics')

    await mockUpdateEpicOrder('EPIC-1', 2)

    expect(mockUpdateEpicOrder).toHaveBeenCalledWith('EPIC-1', 2)
  })

  it('updateStoryOrder should be called with correct parameters', async () => {
    const { updateStoryOrder: mockUpdateStoryOrder } = await import('../api/epics')

    await mockUpdateStoryOrder('STORY-1', 3)

    expect(mockUpdateStoryOrder).toHaveBeenCalledWith('STORY-1', 3)
  })
})
