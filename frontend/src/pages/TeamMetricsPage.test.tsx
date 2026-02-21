import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { TeamMetricsPage } from './TeamMetricsPage'
import { teamsApi } from '../api/teams'
import * as metricsApi from '../api/metrics'
import * as forecastApi from '../api/forecast'
import * as configApi from '../api/config'

vi.mock('../api/teams', () => ({
  teamsApi: {
    getAll: vi.fn(),
  },
}))

vi.mock('../api/metrics', () => ({
  getMetricsSummary: vi.fn(),
  getForecastAccuracy: vi.fn(),
  getDsr: vi.fn(),
  getVelocity: vi.fn(),
  getEpicBurndown: vi.fn(),
  getEpicsForBurndown: vi.fn(),
}))

vi.mock('../api/forecast', () => ({
  getWipHistory: vi.fn(),
  createWipSnapshot: vi.fn(),
  getRoleLoad: vi.fn().mockResolvedValue({ roles: [], alerts: [] }),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

// Mock chart components
vi.mock('../components/metrics/MetricCard', () => ({
  MetricCard: ({ title, value, subtitle }: { title: string; value: string | number; subtitle: string }) => (
    <div data-testid={`metric-card-${title.toLowerCase().replace(/\s+/g, '-')}`}>
      <div>{title}</div>
      <div>{value}</div>
      <div>{subtitle}</div>
    </div>
  ),
}))

vi.mock('../components/metrics/DsrGauge', () => ({
  DsrGauge: ({ title, value }: { title: string; value: number | null }) => (
    <div data-testid={`dsr-gauge-${title.toLowerCase().replace(/\s+/g, '-')}`}>
      <div>{title}</div>
      <div>{value ?? 'N/A'}</div>
    </div>
  ),
}))

vi.mock('../components/metrics/ThroughputChart', () => ({
  ThroughputChart: () => <div data-testid="throughput-chart">ThroughputChart</div>,
}))

vi.mock('../components/metrics/TimeInStatusChart', () => ({
  TimeInStatusChart: () => <div data-testid="time-in-status-chart">TimeInStatusChart</div>,
}))

vi.mock('../components/metrics/AssigneeTable', () => ({
  AssigneeTable: () => <div data-testid="assignee-table">AssigneeTable</div>,
}))

vi.mock('../components/metrics/ForecastAccuracyChart', () => ({
  ForecastAccuracyChart: () => <div data-testid="forecast-accuracy-chart">ForecastAccuracyChart</div>,
}))

vi.mock('../components/metrics/VelocityChart', () => ({
  VelocityChart: () => <div data-testid="velocity-chart">VelocityChart</div>,
}))

vi.mock('../components/metrics/EpicBurndownChart', () => ({
  EpicBurndownChart: () => <div data-testid="epic-burndown-chart">EpicBurndownChart</div>,
}))

vi.mock('../components/metrics/RoleLoadBlock', () => ({
  RoleLoadBlock: () => <div data-testid="role-load-block">RoleLoadBlock</div>,
}))

const mockTeams = [
  { id: 1, name: 'Team Alpha', jiraTeamValue: 'alpha', active: true, memberCount: 5 },
  { id: 2, name: 'Team Beta', jiraTeamValue: 'beta', active: true, memberCount: 3 },
]

const mockMetrics = {
  from: '2024-01-01',
  to: '2024-01-31',
  teamId: 1,
  throughput: {
    totalEpics: 5,
    totalStories: 20,
    totalSubtasks: 50,
    total: 75,
    byPeriod: [],
  },
  leadTime: { avgDays: 10, medianDays: 8, p90Days: 15, minDays: 3, maxDays: 25, sampleSize: 20 },
  cycleTime: { avgDays: 5, medianDays: 4, p90Days: 8, minDays: 1, maxDays: 12, sampleSize: 15 },
  timeInStatuses: [],
  byAssignee: [],
}

const mockDsr = {
  avgDsrActual: 0.95,
  avgDsrForecast: 1.05,
  totalEpics: 10,
  onTimeCount: 8,
  onTimeRate: 80,
  epics: [],
}

const mockForecastAccuracy = {
  teamId: 1,
  from: '2024-01-01',
  to: '2024-01-31',
  avgAccuracyRatio: 0.92,
  onTimeDeliveryRate: 0.75,
  avgScheduleVariance: 2,
  totalCompleted: 12,
  onTimeCount: 9,
  lateCount: 2,
  earlyCount: 1,
  epics: [],
}

const renderTeamMetricsPage = () => {
  return render(
    <BrowserRouter>
      <TeamMetricsPage />
    </BrowserRouter>
  )
}

describe('TeamMetricsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(teamsApi.getAll).mockResolvedValue(mockTeams)
    vi.mocked(metricsApi.getMetricsSummary).mockResolvedValue(mockMetrics)
    vi.mocked(metricsApi.getDsr).mockResolvedValue(mockDsr)
    vi.mocked(metricsApi.getForecastAccuracy).mockResolvedValue(mockForecastAccuracy)
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
  })

  describe('Rendering', () => {
    it('should render page title', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Metrics')).toBeInTheDocument()
      })
    })

    it('should show loading state initially', () => {
      renderTeamMetricsPage()

      expect(screen.getByText('Loading...')).toBeInTheDocument()
    })

    it('should render team selector', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
        expect(screen.getByText('Team Beta')).toBeInTheDocument()
      })
    })

    it('should render period selector', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Last 7 days')).toBeInTheDocument()
        expect(screen.getByText('Last 30 days')).toBeInTheDocument()
        expect(screen.getByText('Last 90 days')).toBeInTheDocument()
      })
    })

    it('should render issue type filter', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('All')).toBeInTheDocument()
        expect(screen.getByText('Epics')).toBeInTheDocument()
        expect(screen.getByText('Stories')).toBeInTheDocument()
      })
    })
  })

  describe('Metrics display', () => {
    it('should render DSR gauges', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('dsr-gauge-dsr-actual')).toBeInTheDocument()
        expect(screen.getByTestId('dsr-gauge-dsr-forecast')).toBeInTheDocument()
      })
    })

    it('should render throughput metric card', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('metric-card-throughput')).toBeInTheDocument()
      })
    })

    it('should render on-time rate metric card', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('metric-card-on-time-rate')).toBeInTheDocument()
      })
    })

    it('should render throughput chart', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('throughput-chart')).toBeInTheDocument()
      })
    })

    it('should render time in status chart', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('time-in-status-chart')).toBeInTheDocument()
      })
    })

    it('should render assignee table', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('assignee-table')).toBeInTheDocument()
      })
    })

    it('should render forecast accuracy chart', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('forecast-accuracy-chart')).toBeInTheDocument()
      })
    })
  })

  describe('Filter interactions', () => {
    it('should change team on selection', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      // Find all selects and use the first one (team)
      const selects = screen.getAllByRole('combobox')
      fireEvent.change(selects[0], { target: { value: '2' } })

      await waitFor(() => {
        expect(metricsApi.getMetricsSummary).toHaveBeenCalledWith(
          2,
          expect.any(String),
          expect.any(String),
          undefined
        )
      })
    })

    it('should change period on selection', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      // Find all selects and use the second one (period)
      const selects = screen.getAllByRole('combobox')
      fireEvent.change(selects[1], { target: { value: '7' } })

      await waitFor(() => {
        // API should be called with new date range
        expect(metricsApi.getMetricsSummary).toHaveBeenCalled()
      })
    })

    it('should change issue type on selection', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      // Clear previous calls
      vi.mocked(metricsApi.getMetricsSummary).mockClear()

      // Find all selects and use the third one (issue type)
      const selects = screen.getAllByRole('combobox')
      fireEvent.change(selects[2], { target: { value: 'Story' } })

      await waitFor(() => {
        // Check that getMetricsSummary was called (may be called multiple times due to re-renders)
        const calls = vi.mocked(metricsApi.getMetricsSummary).mock.calls
        const hasStoryCall = calls.some(call => call[3] === 'Story')
        expect(hasStoryCall).toBe(true)
      })
    })
  })

  describe('Empty state', () => {
    it('should show message when no teams', async () => {
      vi.mocked(teamsApi.getAll).mockResolvedValue([])

      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
      })

      // When there are no teams and no selected team, check for empty or selection message
      await waitFor(() => {
        // The page shows "Select team..." placeholder when no teams
        expect(screen.getByText('Select team...')).toBeInTheDocument()
      })
    })

    it('should show error message when metrics API fails', async () => {
      vi.mocked(metricsApi.getMetricsSummary).mockRejectedValue(new Error('No data'))
      vi.mocked(metricsApi.getDsr).mockRejectedValue(new Error('No data'))
      vi.mocked(metricsApi.getForecastAccuracy).mockRejectedValue(new Error('No data'))

      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Failed to load metrics: No data')).toBeInTheDocument()
      })
    })
  })

  describe('Error handling', () => {
    it('should show error message on teams load failure', async () => {
      vi.mocked(teamsApi.getAll).mockRejectedValue(new Error('Network error'))

      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Failed to load teams: Network error')).toBeInTheDocument()
      })
    })
  })

  describe('Auto-select first team', () => {
    it('should select first team when no team in URL', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      // After loading, the first team should be selected and metrics loaded
      await waitFor(() => {
        expect(metricsApi.getMetricsSummary).toHaveBeenCalled()
      })
    })
  })
})
