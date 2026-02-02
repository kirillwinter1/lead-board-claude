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

      expect(screen.getByText('Загрузка...')).toBeInTheDocument()
    })

    it('should render team selector', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Team Alpha')).toBeInTheDocument()
        expect(screen.getByText('Team Beta')).toBeInTheDocument()
      })
    })

    it('should render "Новая сессия" button', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('+ Новая сессия')).toBeInTheDocument()
      })
    })

    it('should render "Войти по коду" button', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Войти по коду')).toBeInTheDocument()
      })
    })
  })

  describe('Sessions table', () => {
    it('should render sessions table headers', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Эпик')).toBeInTheDocument()
        expect(screen.getByText('Код комнаты')).toBeInTheDocument()
        expect(screen.getByText('Статус')).toBeInTheDocument()
        expect(screen.getByText('Сторей')).toBeInTheDocument()
        expect(screen.getByText('Создана')).toBeInTheDocument()
        expect(screen.getByText('Действия')).toBeInTheDocument()
      })
    })

    it('should render session rows', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
        expect(screen.getByText('EPIC-2')).toBeInTheDocument()
        expect(screen.getByText('ABC123')).toBeInTheDocument()
        expect(screen.getByText('XYZ789')).toBeInTheDocument()
      })
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
        expect(screen.getByText('Активна')).toBeInTheDocument()
        expect(screen.getByText('Завершена')).toBeInTheDocument()
      })
    })

    it('should show "Войти" for active sessions', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Войти')).toBeInTheDocument()
      })
    })

    it('should show "Просмотр" for completed sessions', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Просмотр')).toBeInTheDocument()
      })
    })
  })

  describe('Create session modal', () => {
    it('should open modal on "Новая сессия" click', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('+ Новая сессия')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('+ Новая сессия'))

      await waitFor(() => {
        expect(screen.getByText('Создать сессию Planning Poker')).toBeInTheDocument()
      })
    })

    it('should load eligible epics when modal opens', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Новая сессия'))
      })

      await waitFor(() => {
        expect(pokerApi.getEligibleEpics).toHaveBeenCalledWith(1)
      })
    })

    it('should show epic options in dropdown', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('+ Новая сессия')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('+ Новая сессия'))

      await waitFor(() => {
        expect(screen.getByText(/EPIC-3/)).toBeInTheDocument()
      })
    })

    it('should disable epics that already have sessions', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('+ Новая сессия')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('+ Новая сессия'))

      await waitFor(() => {
        // Check that EPIC-4 (which has a session) is in disabled optgroup
        const select = screen.getAllByRole('combobox').pop()!
        const disabledOption = select.querySelector('option[disabled]')
        expect(disabledOption).toBeInTheDocument()
      })
    })

    it('should close modal on "Отмена" click', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('+ Новая сессия'))
      })

      await waitFor(() => {
        fireEvent.click(screen.getByText('Отмена'))
      })

      expect(screen.queryByText('Создать сессию Planning Poker')).not.toBeInTheDocument()
    })

    it('should create session and navigate on submit', async () => {
      vi.mocked(pokerApi.createSession).mockResolvedValue({
        id: 3,
        roomCode: 'NEW123',
        status: 'PREPARING',
      } as any)

      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('+ Новая сессия')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('+ Новая сессия'))

      await waitFor(() => {
        expect(screen.getByText('Создать сессию Planning Poker')).toBeInTheDocument()
      })

      // Find the epic select in modal (should be the last combobox)
      const selects = screen.getAllByRole('combobox')
      const epicSelect = selects[selects.length - 1]
      fireEvent.change(epicSelect, { target: { value: 'EPIC-3' } })

      fireEvent.click(screen.getByText('Создать →'))

      await waitFor(() => {
        expect(pokerApi.createSession).toHaveBeenCalledWith(1, 'EPIC-3')
        expect(mockNavigate).toHaveBeenCalledWith('/poker/room/NEW123')
      })
    })
  })

  describe('Join room modal', () => {
    it('should open modal on "Войти по коду" click', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Войти по коду'))
      })

      expect(screen.getByText('Войти в комнату')).toBeInTheDocument()
    })

    it('should have room code input', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Войти по коду'))
      })

      expect(screen.getByPlaceholderText('Например: ABC123')).toBeInTheDocument()
    })

    it('should navigate to room on submit', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Войти по коду')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Войти по коду'))

      await waitFor(() => {
        expect(screen.getByText('Войти в комнату')).toBeInTheDocument()
      })

      const input = screen.getByPlaceholderText('Например: ABC123')
      fireEvent.change(input, { target: { value: 'ABC123' } })

      // Find submit button in modal (the one in modal-actions)
      const modalActions = screen.getByText('Отмена').parentElement!
      const submitButton = modalActions.querySelector('button.btn-primary')!
      fireEvent.click(submitButton)

      expect(mockNavigate).toHaveBeenCalledWith('/poker/room/ABC123')
    })

    it('should convert room code to uppercase', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Войти по коду'))
      })

      const input = screen.getByPlaceholderText('Например: ABC123')
      fireEvent.change(input, { target: { value: 'abc123' } })

      expect(input).toHaveValue('ABC123')
    })

    it('should disable submit for short codes', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Войти по коду'))
      })

      const input = screen.getByPlaceholderText('Например: ABC123')
      fireEvent.change(input, { target: { value: 'ABC' } })

      const submitButton = screen.getAllByText('Войти').find(
        el => el.tagName === 'BUTTON' && el.closest('.modal-content')
      )
      expect(submitButton).toBeDisabled()
    })
  })

  describe('Empty state', () => {
    it('should show empty message when no sessions', async () => {
      vi.mocked(pokerApi.getSessionsByTeam).mockResolvedValue([])

      renderPlanningPokerPage()

      await waitFor(() => {
        expect(screen.getByText('Нет сессий Planning Poker.')).toBeInTheDocument()
      })
    })
  })

  describe('Navigation', () => {
    it('should navigate to room on row action click', async () => {
      renderPlanningPokerPage()

      await waitFor(() => {
        fireEvent.click(screen.getByText('Войти'))
      })

      expect(mockNavigate).toHaveBeenCalledWith('/poker/room/ABC123')
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
