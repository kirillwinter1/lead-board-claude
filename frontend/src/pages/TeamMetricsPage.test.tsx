import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { TeamMetricsPage } from './TeamMetricsPage'
import { teamsApi } from '../api/teams'
import * as metricsApi from '../api/metrics'
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
  getThroughput: vi.fn().mockResolvedValue({
    totalEpics: 0,
    totalStories: 0,
    totalSubtasks: 0,
    totalBugs: 0,
    total: 0,
    byPeriod: [
      { periodStart: '2024-01-01', periodEnd: '2024-01-07', epics: 0, stories: 0, subtasks: 0, bugs: 0, total: 3 },
    ],
    movingAverage: [],
  }),
  getVelocity: vi.fn(),
  getEpicBurndown: vi.fn(),
  getEpicsForBurndown: vi.fn(),
  getMetricsDataStatus: vi.fn().mockResolvedValue({
    lastSyncCompletedAt: null,
    syncInProgress: false,
    issuesInScope: 0,
    issuesWithChangelog: 0,
    dataCoveragePercent: 0,
  }),
  getDeliveryHealth: vi.fn().mockResolvedValue({
    score: 0,
    grade: 'N/A',
    dimensions: [],
    alerts: [],
  }),
  getMonthlyDsr: vi.fn().mockResolvedValue({ teamId: 0, months: [] }),
  getExecutiveSummary: vi.fn().mockResolvedValue(
    (() => {
      const kpi = (label: string) => ({
        label,
        value: '0',
        rawValue: 0,
        prevValue: null,
        deltaPercent: null,
        trend: 'STABLE',
        sampleSize: 0,
        target: null,
      })
      return {
        throughput: kpi('Throughput'),
        cycleTimeMedian: kpi('Cycle Time'),
        leadTimeMedian: kpi('Lead Time'),
        predictability: kpi('Predictability'),
        capacityUtilization: kpi('Capacity'),
        blockedRisk: kpi('Blocked/Aging'),
      }
    })()
  ),
  getSparklines: vi.fn().mockResolvedValue({
    throughput: [],
    cycleTimeMedian: [],
    leadTimeMedian: [],
    predictability: [],
    utilization: [],
  }),
}))

vi.mock('../api/forecast', () => ({
  getWipHistory: vi.fn(),
  createWipSnapshot: vi.fn(),
  getRoleLoad: vi.fn().mockResolvedValue({ roles: [], alerts: [] }),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getRoleCodes: () => ['SA', 'DEV', 'QA'],
    getRoleColor: () => '#669DF1',
    getRoleDisplayName: (code: string) => code,
    getIssueTypeIconUrl: () => undefined,
    issueTypeIcons: {},
    issueTypeCategories: {
      'Epic': 'EPIC',
      'Story': 'STORY',
      'Sub-task': 'SUBTASK',
    },
    config: { roles: [], issueTypes: [], statuses: [] },
    loading: false,
  }),
}))

// Mock heavy / API-driven chart components that aren't the subject under test.
// The throughput mock exposes the mode callback so we can drive the page's
// fetching logic without rendering the real chart/dropdown.
vi.mock('../components/metrics/ThroughputChart', () => ({
  ThroughputChart: ({ onModeChange }: { onModeChange: (mode: string) => void }) => (
    <div data-testid="throughput-chart">
      <button onClick={() => onModeChange('Story')}>set-mode-story</button>
      <button onClick={() => onModeChange('all')}>set-mode-all</button>
    </div>
  ),
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

vi.mock('../components/WorklogTimeline', () => ({
  WorklogTimeline: () => <div data-testid="worklog-timeline">WorklogTimeline</div>,
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

// Opens the custom team SingleSelectDropdown (there is exactly one on the page).
const openTeamDropdown = (container: HTMLElement) => {
  const trigger = container.querySelector('.filter-dropdown-trigger')
  expect(trigger).not.toBeNull()
  fireEvent.click(trigger!)
}

// Expands a collapsed <MetricsSection> by clicking its header button.
const expandSection = (title: string) => {
  const header = screen.getByText(title).closest('button')
  expect(header).not.toBeNull()
  fireEvent.click(header!)
}

describe('TeamMetricsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // MetricsSection persists expand/collapse state in localStorage — reset so
    // each test starts from the component's defaultExpanded values.
    localStorage.clear()
    // BrowserRouter shares window.location across tests; selecting a team writes
    // ?teamId=… to the URL. Reset it so each test starts with no team in the URL.
    window.history.pushState({}, '', '/')
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

    it('should render team options in the dropdown', async () => {
      const { container } = renderTeamMetricsPage()

      // First team is auto-selected and shown in the trigger.
      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      openTeamDropdown(container)

      // Both teams are listed once the menu is open.
      expect(screen.getByText('Team Beta')).toBeInTheDocument()
      expect(screen.getAllByText('Team Alpha').length).toBeGreaterThan(0)
    })

    it('should render the date range picker presets', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Metrics')).toBeInTheDocument()
      })

      expect(screen.getByText('30d')).toBeInTheDocument()
      expect(screen.getByText('90d')).toBeInTheDocument()
      expect(screen.getByText('180d')).toBeInTheDocument()
      expect(screen.getByText('1y')).toBeInTheDocument()
    })
  })

  describe('Executive summary', () => {
    it('should render the executive summary KPI cards', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Throughput')).toBeInTheDocument()
        expect(screen.getByText('Predictability')).toBeInTheDocument()
        expect(screen.getByText('Capacity')).toBeInTheDocument()
        expect(screen.getByText('Blocked/Aging')).toBeInTheDocument()
      })
    })

    it('should render the delivery health grade and score', async () => {
      vi.mocked(metricsApi.getDeliveryHealth).mockResolvedValue({
        score: 85,
        grade: 'B',
        dimensions: [],
        alerts: [],
      })

      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('B')).toBeInTheDocument()
        expect(screen.getByText('85')).toBeInTheDocument()
      })
    })
  })

  describe('Metrics display', () => {
    it('should render the throughput chart', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('throughput-chart')).toBeInTheDocument()
      })
    })

    it('should render the assignee table when the Drilldown section is expanded', async () => {
      renderTeamMetricsPage()

      // Drilldown is collapsed by default — its children are not mounted.
      await waitFor(() => {
        expect(screen.getByText('Drilldown')).toBeInTheDocument()
      })
      expect(screen.queryByTestId('assignee-table')).not.toBeInTheDocument()

      expandSection('Drilldown')

      await waitFor(() => {
        expect(screen.getByTestId('assignee-table')).toBeInTheDocument()
      })
    })

    it('should render the forecast accuracy chart when the Drilldown section is expanded', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Drilldown')).toBeInTheDocument()
      })
      expect(screen.queryByTestId('forecast-accuracy-chart')).not.toBeInTheDocument()

      expandSection('Drilldown')

      await waitFor(() => {
        expect(screen.getByTestId('forecast-accuracy-chart')).toBeInTheDocument()
      })
    })
  })

  describe('Filter interactions', () => {
    it('should refetch metrics for the newly selected team', async () => {
      const { container } = renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })

      vi.mocked(metricsApi.getMetricsSummary).mockClear()

      openTeamDropdown(container)
      fireEvent.click(screen.getByText('Team Beta'))

      await waitFor(() => {
        expect(metricsApi.getMetricsSummary).toHaveBeenCalledWith(
          2,
          expect.any(String),
          expect.any(String)
        )
      })
    })
  })

  describe('Throughput type selector', () => {
    it('fetches epic and story throughput in the default "Epics & Stories" mode', async () => {
      renderTeamMetricsPage()

      // Epic + Story type names are resolved from issueTypeCategories ('Epic'→EPIC, 'Story'→STORY).
      await waitFor(() => {
        expect(metricsApi.getThroughput).toHaveBeenCalledWith(1, expect.any(String), expect.any(String), 'Epic')
        expect(metricsApi.getThroughput).toHaveBeenCalledWith(1, expect.any(String), expect.any(String), 'Story')
      })
    })

    it('refetches a single type when the mode switches to one issue type', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('throughput-chart')).toBeInTheDocument()
      })

      vi.mocked(metricsApi.getThroughput).mockClear()
      fireEvent.click(screen.getByText('set-mode-story'))

      await waitFor(() => {
        expect(metricsApi.getThroughput).toHaveBeenCalledWith(1, expect.any(String), expect.any(String), 'Story')
      })
      // Single-type mode issues exactly one request (not the two-series epic+story pair).
      expect(metricsApi.getThroughput).toHaveBeenCalledTimes(1)
    })

    it('does not refetch when switching to "All (total)" — it reuses the summary', async () => {
      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.getByTestId('throughput-chart')).toBeInTheDocument()
      })

      vi.mocked(metricsApi.getThroughput).mockClear()
      fireEvent.click(screen.getByText('set-mode-all'))

      // "all" mode derives its series from the already-loaded summary — no request.
      await waitFor(() => {
        expect(screen.getByTestId('throughput-chart')).toBeInTheDocument()
      })
      expect(metricsApi.getThroughput).not.toHaveBeenCalled()
    })
  })

  describe('Empty state', () => {
    it('should show placeholder when there are no teams', async () => {
      vi.mocked(teamsApi.getAll).mockResolvedValue([])

      renderTeamMetricsPage()

      await waitFor(() => {
        expect(screen.queryByText('Loading...')).not.toBeInTheDocument()
      })

      // The team dropdown falls back to its placeholder when nothing is selected.
      await waitFor(() => {
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

      // After loading, the first team should be selected and metrics loaded.
      await waitFor(() => {
        expect(metricsApi.getMetricsSummary).toHaveBeenCalled()
      })
    })
  })
})
