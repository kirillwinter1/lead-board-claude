import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { MyWorkPage } from './MyWorkPage'
import { HomeRedirect } from '../App'
import { myWorkApi, type MyWorkResponse } from '../api/myWork'
import { useAuth } from '../contexts/AuthContext'

vi.mock('../api/myWork', () => ({ myWorkApi: { getMyWork: vi.fn() } }))
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
        estimateH: 8, spentH: 4, jiraUrl: 'https://jira.example.com/LB-1',
      }],
      upcomingAssigned: [{
        key: 'LB-2', summary: 'Upcoming task', issueType: 'Bug', status: 'To Do',
        parentKey: null, parentSummary: null, epicKey: null, epicSummary: null,
        teamId: 1, teamName: 'Team Alpha', teamColor: '#FF0000',
        estimateH: 4, spentH: null, jiraUrl: 'https://jira.example.com/LB-2',
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
    expect(screen.getByText('LB-3').closest('a')).toHaveAttribute('href', 'https://jira.example.com/LB-3')

    // Team badges render for both the header and task rows.
    expect(screen.getAllByText('Team Alpha').length).toBeGreaterThan(1)
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
})
