import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PokerRoomPage } from './PokerRoomPage'
import { AuthProvider } from '../contexts/AuthContext'
import * as pokerApi from '../api/poker'
import * as configApi from '../api/config'

const mockAxiosGet = vi.fn()

vi.mock('axios', () => ({
  default: {
    get: (...args: any[]) => mockAxiosGet(...args),
    post: vi.fn().mockResolvedValue({ data: {} }),
    put: vi.fn().mockResolvedValue({ data: {} }),
    delete: vi.fn().mockResolvedValue({ data: {} }),
    isAxiosError: () => false,
  },
}))

vi.mock('../api/poker', () => ({
  getSessionByRoomCode: vi.fn(),
  getEpicStories: vi.fn(),
  addStory: vi.fn(),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

const stableGetRoleCodes = () => ['SA', 'DEV', 'QA']
const stableGetRoleColor = () => '#666'
const stableGetRoleDisplayName = (code: string) => code
const stableGetIssueTypeIconUrl = () => ''

vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getRoleCodes: stableGetRoleCodes,
    getRoleColor: stableGetRoleColor,
    getRoleDisplayName: stableGetRoleDisplayName,
    getIssueTypeIconUrl: stableGetIssueTypeIconUrl,
    issueTypeIcons: {},
    config: { roles: [], issueTypes: [], statuses: [] },
    loading: false,
  }),
}))

vi.mock('../hooks/usePokerWebSocket', () => ({
  usePokerWebSocket: vi.fn(() => ({
    connected: true,
    sendVote: vi.fn(),
    sendReveal: vi.fn(),
    sendSetFinal: vi.fn(),
    sendNextStory: vi.fn(),
    sendStartSession: vi.fn(),
  })),
}))

vi.mock('../api/teams', () => ({
  teamsApi: {
    getMembers: vi.fn().mockResolvedValue([
      { id: 1, jiraAccountId: 'user-123', displayName: 'Test User', role: 'DEV', avatarUrl: null },
    ]),
  },
}))

const mockAuthResponse = {
  data: {
    authenticated: true,
    user: {
      id: 1,
      accountId: 'user-123',
      displayName: 'Test User',
      email: 'test@example.com',
      avatarUrl: null,
      role: 'ADMIN',
      permissions: [],
    },
  },
}

const mockSession = {
  id: 1,
  teamId: 1,
  epicKey: 'EPIC-1',
  facilitatorAccountId: 'user-123',
  status: 'ACTIVE' as const,
  roomCode: 'ABC123',
  createdAt: '2024-01-15T10:00:00Z',
  startedAt: '2024-01-15T10:05:00Z',
  completedAt: null,
  stories: [
    {
      id: 1,
      storyKey: 'STORY-1',
      title: 'First Story',
      needsRoles: ['SA', 'DEV', 'QA'],
      status: 'VOTING' as const,
      finalEstimates: {},
      orderIndex: 0,
      votes: [],
    },
  ],
  currentStoryId: 1,
}

const renderPokerRoomPage = (roomCode = 'ABC123') => {
  return render(
    <MemoryRouter initialEntries={[`/poker/room/${roomCode}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/poker/room/:roomCode" element={<PokerRoomPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('PokerRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(pokerApi.getSessionByRoomCode).mockResolvedValue(mockSession as any)
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
    mockAxiosGet.mockResolvedValue(mockAuthResponse)
  })

  describe('Rendering', () => {
    it('should render room code after loading', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getAllByText('ABC123').length).toBeGreaterThan(0)
      })
    })

    it('should render epic key', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should render session data after loading', async () => {
      renderPokerRoomPage()

      // Wait for session to load and display room code and epic key
      await waitFor(() => {
        expect(screen.getAllByText('ABC123').length).toBeGreaterThan(0)
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should render vote options', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText('2')).toBeInTheDocument()
        expect(screen.getByText('8')).toBeInTheDocument()
        expect(screen.getByText('?')).toBeInTheDocument()
      })
    })
  })

  describe('API calls', () => {
    it('should call getSessionByRoomCode with room code', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(pokerApi.getSessionByRoomCode).toHaveBeenCalledWith('ABC123')
      })
    })
  })

  describe('Error handling', () => {
    it('should show error message on API failure', async () => {
      vi.mocked(pokerApi.getSessionByRoomCode).mockRejectedValue(new Error('Session not found'))

      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText(/не удалось загрузить/i)).toBeInTheDocument()
      })
    })
  })

  describe('Session status', () => {
    it('should fetch session for room code', async () => {
      renderPokerRoomPage('XYZ789')

      await waitFor(() => {
        expect(pokerApi.getSessionByRoomCode).toHaveBeenCalledWith('XYZ789')
      })
    })
  })
})
