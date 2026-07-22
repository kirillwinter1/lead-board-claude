import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { TimelinePage, calculateDateRange, DEFAULT_PAST_DAYS } from './TimelinePage'
import { daysBetween } from '../utils/dateGrid'
import { teamsApi } from '../api/teams'
import * as forecastApi from '../api/forecast'
import * as configApi from '../api/config'
import * as boardApi from '../api/board'
import * as statusHistoryApi from '../api/statusHistory'

vi.mock('../api/teams', () => ({
  teamsApi: {
    getAll: vi.fn(),
  },
}))

vi.mock('../api/forecast', () => ({
  getForecast: vi.fn(),
  getUnifiedPlanning: vi.fn(),
  getRetrospective: vi.fn(),
  getAvailableSnapshotDates: vi.fn(),
  getUnifiedPlanningSnapshot: vi.fn(),
  getForecastSnapshot: vi.fn(),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

vi.mock('../api/board', () => ({
  getStatusStyles: vi.fn().mockResolvedValue({}),
}))

vi.mock('../api/statusHistory', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/statusHistory')>()
  return { ...actual, getStatusHistory: vi.fn() }
})

vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getRoleCodes: () => ['SA', 'DEV', 'QA'],
    getRoleColor: () => '#669DF1',
    getRoleDisplayName: (code: string) => code,
    getIssueTypeIconUrl: () => undefined,
    getIssueTypeCategory: () => null,
    getTypeNameByCategory: () => null,
    issueTypeIcons: {},
    issueTypeCategories: {},
    config: { roles: [], issueTypes: [], statuses: [] },
    loading: false,
  }),
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
    vi.mocked(forecastApi.getRetrospective).mockResolvedValue({ teamId: 1, epics: [] })
    vi.mocked(forecastApi.getAvailableSnapshotDates).mockResolvedValue([])
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
  })

  describe('Rendering', () => {
    it('should render team selector after loading', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getAllByText('Team Alpha').length).toBeGreaterThan(0)
        expect(screen.getByText('Today (live)')).toBeInTheDocument()
      })
    })

    it('should render epic info after loading', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('First Epic')).toBeInTheDocument()
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should render actuals mode dropdown with Logged time default', async () => {
      renderTimelinePage()

      await waitFor(() => {
        expect(screen.getAllByText('Logged time').length).toBeGreaterThan(0)
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
        expect(screen.getAllByText('Team Alpha').length).toBeGreaterThan(0)
      })

      vi.mocked(forecastApi.getUnifiedPlanning).mockClear()

      fireEvent.click(screen.getAllByText('Team Alpha')[0])
      fireEvent.click(screen.getByText('Team Beta'))

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
        // When no teams, the select shows "Select team..." placeholder
        expect(screen.getByText('Select team...')).toBeInTheDocument()
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

  describe('Actuals mode', () => {
    const daysAgo = (n: number) => {
      const d = new Date()
      d.setHours(0, 0, 0, 0)
      d.setDate(d.getDate() - n)
      return d.toISOString().slice(0, 10)
    }

    const retroWithStatuses = {
      teamId: 1,
      calculatedAt: new Date().toISOString(),
      epics: [
        {
          epicKey: 'EPIC-9',
          summary: 'Retro Epic',
          status: 'Done',
          startDate: daysAgo(10),
          endDate: daysAgo(2),
          progressPercent: 100,
          stories: [
            {
              storyKey: 'PROJ-9',
              summary: 'Retro Story',
              status: 'Done',
              issueType: 'Story',
              completed: true,
              startDate: daysAgo(10),
              endDate: daysAgo(2),
              progressPercent: 100,
              autoScore: null,
              totalEstimateSeconds: null,
              totalLoggedSeconds: null,
              roleProgress: null,
              phases: {
                DEV: { roleCode: 'DEV', startDate: daysAgo(10), endDate: daysAgo(2), durationDays: 8, active: false },
              },
              worklogDays: [{ date: daysAgo(9), roleCode: 'DEV', timeSpentSeconds: 3600 }],
              statusIntervals: [
                { status: 'To Do', startDate: daysAgo(10), endDate: daysAgo(7) },
                { status: 'In Development', startDate: daysAgo(7), endDate: daysAgo(2) },
              ],
            },
          ],
        },
      ],
    }

    beforeEach(() => {
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue(retroWithStatuses as any)
      vi.mocked(boardApi.getStatusStyles).mockResolvedValue({
        'In Development': { color: '#B55FEB', statusCategory: 'indeterminate' },
      } as any)
    })

    const findStatusSegment = (container: HTMLElement) =>
      Array.from(container.querySelectorAll('.story-bar div')).find(
        el => (el as HTMLElement).style.backgroundColor === 'rgb(181, 95, 235)'
      )

    it('does not render status segments in default worklog mode', async () => {
      const { container } = renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('Retro Epic')).toBeInTheDocument()
      })

      expect(findStatusSegment(container)).toBeUndefined()
    })

    it('renders status-colored segments after switching to Story statuses', async () => {
      const { container } = renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('Retro Epic')).toBeInTheDocument()
      })

      fireEvent.click(screen.getAllByText('Logged time')[0])
      fireEvent.click(screen.getByText('Story statuses'))

      await waitFor(() => {
        expect(findStatusSegment(container)).toBeTruthy()
      })
    })

    it('widens the bar when status history extends beyond subtask dates', async () => {
      // Statuses often move after the subtask work is done — the colored interval
      // lies entirely beyond the subtask-derived bar end and must still render.
      const retro = JSON.parse(JSON.stringify(retroWithStatuses))
      const story = retro.epics[0].stories[0]
      story.startDate = daysAgo(12)
      story.endDate = daysAgo(8)
      story.statusIntervals = [
        { status: 'To Do', startDate: daysAgo(12), endDate: daysAgo(6) },
        { status: 'In Development', startDate: daysAgo(6), endDate: daysAgo(3) },
      ]
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue(retro as any)
      const { container } = renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('Retro Epic')).toBeInTheDocument()
      })

      fireEvent.click(screen.getAllByText('Logged time')[0])
      fireEvent.click(screen.getByText('Story statuses'))

      await waitFor(() => {
        expect(findStatusSegment(container)).toBeTruthy()
      })
    })

    it('renders hybrid bar segments as direct children of the bar (no wrapper div)', async () => {
      // Regression: absolute segments wrapped in a bare <div> collapse on
      // .story-bar:hover (the CSS filter turns the 0-height wrapper into the
      // containing block — height:100% becomes 0 and the bar looks grey on hover).
      // Both the solid worklog day and the striped remainder must sit directly in the bar.
      const daysFromNow = (n: number) => {
        const d = new Date()
        d.setHours(0, 0, 0, 0)
        d.setDate(d.getDate() + n)
        return d.toISOString().slice(0, 10)
      }
      const plan = JSON.parse(JSON.stringify(mockUnifiedPlan))
      plan.epics[0].stories = [
        {
          storyKey: 'PROJ-1',
          summary: 'Hybrid Story',
          status: 'In Progress',
          issueType: 'Story',
          autoScore: null,
          startDate: daysAgo(5),
          endDate: daysFromNow(5),
          warnings: [],
          phases: {
            DEV: {
              assigneeAccountId: null,
              assigneeDisplayName: null,
              startDate: daysAgo(5),
              endDate: daysFromNow(5),
              hours: 40,
              noCapacity: false,
            },
          },
        },
      ]
      vi.mocked(forecastApi.getUnifiedPlanning).mockResolvedValue(plan as any)
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue({
        teamId: 1,
        calculatedAt: new Date().toISOString(),
        epics: [
          {
            epicKey: 'EPIC-1',
            summary: 'First Epic',
            status: 'In Progress',
            startDate: daysAgo(5),
            endDate: null,
            progressPercent: 0,
            stories: [
              {
                storyKey: 'PROJ-1',
                summary: 'Hybrid Story',
                status: 'In Progress',
                issueType: 'Story',
                completed: false,
                startDate: daysAgo(5),
                endDate: null,
                progressPercent: 0,
                autoScore: null,
                totalEstimateSeconds: null,
                totalLoggedSeconds: null,
                roleProgress: null,
                phases: {
                  DEV: { roleCode: 'DEV', startDate: daysAgo(5), endDate: null, durationDays: 5, active: true },
                },
                worklogDays: [{ date: daysAgo(4), roleCode: 'DEV', timeSpentSeconds: 3600 }],
                statusIntervals: null,
              },
            ],
          },
        ],
      } as any)

      const { container } = renderTimelinePage()

      await waitFor(() => {
        expect(container.querySelector('.story-bar[aria-label="Story PROJ-1"]')).toBeTruthy()
      })

      const bar = container.querySelector('.story-bar[aria-label="Story PROJ-1"]') as HTMLElement
      const striped = bar.querySelector('.phase-bar-forecast') as HTMLElement
      const solid = Array.from(bar.querySelectorAll('div')).find(
        el => (el as HTMLElement).style.backgroundColor !== ''
      ) as HTMLElement
      expect(striped).toBeTruthy()
      expect(solid).toBeTruthy()
      expect(striped.parentElement).toBe(bar)
      expect(solid.parentElement).toBe(bar)
    })

    it('completed story bar spans first→last worklog day, not subtask phase dates', async () => {
      // LB-303 case: subtask phases run wider than the actual logged days — the bar
      // must start at the first worklog and end at the last one (no grey tail).
      const retro = JSON.parse(JSON.stringify(retroWithStatuses))
      const story = retro.epics[0].stories[0]
      story.startDate = daysAgo(20)
      story.endDate = daysAgo(2)
      story.phases = {
        SA: { roleCode: 'SA', startDate: daysAgo(20), endDate: daysAgo(15), durationDays: 5, active: false },
        DEV: { roleCode: 'DEV', startDate: daysAgo(15), endDate: daysAgo(2), durationDays: 13, active: false },
      }
      story.worklogDays = [
        { date: daysAgo(18), roleCode: 'SA', timeSpentSeconds: 3600 },
        { date: daysAgo(10), roleCode: 'DEV', timeSpentSeconds: 3600 },
      ]
      vi.mocked(forecastApi.getUnifiedPlanning).mockResolvedValue({ ...mockUnifiedPlan, epics: [] } as any)
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue(retro as any)

      const { container } = renderTimelinePage()
      await waitFor(() => {
        expect(container.querySelector('.story-bar[aria-label="Story PROJ-9"]')).toBeTruthy()
      })
      const bar = container.querySelector('.story-bar[aria-label="Story PROJ-9"]') as HTMLElement

      // Replicate the page's date range from the merged story dates
      const range = calculateDateRange(
        { epics: [{ isRoughEstimate: false, stories: [{ startDate: daysAgo(20), endDate: daysAgo(2), phases: {} }] }] } as any,
        null, DEFAULT_PAST_DAYS)
      const totalDays = daysBetween(range.start, range.end)
      const expLeft = (daysBetween(range.start, new Date(daysAgo(18))) / totalDays) * 100
      const expWidth = ((daysBetween(new Date(daysAgo(18)), new Date(daysAgo(10))) + 1) / totalDays) * 100

      expect(Math.abs(parseFloat(bar.style.left) - expLeft)).toBeLessThan(0.7)
      expect(Math.abs(parseFloat(bar.style.width) - expWidth)).toBeLessThan(0.7)
      // Both worklog days painted (per-role solid segments)
      const solid = Array.from(bar.querySelectorAll('div')).filter(
        el => (el as HTMLElement).style.backgroundColor !== ''
      )
      expect(solid.length).toBe(2)
    })

    it('in-progress story bar starts at first worklog and shows striped forecast remainder', async () => {
      const daysFromNow = (n: number) => {
        const d = new Date()
        d.setHours(0, 0, 0, 0)
        d.setDate(d.getDate() + n)
        return d.toISOString().slice(0, 10)
      }
      const plan = JSON.parse(JSON.stringify(mockUnifiedPlan))
      plan.epics[0].stories = [{
        storyKey: 'PROJ-1', summary: 'Hybrid Story', status: 'In Progress', issueType: 'Story',
        autoScore: null, startDate: daysAgo(5), endDate: daysFromNow(5), warnings: [],
        phases: {
          DEV: { assigneeAccountId: null, assigneeDisplayName: null, startDate: daysAgo(5), endDate: daysFromNow(5), hours: 40, noCapacity: false },
        },
      }]
      vi.mocked(forecastApi.getUnifiedPlanning).mockResolvedValue(plan as any)
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue({
        teamId: 1, calculatedAt: new Date().toISOString(),
        epics: [{
          epicKey: 'EPIC-1', summary: 'First Epic', status: 'In Progress',
          startDate: daysAgo(5), endDate: null, progressPercent: 0,
          stories: [{
            storyKey: 'PROJ-1', summary: 'Hybrid Story', status: 'In Progress', issueType: 'Story',
            completed: false, startDate: daysAgo(5), endDate: null, progressPercent: 10,
            autoScore: null, totalEstimateSeconds: null, totalLoggedSeconds: null, roleProgress: null,
            phases: { DEV: { roleCode: 'DEV', startDate: daysAgo(5), endDate: null, durationDays: 5, active: true } },
            worklogDays: [{ date: daysAgo(3), roleCode: 'DEV', timeSpentSeconds: 3600 }],
            statusIntervals: null,
          }],
        }],
      } as any)

      const { container } = renderTimelinePage()
      await waitFor(() => {
        expect(container.querySelector('.story-bar[aria-label="Story PROJ-1"]')).toBeTruthy()
      })
      const bar = container.querySelector('.story-bar[aria-label="Story PROJ-1"]') as HTMLElement

      // Striped autoplanner remainder for the unfinished part
      expect(bar.querySelector('.phase-bar-forecast')).toBeTruthy()
      // Exactly one solid segment — the single logged day
      const solid = Array.from(bar.querySelectorAll('div')).filter(
        el => (el as HTMLElement).style.backgroundColor !== ''
      )
      expect(solid.length).toBe(1)

      // Bar starts at the first worklog, not at the phase start
      const range = calculateDateRange(
        { epics: [{ isRoughEstimate: false, stories: [{ startDate: daysAgo(5), endDate: daysFromNow(5), phases: {} }] }] } as any,
        null, DEFAULT_PAST_DAYS)
      const totalDays = daysBetween(range.start, range.end)
      const expLeft = (daysBetween(range.start, new Date(daysAgo(3))) / totalDays) * 100
      expect(Math.abs(parseFloat(bar.style.left) - expLeft)).toBeLessThan(0.7)
    })

    it('in-progress story without worklogs renders only the striped remainder (no solid past)', async () => {
      // LB-602 case: nothing logged — the past must stay empty, only the autoplanner
      // remainder (from today) is drawn, striped.
      const daysFromNow = (n: number) => {
        const d = new Date()
        d.setHours(0, 0, 0, 0)
        d.setDate(d.getDate() + n)
        return d.toISOString().slice(0, 10)
      }
      const plan = JSON.parse(JSON.stringify(mockUnifiedPlan))
      plan.epics[0].stories = [{
        storyKey: 'PROJ-1', summary: 'Hybrid Story', status: 'In Progress', issueType: 'Story',
        autoScore: null, startDate: daysAgo(5), endDate: daysFromNow(5), warnings: [],
        phases: {
          DEV: { assigneeAccountId: null, assigneeDisplayName: null, startDate: daysAgo(5), endDate: daysFromNow(5), hours: 40, noCapacity: false },
        },
      }]
      vi.mocked(forecastApi.getUnifiedPlanning).mockResolvedValue(plan as any)
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue({
        teamId: 1, calculatedAt: new Date().toISOString(),
        epics: [{
          epicKey: 'EPIC-1', summary: 'First Epic', status: 'In Progress',
          startDate: daysAgo(5), endDate: null, progressPercent: 0,
          stories: [{
            storyKey: 'PROJ-1', summary: 'Hybrid Story', status: 'In Progress', issueType: 'Story',
            completed: false, startDate: daysAgo(5), endDate: null, progressPercent: 0,
            autoScore: null, totalEstimateSeconds: null, totalLoggedSeconds: null, roleProgress: null,
            phases: { DEV: { roleCode: 'DEV', startDate: daysAgo(5), endDate: null, durationDays: 5, active: true } },
            worklogDays: null,
            statusIntervals: null,
          }],
        }],
      } as any)

      const { container } = renderTimelinePage()
      await waitFor(() => {
        expect(container.querySelector('.story-bar[aria-label="Story PROJ-1"]')).toBeTruthy()
      })
      const bar = container.querySelector('.story-bar[aria-label="Story PROJ-1"]') as HTMLElement

      const solid = Array.from(bar.querySelectorAll('div')).filter(
        el => (el as HTMLElement).style.backgroundColor !== ''
      )
      expect(solid.length).toBe(0)
      expect(bar.querySelector('.phase-bar-forecast')).toBeTruthy()

      // Bar starts today (remainder only)
      const range = calculateDateRange(
        { epics: [{ isRoughEstimate: false, stories: [{ startDate: daysAgo(5), endDate: daysFromNow(5), phases: {} }] }] } as any,
        null, DEFAULT_PAST_DAYS)
      const totalDays = daysBetween(range.start, range.end)
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const expLeft = (daysBetween(range.start, today) / totalDays) * 100
      expect(Math.abs(parseFloat(bar.style.left) - expLeft)).toBeLessThan(0.7)
    })

    it('completed story without any worklogs renders no bar in worklog mode', async () => {
      const retro = JSON.parse(JSON.stringify(retroWithStatuses))
      retro.epics[0].stories[0].worklogDays = null
      vi.mocked(forecastApi.getUnifiedPlanning).mockResolvedValue({ ...mockUnifiedPlan, epics: [] } as any)
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue(retro as any)

      const { container } = renderTimelinePage()
      await waitFor(() => {
        expect(screen.getByText('Retro Epic')).toBeInTheDocument()
      })
      expect(container.querySelector('.story-bar[aria-label="Story PROJ-9"]')).toBeNull()
    })

    it('falls back to StatusBadge palette for status without configured color', async () => {
      vi.mocked(boardApi.getStatusStyles).mockResolvedValue({} as any)
      const { container } = renderTimelinePage()

      await waitFor(() => {
        expect(screen.getByText('Retro Epic')).toBeInTheDocument()
      })

      fireEvent.click(screen.getAllByText('Logged time')[0])
      fireEvent.click(screen.getByText('Story statuses'))

      await waitFor(() => {
        const bgColors = Array.from(container.querySelectorAll('.story-bar div')).map(
          el => (el as HTMLElement).style.backgroundColor
        )
        // 'To Do' → .status-badge.to-do bg #f4f5f7 → rgb(244, 245, 247)
        expect(bgColors).toContain('rgb(244, 245, 247)')
        // 'In Development' → default .status-badge bg #dfe1e6 → rgb(223, 225, 230)
        expect(bgColors).toContain('rgb(223, 225, 230)')
      })
    })

    it('status mode hides NEW and DONE intervals and clamps the bar to in-progress ones', async () => {
      const retro = JSON.parse(JSON.stringify(retroWithStatuses))
      retro.epics[0].stories[0].statusIntervals = [
        { status: 'To Do',          startDate: daysAgo(12), endDate: daysAgo(9) },  // NEW
        { status: 'In Development', startDate: daysAgo(9),  endDate: daysAgo(4) },  // IN_PROGRESS
        { status: 'Closed',         startDate: daysAgo(4),  endDate: daysAgo(2) },  // DONE
      ]
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue(retro as any)
      vi.mocked(boardApi.getStatusStyles).mockResolvedValue({
        'To Do':          { color: '#DFE1E6', statusCategory: 'NEW' },
        'In Development': { color: '#abe7d3', statusCategory: 'IN_PROGRESS' },
        'Closed':         { color: '#E3FCEF', statusCategory: 'DONE' },
      } as any)
      const { container } = renderTimelinePage()
      await waitFor(() => expect(screen.getByText('Retro Epic')).toBeInTheDocument())
      fireEvent.click(screen.getAllByText('Logged time')[0])
      fireEvent.click(screen.getByText('Story statuses'))
      await waitFor(() => {
        const bar = container.querySelector('.story-bar[aria-label="Story PROJ-9"]') as HTMLElement
        const segs = Array.from(bar.querySelectorAll('div')).filter(d => (d as HTMLElement).style.backgroundColor !== '')
        // Only one segment — In Development; NEW and DONE are not rendered
        expect(segs.length).toBe(1)
        expect((segs[0] as HTMLElement).style.backgroundColor).toBe('rgb(171, 231, 211)')
        // Bar clamped to the in-progress interval: width = 6 days, from 9 to 4 days ago
        const range = calculateDateRange(
          { epics: [{ isRoughEstimate: false, stories: [{ startDate: daysAgo(12), endDate: daysAgo(2), phases: {} }] }] } as any,
          null, DEFAULT_PAST_DAYS)
        const totalDays = daysBetween(range.start, range.end)
        const expLeft = (daysBetween(range.start, new Date(daysAgo(9))) / totalDays) * 100
        expect(Math.abs(parseFloat(bar.style.left) - expLeft)).toBeLessThan(0.7)
        // Bar narrowed away from the DONE tail: width spans only the visible
        // In Development interval (9→4 days ago), not up to the Closed interval end.
        const expWidth = ((daysBetween(new Date(daysAgo(9)), new Date(daysAgo(4))) + 1) / totalDays) * 100
        expect(Math.abs(parseFloat(bar.style.width) - expWidth)).toBeLessThan(0.7)
      })
    })

    it('extends the bar to today for an active story whose last visible interval has no DONE after it', async () => {
      const retro = JSON.parse(JSON.stringify(retroWithStatuses))
      retro.epics[0].stories[0].statusIntervals = [
        { status: 'To Do',          startDate: daysAgo(10), endDate: daysAgo(6) }, // NEW
        { status: 'In Development', startDate: daysAgo(6),  endDate: daysAgo(-2) }, // IN_PROGRESS, still ongoing (endDate beyond today — must clamp)
      ]
      vi.mocked(forecastApi.getRetrospective).mockResolvedValue(retro as any)
      vi.mocked(boardApi.getStatusStyles).mockResolvedValue({
        'To Do':          { color: '#DFE1E6', statusCategory: 'NEW' },
        'In Development': { color: '#abe7d3', statusCategory: 'IN_PROGRESS' },
      } as any)
      const { container } = renderTimelinePage()
      await waitFor(() => expect(screen.getByText('Retro Epic')).toBeInTheDocument())
      fireEvent.click(screen.getAllByText('Logged time')[0])
      fireEvent.click(screen.getByText('Story statuses'))
      await waitFor(() => {
        const bar = container.querySelector('.story-bar[aria-label="Story PROJ-9"]') as HTMLElement
        const segs = Array.from(bar.querySelectorAll('div')).filter(d => (d as HTMLElement).style.backgroundColor !== '')
        // Only In Development renders — the NEW interval is hidden
        expect(segs.length).toBe(1)
        expect((segs[0] as HTMLElement).style.backgroundColor).toBe('rgb(171, 231, 211)')

        // Bar reaches today (the subtask-derived endDate of the base fixture is 2 days
        // ago — the active status interval must still stretch the bar out to today).
        const range = calculateDateRange(
          { epics: [{ isRoughEstimate: false, stories: [{ startDate: daysAgo(10), endDate: daysAgo(2), phases: {} }] }] } as any,
          null, DEFAULT_PAST_DAYS)
        const totalDays = daysBetween(range.start, range.end)
        const today = new Date()
        today.setHours(0, 0, 0, 0)
        const expLeft = (daysBetween(range.start, new Date(daysAgo(6))) / totalDays) * 100
        const expRight = ((daysBetween(range.start, today) + 1) / totalDays) * 100
        expect(Math.abs(parseFloat(bar.style.left) - expLeft)).toBeLessThan(0.7)
        expect(Math.abs(parseFloat(bar.style.left) + parseFloat(bar.style.width) - expRight)).toBeLessThan(0.7)
      })
    })

    describe('Status path tooltip (F92)', () => {
      it('lazily loads and shows the Status path section when hovering a bar in Story statuses mode', async () => {
        vi.mocked(statusHistoryApi.getStatusHistory).mockResolvedValue({
          issueKey: 'PROJ-9',
          currentStatus: 'In Development',
          totalSeconds: 9 * 86400,
          segments: [
            { status: 'To Do', durationSeconds: 3 * 86400, current: false },
            { status: 'In Development', durationSeconds: 4 * 86400, current: true },
          ],
        })

        const { container } = renderTimelinePage()
        await waitFor(() => expect(screen.getByText('Retro Epic')).toBeInTheDocument())

        fireEvent.click(screen.getAllByText('Logged time')[0])
        fireEvent.click(screen.getByText('Story statuses'))

        const bar = await waitFor(() => {
          const el = container.querySelector('.story-bar[aria-label="Story PROJ-9"]') as HTMLElement
          expect(el).toBeTruthy()
          return el
        })

        fireEvent.mouseEnter(bar)

        await waitFor(() =>
          expect(statusHistoryApi.getStatusHistory).toHaveBeenCalledWith('PROJ-9', expect.anything())
        )
        expect(await screen.findByText('Status path')).toBeInTheDocument()
        expect(screen.getByText('4d')).toBeInTheDocument()
      })

      it('retries the fetch on re-hover after a quick hover cancelled the debounce', async () => {
        vi.mocked(statusHistoryApi.getStatusHistory).mockResolvedValue({
          issueKey: 'PROJ-9',
          currentStatus: 'In Development',
          totalSeconds: 9 * 86400,
          segments: [
            { status: 'To Do', durationSeconds: 3 * 86400, current: false },
            { status: 'In Development', durationSeconds: 4 * 86400, current: true },
          ],
        })

        const { container } = renderTimelinePage()
        await waitFor(() => expect(screen.getByText('Retro Epic')).toBeInTheDocument())

        fireEvent.click(screen.getAllByText('Logged time')[0])
        fireEvent.click(screen.getByText('Story statuses'))

        const bar = await waitFor(() => {
          const el = container.querySelector('.story-bar[aria-label="Story PROJ-9"]') as HTMLElement
          expect(el).toBeTruthy()
          return el
        })

        // Quick hover then leave BEFORE the 300ms debounce fires — nothing fetched/cached.
        fireEvent.mouseEnter(bar)
        fireEvent.mouseLeave(bar)
        expect(statusHistoryApi.getStatusHistory).not.toHaveBeenCalled()

        // Re-hover the SAME story: the debounce must be re-armed and the fetch retried
        // (the pre-fix "already fetched" gate wrongly short-circuited here forever).
        fireEvent.mouseEnter(bar)
        await waitFor(() =>
          expect(statusHistoryApi.getStatusHistory).toHaveBeenCalledWith('PROJ-9', expect.anything())
        )
        expect(await screen.findByText('Status path')).toBeInTheDocument()
      })

      it('keeps the old tooltip body (no Status path) when hovering in Logged time mode', async () => {
        const { container } = renderTimelinePage()
        await waitFor(() => expect(screen.getByText('Retro Epic')).toBeInTheDocument())

        const bar = container.querySelector('.story-bar[aria-label="Story PROJ-9"]') as HTMLElement
        fireEvent.mouseEnter(bar)

        // Sanity: the tooltip did open (existing worklog-mode body still shows the summary).
        await waitFor(() => expect(screen.getByText('Retro Story')).toBeInTheDocument())
        expect(screen.queryByText('Status path')).not.toBeInTheDocument()
        expect(statusHistoryApi.getStatusHistory).not.toHaveBeenCalled()
      })
    })
  })
})

describe('calculateDateRange past clamping', () => {
  const daysAgo = (n: number) => {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    d.setDate(d.getDate() - n)
    return d.toISOString().slice(0, 10)
  }
  const daysAhead = (n: number) => {
    const d = new Date()
    d.setHours(0, 0, 0, 0)
    d.setDate(d.getDate() + n)
    return d.toISOString().slice(0, 10)
  }

  // Minimal unified plan: one epic with a story that started 90 days ago and ends 5 days out.
  const plan = {
    epics: [
      {
        isRoughEstimate: false,
        stories: [{ startDate: daysAgo(90), endDate: daysAhead(5), phases: {} }],
      },
    ],
  } as unknown as Parameters<typeof calculateDateRange>[0]

  it('renders the full history when clamp is disabled (null)', () => {
    const range = calculateDateRange(plan, null, null)
    // Start should be near the 90-days-ago story start (allowing for week alignment/padding)
    expect(range.start.getTime()).toBeLessThan(Date.now() - 80 * 24 * 3600 * 1000)
  })

  it('clamps the start to roughly DEFAULT_PAST_DAYS ago', () => {
    const full = calculateDateRange(plan, null, null)
    const clamped = calculateDateRange(plan, null, DEFAULT_PAST_DAYS)

    // Clamp moved the start forward (hides the 90-days-ago work)
    expect(clamped.start.getTime()).toBeGreaterThan(full.start.getTime())

    // Clamped start sits within ~2 weeks of (today - DEFAULT_PAST_DAYS), accounting for
    // the -3 day pad and week-boundary alignment inside calculateDateRange.
    const target = Date.now() - DEFAULT_PAST_DAYS * 24 * 3600 * 1000
    const slackMs = 14 * 24 * 3600 * 1000
    expect(clamped.start.getTime()).toBeGreaterThan(target - slackMs)
    expect(clamped.start.getTime()).toBeLessThan(target + slackMs)
  })
})
