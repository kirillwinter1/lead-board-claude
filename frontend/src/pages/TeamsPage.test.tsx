import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { TeamsPage } from './TeamsPage'
import { teamsApi } from '../api/teams'

vi.mock('../api/teams', () => ({
  teamsApi: {
    getAll: vi.fn(),
    getConfig: vi.fn(),
    getSyncStatus: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    triggerSync: vi.fn(),
  },
}))

const mockTeams = [
  { id: 1, name: 'Team Alpha', jiraTeamValue: 'alpha', color: '#0052CC', active: true, memberCount: 5, createdAt: '2024-01-01T00:00:00Z', updatedAt: '2024-01-01T00:00:00Z' },
  { id: 2, name: 'Team Beta', jiraTeamValue: null, color: '#00875A', active: true, memberCount: 3, createdAt: '2024-01-02T00:00:00Z', updatedAt: '2024-01-02T00:00:00Z' },
]

const renderTeamsPage = () => {
  return render(
    <BrowserRouter>
      <TeamsPage />
    </BrowserRouter>
  )
}

describe('TeamsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(teamsApi.getAll).mockResolvedValue(mockTeams)
    vi.mocked(teamsApi.getConfig).mockResolvedValue({ manualTeamManagement: true, organizationId: '' })
    vi.mocked(teamsApi.getSyncStatus).mockResolvedValue({ syncInProgress: false, lastSyncTime: null, error: null })
  })

  describe('Rendering', () => {
    it('should render page title', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('Teams')).toBeInTheDocument()
      })
    })

    it('should render teams table after loading', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
        expect(screen.getByText('Team Beta')).toBeInTheDocument()
      })
    })

    it('should show loading state initially', () => {
      renderTeamsPage()

      expect(screen.getByText('Loading teams...')).toBeInTheDocument()
    })

    it('should render table headers', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('NAME')).toBeInTheDocument()
        expect(screen.getByText('JIRA TEAM VALUE')).toBeInTheDocument()
        expect(screen.getByText('MEMBERS')).toBeInTheDocument()
        expect(screen.getByText('CREATED')).toBeInTheDocument()
        expect(screen.getByText('ACTIONS')).toBeInTheDocument()
      })
    })

    it('should show member count', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('5')).toBeInTheDocument()
        expect(screen.getByText('3')).toBeInTheDocument()
      })
    })

    it('should show -- for null jiraTeamValue', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('alpha')).toBeInTheDocument()
        expect(screen.getAllByText('--').length).toBeGreaterThan(0)
      })
    })
  })

  describe('Add Team button', () => {
    it('should show Add Team button when manualTeamManagement is true', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('+ Add Team')).toBeInTheDocument()
      })
    })

    it('should not show Add Team button when manualTeamManagement is false', async () => {
      vi.mocked(teamsApi.getConfig).mockResolvedValue({ manualTeamManagement: false, organizationId: '' })

      renderTeamsPage()

      await waitFor(() => {
        expect(screen.queryByText('+ Add Team')).not.toBeInTheDocument()
      })
    })

    it('should open modal when Add Team is clicked', async () => {
      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('+ Add Team')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('+ Add Team'))

      expect(screen.getByText('Create Team')).toBeInTheDocument()
    })
  })

  describe('Create Team Modal', () => {
    it('should render form fields', async () => {
      renderTeamsPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Add Team'))
      })

      expect(screen.getByLabelText('Team Name *')).toBeInTheDocument()
      expect(screen.getByLabelText('Jira Team Value')).toBeInTheDocument()
    })

    it('should close modal on Cancel', async () => {
      renderTeamsPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Add Team'))
      })

      fireEvent.click(screen.getByText('Cancel'))

      expect(screen.queryByText('Create Team')).not.toBeInTheDocument()
    })

    it('should call create API on submit', async () => {
      vi.mocked(teamsApi.create).mockResolvedValue({ id: 3, name: 'New Team', jiraTeamValue: '', color: null, active: true, memberCount: 0, createdAt: '', updatedAt: '' })

      renderTeamsPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Add Team'))
      })

      fireEvent.change(screen.getByLabelText('Team Name *'), { target: { value: 'New Team' } })
      fireEvent.click(screen.getByText('Create'))

      await waitFor(() => {
        expect(teamsApi.create).toHaveBeenCalledWith({ name: 'New Team', jiraTeamValue: '', color: undefined })
      })
    })
  })

  describe('Edit Team', () => {
    it('should show Edit button for each team', async () => {
      renderTeamsPage()

      await waitFor(() => {
        const editButtons = screen.getAllByText('Edit')
        expect(editButtons.length).toBe(2)
      })
    })

    it('should open edit modal with team data', async () => {
      renderTeamsPage()

      await waitFor(() => {
        const editButtons = screen.getAllByText('Edit')
        fireEvent.click(editButtons[0])
      })

      expect(screen.getByText('Edit Team')).toBeInTheDocument()
      expect(screen.getByDisplayValue('Team Alpha')).toBeInTheDocument()
    })
  })

  describe('Delete Team', () => {
    it('should show Delete button for each team', async () => {
      renderTeamsPage()

      await waitFor(() => {
        const deleteButtons = screen.getAllByText('Delete')
        expect(deleteButtons.length).toBe(2)
      })
    })

    it('should call delete API after confirmation', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      vi.mocked(teamsApi.delete).mockResolvedValue(undefined)

      renderTeamsPage()

      await waitFor(() => {
        const deleteButtons = screen.getAllByText('Delete')
        fireEvent.click(deleteButtons[0])
      })

      expect(teamsApi.delete).toHaveBeenCalledWith(1)
    })

    it('should not call delete API if not confirmed', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false)

      renderTeamsPage()

      await waitFor(() => {
        const deleteButtons = screen.getAllByText('Delete')
        fireEvent.click(deleteButtons[0])
      })

      expect(teamsApi.delete).not.toHaveBeenCalled()
    })
  })

  describe('Sync status', () => {
    it('should show sync info when organizationId is set', async () => {
      vi.mocked(teamsApi.getConfig).mockResolvedValue({ manualTeamManagement: true, organizationId: 'org-123' })
      vi.mocked(teamsApi.getSyncStatus).mockResolvedValue({ syncInProgress: false, lastSyncTime: '2024-01-15T10:00:00Z', error: null })

      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText(/Last sync:/)).toBeInTheDocument()
      })
    })

    it('should not show sync info when organizationId is empty', async () => {
      vi.mocked(teamsApi.getConfig).mockResolvedValue({ manualTeamManagement: true, organizationId: '' })

      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('Teams')).toBeInTheDocument()
      })

      expect(screen.queryByText(/Last sync:/)).not.toBeInTheDocument()
    })
  })

  describe('Empty state', () => {
    it('should show empty message when no teams', async () => {
      vi.mocked(teamsApi.getAll).mockResolvedValue([])

      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('No teams yet. Create your first team!')).toBeInTheDocument()
      })
    })
  })

  describe('Error handling', () => {
    it('should show error message on API failure', async () => {
      vi.mocked(teamsApi.getAll).mockRejectedValue({ message: 'Network error' })

      renderTeamsPage()

      await waitFor(() => {
        expect(screen.getByText('Error: Network error')).toBeInTheDocument()
      })
    })
  })

  describe('Navigation', () => {
    it('should have links to team members page', async () => {
      renderTeamsPage()

      await waitFor(() => {
        const teamLink = screen.getByText('Team Alpha').closest('a')
        expect(teamLink).toHaveAttribute('href', '/board/teams/1')
      })
    })
  })
})
