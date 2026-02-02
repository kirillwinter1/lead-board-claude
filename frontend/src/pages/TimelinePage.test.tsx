import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { TimelinePage } from './TimelinePage'
import { teamsApi } from '../api/teams'
import * as forecastApi from '../api/forecast'
import * as configApi from '../api/config'

vi.mock('../api/teams', () => ({
  teamsApi: {
    getAll: vi.fn(),
  },
}))

vi.mock('../api/forecast', () => ({
  getForecast: vi.fn(),
  getUnifiedPlanning: vi.fn(),
  getAvailableSnapshotDates: vi.fn(),
  getUnifiedPlanningSnapshot: vi.fn(),
  getForecastSnapshot: vi.fn(),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

// Mock issue type icons
vi.mock('../icons/story.png', () => ({ default: 'story.png' }))
vi.mock('../icons/bug.png', () => ({ default: 'bug.png' }))
vi.mock('../icons/epic.png', () => ({ default: 'epic.png' }))
vi.mock('../icons/subtask.png', () => ({ default: 'subtask.png' }))

const mockTeams = [
  { id: 1, name: 'Team Alpha', jiraTeamValue: 'alpha', active: true, memberCount: 5 },
  { id: 2, name: 'Team Beta', jiraTeamValue: 'beta', active: true, memberCount: 3 },
]

const mockUnifiedPlan = {
  teamId: 1,
  planningDate: '2024-01-15',
  epics: [
    {
      epicKey: 'EPIC-1',
      summary: 'First Epic',
      autoScore: 80,
      startDate: '2024-01-16',
      endDate: '2024-02-15',
      status: 'In Progress',
      dueDate: '2024-02-20',
      stories: [],
      phaseAggregation: {
        saHours: 16,
        devHours: 32,
        qaHours: 8,
        saStartDate: '2024-01-16',
        saEndDate: '2024-01-18',
        devStartDate: '2024-01-19',
        devEndDate: '2024-01-23',
        qaStartDate: '2024-01-24',
        qaEndDate: '2024-01-25',
      },
      storiesTotal: 1,
      storiesActive: 1,
    },
  ],
  warnings: [],
  assigneeUtilization: {},
}

const mockForecast = {
  calculatedAt: '2024-01-15T10:00:00Z',
  teamId: 1,
  teamCapacity: { saHoursPerDay: 8, devHoursPerDay: 24, qaHoursPerDay: 8 },
  wipStatus: { limit: 5, current: 1, exceeded: false },
  epics: [],
}

const renderTimelinePage = () => {
  return render(
    <BrowserRouter>
      <TimelinePage />
    </BrowserRouter>
  )
}

describe('TimelinePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(teamsApi.getAll).mockResolvedValue(mockTeams)
    vi.mocked(forecastApi.getUnifiedPlanning).mockResolvedValue(mockUnifiedPlan as any)
    vi.mocked(forecastApi.getForecast).mockResolvedValue(mockForecast as any)
    vi.mocked(forecastApi.getAvailableSnapshotDates).mockResolvedValue([])
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
  })

  describe('Rendering', () => {
    it('should render team selector after loading', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
        expect(screen.getByText('Team Beta')).toBeInTheDocument()
      })
    })

    it('should render epic info after loading', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('First Epic')).toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })
  })

  describe('Team selection', () => {
    it('should load unified planning for first team', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(forecastApi.getUnifiedPlanning).toHaveBeenCalledWith(1)
      })
    })

    it('should reload data on team change', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      vi.mocked(forecastApi.getUnifiedPlanning).mockClear()

      const selects = screen.getAllByRole('combobox')
      fireEvent.change(selects[0], { target: { value: '2' } })

      await waitFor(() => {
        expect(forecastApi.getUnifiedPlanning).toHaveBeenCalledWith(2)
      })
    })
  })

  describe('Empty state', () => {
    it('should show empty select when no teams', async () => {
      vi.mocked(teamsApi.getAll).mockResolvedValue([])

      renderTimelinePage()

      await waitFor(() => {
        // When no teams, the select shows "Выберите команду..." placeholder
        expect(screen.getByText('Выберите команду...')).toBeInTheDocument()
      })
    })
  })

  describe('Error handling', () => {
    it('should show error on teams load failure', async () => {
      vi.mocked(teamsApi.getAll).mockRejectedValue(new Error('Network error'))

      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText(/Failed to load teams/i)).toBeInTheDocument()
      })
    })
  })
})
