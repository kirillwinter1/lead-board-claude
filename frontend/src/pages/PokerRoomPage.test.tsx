import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PokerRoomPage } from './PokerRoomPage'
import * as pokerApi from '../api/poker'
import * as configApi from '../api/config'

vi.mock('../api/poker', () => ({
  getSessionByRoomCode: vi.fn(),
  getEpicStories: vi.fn(),
  addStory: vi.fn(),
}))

vi.mock('../api/config', () => ({
  getConfig: vi.fn(),
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
      needsSa: true,
      needsDev: true,
      needsQa: true,
      status: 'VOTING' as const,
      finalSaHours: null,
      finalDevHours: null,
      finalQaHours: null,
      orderIndex: 0,
      votes: [],
    },
  ],
  currentStoryId: 1,
}

const renderPokerRoomPage = (roomCode = 'ABC123') => {
  return render(
    <MemoryRouter initialEntries={[`/poker/room/${roomCode}`]}>
      <Routes>
        <Route path="/poker/room/:roomCode" element={<PokerRoomPage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('PokerRoomPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(pokerApi.getSessionByRoomCode).mockResolvedValue(mockSession as any)
    vi.mocked(configApi.getConfig).mockResolvedValue({ jiraBaseUrl: 'https://jira.example.com/browse/' })
  })

  describe('Rendering', () => {
    it('should render room code after loading', async () => {
      renderPokerRoomPage()

      await waitFor(() => {
        expect(screen.getByText('ABC123')).toBeInTheDocument()
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
        expect(screen.getByText('ABC123')).toBeInTheDocument()
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
        expect(screen.getByText(/Failed to load session/i)).toBeInTheDocument()
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
