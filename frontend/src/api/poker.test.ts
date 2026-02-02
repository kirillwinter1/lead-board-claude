import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  getEligibleEpics,
  getEpicStories,
  createSession,
  getSession,
  getSessionByRoomCode,
  getSessionsByTeam,
  addStory,
  getVotes,
} from './poker'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('Poker API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getEligibleEpics', () => {
    it('should fetch eligible epics for a team', async () => {
      const mockEpics = [
        { epicKey: 'EPIC-1', summary: 'First Epic', status: 'To Do', hasPokerSession: false },
        { epicKey: 'EPIC-2', summary: 'Second Epic', status: 'In Progress', hasPokerSession: true },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockEpics })

      const result = await getEligibleEpics(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/poker/eligible-epics/1')
      expect(result).toHaveLength(2)
      expect(result[0].epicKey).toBe('EPIC-1')
    })
  })

  describe('getEpicStories', () => {
    it('should fetch stories for an epic', async () => {
      const mockStories = [
        {
          storyKey: 'STORY-1',
          summary: 'First Story',
          status: 'To Do',
          hasSaSubtask: true,
          hasDevSubtask: true,
          hasQaSubtask: false,
          saEstimate: 8,
          devEstimate: 16,
          qaEstimate: null,
        },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockStories })

      const result = await getEpicStories('EPIC-1')

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/poker/epic-stories/EPIC-1')
      expect(result[0].hasSaSubtask).toBe(true)
    })
  })

  describe('createSession', () => {
    it('should create a new poker session', async () => {
      const mockSession = {
        id: 1,
        teamId: 1,
        epicKey: 'EPIC-1',
        facilitatorAccountId: 'acc-123',
        status: 'PREPARING',
        roomCode: 'ABC123',
        createdAt: '2024-01-15T10:00:00Z',
        startedAt: null,
        completedAt: null,
        stories: [],
        currentStoryId: null,
      }
      mockedAxios.post.mockResolvedValueOnce({ data: mockSession })

      const result = await createSession(1, 'EPIC-1')

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/poker/sessions', { teamId: 1, epicKey: 'EPIC-1' })
      expect(result.roomCode).toBe('ABC123')
      expect(result.status).toBe('PREPARING')
    })
  })

  describe('getSession', () => {
    it('should fetch session by id', async () => {
      const mockSession = {
        id: 1,
        teamId: 1,
        epicKey: 'EPIC-1',
        status: 'ACTIVE',
        roomCode: 'ABC123',
        stories: [],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockSession })

      const result = await getSession(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/poker/sessions/1')
      expect(result.status).toBe('ACTIVE')
    })
  })

  describe('getSessionByRoomCode', () => {
    it('should fetch session by room code', async () => {
      const mockSession = {
        id: 1,
        roomCode: 'ABC123',
        status: 'ACTIVE',
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockSession })

      const result = await getSessionByRoomCode('ABC123')

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/poker/sessions/room/ABC123')
      expect(result.roomCode).toBe('ABC123')
    })
  })

  describe('getSessionsByTeam', () => {
    it('should fetch all sessions for a team', async () => {
      const mockSessions = [
        { id: 1, teamId: 1, epicKey: 'EPIC-1', status: 'COMPLETED' },
        { id: 2, teamId: 1, epicKey: 'EPIC-2', status: 'ACTIVE' },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockSessions })

      const result = await getSessionsByTeam(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/poker/sessions/team/1')
      expect(result).toHaveLength(2)
    })
  })

  describe('addStory', () => {
    it('should add a story to session with Jira creation', async () => {
      const request = {
        title: 'New Story',
        needsSa: true,
        needsDev: true,
        needsQa: true,
      }
      const mockStory = {
        id: 1,
        storyKey: 'STORY-1',
        title: 'New Story',
        needsSa: true,
        needsDev: true,
        needsQa: true,
        status: 'PENDING',
      }
      mockedAxios.post.mockResolvedValueOnce({ data: mockStory })

      const result = await addStory(1, request, true)

      expect(mockedAxios.post).toHaveBeenCalledWith(
        '/api/poker/sessions/1/stories?createInJira=true',
        request
      )
      expect(result.title).toBe('New Story')
    })

    it('should add a story without Jira creation', async () => {
      const request = {
        title: 'Temp Story',
        needsSa: false,
        needsDev: true,
        needsQa: false,
      }
      mockedAxios.post.mockResolvedValueOnce({ data: { id: 2, ...request, status: 'PENDING' } })

      await addStory(1, request, false)

      expect(mockedAxios.post).toHaveBeenCalledWith(
        '/api/poker/sessions/1/stories?createInJira=false',
        request
      )
    })

    it('should add existing story to session', async () => {
      const request = {
        title: 'Existing Story',
        needsSa: true,
        needsDev: true,
        needsQa: true,
        existingStoryKey: 'STORY-99',
      }
      mockedAxios.post.mockResolvedValueOnce({ data: { id: 3, storyKey: 'STORY-99', ...request } })

      const result = await addStory(1, request)

      expect(result.storyKey).toBe('STORY-99')
    })
  })

  describe('getVotes', () => {
    it('should fetch votes for a story', async () => {
      const mockVotes = [
        {
          id: 1,
          voterAccountId: 'acc-1',
          voterDisplayName: 'John',
          voterRole: 'DEV',
          voteHours: 8,
          hasVoted: true,
          votedAt: '2024-01-15T10:30:00Z',
        },
        {
          id: 2,
          voterAccountId: 'acc-2',
          voterDisplayName: 'Jane',
          voterRole: 'QA',
          voteHours: null,
          hasVoted: false,
          votedAt: null,
        },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockVotes })

      const result = await getVotes(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/poker/stories/1/votes')
      expect(result).toHaveLength(2)
      expect(result[0].hasVoted).toBe(true)
      expect(result[1].hasVoted).toBe(false)
    })
  })

  describe('Error handling', () => {
    it('should propagate errors from API', async () => {
      mockedAxios.get.mockRejectedValueOnce(new Error('Session not found'))

      await expect(getSession(999)).rejects.toThrow('Session not found')
    })
  })
})
