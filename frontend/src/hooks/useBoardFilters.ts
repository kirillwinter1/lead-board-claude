import { useState, useEffect, useMemo, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import type { BoardNode } from '../components/board/types'
import { searchBoard, type BoardSearchResult } from '../api/board'

export function useBoardFilters(board: BoardNode[]) {
  const [searchParams, setSearchParams] = useSearchParams()
  const [searchKey, setSearchKey] = useState('')
  const [selectedStatuses, setSelectedStatuses] = useState<Set<string>>(new Set())
  const [selectedTeams, setSelectedTeams] = useState<Set<string>>(new Set())
  const [urlTeamInitialized, setUrlTeamInitialized] = useState(false)
  const [searchResult, setSearchResult] = useState<BoardSearchResult | null>(null)
  const [searchLoading, setSearchLoading] = useState(false)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

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

  // Debounced semantic search for queries >= 3 chars
  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current)
    }

    if (searchKey.length < 3) {
      setSearchResult(null)
      setSearchLoading(false)
      return
    }

    setSearchLoading(true)
    debounceRef.current = setTimeout(() => {
      searchBoard(searchKey, allTeamIds.length > 0 ? allTeamIds : undefined)
        .then(result => {
          setSearchResult(result)
          setSearchLoading(false)
        })
        .catch(() => {
          setSearchResult(null)
          setSearchLoading(false)
        })
    }, 300)

    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current)
      }
    }
  }, [searchKey, allTeamIds])

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

  const teamColorMap = useMemo(() => {
    const map = new Map<string, string>()
    board.forEach(epic => {
      if (epic.teamName && epic.teamColor && !map.has(epic.teamName)) {
        map.set(epic.teamName, epic.teamColor)
      }
    })
    return map
  }, [board])

  const filteredBoard = useMemo(() => {
    return board.filter(epic => {
      if (searchKey) {
        if (searchKey.length >= 3 && searchResult) {
          // Server-side search result: filter by matched epic keys
          if (!searchResult.matchedEpicKeys.includes(epic.issueKey)) {
            return false
          }
        } else {
          // Short query: local search by key
          const keyLower = searchKey.toLowerCase()
          if (!epic.issueKey.toLowerCase().includes(keyLower)) {
            return false
          }
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
  }, [board, searchKey, searchResult, selectedStatuses, selectedTeams])

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
    setSearchResult(null)
    setSelectedStatuses(new Set())
    setSelectedTeams(new Set())
  }

  const searchMode = searchResult?.searchMode ?? null

  return {
    searchKey,
    setSearchKey,
    selectedStatuses,
    selectedTeams,
    availableStatuses,
    availableTeams,
    teamColorMap,
    filteredBoard,
    canReorder,
    allTeamIds,
    searchMode,
    searchLoading,
    handleStatusToggle,
    handleTeamToggle,
    clearFilters,
  }
}
