import { useCallback, useEffect, useState } from 'react'
import { updateEpicOrder, updateStoryOrder } from '../api/epics'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { FilterPanel, BoardTable } from '../components/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { useBoardData } from '../hooks/useBoardData'
import { useBoardFilters } from '../hooks/useBoardFilters'
import { useBoardForecasts } from '../hooks/useBoardForecasts'
import './BoardPage.css'

export function BoardPage() {
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})

  useEffect(() => {
    getStatusStyles().then(setStatusStyles).catch(() => {})
  }, [])

  const {
    board,
    setBoard,
    loading,
    error,
    syncStatus,
    syncing,
    roughEstimateConfig,
    fetchBoard,
    triggerSync,
    handleRoughEstimateUpdate,
  } = useBoardData()

  const {
    searchKey,
    setSearchKey,
    availableStatuses,
    selectedStatuses,
    availableTeams,
    selectedTeams,
    filteredBoard,
    canReorder,
    allTeamIds,
    handleStatusToggle,
    handleTeamToggle,
    clearFilters,
  } = useBoardFilters(board)

  const {
    forecastMap,
    storyPlanningMap,
    loadForecasts,
  } = useBoardForecasts(allTeamIds)

  const handleSync = useCallback(() => {
    triggerSync(loadForecasts)
  }, [triggerSync, loadForecasts])

  // Handle reorder via drag & drop - simple position-based API
  // Pattern: Optimistic UI + Backend Reconciliation
  const handleReorder = useCallback(async (epicKey: string, targetIndex: number) => {
    const newPosition = targetIndex + 1

    // Optimistic update: reorder within the selected team only
    setBoard(prevBoard => {
      const epicToMove = prevBoard.find(e => e.issueKey === epicKey)
      if (!epicToMove) return prevBoard
      const teamId = epicToMove.teamId

      const teamItems = prevBoard.filter(e => e.teamId === teamId)
      const oldIndex = teamItems.findIndex(e => e.issueKey === epicKey)
      if (oldIndex === -1 || oldIndex === targetIndex) return prevBoard

      const [movedItem] = teamItems.splice(oldIndex, 1)
      teamItems.splice(targetIndex, 0, movedItem)

      const reorderedTeam = teamItems.map((item, idx) => ({
        ...item,
        manualOrder: idx + 1
      }))

      let teamIdx = 0
      return prevBoard.map(item => {
        if (item.teamId === teamId) {
          return reorderedTeam[teamIdx++]
        }
        return item
      })
    })

    try {
      await updateEpicOrder(epicKey, newPosition)
      loadForecasts()
    } catch (err) {
      console.error('Failed to reorder epic:', err)
      await fetchBoard()
    }
  }, [setBoard, fetchBoard, loadForecasts])

  // Handle story reorder via drag & drop - simple position-based API
  // Pattern: Optimistic UI + Backend Reconciliation
  const handleStoryReorder = useCallback(async (storyKey: string, parentEpicKey: string, newIndex: number) => {
    const newPosition = newIndex + 1

    setBoard(prevBoard => {
      return prevBoard.map(epic => {
        if (epic.issueKey !== parentEpicKey) return epic

        const children = [...epic.children]
        const oldIndex = children.findIndex(s => s.issueKey === storyKey)
        if (oldIndex === -1 || oldIndex === newIndex) return epic

        const [movedItem] = children.splice(oldIndex, 1)
        children.splice(newIndex, 0, movedItem)

        const updatedChildren = children.map((child, idx) => ({
          ...child,
          manualOrder: idx + 1
        }))

        return { ...epic, children: updatedChildren }
      })
    })

    try {
      await updateStoryOrder(storyKey, newPosition)
      loadForecasts()
    } catch (err) {
      console.error('Failed to reorder story:', err)
      await fetchBoard()
    }
  }, [setBoard, fetchBoard, loadForecasts])

  return (
    <StatusStylesProvider value={statusStyles}>
      <FilterPanel
        searchKey={searchKey}
        onSearchKeyChange={setSearchKey}
        availableStatuses={availableStatuses}
        selectedStatuses={selectedStatuses}
        onStatusToggle={handleStatusToggle}
        availableTeams={availableTeams}
        selectedTeams={selectedTeams}
        onTeamToggle={handleTeamToggle}
        onClearFilters={clearFilters}
        syncStatus={syncStatus}
        syncing={syncing}
        onSync={handleSync}
      />

      <main className="main-content">
        {loading && <div className="loading">Loading board...</div>}
        {error && <div className="error">Error: {error}</div>}
        {!loading && !error && filteredBoard.length === 0 && (
          <div className="empty">No epics found</div>
        )}
        {!loading && !error && filteredBoard.length > 0 && (
          <BoardTable
            items={filteredBoard}
            roughEstimateConfig={roughEstimateConfig}
            onRoughEstimateUpdate={handleRoughEstimateUpdate}
            forecastMap={forecastMap}
            storyPlanningMap={storyPlanningMap}
            canReorder={canReorder}
            onReorder={handleReorder}
            onStoryReorder={handleStoryReorder}
          />
        )}
      </main>
    </StatusStylesProvider>
  )
}
