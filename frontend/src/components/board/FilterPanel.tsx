import { MultiSelectDropdown } from '../MultiSelectDropdown'
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
  onClearFilters: () => void
  syncStatus: SyncStatus | null
  syncing: boolean
  onSync: () => void
  teamColorMap?: Map<string, string>
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
  syncStatus,
  syncing,
  onSync,
  teamColorMap,
}: FilterPanelProps) {
  const hasActiveFilters = searchKey || selectedStatuses.size > 0 || selectedTeams.size > 0

  return (
    <div className="filter-panel">
      <div className="filter-group filter-search">
        <svg
          className="search-icon"
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="none"
        >
          <path
            d="M7 12C9.76142 12 12 9.76142 12 7C12 4.23858 9.76142 2 7 2C4.23858 2 2 4.23858 2 7C2 9.76142 4.23858 12 7 12Z"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <path
            d="M14 14L10.5 10.5"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        <input
          type="text"
          placeholder="Search by key..."
          value={searchKey}
          onChange={(e) => onSearchKeyChange(e.target.value)}
          className="filter-input"
        />
      </div>

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
      />

      {hasActiveFilters && (
        <button className="btn btn-secondary btn-clear" onClick={onClearFilters}>
          Clear
        </button>
      )}

      <div className="sync-status filter-group-right">
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
    </div>
  )
}
