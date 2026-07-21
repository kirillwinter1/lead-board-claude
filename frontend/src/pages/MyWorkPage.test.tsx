import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { MyWorkPage, defaultFrom, defaultTo } from './MyWorkPage'
import { HomeRedirect } from '../App'
import { myWorkApi, type MyWorkResponse } from '../api/myWork'
import { useAuth } from '../contexts/AuthContext'

vi.mock('../api/myWork', () => ({ myWorkApi: { getMyWork: vi.fn(), logTime: vi.fn(), worklogCalendar: vi.fn() } }))
vi.mock('../api/board', () => ({ getStatusStyles: vi.fn().mockResolvedValue({}) }))
vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getIssueTypeIconUrl: () => null,
    getIssueTypeCategory: () => null,
    getRoleColor: () => '#803FA5',
    getRoleDisplayName: (code: string) => code,
  }),
}))
vi.mock('../contexts/AuthContext', () => ({ useAuth: vi.fn() }))

const oneTeamMember = {
  displayName: 'Jane Smith',
  avatarUrl: null,
  role: 'DEV',
  grade: 'MIDDLE',
  hoursPerDay: 8,
  teams: [{ teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000' }],
}

const twoTeamMember = {
  ...oneTeamMember,
  teams: [
    { teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000' },
    { teamId: 2, teamName: 'Team Beta', teamColor: '#00FF00' },
  ],
}

const emptyResponse: MyWorkResponse = {
  hasMembership: false,
  member: null,
  upcomingAbsences: [],
  activeTasks: [],
  upcomingAssigned: [],
  teamQueue: [],
  worklogCalendar: [],
  analytics: null,
}

function renderMyWorkPage() {
  return render(
    <MemoryRouter initialEntries={['/my-work']}>
      <Routes>
        <Route path="/my-work" element={<MyWorkPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('MyWorkPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders empty state when no membership', async () => {
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(emptyResponse)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByText(/not a member of any team/i)).toBeInTheDocument()
    })

    // Empty state renders nothing else — no task sections.
    expect(screen.queryByRole('heading', { name: 'In Progress' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Up Next' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Team Queue' })).toBeNull()
  })

  it('renders task sections with team badges', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [{
        key: 'LB-1', summary: 'Active task', issueType: 'Story', status: 'In Progress',
        parentKey: null, parentSummary: null, epicKey: 'LB-100', epicSummary: 'Epic A',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 8, spentH: 4, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-1',
      }],
      upcomingAssigned: [{
        key: 'LB-2', summary: 'Upcoming task', issueType: 'Bug', status: 'To Do',
        parentKey: null, parentSummary: null, epicKey: null, epicSummary: null,
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 4, spentH: null, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-2',
      }],
      teamQueue: [{
        key: 'LB-3', summary: 'Queue story', issueType: 'Story', status: 'Backlog',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        epicKey: null, epicSummary: null,
        myPhaseSubtasks: 2, myPhaseEstimateH: 6, jiraUrl: 'https://jira.example.com/LB-3',
      }],
      worklogCalendar: [],
      analytics: null,
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'In Progress' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Up Next' })).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: 'Team Queue' })).toBeInTheDocument()
    })

    expect(screen.getByText('LB-1').closest('a')).toHaveAttribute('href', 'https://jira.example.com/LB-1')
    expect(screen.getByText('LB-2').closest('a')).toHaveAttribute('href', 'https://jira.example.com/LB-2')

    const teamQueueToggle = screen.getByRole('button', { name: /Team Queue/i })
    expect(teamQueueToggle).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(teamQueueToggle)

    expect(screen.getByText('LB-3').closest('a')).toHaveAttribute('href', 'https://jira.example.com/LB-3')

    // Team badges render for both the header and task rows.
    expect(screen.getAllByText('Team Alpha').length).toBeGreaterThan(1)
  })

  it('collapses and expands a task section independently of the others', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [{
        key: 'LB-1', summary: 'Active task', issueType: 'Story', status: 'In Progress',
        parentKey: null, parentSummary: null, epicKey: 'LB-100', epicSummary: 'Epic A',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 8, spentH: 4, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-1',
      }],
      upcomingAssigned: [{
        key: 'LB-2', summary: 'Upcoming task', issueType: 'Bug', status: 'To Do',
        parentKey: null, parentSummary: null, epicKey: null, epicSummary: null,
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 4, spentH: null, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-2',
      }],
      teamQueue: [{
        key: 'LB-3', summary: 'Queue story', issueType: 'Story', status: 'Backlog',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        epicKey: null, epicSummary: null,
        myPhaseSubtasks: 2, myPhaseEstimateH: 6, jiraUrl: 'https://jira.example.com/LB-3',
      }],
      worklogCalendar: [],
      analytics: null,
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByText('LB-1')).toBeInTheDocument()
    })

    const inProgressToggle = screen.getByRole('button', { name: /In Progress/i })
    expect(inProgressToggle).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(inProgressToggle)

    expect(inProgressToggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('LB-1')).toBeNull()
    // Up Next stays expanded; Team Queue exposes a compact preview while the
    // full table remains collapsed.
    expect(screen.getByText('LB-2')).toBeInTheDocument()
    expect(screen.getByText('LB-3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Team Queue/i })).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(inProgressToggle)

    expect(inProgressToggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('LB-1')).toBeInTheDocument()
  })

  it('previews three Team Queue rows and reveals the rest on demand', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [],
      upcomingAssigned: [],
      teamQueue: Array.from({ length: 4 }, (_, index) => ({
        key: `LB-Q${index + 1}`,
        summary: `Queue story ${index + 1}`,
        issueType: 'Story',
        status: 'Backlog',
        teamId: 1,
        teamName: 'Team Alpha',
        teamColor: '#FF0000',
        epicKey: null,
        epicSummary: null,
        myPhaseSubtasks: 1,
        myPhaseEstimateH: 4,
        jiraUrl: `https://jira.example.com/LB-Q${index + 1}`,
      })),
      worklogCalendar: [],
      analytics: null,
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByText('LB-Q1')).toBeInTheDocument()
    })

    expect(screen.getByText('LB-Q3')).toBeInTheDocument()
    expect(screen.queryByText('LB-Q4')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'View all 4 tasks' }))

    expect(screen.getByText('LB-Q4')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Team Queue/i })).toHaveAttribute('aria-expanded', 'true')
  })

  it('team filter chips refetch with teamId', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: twoTeamMember,
      upcomingAbsences: [],
      activeTasks: [],
      upcomingAssigned: [],
      teamQueue: [],
      worklogCalendar: [],
      analytics: null,
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Team Alpha' })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: 'Team Alpha' }))

    await waitFor(() => {
      expect(myWorkApi.getMyWork).toHaveBeenLastCalledWith(expect.any(String), expect.any(String), 1)
    })
  })

  it('renders performance cards and breakdowns', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [],
      upcomingAssigned: [],
      teamQueue: [],
      worklogCalendar: [],
      analytics: {
        summary: {
          completedCount: 5,
          avgDsr: 0.87,
          avgCycleTimeDays: 3,
          utilization: 80,
          totalSpentH: 40,
          totalEstimateH: 44,
        },
        weeklyTrend: [
          { week: 'W1', weekStart: '2026-06-01', dsr: 0.9, tasksCompleted: 2, hoursLogged: 16 },
          { week: 'W2', weekStart: '2026-06-08', dsr: 0.95, tasksCompleted: 3, hoursLogged: 24 },
        ],
        completedTasks: [{
          key: 'LB-10', summary: 'Completed task', epicKey: 'LB-200', epicSummary: 'Epic X',
          teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
          estimateH: 8, spentH: 7, remainingH: 0, dsr: 0.88, doneDate: '2026-06-10', jiraUrl: 'https://jira.example.com/LB-10',
        }],
        dsrByParentType: [
          { key: 'Story', label: 'Story', taskCount: 4, estimateH: 32, spentH: 30, dsr: 0.94 },
        ],
        dsrByEpic: [
          { key: 'LB-200', label: 'Epic X', taskCount: 3, estimateH: 24, spentH: 22, dsr: 0.92 },
        ],
      },
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'View performance details' })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: 'View performance details' }))

    expect(screen.getByText('Closed tasks')).toBeInTheDocument()
    expect(screen.getAllByText('0.87').length).toBeGreaterThan(0)
    expect(screen.getByText('DSR by Task Type')).toBeInTheDocument()
    expect(screen.getByText('DSR by Epic')).toBeInTheDocument()
    expect(screen.getByText('Epic X')).toBeInTheDocument()

    expect(screen.getByText('LB-10').closest('a')).toHaveAttribute('href', 'https://jira.example.com/LB-10')
  })

  it('period change refetches analytics', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [],
      upcomingAssigned: [],
      teamQueue: [],
      worklogCalendar: [],
      analytics: {
        summary: {
          completedCount: 1,
          avgDsr: 1.0,
          avgCycleTimeDays: 2,
          utilization: 50,
          totalSpentH: 8,
          totalEstimateH: 8,
        },
        weeklyTrend: [
          { week: 'W1', weekStart: '2026-06-01', dsr: 1.0, tasksCompleted: 1, hoursLogged: 8 },
          { week: 'W2', weekStart: '2026-06-08', dsr: null, tasksCompleted: 0, hoursLogged: 0 },
        ],
        completedTasks: [],
        dsrByParentType: [],
        dsrByEpic: [],
      },
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'View performance details' })).toBeInTheDocument()
    })
    fireEvent.click(screen.getByRole('button', { name: 'View performance details' }))

    await waitFor(() => {
      expect(screen.getByLabelText('From')).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-01-01' } })

    await waitFor(() => {
      const lastCall = vi.mocked(myWorkApi.getMyWork).mock.calls.at(-1)
      expect(lastCall?.[0]).toBe('2026-01-01')
    })
  })

  it('log buttons on In Progress and Up Next rows open modal; team queue rows have none', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [{
        key: 'LB-1', summary: 'Active task', issueType: 'Story', status: 'In Progress',
        parentKey: null, parentSummary: null, epicKey: 'LB-100', epicSummary: 'Epic A',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 8, spentH: 4, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-1',
      }],
      upcomingAssigned: [{
        key: 'LB-2', summary: 'Upcoming task', issueType: 'Bug', status: 'To Do',
        parentKey: null, parentSummary: null, epicKey: null, epicSummary: null,
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 4, spentH: null, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-2',
      }],
      teamQueue: [{
        key: 'LB-3', summary: 'Queue story', issueType: 'Story', status: 'Backlog',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        epicKey: null, epicSummary: null,
        myPhaseSubtasks: 2, myPhaseEstimateH: 6, jiraUrl: 'https://jira.example.com/LB-3',
      }],
      worklogCalendar: [],
      analytics: null,
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByText('LB-1')).toBeInTheDocument()
    })

    // In Progress (LB-1) + Up Next (LB-2) each get a button; Team Queue (LB-3) gets none.
    const logButtons = screen.getAllByTitle('Log time')
    expect(logButtons).toHaveLength(2)

    // First button belongs to the In Progress row.
    fireEvent.click(logButtons[0])
    await waitFor(() => {
      expect(screen.getByText('Log time — LB-1')).toBeInTheDocument()
    })

    // Second button belongs to the Up Next row.
    fireEvent.click(logButtons[1])
    await waitFor(() => {
      expect(screen.getByText('Log time — LB-2')).toBeInTheDocument()
    })
  })

  it('log button on a Completed row opens modal with that task', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [],
      upcomingAssigned: [],
      teamQueue: [],
      worklogCalendar: [],
      analytics: {
        summary: {
          completedCount: 1,
          avgDsr: 0.88,
          avgCycleTimeDays: 3,
          utilization: 80,
          totalSpentH: 7,
          totalEstimateH: 8,
        },
        weeklyTrend: [],
        completedTasks: [{
          key: 'LB-10', summary: 'Completed task', epicKey: 'LB-200', epicSummary: 'Epic X',
          teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
          estimateH: 8, spentH: 7, remainingH: 0, dsr: 0.88, doneDate: '2026-06-10', jiraUrl: 'https://jira.example.com/LB-10',
        }],
        dsrByParentType: [],
        dsrByEpic: [],
      },
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'View performance details' })).toBeInTheDocument()
    })
    fireEvent.click(screen.getByRole('button', { name: 'View performance details' }))

    await waitFor(() => {
      expect(screen.getByText('Completed task')).toBeInTheDocument()
    })

    // No active/upcoming rows, so the only log button on the page is the Completed row's.
    const logButtons = screen.getAllByTitle('Log time')
    expect(logButtons).toHaveLength(1)

    fireEvent.click(logButtons[0])

    await waitFor(() => {
      expect(screen.getByText('Log time — LB-10')).toBeInTheDocument()
    })
  })

  it('successful log refetches my work', async () => {
    const response: MyWorkResponse = {
      hasMembership: true,
      member: oneTeamMember,
      upcomingAbsences: [],
      activeTasks: [{
        key: 'LB-1', summary: 'Active task', issueType: 'Story', status: 'In Progress',
        parentKey: null, parentSummary: null, epicKey: 'LB-100', epicSummary: 'Epic A',
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 8, spentH: 4, remainingH: 4, jiraUrl: 'https://jira.example.com/LB-1',
      }],
      upcomingAssigned: [],
      teamQueue: [],
      worklogCalendar: [],
      analytics: null,
    }
    vi.mocked(myWorkApi.getMyWork).mockResolvedValue(response)
    vi.mocked(myWorkApi.logTime).mockResolvedValue({ worklogId: 'wl-1' })

    renderMyWorkPage()

    await waitFor(() => {
      expect(screen.getByText('LB-1')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTitle('Log time'))

    await waitFor(() => {
      expect(screen.getByLabelText('Time spent')).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText('Time spent'), { target: { value: '2h' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() => {
      expect(myWorkApi.getMyWork).toHaveBeenCalledTimes(2)
    })
  })

  it('HomeRedirect sends MEMBER to /my-work', async () => {
    vi.mocked(useAuth).mockReturnValue({
      loading: false,
      isMember: () => true,
    } as unknown as ReturnType<typeof useAuth>)

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/my-work" element={<div>MYWORK</div>} />
        </Routes>
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('MYWORK')).toBeInTheDocument()
    })
  })

  it('MEMBER visiting /board sees the board (no redirect)', async () => {
    // F88: MEMBER lands on /my-work from "/" (HomeRedirect), but the Board tab now
    // points at the dedicated /board route, which renders BoardPage directly with no
    // membership-based redirect — so a MEMBER can still open the board on demand.
    vi.mocked(useAuth).mockReturnValue({
      loading: false,
      isMember: () => true,
    } as unknown as ReturnType<typeof useAuth>)

    render(
      <MemoryRouter initialEntries={['/board']}>
        <Routes>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/board" element={<div>BOARD</div>} />
          <Route path="/my-work" element={<div>MYWORK</div>} />
        </Routes>
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('BOARD')).toBeInTheDocument()
    })
    expect(screen.queryByText('MYWORK')).toBeNull()
  })
})

describe('default analytics period', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  // Between 00:00 and 03:00 MSK, `new Date().toISOString()` still points at the
  // previous UTC day, so the default range dropped "today" from analytics and
  // Completed. Defaults must follow the LOCAL calendar day, same as
  // LogTimeModal.todayLocal.
  it('defaultTo returns the local calendar day, not the UTC day', () => {
    // 2026-07-11 00:30 in a UTC+3 zone == 2026-07-10 21:30 UTC.
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-10T21:30:00.000Z'))
    const now = new Date()
    const localToday = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    expect(defaultTo()).toBe(localToday)
    if (now.getUTCDate() !== now.getDate()) {
      expect(defaultTo()).not.toBe(new Date().toISOString().slice(0, 10))
    }
  })

  it('defaultFrom is 90 local days before defaultTo', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-10T21:30:00.000Z'))
    const to = new Date(defaultTo() + 'T00:00:00')
    const from = new Date(defaultFrom() + 'T00:00:00')
    const diffDays = Math.round((to.getTime() - from.getTime()) / 86_400_000)
    expect(diffDays).toBe(90)
  })
})
