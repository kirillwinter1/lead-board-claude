import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import {
  getMetricsSummary,
  getThroughput,
  getLeadTime,
  getCycleTime,
  getTimeInStatus,
  getByAssignee,
  getDsr,
  getForecastAccuracy,
} from './metrics'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('Metrics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const defaultParams = {
    teamId: 1,
    from: '2024-01-01',
    to: '2024-01-31',
  }

  describe('getMetricsSummary', () => {
    it('should fetch metrics summary with required params', async () => {
      const mockSummary = {
        from: '2024-01-01',
        to: '2024-01-31',
        teamId: 1,
        throughput: { total: 10 },
        leadTime: { avgDays: 5 },
        cycleTime: { avgDays: 3 },
        timeInStatuses: [],
        byAssignee: [],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockSummary })

      const result = await getMetricsSummary(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/summary?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result.teamId).toBe(1)
    })

    it('should include issueType filter when provided', async () => {
      mockedAxios.get.mockResolvedValueOnce({ data: {} })

      await getMetricsSummary(1, '2024-01-01', '2024-01-31', 'Story')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/summary?teamId=1&from=2024-01-01&to=2024-01-31&issueType=Story'
      )
    })

    it('should include epicKey filter when provided', async () => {
      mockedAxios.get.mockResolvedValueOnce({ data: {} })

      await getMetricsSummary(1, '2024-01-01', '2024-01-31', undefined, 'EPIC-123')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/summary?teamId=1&from=2024-01-01&to=2024-01-31&epicKey=EPIC-123'
      )
    })
  })

  describe('getThroughput', () => {
    it('should fetch throughput data', async () => {
      const mockThroughput = {
        totalEpics: 5,
        totalStories: 20,
        totalSubtasks: 50,
        total: 75,
        byPeriod: [],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockThroughput })

      const result = await getThroughput(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/throughput?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result.total).toBe(75)
    })

    it('should include assigneeAccountId filter when provided', async () => {
      mockedAxios.get.mockResolvedValueOnce({ data: {} })

      await getThroughput(1, '2024-01-01', '2024-01-31', undefined, undefined, 'acc-123')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/throughput?teamId=1&from=2024-01-01&to=2024-01-31&assigneeAccountId=acc-123'
      )
    })
  })

  describe('getLeadTime', () => {
    it('should fetch lead time data', async () => {
      const mockLeadTime = {
        avgDays: 10,
        medianDays: 8,
        p90Days: 15,
        minDays: 3,
        maxDays: 25,
        sampleSize: 20,
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockLeadTime })

      const result = await getLeadTime(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/lead-time?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result.avgDays).toBe(10)
      expect(result.sampleSize).toBe(20)
    })
  })

  describe('getCycleTime', () => {
    it('should fetch cycle time data', async () => {
      const mockCycleTime = {
        avgDays: 5,
        medianDays: 4,
        p90Days: 8,
        minDays: 1,
        maxDays: 12,
        sampleSize: 15,
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockCycleTime })

      const result = await getCycleTime(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/cycle-time?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result.avgDays).toBe(5)
    })
  })

  describe('getTimeInStatus', () => {
    it('should fetch time in status data', async () => {
      const mockTimeInStatus = [
        { status: 'To Do', avgHours: 24, medianHours: 20, transitionsCount: 10 },
        { status: 'In Progress', avgHours: 48, medianHours: 40, transitionsCount: 8 },
        { status: 'Done', avgHours: 0, medianHours: 0, transitionsCount: 15 },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockTimeInStatus })

      const result = await getTimeInStatus(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/time-in-status?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result).toHaveLength(3)
      expect(result[1].status).toBe('In Progress')
    })
  })

  describe('getByAssignee', () => {
    it('should fetch metrics by assignee', async () => {
      const mockByAssignee = [
        { accountId: 'acc-1', displayName: 'John', issuesClosed: 10, avgLeadTimeDays: 5, avgCycleTimeDays: 3 },
        { accountId: 'acc-2', displayName: 'Jane', issuesClosed: 8, avgLeadTimeDays: 6, avgCycleTimeDays: 4 },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockByAssignee })

      const result = await getByAssignee(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/by-assignee?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result).toHaveLength(2)
      expect(result[0].displayName).toBe('John')
    })
  })

  describe('getDsr', () => {
    it('should fetch DSR data', async () => {
      const mockDsr = {
        avgDsrActual: 0.95,
        avgDsrForecast: 1.05,
        totalEpics: 10,
        onTimeCount: 8,
        onTimeRate: 0.8,
        epics: [
          {
            epicKey: 'EPIC-1',
            summary: 'First Epic',
            workingDaysActual: 10,
            estimateDays: 12,
            forecastDays: 11,
            dsrActual: 0.83,
            dsrForecast: 0.91,
          },
        ],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockDsr })

      const result = await getDsr(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/dsr?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result.onTimeRate).toBe(0.8)
      expect(result.epics).toHaveLength(1)
    })
  })

  describe('getForecastAccuracy', () => {
    it('should fetch forecast accuracy data', async () => {
      const mockAccuracy = {
        teamId: 1,
        from: '2024-01-01',
        to: '2024-01-31',
        avgAccuracyRatio: 0.92,
        onTimeDeliveryRate: 0.75,
        avgScheduleVariance: 2,
        totalCompleted: 12,
        onTimeCount: 9,
        lateCount: 2,
        earlyCount: 1,
        epics: [
          {
            epicKey: 'EPIC-1',
            summary: 'First Epic',
            plannedStart: '2024-01-05',
            plannedEnd: '2024-01-15',
            actualStart: '2024-01-05',
            actualEnd: '2024-01-14',
            plannedDays: 10,
            actualDays: 9,
            accuracyRatio: 1.1,
            scheduleVariance: -1,
            status: 'EARLY' as const,
            initialEstimateHours: 80,
            developingEstimateHours: 72,
          },
        ],
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockAccuracy })

      const result = await getForecastAccuracy(1, '2024-01-01', '2024-01-31')

      expect(mockedAxios.get).toHaveBeenCalledWith(
        '/api/metrics/forecast-accuracy?teamId=1&from=2024-01-01&to=2024-01-31'
      )
      expect(result.avgAccuracyRatio).toBe(0.92)
      expect(result.epics[0].status).toBe('EARLY')
    })
  })

  describe('Error handling', () => {
    it('should propagate errors from API', async () => {
      mockedAxios.get.mockRejectedValueOnce(new Error('Network error'))

      await expect(getThroughput(1, '2024-01-01', '2024-01-31')).rejects.toThrow('Network error')
    })
  })
})
