import { useMemo } from 'react'
import { MultiSelectDropdown } from '../MultiSelectDropdown'
import { SearchInput } from '../SearchInput'
import { FilterBar } from '../FilterBar'
import { FilterChip } from '../FilterChips'
import { useStatusStyles } from './StatusStylesContext'
import { SyncStatus } from './types'

interface FilterPanelProps {
  searchKey: string
  onSearchKeyChange: (value: string) => void
  availableStatuses: string[]
  selectedStatuses: Set<string>
  onStatusToggle: (status: string) => void
  availableTeams: string[]
  selectedTeams: Set<string>
  onTeamToggle: (team: string) => void
  availableProjects?: string[]
  selectedProjects?: Set<string>
  onProjectToggle?: (project: string) => void
  onClearFilters: () => void
  syncStatus: SyncStatus | null
  syncing: boolean
  onSync: () => void
  teamColorMap?: Map<string, string>
  searchMode?: 'semantic' | 'substring' | null
  searchLoading?: boolean
  hideNew?: boolean
  hideDone?: boolean
  onHideNewToggle?: () => void
  onHideDoneToggle?: () => void
  epicTitles?: string[]
}

function formatSyncTime(isoString: string | null): string {
  if (!isoString) return 'Never'
  const date = new Date(isoString)
  return date.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function FilterPanel({
  searchKey,
  onSearchKeyChange,
  availableStatuses,
  selectedStatuses,
  onStatusToggle,
  availableTeams,
  selectedTeams,
  onTeamToggle,
  onClearFilters,
  availableProjects,
  selectedProjects,
  onProjectToggle,
  syncStatus,
  syncing,
  onSync,
  teamColorMap,
  searchMode,
  searchLoading,
  hideNew,
  hideDone,
  onHideNewToggle,
  onHideDoneToggle,
  epicTitles,
}: FilterPanelProps) {
  const statusStyles = useStatusStyles()

  const statusColorMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const [status, style] of Object.entries(statusStyles)) {
      if (style.color) map.set(status, style.color)
    }
    return map
  }, [statusStyles])

  const chips = useMemo(() => {
    const result: FilterChip[] = []
    if (selectedProjects) {
      for (const project of selectedProjects) {
        result.push({
          category: 'Project',
          value: project,
          onRemove: () => onProjectToggle?.(project),
        })
      }
    }
    for (const team of selectedTeams) {
      result.push({
        category: 'Team',
        value: team,
        color: teamColorMap?.get(team),
        onRemove: () => onTeamToggle(team),
      })
    }
    for (const status of selectedStatuses) {
      result.push({
        category: 'Status',
        value: status,
        color: statusColorMap.get(status),
        onRemove: () => onStatusToggle(status),
      })
    }
    if (hideNew) {
      result.push({ category: 'Filter', value: 'Hide NEW', onRemove: () => onHideNewToggle?.() })
    }
    if (hideDone) {
      result.push({ category: 'Filter', value: 'Hide DONE', onRemove: () => onHideDoneToggle?.() })
    }
    if (searchKey) {
      result.push({ category: 'Search', value: `"${searchKey}"`, onRemove: () => onSearchKeyChange('') })
    }
    return result
  }, [selectedProjects, selectedTeams, selectedStatuses, hideNew, hideDone, searchKey, teamColorMap, statusColorMap, onProjectToggle, onTeamToggle, onStatusToggle, onHideNewToggle, onHideDoneToggle, onSearchKeyChange])

  const searchBadge = searchMode
    ? { label: searchMode === 'semantic' ? 'AI' : 'TXT', variant: (searchMode === 'semantic' ? 'ai' : 'text') as 'ai' | 'text' }
    : undefined

  const searchHints = useMemo(() => {
    if (!epicTitles || epicTitles.length === 0) return undefined
    // Pick up to 4 short epic titles as search hints
    return epicTitles
      .filter(t => t.length >= 5 && t.length <= 50)
      .slice(0, 4)
  }, [epicTitles])

  return (
    <FilterBar
      chips={chips}
      onClearAll={onClearFilters}
      trailing={
        <div className="sync-status" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {syncStatus && (
            <span className="sync-info">
              Last sync: {formatSyncTime(syncStatus.lastSyncCompletedAt)}
            </span>
          )}
          <button
            className={`btn btn-primary btn-refresh ${syncing ? 'syncing' : ''}`}
            onClick={onSync}
            disabled={syncing}
          >
            {syncing ? 'Syncing...' : 'Refresh'}
          </button>
        </div>
      }
    >
      <SearchInput
        value={searchKey}
        onChange={onSearchKeyChange}
        placeholder="AI search: key, text, or meaning..."
        loading={searchLoading}
        badge={searchBadge}
        hints={searchHints}
      />

      {availableProjects && availableProjects.length > 1 && onProjectToggle && selectedProjects && (
        <MultiSelectDropdown
          label="Project"
          options={availableProjects}
          selected={selectedProjects}
          onToggle={onProjectToggle}
          placeholder="All projects"
        />
      )}

      <MultiSelectDropdown
        label="Team"
        options={availableTeams}
        selected={selectedTeams}
        onToggle={onTeamToggle}
        placeholder="All teams"
        colorMap={teamColorMap}
      />

      <MultiSelectDropdown
        label="Status"
        options={availableStatuses}
        selected={selectedStatuses}
        onToggle={onStatusToggle}
        placeholder="All statuses"
        colorMap={statusColorMap}
      />

      {onHideNewToggle && (
        <button
          className={`btn btn-sm btn-toggle ${hideNew ? 'btn-toggle-active' : ''}`}
          onClick={onHideNewToggle}
        >
          Hide NEW
        </button>
      )}
      {onHideDoneToggle && (
        <button
          className={`btn btn-sm btn-toggle ${hideDone ? 'btn-toggle-active' : ''}`}
          onClick={onHideDoneToggle}
        >
          Hide DONE
        </button>
      )}
    </FilterBar>
  )
}
