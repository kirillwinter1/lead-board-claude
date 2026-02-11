import { useState, useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { BoardNode } from '../components/board/types'

export function useBoardFilters(board: BoardNode[]) {
  const [searchParams, setSearchParams] = useSearchParams()
  const [searchKey, setSearchKey] = useState('')
  const [selectedStatuses, setSelectedStatuses] = useState<Set<string>>(new Set())
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(new Set())
  const [urlTeamInitialized, setUrlTeamInitialized] = useState(false)

  // Initialize team filter from URL after board loads
  useEffect(() => {
    if (board.length === 0 || urlTeamInitialized) return

    const urlTeamId = searchParams.get('teamId')
    if (urlTeamId) {
      const teamIdNum = Number(urlTeamId)
      const epic = board.find(e => e.teamId === teamIdNum)
      if (epic?.teamName) {
        setSelectedTeams(new Set([epic.teamName]))
      }
    }
    setUrlTeamInitialized(true)
  }, [board, searchParams, urlTeamInitialized])

  // Sync selectedTeams to URL (when exactly one team selected)
  useEffect(() => {
    if (!urlTeamInitialized) return

    if (selectedTeams.size === 1) {
      const teamName = Array.from(selectedTeams)[0]
      const epic = board.find(e => e.teamName === teamName)
      if (epic?.teamId) {
        const currentTeamId = searchParams.get('teamId')
        if (currentTeamId !== String(epic.teamId)) {
          setSearchParams({ teamId: String(epic.teamId) })
        }
      }
    } else {
      if (searchParams.get('teamId')) {
        setSearchParams({})
      }
    }
  }, [selectedTeams, board, urlTeamInitialized, searchParams, setSearchParams])

  // Get all unique team IDs from board
  const allTeamIds = useMemo(() => {
    const ids = new Set<number>()
    board.forEach(epic => {
      if (epic.teamId) ids.add(epic.teamId)
    })
    return Array.from(ids)
  }, [board])

  const availableStatuses = useMemo(() => {
    const statuses = new Set<string>()
    board.forEach(epic => statuses.add(epic.status))
    return Array.from(statuses).sort()
  }, [board])

  const availableTeams = useMemo(() => {
    const teams = new Set<string>()
    board.forEach(epic => {
      if (epic.teamName) {
        teams.add(epic.teamName)
      }
    })
    return Array.from(teams).sort()
  }, [board])

  const filteredBoard = useMemo(() => {
    return board.filter(epic => {
      if (searchKey) {
        const keyLower = searchKey.toLowerCase()
        if (!epic.issueKey.toLowerCase().includes(keyLower)) {
          return false
        }
      }
      if (selectedStatuses.size > 0 && !selectedStatuses.has(epic.status)) {
        return false
      }
      if (selectedTeams.size > 0 && (!epic.teamName || !selectedTeams.has(epic.teamName))) {
        return false
      }
      return true
    })
  }, [board, searchKey, selectedStatuses, selectedTeams])

  // Get selected team ID for forecast loading
  const selectedTeamId = useMemo(() => {
    if (selectedTeams.size !== 1) return null
    const teamName = Array.from(selectedTeams)[0]
    const epic = board.find(e => e.teamName === teamName)
    return epic?.teamId || null
  }, [selectedTeams, board])

  // Drag & drop is enabled when exactly one team is selected
  const canReorder = selectedTeamId !== null

  const handleStatusToggle = (status: string) => {
    setSelectedStatuses(prev => {
      const next = new Set(prev)
      if (next.has(status)) {
        next.delete(status)
      } else {
        next.add(status)
      }
      return next
    })
  }

  const handleTeamToggle = (team: string) => {
    setSelectedTeams(prev => {
      const next = new Set(prev)
      if (next.has(team)) {
        next.delete(team)
      } else {
        next.add(team)
      }
      return next
    })
  }

  const clearFilters = () => {
    setSearchKey('')
    setSelectedStatuses(new Set())
    setSelectedTeams(new Set())
  }

  return {
    searchKey,
    setSearchKey,
    selectedStatuses,
    selectedTeams,
    availableStatuses,
    availableTeams,
    filteredBoard,
    canReorder,
    allTeamIds,
    handleStatusToggle,
    handleTeamToggle,
    clearFilters,
  }
}
