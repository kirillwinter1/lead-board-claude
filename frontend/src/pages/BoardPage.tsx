import { useCallback, useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { updateEpicOrder, updateStoryOrder } from '../api/epics'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { FilterPanel, BoardTable } from '../components/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { BoardSkeleton } from '../components/skeletons'
import { ViewToggle } from '../components/ViewToggle'
import { useBoardData } from '../hooks/useBoardData'
import { useBoardFilters } from '../hooks/useBoardFilters'
import { useBoardForecasts } from '../hooks/useBoardForecasts'
import { invalidateApiCache } from '../hooks/useApiCache'
import { TimelineContent } from './TimelinePage'
import './BoardPage.css'

type BoardWorkspaceView = 'board' | 'timeline'

export function BoardPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [timelineRefreshToken, setTimelineRefreshToken] = useState(0)
  const [includeArchived, setIncludeArchived] = useState(false)

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
  } = useBoardData(includeArchived)

  const {
    searchKey,
    setSearchKey,
    availableStatuses,
    selectedStatuses,
    availableTeams,
    teamColorMap,
    selectedTeams,
    availableProjects,
    selectedProjects,
    availableQuarters,
    selectedQuarters,
    selectedTeamId,
    filteredBoard,
    canReorder,
    allTeamIds,
    searchMode,
    searchLoading,
    hideNew,
    setHideNew,
    handleStatusToggle,
    handleTeamToggle,
    handleProjectToggle,
    handleQuarterToggle,
    clearFilters,
  } = useBoardFilters(board, () => setIncludeArchived(false))

  const {
    forecastMap,
    storyPlanningMap,
    loadForecasts,
  } = useBoardForecasts(allTeamIds)

  const handleSync = useCallback(() => {
    triggerSync(() => {
      loadForecasts()
      setTimelineRefreshToken(prev => prev + 1)
    })
  }, [triggerSync, loadForecasts])

  // Reordering epics/stories on the board changes planning order; drop the Timeline's
  // cached plan and bump its refresh token so it re-fetches the new order (no stale view).
  const refreshTimeline = useCallback(() => {
    if (selectedTeamId != null) invalidateApiCache(`timeline-${selectedTeamId}`)
    setTimelineRefreshToken(prev => prev + 1)
  }, [selectedTeamId])

  const handleReorder = useCallback(async (epicKey: string, targetIndex: number) => {
    const newPosition = targetIndex + 1

    setBoard(prevBoard => {
      const epicToMove = prevBoard.find(e => e.issueKey === epicKey)
      if (!epicToMove) return prevBoard
      const teamId = epicToMove.teamId

      // targetIndex is relative to ACTIVE epics only (done epics form a read-only band
      // at the top and never participate in reordering).
      const teamItems = prevBoard.filter(e => e.teamId === teamId)
      const doneItems = teamItems.filter(e => e.epicDone)
      const activeItems = teamItems.filter(e => !e.epicDone)

      const oldIndex = activeItems.findIndex(e => e.issueKey === epicKey)
      if (oldIndex === -1 || oldIndex === targetIndex) return prevBoard

      const [movedItem] = activeItems.splice(oldIndex, 1)
      activeItems.splice(targetIndex, 0, movedItem)

      const reorderedActive = activeItems.map((item, idx) => ({
        ...item,
        manualOrder: idx + 1,
      }))

      // Rebuild team order: done band (unchanged) on top, reordered active below.
      const newTeamOrder = [...doneItems, ...reorderedActive]
      let teamIdx = 0
      return prevBoard.map(item => {
        if (item.teamId === teamId) {
          return newTeamOrder[teamIdx++]
        }
        return item
      })
    })

    try {
      await updateEpicOrder(epicKey, newPosition)
      loadForecasts()
      refreshTimeline()
    } catch (err) {
      console.error('Failed to reorder epic:', err)
      await fetchBoard()
    }
  }, [setBoard, fetchBoard, loadForecasts, refreshTimeline])

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
          manualOrder: idx + 1,
        }))

        return { ...epic, children: updatedChildren }
      })
    })

    try {
      await updateStoryOrder(storyKey, newPosition)
      loadForecasts()
      refreshTimeline()
    } catch (err) {
      console.error('Failed to reorder story:', err)
      await fetchBoard()
    }
  }, [setBoard, fetchBoard, loadForecasts, refreshTimeline])

  const view: BoardWorkspaceView = searchParams.get('view') === 'timeline' ? 'timeline' : 'board'
  const updateView = (nextView: BoardWorkspaceView) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (nextView === 'timeline') {
        next.set('view', 'timeline')
      } else {
        next.delete('view')
      }
      return next
    }, { replace: true })
  }

  const filteredEpicKeys = useMemo(() => new Set(filteredBoard.map(epic => epic.issueKey)), [filteredBoard])
  const epicTitles = useMemo(() => board.map(e => e.title), [board])

  return (
    <StatusStylesProvider value={statusStyles}>
      <main className="main-content">
        <div style={{ padding: '12px 16px 0', display: 'flex', alignItems: 'center', gap: 12 }}>
          <h2 style={{ margin: 0 }}>Board</h2>
          <ViewToggle
            value={view}
            onChange={value => updateView(value as BoardWorkspaceView)}
            options={[
              { value: 'board', label: 'Board' },
              { value: 'timeline', label: 'Timeline' },
            ]}
          />
        </div>

        <div style={{ padding: '0 16px' }}>
          <FilterPanel
            searchKey={searchKey}
            onSearchKeyChange={setSearchKey}
            availableStatuses={availableStatuses}
            selectedStatuses={selectedStatuses}
            onStatusToggle={handleStatusToggle}
            availableTeams={availableTeams}
            selectedTeams={selectedTeams}
            onTeamToggle={handleTeamToggle}
            availableProjects={availableProjects}
            selectedProjects={selectedProjects}
            onProjectToggle={handleProjectToggle}
            availableQuarters={availableQuarters}
            selectedQuarters={selectedQuarters}
            onQuarterToggle={handleQuarterToggle}
            onClearFilters={clearFilters}
            syncStatus={syncStatus}
            syncing={syncing}
            onSync={handleSync}
            teamColorMap={teamColorMap}
            searchMode={searchMode}
            searchLoading={searchLoading}
            hideNew={hideNew}
            includeArchived={includeArchived}
            onHideNewToggle={() => setHideNew(v => !v)}
            onIncludeArchivedToggle={() => setIncludeArchived(v => !v)}
            epicTitles={epicTitles}
          />
        </div>

        <div style={{ padding: '0 16px' }}>
          {view === 'board' && (
            <>
              {loading && <BoardSkeleton />}
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
            </>
          )}

          {view === 'timeline' && (
            <TimelineContent
              selectedTeamId={selectedTeamId}
              filteredEpicKeys={filteredEpicKeys}
              refreshToken={timelineRefreshToken}
              showFilterBar={false}
            />
          )}
        </div>
      </main>
    </StatusStylesProvider>
  )
}
