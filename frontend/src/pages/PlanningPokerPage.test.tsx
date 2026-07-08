import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { BrowserRouter } from 'react-router-dom'
import { PlanningPokerPage } from './PlanningPokerPage'
import { teamsApi } from '../api/teams'
import * as pokerApi from '../api/poker'
import * as configApi from '../api/config'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('../api/teams', () => ({
  teamsApi: {
    getAll: vi.fn(),
  },
}))

vi.mock('../api/poker', () => ({
  getSessionsByTeam: vi.fn(),
  getEligibleEpics: vi.fn(),
  createSession: vi.fn(),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

const mockTeams = [
  { id: 1, name: 'Team Alpha', jiraTeamValue: 'alpha', active: true, memberCount: 5 },
  { id: 2, name: 'Team Beta', jiraTeamValue: 'beta', active: true, memberCount: 3 },
]

const mockSessions = [
  {
    id: 1,
    teamId: 1,
    epicKey: 'EPIC-1',
    facilitatorAccountId: 'acc-1',
    status: 'ACTIVE' as const,
    roomCode: 'ABC123',
    createdAt: '2024-01-15T10:00:00Z',
    startedAt: '2024-01-15T10:05:00Z',
    completedAt: null,
    stories: [{ id: 1 }, { id: 2 }],
    currentStoryId: 1,
  },
  {
    id: 2,
    teamId: 1,
    epicKey: 'EPIC-2',
    facilitatorAccountId: 'acc-1',
    status: 'COMPLETED' as const,
    roomCode: 'XYZ789',
    createdAt: '2024-01-14T10:00:00Z',
    startedAt: '2024-01-14T10:05:00Z',
    completedAt: '2024-01-14T12:00:00Z',
    stories: [{ id: 3 }],
    currentStoryId: null,
  },
]

const mockEligibleEpics = [
  { epicKey: 'EPIC-3', summary: 'New Epic for Poker', status: 'To Do', hasPokerSession: false },
  { epicKey: 'EPIC-4', summary: 'Another Epic', status: 'In Progress', hasPokerSession: true },
]

const renderPlanningPokerPage = () => {
  return render(
    <BrowserRouter>
      <PlanningPokerPage />
    </BrowserRouter>
  )
}

describe('PlanningPokerPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(teamsApi.getAll).mockResolvedValue(mockTeams)
    vi.mocked(pokerApi.getSessionsByTeam).mockResolvedValue(mockSessions as any)
    vi.mocked(pokerApi.getEligibleEpics).mockResolvedValue(mockEligibleEpics)
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
  })

  describe('Rendering', () => {
    it('should render page title', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Planning Poker')).toBeInTheDocument()
      })
    })

    it('should show loading state initially', () => {
      renderPlanningPokerPage()

      expect(screen.getByText('Loading...')).toBeInTheDocument()
    })

    it('should render team selector with selected team, others on open', async () => {
      renderPlanningPokerPage()

      // First active team is auto-selected and shown in the pill trigger
      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
      })
      // Other teams appear only after opening the dropdown
      expect(screen.queryByText('Team Beta')).not.toBeInTheDocument()
      fireEvent.click(screen.getByText('Team Alpha'))
      expect(screen.getByText('Team Beta')).toBeInTheDocument()
    })

    it('should render "New session" button', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('New session')).toBeInTheDocument()
      })
    })

    it('should render "Join by key" button', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Join by key')).toBeInTheDocument()
      })
    })
  })

  describe('Sessions table', () => {
    it('should render sessions table headers (no room code)', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Epic')).toBeInTheDocument()
        expect(screen.getByText('Status')).toBeInTheDocument()
        expect(screen.getByText('Stories')).toBeInTheDocument()
        expect(screen.getByText('Created')).toBeInTheDocument()
        expect(screen.getByText('Actions')).toBeInTheDocument()
      })
      // Room code column has been removed from the UI entirely
      expect(screen.queryByText('Room code')).not.toBeInTheDocument()
    })

    it('should render epic rows without room codes', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-2')).toBeInTheDocument()
      })
      expect(screen.queryByText('ABC123')).not.toBeInTheDocument()
      expect(screen.queryByText('XYZ789')).not.toBeInTheDocument()
    })

    it('should show story count', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('2')).toBeInTheDocument() // EPIC-1 has 2 stories
        expect(screen.getByText('1')).toBeInTheDocument() // EPIC-2 has 1 story
      })
    })

    it('should show status badges', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Active')).toBeInTheDocument()
        expect(screen.getByText('Completed')).toBeInTheDocument()
      })
    })

    it('should show "Open" action for every session', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getAllByText('Open').length).toBe(2)
      })
    })
  })

  describe('Create session (epic picker)', () => {
    const openPicker = async () => {
      await waitFor(() => {
        expect(screen.getByText('New session')).toBeInTheDocument()
      })
      fireEvent.click(screen.getByText('New session'))
    }

    it('should show inline epic picker on "New session" click', async () => {
      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(screen.getByText('Select an epic to estimate')).toBeInTheDocument()
      })
    })

    it('should load eligible epics when picker opens', async () => {
      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(pokerApi.getEligibleEpics).toHaveBeenCalledWith(1)
      })
    })

    it('should show epic rows in the list', async () => {
      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(screen.getByText('EPIC-3')).toBeInTheDocument()
        expect(screen.getByText('New Epic for Poker')).toBeInTheDocument()
      })
    })

    it('should mark epics that already have sessions', async () => {
      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(screen.getByText('Has session')).toBeInTheDocument()
      })

      // EPIC-4 row is disabled (has a session)
      const busyRow = screen.getByText('EPIC-4').closest('button')!
      expect(busyRow).toBeDisabled()
    })

    it('should filter epics by search query', async () => {
      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(screen.getByText('EPIC-3')).toBeInTheDocument()
      })

      fireEvent.change(screen.getByPlaceholderText(/Search/), { target: { value: 'Another' } })

      await waitFor(() => {
        expect(screen.queryByText('New Epic for Poker')).not.toBeInTheDocument()
        expect(screen.getByText('Another Epic')).toBeInTheDocument()
      })
    })

    it('should return to sessions list on "Cancel" click', async () => {
      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(screen.getByText('Select an epic to estimate')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Cancel'))

      expect(screen.queryByText('Select an epic to estimate')).not.toBeInTheDocument()
    })

    it('should create session and navigate by epic key on epic row click', async () => {
      vi.mocked(pokerApi.createSession).mockResolvedValue({
        id: 3,
        epicKey: 'EPIC-3',
        roomCode: 'NEW123',
        status: 'PREPARING',
      } as any)

      renderPlanningPokerPage()
      await openPicker()

      await waitFor(() => {
        expect(screen.getByText('EPIC-3')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('EPIC-3').closest('button')!)

      await waitFor(() => {
        expect(pokerApi.createSession).toHaveBeenCalledWith(1, 'EPIC-3')
        expect(mockNavigate).toHaveBeenCalledWith('/poker/EPIC-3')
      })
    })
  })

  describe('Join by epic key modal', () => {
    it('should open modal on "Join by key" click', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Join by key'))
      })

      expect(screen.getByText('Join by epic key')).toBeInTheDocument()
    })

    it('should have epic key input', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Join by key'))
      })

      expect(screen.getByPlaceholderText('e.g. LB-203')).toBeInTheDocument()
    })

    it('should navigate to room by epic key on submit', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Join by key')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Join by key'))

      await waitFor(() => {
        expect(screen.getByText('Join by epic key')).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText('e.g. LB-203')
      fireEvent.change(input, { target: { value: 'LB-203' } })

      const modalActions = screen.getByText('Cancel').parentElement!
      const submitButton = modalActions.querySelector('button.btn-primary')!
      fireEvent.click(submitButton)

      expect(mockNavigate).toHaveBeenCalledWith('/poker/LB-203')
    })

    it('should convert epic key to uppercase', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Join by key'))
      })

      const input = screen.getByPlaceholderText('e.g. LB-203')
      fireEvent.change(input, { target: { value: 'lb-203' } })

      expect(input).toHaveValue('LB-203')
    })

    it('should disable submit for empty key', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Join by key'))
      })

      const submitButton = screen.getByText('Open room')
      expect(submitButton).toBeDisabled()
    })
  })

  describe('Empty state', () => {
    it('should show empty state with CTA when no sessions', async () => {
      vi.mocked(pokerApi.getSessionsByTeam).mockResolvedValue([])

      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('No sessions yet')).toBeInTheDocument()
      })
      // CTA buttons present inside the empty state (plus the header ones)
      expect(screen.getAllByText('New session').length).toBeGreaterThanOrEqual(1)
    })
  })

  describe('Navigation', () => {
    it('should navigate to room by epic key on row action click', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getAllByText('Open').length).toBe(2)
      })
      fireEvent.click(screen.getAllByText('Open')[0])

      expect(mockNavigate).toHaveBeenCalledWith('/poker/EPIC-1')
    })
  })

  describe('Error handling', () => {
    it('should show error message on API failure', async () => {
      vi.mocked(teamsApi.getAll).mockRejectedValue(new Error('Network error'))

      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Failed to load data: Network error')).toBeInTheDocument()
      })
    })
  })
})
