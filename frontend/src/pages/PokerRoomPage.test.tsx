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
  getSessionByEpicKey: vi.fn(),
  getEpicStories: vi.fn(),
  getProjectComponents: vi.fn().mockResolvedValue([]),
  getSessionSummary: vi.fn().mockResolvedValue(null),
  publishSession: vi.fn().mockResolvedValue({ results: [] }),
  addStory: vi.fn(),
  updateStory: vi.fn(),
  deleteStory: vi.fn(),
  apiError: (e: unknown) => (e as Error)?.message ?? 'Unknown error',
  formatDays: (h: number) => `${h / 8}d`,
  formatDayValue: (d: number) => `${d}d`,
  formatDeltaDayValue: (d: number) => `${d}d`,
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
}))

const stableGetRoleCodes = () => ['SA', 'DEV', 'QA']
const stableGetRoleColor = () => '#666666'
const stableGetRoleDisplayName = (code: string) => code
const stableGetIssueTypeIconUrl = () => ''
const stableGetTypeNameByCategory = (cat: string) => (cat === 'EPIC' ? 'Epic' : 'Story')

vi.mock('../contexts/WorkflowConfigContext', () => ({
  useWorkflowConfig: () => ({
    getRoleCodes: stableGetRoleCodes,
    getRoleColor: stableGetRoleColor,
    getRoleDisplayName: stableGetRoleDisplayName,
    getIssueTypeIconUrl: stableGetIssueTypeIconUrl,
    getTypeNameByCategory: stableGetTypeNameByCategory,
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
  epicSummary: 'Mobile App',
  epicDescription: null,
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

const renderPokerRoomPage = (epicKey = 'EPIC-1') => {
  return render(
    <MemoryRouter initialEntries={[`/poker/${epicKey}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/poker/:epicKey" element={<PokerRoomPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('PokerRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(pokerApi.getSessionByEpicKey).mockResolvedValue(mockSession as any)
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
    mockAxiosGet.mockResolvedValue(mockAuthResponse)
  })

  describe('Rendering', () => {
    it('should render a Copy link control after loading', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getAllByText('Copy link').length).toBeGreaterThan(0)
      })
    })

    it('should render epic key', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
    })

    it('should not display any room code', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText('EPIC-1')).toBeInTheDocument()
      })
      expect(screen.queryByText('ABC123')).not.toBeInTheDocument()
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
    it('should call getSessionByEpicKey with the epic key', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(pokerApi.getSessionByEpicKey).toHaveBeenCalledWith('EPIC-1')
      })
    })
  })

  describe('Error handling', () => {
    it('should show error message on API failure', async () => {
      vi.mocked(pokerApi.getSessionByEpicKey).mockRejectedValue(new Error('Session not found'))

      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText(/failed to load/i)).toBeInTheDocument()
      })
    })
  })

  describe('Session lookup', () => {
    it('should fetch session for a different epic key', async () => {
      renderPokerRoomPage('LB-999')

      await waitFor(() => {
        expect(pokerApi.getSessionByEpicKey).toHaveBeenCalledWith('LB-999')
      })
    })
  })
})
