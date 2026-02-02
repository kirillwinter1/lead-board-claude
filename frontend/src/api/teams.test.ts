import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'
import { teamsApi } from './teams'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

describe('Teams API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getConfig', () => {
    it('should fetch teams config', async () => {
      const mockConfig = {
        manualTeamManagement: true,
        organizationId: 'org-123',
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockConfig })

      const result = await teamsApi.getConfig()

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/teams/config')
      expect(result).toEqual(mockConfig)
    })
  })

  describe('getSyncStatus', () => {
    it('should fetch sync status', async () => {
      const mockStatus = {
        syncInProgress: false,
        lastSyncTime: '2024-01-15T10:00:00Z',
        error: null,
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockStatus })

      const result = await teamsApi.getSyncStatus()

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/teams/sync/status')
      expect(result).toEqual(mockStatus)
    })
  })

  describe('triggerSync', () => {
    it('should trigger team sync', async () => {
      const mockStatus = {
        syncInProgress: true,
        lastSyncTime: null,
        error: null,
      }
      mockedAxios.post.mockResolvedValueOnce({ data: mockStatus })

      const result = await teamsApi.triggerSync()

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/teams/sync/trigger')
      expect(result.syncInProgress).toBe(true)
    })
  })

  describe('getAll', () => {
    it('should fetch all teams', async () => {
      const mockTeams = [
        { id: 1, name: 'Team A', jiraTeamValue: 'team-a', active: true, memberCount: 5 },
        { id: 2, name: 'Team B', jiraTeamValue: 'team-b', active: true, memberCount: 3 },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockTeams })

      const result = await teamsApi.getAll()

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/teams')
      expect(result).toHaveLength(2)
      expect(result[0].name).toBe('Team A')
    })
  })

  describe('getById', () => {
    it('should fetch team by id', async () => {
      const mockTeam = { id: 1, name: 'Team A', jiraTeamValue: 'team-a', active: true, memberCount: 5 }
      mockedAxios.get.mockResolvedValueOnce({ data: mockTeam })

      const result = await teamsApi.getById(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/teams/1')
      expect(result.name).toBe('Team A')
    })
  })

  describe('create', () => {
    it('should create a new team', async () => {
      const newTeam = { name: 'New Team', jiraTeamValue: 'new-team' }
      const mockResponse = { id: 3, ...newTeam, active: true, memberCount: 0 }
      mockedAxios.post.mockResolvedValueOnce({ data: mockResponse })

      const result = await teamsApi.create(newTeam)

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/teams', newTeam)
      expect(result.id).toBe(3)
      expect(result.name).toBe('New Team')
    })

    it('should create team without jiraTeamValue', async () => {
      const newTeam = { name: 'Simple Team' }
      const mockResponse = { id: 4, ...newTeam, jiraTeamValue: null, active: true, memberCount: 0 }
      mockedAxios.post.mockResolvedValueOnce({ data: mockResponse })

      const result = await teamsApi.create(newTeam)

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/teams', newTeam)
      expect(result.jiraTeamValue).toBeNull()
    })
  })

  describe('update', () => {
    it('should update team', async () => {
      const updateData = { name: 'Updated Team' }
      const mockResponse = { id: 1, name: 'Updated Team', jiraTeamValue: 'team-a', active: true }
      mockedAxios.put.mockResolvedValueOnce({ data: mockResponse })

      const result = await teamsApi.update(1, updateData)

      expect(mockedAxios.put).toHaveBeenCalledWith('/api/teams/1', updateData)
      expect(result.name).toBe('Updated Team')
    })
  })

  describe('delete', () => {
    it('should delete team', async () => {
      mockedAxios.delete.mockResolvedValueOnce({})

      await teamsApi.delete(1)

      expect(mockedAxios.delete).toHaveBeenCalledWith('/api/teams/1')
    })
  })

  describe('getMembers', () => {
    it('should fetch team members', async () => {
      const mockMembers = [
        { id: 1, teamId: 1, jiraAccountId: 'acc-1', displayName: 'John', role: 'DEV', grade: 'SENIOR' },
        { id: 2, teamId: 1, jiraAccountId: 'acc-2', displayName: 'Jane', role: 'QA', grade: 'MIDDLE' },
      ]
      mockedAxios.get.mockResolvedValueOnce({ data: mockMembers })

      const result = await teamsApi.getMembers(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/teams/1/members')
      expect(result).toHaveLength(2)
      expect(result[0].role).toBe('DEV')
    })
  })

  describe('addMember', () => {
    it('should add team member', async () => {
      const newMember = {
        jiraAccountId: 'acc-3',
        displayName: 'Bob',
        role: 'SA' as const,
        grade: 'JUNIOR' as const,
        hoursPerDay: 6,
      }
      const mockResponse = { id: 3, teamId: 1, ...newMember, active: true }
      mockedAxios.post.mockResolvedValueOnce({ data: mockResponse })

      const result = await teamsApi.addMember(1, newMember)

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/teams/1/members', newMember)
      expect(result.displayName).toBe('Bob')
    })
  })

  describe('updateMember', () => {
    it('should update team member', async () => {
      const updateData = { grade: 'SENIOR' as const, hoursPerDay: 8 }
      const mockResponse = { id: 1, teamId: 1, jiraAccountId: 'acc-1', grade: 'SENIOR', hoursPerDay: 8 }
      mockedAxios.put.mockResolvedValueOnce({ data: mockResponse })

      const result = await teamsApi.updateMember(1, 1, updateData)

      expect(mockedAxios.put).toHaveBeenCalledWith('/api/teams/1/members/1', updateData)
      expect(result.grade).toBe('SENIOR')
    })
  })

  describe('deactivateMember', () => {
    it('should deactivate team member', async () => {
      mockedAxios.post.mockResolvedValueOnce({})

      await teamsApi.deactivateMember(1, 2)

      expect(mockedAxios.post).toHaveBeenCalledWith('/api/teams/1/members/2/deactivate')
    })
  })

  describe('getPlanningConfig', () => {
    it('should fetch planning config', async () => {
      const mockConfig = {
        gradeCoefficients: { senior: 0.8, middle: 1.0, junior: 1.5 },
        riskBuffer: 1.2,
        wipLimits: { team: 10, sa: 3, dev: 5, qa: 2 },
        storyDuration: { sa: 2, dev: 5, qa: 2 },
      }
      mockedAxios.get.mockResolvedValueOnce({ data: mockConfig })

      const result = await teamsApi.getPlanningConfig(1)

      expect(mockedAxios.get).toHaveBeenCalledWith('/api/teams/1/planning-config')
      expect(result.gradeCoefficients.senior).toBe(0.8)
    })
  })

  describe('updatePlanningConfig', () => {
    it('should update planning config', async () => {
      const newConfig = {
        gradeCoefficients: { senior: 0.7, middle: 1.0, junior: 1.6 },
        riskBuffer: 1.3,
        wipLimits: { team: 12, sa: 4, dev: 6, qa: 2 },
        storyDuration: { sa: 3, dev: 6, qa: 3 },
      }
      mockedAxios.put.mockResolvedValueOnce({ data: newConfig })

      const result = await teamsApi.updatePlanningConfig(1, newConfig)

      expect(mockedAxios.put).toHaveBeenCalledWith('/api/teams/1/planning-config', newConfig)
      expect(result.riskBuffer).toBe(1.3)
    })
  })
})
