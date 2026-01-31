import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { updateEpicOrder, updateStoryOrder, updateRoughEstimate, getRoughEstimateConfig } from './epics'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('Epics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('updateEpicOrder', () => {
    it('should call PUT /api/epics/{epicKey}/order with correct payload', async () => {
      const mockResponse = {
        data: { issueKey: 'EPIC-1', manualOrder: 3, autoScore: 45.5 }
      }
      mockedAxios.put.mockResolvedValueOnce(mockResponse)

      const result = await updateEpicOrder('EPIC-1', 3)

      expect(mockedAxios.put).toHaveBeenCalledWith(
        '/api/epics/EPIC-1/order',
        { position: 3 }
      )
      expect(result).toEqual(mockResponse.data)
    })

    it('should handle position 1 (first position)', async () => {
      const mockResponse = {
        data: { issueKey: 'EPIC-5', manualOrder: 1, autoScore: 30.0 }
      }
      mockedAxios.put.mockResolvedValueOnce(mockResponse)

      const result = await updateEpicOrder('EPIC-5', 1)

      expect(mockedAxios.put).toHaveBeenCalledWith(
        '/api/epics/EPIC-5/order',
        { position: 1 }
      )
      expect(result.manualOrder).toBe(1)
    })

    it('should propagate errors from API', async () => {
      mockedAxios.put.mockRejectedValueOnce(new Error('Network error'))

      await expect(updateEpicOrder('EPIC-1', 2)).rejects.toThrow('Network error')
    })
  })

  describe('updateStoryOrder', () => {
    it('should call PUT /api/stories/{storyKey}/order with correct payload', async () => {
      const mockResponse = {
        data: { issueKey: 'STORY-1', manualOrder: 2, autoScore: 75.0 }
      }
      mockedAxios.put.mockResolvedValueOnce(mockResponse)

      const result = await updateStoryOrder('STORY-1', 2)

      expect(mockedAxios.put).toHaveBeenCalledWith(
        '/api/stories/STORY-1/order',
        { position: 2 }
      )
      expect(result).toEqual(mockResponse.data)
    })

    it('should handle Bug type issues', async () => {
      const mockResponse = {
        data: { issueKey: 'BUG-42', manualOrder: 1, autoScore: null }
      }
      mockedAxios.put.mockResolvedValueOnce(mockResponse)

      const result = await updateStoryOrder('BUG-42', 1)

      expect(mockedAxios.put).toHaveBeenCalledWith(
        '/api/stories/BUG-42/order',
        { position: 1 }
      )
      expect(result.autoScore).toBeNull()
    })
  })

  describe('getRoughEstimateConfig', () => {
    it('should fetch rough estimate config', async () => {
      const mockResponse = {
        data: {
          enabled: true,
          allowedEpicStatuses: ['To Do', 'In Progress'],
          stepDays: 5,
          minDays: 1,
          maxDays: 100
        }
      }
      mockedAxios.get.mockResolvedValueOnce(mockResponse)

      const result = await getRoughEstimateConfig()

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/epics/config/rough-estimate')
      expect(result.enabled).toBe(true)
      expect(result.stepDays).toBe(5)
    })
  })

  describe('updateRoughEstimate', () => {
    it('should update SA rough estimate', async () => {
      const mockResponse = {
        data: {
          epicKey: 'EPIC-1',
          role: 'sa',
          updatedDays: 10,
          saDays: 10,
          devDays: null,
          qaDays: null
        }
      }
      mockedAxios.patch.mockResolvedValueOnce(mockResponse)

      const result = await updateRoughEstimate('EPIC-1', 'sa', { days: 10 })

      expect(mockedAxios.patch).toHaveBeenCalledWith(
        '/api/epics/EPIC-1/rough-estimate/sa',
        { days: 10 }
      )
      expect(result.saDays).toBe(10)
    })

    it('should clear rough estimate when days is null', async () => {
      const mockResponse = {
        data: {
          epicKey: 'EPIC-1',
          role: 'dev',
          updatedDays: null,
          saDays: 10,
          devDays: null,
          qaDays: 5
        }
      }
      mockedAxios.patch.mockResolvedValueOnce(mockResponse)

      const result = await updateRoughEstimate('EPIC-1', 'dev', { days: null })

      expect(mockedAxios.patch).toHaveBeenCalledWith(
        '/api/epics/EPIC-1/rough-estimate/dev',
        { days: null }
      )
      expect(result.devDays).toBeNull()
    })
  })
})
