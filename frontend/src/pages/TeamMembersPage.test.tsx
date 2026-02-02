import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { TeamMembersPage } from './TeamMembersPage'
import { teamsApi } from '../api/teams'

vi.mock('../api/teams', () => ({
  teamsApi: {
    getById: vi.fn(),
    getMembers: vi.fn(),
    getPlanningConfig: vi.fn(),
    addMember: vi.fn(),
    updateMember: vi.fn(),
    deactivateMember: vi.fn(),
    updatePlanningConfig: vi.fn(),
  },
}))

const mockTeam = {
  id: 1,
  name: 'Team Alpha',
  jiraTeamValue: 'alpha',
  active: true,
  memberCount: 3,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
}

const mockMembers = [
  { id: 1, teamId: 1, jiraAccountId: 'acc-1', displayName: 'John Doe', role: 'DEV', grade: 'SENIOR', hoursPerDay: 8, active: true },
  { id: 2, teamId: 1, jiraAccountId: 'acc-2', displayName: 'Jane Smith', role: 'QA', grade: 'MIDDLE', hoursPerDay: 6, active: true },
  { id: 3, teamId: 1, jiraAccountId: 'acc-3', displayName: null, role: 'SA', grade: 'JUNIOR', hoursPerDay: 4, active: true },
]

const mockPlanningConfig = {
  gradeCoefficients: { senior: 0.8, middle: 1.0, junior: 1.5 },
  riskBuffer: 0.2,
  wipLimits: { team: 6, sa: 2, dev: 3, qa: 2 },
  storyDuration: { sa: 2, dev: 2, qa: 2 },
}

const renderTeamMembersPage = (teamId = '1') => {
  return render(
    <MemoryRouter initialEntries={[`/teams/${teamId}`]}>
      <Routes>
        <Route path="/teams/:teamId" element={<TeamMembersPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('TeamMembersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(teamsApi.getById).mockResolvedValue(mockTeam)
    vi.mocked(teamsApi.getMembers).mockResolvedValue(mockMembers as any)
    vi.mocked(teamsApi.getPlanningConfig).mockResolvedValue(mockPlanningConfig)
  })

  describe('Rendering', () => {
    it('should show loading state initially', () => {
      renderTeamMembersPage()

      expect(screen.getByText('Loading...')).toBeInTheDocument()
    })

    it('should render team name', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })
    })

    it('should render jira team value badge', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('alpha')).toBeInTheDocument()
      })
    })

    it('should render back link', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('← Back to Teams')).toBeInTheDocument()
      })
    })

    it('should render Add Member button', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('+ Add Member')).toBeInTheDocument()
      })
    })
  })

  describe('Members table', () => {
    it('should render table headers', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('NAME')).toBeInTheDocument()
        expect(screen.getByText('JIRA ACCOUNT ID')).toBeInTheDocument()
        expect(screen.getByText('ROLE')).toBeInTheDocument()
        expect(screen.getByText('GRADE')).toBeInTheDocument()
        expect(screen.getByText('HOURS PER DAY')).toBeInTheDocument()
        expect(screen.getByText('ACTIONS')).toBeInTheDocument()
      })
    })

    it('should render member names', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('John Doe')).toBeInTheDocument()
        expect(screen.getByText('Jane Smith')).toBeInTheDocument()
      })
    })

    it('should show "Not set" for null display name', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('Not set')).toBeInTheDocument()
      })
    })

    it('should render role badges', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('DEV')).toBeInTheDocument()
        expect(screen.getByText('QA')).toBeInTheDocument()
        expect(screen.getByText('SA')).toBeInTheDocument()
      })
    })

    it('should render grade badges', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('SENIOR')).toBeInTheDocument()
        expect(screen.getByText('MIDDLE')).toBeInTheDocument()
        expect(screen.getByText('JUNIOR')).toBeInTheDocument()
      })
    })

    it('should render hours per day', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('8h')).toBeInTheDocument()
        expect(screen.getByText('6h')).toBeInTheDocument()
        expect(screen.getByText('4h')).toBeInTheDocument()
      })
    })

    it('should render Edit buttons for each member', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        const editButtons = screen.getAllByText('Edit')
        expect(editButtons.length).toBe(3)
      })
    })

    it('should render Deactivate buttons for each member', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        const deactivateButtons = screen.getAllByText('Deactivate')
        expect(deactivateButtons.length).toBe(3)
      })
    })
  })

  describe('Add Member modal', () => {
    it('should open modal on Add Member click', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('+ Add Member')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('+ Add Member'))

      expect(screen.getByText('Add Member')).toBeInTheDocument()
    })

    it('should render form fields', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Add Member'))
      })

      expect(screen.getByLabelText('Jira Account ID *')).toBeInTheDocument()
      expect(screen.getByLabelText('Display Name')).toBeInTheDocument()
      expect(screen.getByLabelText('Role')).toBeInTheDocument()
      expect(screen.getByLabelText('Grade')).toBeInTheDocument()
      expect(screen.getByLabelText('Hours/Day')).toBeInTheDocument()
    })

    it('should close modal on Cancel', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Add Member'))
      })

      fireEvent.click(screen.getByText('Cancel'))

      expect(screen.queryByLabelText('Jira Account ID *')).not.toBeInTheDocument()
    })

    it('should call addMember API on submit', async () => {
      vi.mocked(teamsApi.addMember).mockResolvedValue({} as any)

      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Add Member'))
      })

      fireEvent.change(screen.getByLabelText('Jira Account ID *'), { target: { value: 'new-acc-123' } })
      fireEvent.change(screen.getByLabelText('Display Name'), { target: { value: 'New Member' } })
      fireEvent.click(screen.getByText('Add'))

      await waitFor(() => {
        expect(teamsApi.addMember).toHaveBeenCalledWith(1, expect.objectContaining({
          jiraAccountId: 'new-acc-123',
          displayName: 'New Member',
        }))
      })
    })
  })

  describe('Edit Member modal', () => {
    it('should open edit modal with member data', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        const editButtons = screen.getAllByText('Edit')
        fireEvent.click(editButtons[0])
      })

      expect(screen.getByText('Edit Member')).toBeInTheDocument()
      expect(screen.getByDisplayValue('John Doe')).toBeInTheDocument()
    })

    it('should disable Jira Account ID in edit mode', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        const editButtons = screen.getAllByText('Edit')
        fireEvent.click(editButtons[0])
      })

      const accountIdInput = screen.getByLabelText('Jira Account ID *')
      expect(accountIdInput).toBeDisabled()
    })

    it('should call updateMember API on save', async () => {
      vi.mocked(teamsApi.updateMember).mockResolvedValue({} as any)

      renderTeamMembersPage()

      await waitFor(() => {
        const editButtons = screen.getAllByText('Edit')
        fireEvent.click(editButtons[0])
      })

      fireEvent.change(screen.getByLabelText('Display Name'), { target: { value: 'Updated Name' } })
      fireEvent.click(screen.getByText('Save'))

      await waitFor(() => {
        expect(teamsApi.updateMember).toHaveBeenCalledWith(1, 1, expect.objectContaining({
          displayName: 'Updated Name',
        }))
      })
    })
  })

  describe('Deactivate Member', () => {
    it('should call deactivateMember API after confirmation', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      vi.mocked(teamsApi.deactivateMember).mockResolvedValue(undefined)

      renderTeamMembersPage()

      await waitFor(() => {
        const deactivateButtons = screen.getAllByText('Deactivate')
        fireEvent.click(deactivateButtons[0])
      })

      expect(teamsApi.deactivateMember).toHaveBeenCalledWith(1, 1)
    })

    it('should not call API if not confirmed', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false)

      renderTeamMembersPage()

      await waitFor(() => {
        const deactivateButtons = screen.getAllByText('Deactivate')
        fireEvent.click(deactivateButtons[0])
      })

      expect(teamsApi.deactivateMember).not.toHaveBeenCalled()
    })
  })

  describe('Planning Config', () => {
    it('should render planning config section', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('Настройки планирования')).toBeInTheDocument()
      })
    })

    it('should expand planning config on click', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Настройки планирования'))
      })

      expect(screen.getByText('Коэффициенты грейдов')).toBeInTheDocument()
      expect(screen.getByText('Буфер рисков')).toBeInTheDocument()
      expect(screen.getByText('WIP лимиты (рекомендательные)')).toBeInTheDocument()
    })

    it('should show grade coefficient inputs', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Настройки планирования'))
      })

      expect(screen.getByDisplayValue('0.8')).toBeInTheDocument() // Senior
      expect(screen.getByDisplayValue('1')).toBeInTheDocument() // Middle
      expect(screen.getByDisplayValue('1.5')).toBeInTheDocument() // Junior
    })

    it('should show save button', async () => {
      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Настройки планирования'))
      })

      expect(screen.getByText('Сохранить настройки')).toBeInTheDocument()
    })

    it('should call updatePlanningConfig on save', async () => {
      vi.mocked(teamsApi.updatePlanningConfig).mockResolvedValue(mockPlanningConfig)

      renderTeamMembersPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Настройки планирования'))
      })

      fireEvent.click(screen.getByText('Сохранить настройки'))

      await waitFor(() => {
        expect(teamsApi.updatePlanningConfig).toHaveBeenCalledWith(1, expect.any(Object))
      })
    })
  })

  describe('Empty state', () => {
    it('should show empty message when no members', async () => {
      vi.mocked(teamsApi.getMembers).mockResolvedValue([])

      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('No members in this team yet. Add your first member!')).toBeInTheDocument()
      })
    })
  })

  describe('Error handling', () => {
    it('should show error message on API failure', async () => {
      vi.mocked(teamsApi.getById).mockRejectedValue({ message: 'Network error' })

      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('Error: Network error')).toBeInTheDocument()
      })
    })

    it('should show "Team not found" when team is null', async () => {
      vi.mocked(teamsApi.getById).mockResolvedValue(null as any)

      renderTeamMembersPage()

      await waitFor(() => {
        expect(screen.getByText('Team not found')).toBeInTheDocument()
      })
    })
  })
})
