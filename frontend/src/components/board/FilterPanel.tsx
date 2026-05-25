import { useMemo } from 'react'
import { MultiSelectDropdown } from '../MultiSelectDropdown'
import { SearchInput } from '../SearchInput'
import { FilterBar } from '../FilterBar'
import { FilterChip } from '../FilterChips'
import { useStatusStyles } from './StatusStylesContext'
import { SyncStatus } from './types'
import { useAuth } from '../../contexts/AuthContext'

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
  availableQuarters?: string[]
  selectedQuarters?: Set<string>
  onQuarterToggle?: (quarter: string) => void
  onClearFilters: () => void
  syncStatus: SyncStatus | null
  syncing: boolean
  onSync: () => void
  teamColorMap?: Map<string, string>
  searchMode?: 'semantic' | 'substring' | null
  searchLoading?: boolean
  hideNew?: boolean
  includeArchived?: boolean
  onHideNewToggle?: () => void
  onIncludeArchivedToggle?: () => void
  epicTitles?: string[]
}

function formatSyncTime(isoString: string | null): string {
  if (!isoString) return 'Never'
  const date = new Date(isoString)
  return date.toLocaleString(undefined, {
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
  availableQuarters,
  selectedQuarters,
  onQuarterToggle,
  syncStatus,
  syncing,
  onSync,
  teamColorMap,
  searchMode,
  searchLoading,
  hideNew,
  includeArchived,
  onHideNewToggle,
  onIncludeArchivedToggle,
  epicTitles,
}: FilterPanelProps) {
  const statusStyles = useStatusStyles()
  // Only ADMIN can trigger a sync — the backend endpoint requires ROLE_ADMIN
  // and returns 403 for everyone else. Hide the button instead of letting
  // non-admins click it and see a confusing "Request failed 403" toast.
  //
  // Wait for `loading` to settle before evaluating isAdmin() — until the auth
  // fetch resolves, `role` is empty and the helper returns false, which would
  // flash the button as hidden on every page load for admins.
  const { isAdmin, loading: authLoading } = useAuth()
  const canTriggerSync = !authLoading && isAdmin()

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
    if (selectedQuarters) {
      for (const quarter of selectedQuarters) {
        result.push({
          category: 'Quarter',
          value: quarter === '__NO_QUARTER__' ? 'No Quarter' : quarter,
          onRemove: () => onQuarterToggle?.(quarter),
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
    if (includeArchived) {
      result.push({ category: 'Filter', value: 'Showing archived', onRemove: () => onIncludeArchivedToggle?.() })
    }
    if (searchKey) {
      result.push({ category: 'Search', value: `"${searchKey}"`, onRemove: () => onSearchKeyChange('') })
    }
    return result
  }, [selectedProjects, selectedQuarters, selectedTeams, selectedStatuses, hideNew, includeArchived, searchKey, teamColorMap, statusColorMap, onProjectToggle, onQuarterToggle, onTeamToggle, onStatusToggle, onHideNewToggle, onIncludeArchivedToggle, onSearchKeyChange])

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
          {canTriggerSync && (
            <button
              className={`btn btn-primary btn-refresh ${syncing ? 'syncing' : ''}`}
              onClick={onSync}
              disabled={syncing}
            >
              {syncing ? 'Syncing...' : 'Refresh'}
            </button>
          )}
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

      {availableQuarters && availableQuarters.length > 0 && onQuarterToggle && selectedQuarters && (
        <MultiSelectDropdown
          label="Quarter"
          options={availableQuarters}
          selected={selectedQuarters}
          onToggle={onQuarterToggle}
          placeholder="All quarters"
          renderOption={(option) => option === '__NO_QUARTER__' ? 'No Quarter' : option}
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
      {onIncludeArchivedToggle && (
        <button
          className={`btn btn-sm btn-toggle ${includeArchived ? 'btn-toggle-active' : ''}`}
          onClick={onIncludeArchivedToggle}
          title="Закрытые эпики старше 14 дней"
        >
          Show archived
        </button>
      )}
    </FilterBar>
  )
}
