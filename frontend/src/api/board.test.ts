import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { getScoreBreakdown } from './board'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('Board API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getScoreBreakdown', () => {
    it('should fetch score breakdown for an epic', async () => {
      const mockBreakdown = {
        issueKey: 'EPIC-123',
        issueType: 'Epic',
        totalScore: 75.5,
        breakdown: {
          'Business Value': 20,
          'Team Priority': 15,
          'Dependencies': 10,
          'Risk': 12.5,
          'Time Sensitivity': 8,
          'Technical Debt': 5,
          'Stakeholder Priority': 5,
        },
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockBreakdown })

      const result = await getScoreBreakdown('EPIC-123')

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/board/EPIC-123/score-breakdown')
      expect(result.issueKey).toBe('EPIC-123')
      expect(result.totalScore).toBe(75.5)
      expect(result.breakdown['Business Value']).toBe(20)
    })

    it('should fetch score breakdown for a story', async () => {
      const mockBreakdown = {
        issueKey: 'STORY-456',
        issueType: 'Story',
        totalScore: 60.0,
        breakdown: {
          'Epic Priority': 25,
          'Blocking Count': 15,
          'Dependencies': 10,
          'Progress': 5,
          'Estimate Size': 5,
        },
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockBreakdown })

      const result = await getScoreBreakdown('STORY-456')

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/board/STORY-456/score-breakdown')
      expect(result.issueType).toBe('Story')
    })

    it('should handle null totalScore', async () => {
      const mockBreakdown = {
        issueKey: 'EPIC-789',
        issueType: 'Epic',
        totalScore: null,
        breakdown: {},
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockBreakdown })

      const result = await getScoreBreakdown('EPIC-789')

      expect(result.totalScore).toBeNull()
      expect(result.breakdown).toEqual({})
    })

    it('should propagate errors from API', async () => {
      mockedAxios.get.mockRejectedValueOnce(new Error('Issue not found'))

      await expect(getScoreBreakdown('INVALID-KEY')).rejects.toThrow('Issue not found')
    })

    it('should handle 404 error', async () => {
      const error = new Error('Not found')
      ;(error as any).response = { status: 404 }
      mockedAxios.get.mockRejectedValueOnce(error)

      await expect(getScoreBreakdown('NONEXISTENT-1')).rejects.toThrow('Not found')
    })
  })
})
