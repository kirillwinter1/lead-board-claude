import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  getForecast,
  getWipHistory,
  createWipSnapshot,
  getEpicStories,
  getUnifiedPlanning,
  getAvailableSnapshotDates,
  getUnifiedPlanningSnapshot,
  getForecastSnapshot,
} from './forecast'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('Forecast API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getForecast', () => {
    it('should fetch forecast for a team', async () => {
      const mockForecast = {
        calculatedAt: '2024-01-15T10:00:00Z',
        teamId: 1,
        teamCapacity: { saHoursPerDay: 8, devHoursPerDay: 24, qaHoursPerDay: 8 },
        wipStatus: { limit: 5, current: 3, exceeded: false },
        epics: [
          {
            epicKey: 'EPIC-1',
            summary: 'First Epic',
            autoScore: 75,
            expectedDone: '2024-02-15',
            confidence: 'HIGH',
          },
        ],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockForecast })

      const result = await getForecast(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/planning/forecast?teamId=1')
      expect(result.teamId).toBe(1)
      expect(result.epics).toHaveLength(1)
    })

    it('should include status filters when provided', async () => {
      mockedAxios.get.mockResolvedValueOnce({ data: { epics: [] } })

      await getForecast(1, ['To Do', 'In Progress'])

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/planning/forecast?teamId=1&statuses=To+Do&statuses=In+Progress'
      )
    })

    it('should handle empty statuses array', async () => {
      mockedAxios.get.mockResolvedValueOnce({ data: { epics: [] } })

      await getForecast(1, [])

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/planning/forecast?teamId=1')
    })
  })

  describe('getWipHistory', () => {
    it('should fetch WIP history with default days', async () => {
      const mockHistory = {
        teamId: 1,
        from: '2023-12-16',
        to: '2024-01-15',
        dataPoints: [
          { date: '2024-01-14', teamLimit: 5, teamCurrent: 3 },
          { date: '2024-01-15', teamLimit: 5, teamCurrent: 4 },
        ],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockHistory })

      const result = await getWipHistory(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/planning/wip-history?teamId=1&days=30')
      expect(result.dataPoints).toHaveLength(2)
    })

    it('should fetch WIP history with custom days', async () => {
      mockedAxios.get.mockResolvedValueOnce({ data: { dataPoints: [] } })

      await getWipHistory(1, 90)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/planning/wip-history?teamId=1&days=90')
    })
  })

  describe('createWipSnapshot', () => {
    it('should create a WIP snapshot', async () => {
      const mockResponse = {
        status: 'created',
        date: '2024-01-15',
        teamWip: '3/5',
      }
      mockedAxios.post.mockResolvedValueOnce({ data: mockResponse })

      const result = await createWipSnapshot(1)

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/planning/wip-snapshot?teamId=1')
      expect(result.status).toBe('created')
    })
  })

  describe('getEpicStories', () => {
    it('should fetch stories for an epic', async () => {
      const mockStories = [
        {
          storyKey: 'STORY-1',
          summary: 'First Story',
          status: 'In Progress',
          issueType: 'Story',
          assignee: 'John',
          phase: 'DEV',
          autoScore: 50,
        },
        {
          storyKey: 'STORY-2',
          summary: 'Second Story',
          status: 'To Do',
          issueType: 'Bug',
          assignee: null,
          phase: 'SA',
          autoScore: 30,
        },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockStories })

      const result = await getEpicStories('EPIC-1')

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/planning/epics/EPIC-1/stories')
      expect(result).toHaveLength(2)
      expect(result[0].phase).toBe('DEV')
    })
  })

  describe('getUnifiedPlanning', () => {
    it('should fetch unified planning for a team', async () => {
      const mockPlanning = {
        teamId: 1,
        planningDate: '2024-01-15',
        epics: [
          {
            epicKey: 'EPIC-1',
            summary: 'First Epic',
            autoScore: 80,
            startDate: '2024-01-16',
            endDate: '2024-02-15',
            stories: [],
            phaseAggregation: {
              saHours: 16,
              devHours: 40,
              qaHours: 16,
            },
          },
        ],
        warnings: [],
        assigneeUtilization: {},
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockPlanning })

      const result = await getUnifiedPlanning(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/planning/unified?teamId=1')
      expect(result.epics).toHaveLength(1)
      expect(result.epics[0].phaseAggregation.devHours).toBe(40)
    })
  })

  describe('getAvailableSnapshotDates', () => {
    it('should fetch available snapshot dates', async () => {
      const mockDates = ['2024-01-15', '2024-01-14', '2024-01-13']
      mockedAxios.get.mockResolvedValueOnce({ data: mockDates })

      const result = await getAvailableSnapshotDates(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/forecast-snapshots/dates?teamId=1')
      expect(result).toHaveLength(3)
      expect(result[0]).toBe('2024-01-15')
    })
  })

  describe('getUnifiedPlanningSnapshot', () => {
    it('should fetch unified planning from snapshot', async () => {
      const mockSnapshot = {
        teamId: 1,
        planningDate: '2024-01-10',
        epics: [],
        warnings: [],
        assigneeUtilization: {},
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockSnapshot })

      const result = await getUnifiedPlanningSnapshot(1, '2024-01-10')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/forecast-snapshots/unified?teamId=1&date=2024-01-10'
      )
      expect(result.planningDate).toBe('2024-01-10')
    })
  })

  describe('getForecastSnapshot', () => {
    it('should fetch forecast from snapshot', async () => {
      const mockSnapshot = {
        calculatedAt: '2024-01-10T10:00:00Z',
        teamId: 1,
        teamCapacity: { saHoursPerDay: 8, devHoursPerDay: 16, qaHoursPerDay: 8 },
        wipStatus: { limit: 5, current: 2, exceeded: false },
        epics: [],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockSnapshot })

      const result = await getForecastSnapshot(1, '2024-01-10')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/forecast-snapshots/forecast?teamId=1&date=2024-01-10'
      )
      expect(result.teamId).toBe(1)
    })
  })

  describe('Error handling', () => {
    it('should propagate errors from getForecast', async () => {
      mockedAxios.get.mockRejectedValueOnce(new Error('Team not found'))

      await expect(getForecast(999)).rejects.toThrow('Team not found')
    })

    it('should propagate errors from getUnifiedPlanning', async () => {
      mockedAxios.get.mockRejectedValueOnce(new Error('No capacity configured'))

      await expect(getUnifiedPlanning(1)).rejects.toThrow('No capacity configured')
    })
  })
})
