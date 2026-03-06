import { useEffect, useState, useMemo, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { projectsApi, ProjectDto, ProjectDetailDto, ProjectTimelineDto, ProjectRecommendation } from '../api/projects'
import { getStatusStyles, StatusStyle, searchBoard, BoardSearchResult } from '../api/board'
import { getConfig } from '../api/config'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { StatusBadge } from '../components/board/StatusBadge'
import { MultiSelectDropdown } from '../components/MultiSelectDropdown'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { SearchInput } from '../components/SearchInput'
import { FilterBar } from '../components/FilterBar'
import { FilterChip } from '../components/FilterChips'
import { TeamBadge } from '../components/TeamBadge'
import { ViewToggle } from '../components/ViewToggle'
import { ProjectGanttView, ZoomLevel, lightenColor } from '../components/ProjectGanttView'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../components/board/helpers'
import { RiceForm } from '../components/rice/RiceForm'
import { RiceScoreBadge } from '../components/rice/RiceScoreBadge'
import { Modal } from '../components/Modal'
import './ProjectTimelinePage.css'

type ViewMode = 'list' | 'gantt'
type SortOption = 'default' | 'progress-asc' | 'progress-desc' | 'rice-desc' | 'expected-done' | 'epics-desc'

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '\u2014'
  const d = new Date(dateStr)
  return d.toLocaleDateString('en-US', { day: '2-digit', month: 'short' })
}

function formatHours(seconds: number | null): string {
  if (!seconds || seconds <= 0) return '0h'
  const hours = Math.round(seconds / 3600)
  if (hours < 8) return `${hours}h`
  const days = Math.round(hours / 8)
  return `${days}d`
}

function ProgressBar({ percent, width = 100 }: { percent: number; width?: number }) {
  const clampedPercent = Math.max(0, Math.min(percent, 100))
  const color = clampedPercent >= 100 ? '#36B37E' : '#0065FF'
  return (
    <div style={{
      width,
      height: 6,
      background: '#DFE1E6',
      borderRadius: 3,
      overflow: 'hidden',
      flexShrink: 0,
    }}>
      {clampedPercent > 0 && (
        <div style={{
          width: `${clampedPercent}%`,
          minWidth: 2,
          height: '100%',
          background: color,
          borderRadius: 3,
        }} />
      )}
    </div>
  )
}

function AlignmentBadge({ delayDays }: { delayDays: number | null }) {
  if (delayDays == null) {
    return <span style={{ color: '#97A0AF' }}>{'\u2014'}</span>
  }
  if (delayDays > 2) {
    return (
      <span title={`${delayDays}d behind average`} style={{
        display: 'inline-flex', alignItems: 'center', gap: 3,
        color: '#FF8B00', fontWeight: 600, fontSize: 12,
      }}>
        {'\u26A0'} +{delayDays}d
      </span>
    )
  }
  return (
    <span title="On track" style={{ color: '#36B37E', fontWeight: 600, fontSize: 12 }}>
      {'\u2713'}
    </span>
  )
}

function JiraLink({ issueKey, jiraBaseUrl }: { issueKey: string; jiraBaseUrl: string }) {
  if (!jiraBaseUrl) {
    return <span style={{ fontSize: 13, color: '#0052CC', fontWeight: 600 }}>{issueKey}</span>
  }
  return (
    <a
      href={`${jiraBaseUrl}${issueKey}`}
      target="_blank"
      rel="noopener noreferrer"
      onClick={e => e.stopPropagation()}
      style={{ fontSize: 13, color: '#0052CC', fontWeight: 600, textDecoration: 'none' }}
      onMouseEnter={e => (e.currentTarget.style.textDecoration = 'underline')}
      onMouseLeave={e => (e.currentTarget.style.textDecoration = 'none')}
    >
      {issueKey}
    </a>
  )
}

function AssigneeBadge({ name, avatarUrl }: { name: string; avatarUrl: string | null }) {
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 5,
      fontSize: 12,
      color: '#42526E',
      background: '#F4F5F7',
      padding: '2px 8px 2px 3px',
      borderRadius: 12,
      whiteSpace: 'nowrap',
      flexShrink: 0,
    }}>
      {avatarUrl ? (
        <img
          src={avatarUrl}
          alt={name}
          style={{ width: 20, height: 20, borderRadius: '50%', flexShrink: 0 }}
        />
      ) : (
        <span style={{
          width: 20,
          height: 20,
          borderRadius: '50%',
          background: '#0052CC',
          color: '#fff',
          fontSize: 10,
          fontWeight: 600,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}>
          {name.charAt(0).toUpperCase()}
        </span>
      )}
      {name}
    </span>
  )
}

function RecommendationsBlock({ recommendations }: { recommendations: ProjectRecommendation[] }) {
  if (recommendations.length === 0) return null
  return (
    <div style={{
      margin: '12px 0 4px',
      padding: '10px 14px',
      background: '#FFFAE6',
      border: '1px solid #FFE380',
      borderRadius: 6,
      fontSize: 13,
    }}>
      {recommendations.map((r, i) => (
        <div key={i} style={{ display: 'flex', gap: 6, marginBottom: i < recommendations.length - 1 ? 6 : 0 }}>
          <span style={{ flexShrink: 0 }}>
            {r.severity === 'WARNING' ? '\u26A0\uFE0F' : '\u2139\uFE0F'}
          </span>
          <span style={{ color: '#172B4D' }}>{r.message}</span>
        </div>
      ))}
    </div>
  )
}

export function ProjectsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { getRoleColor, getRoleCodes, getIssueTypeIconUrl } = useWorkflowConfig()

  // View mode
  const view: ViewMode = searchParams.get('view') === 'gantt' ? 'gantt' : 'list'

  // Shared data
  const [listProjects, setListProjects] = useState<ProjectDto[]>([])
  const [timelineProjects, setTimelineProjects] = useState<ProjectTimelineDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // Shared filter state from URL
  const search = searchParams.get('search') || ''
  const selectedPMs = useMemo(() => new Set(searchParams.get('pm')?.split(',').filter(Boolean) || []), [searchParams])
  const selectedStatuses = useMemo(() => new Set(searchParams.get('status')?.split(',').filter(Boolean) || []), [searchParams])
  const selectedTeams = useMemo(() => new Set(searchParams.get('team')?.split(',').filter(Boolean) || []), [searchParams])
  const sortBy = (searchParams.get('sort') as SortOption) || 'default'
  const zoom = (searchParams.get('zoom') as ZoomLevel) || 'week'

  // List-specific state (multi-expand)
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set())
  const [details, setDetails] = useState<Map<string, ProjectDetailDto>>(new Map())
  const [detailsLoading, setDetailsLoading] = useState<Set<string>>(new Set())
  const [recsMap, setRecsMap] = useState<Map<string, ProjectRecommendation[]>>(new Map())
  const [riceModalKey, setRiceModalKey] = useState<string | null>(null)

  // Gantt-specific state
  const [ganttExpanded, setGanttExpanded] = useState<Record<string, boolean>>({})

  // Smart search state
  const [searchResult, setSearchResult] = useState<BoardSearchResult | null>(null)
  const [searchLoading, setSearchLoading] = useState(false)
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const updateParam = useCallback((key: string, value: string | null) => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      if (value) next.set(key, value)
      else next.delete(key)
      return next
    }, { replace: true })
  }, [setSearchParams])

  // Load both datasets
  useEffect(() => {
    setLoading(true)
    Promise.all([
      projectsApi.list(),
      projectsApi.getTimeline(),
      getStatusStyles().catch(() => ({} as Record<string, StatusStyle>)),
      getConfig().then(c => c.jiraBaseUrl || '').catch(() => ''),
    ]).then(([list, timeline, styles, baseUrl]) => {
      setListProjects(list)
      setTimelineProjects(timeline)
      setStatusStyles(styles)
      setJiraBaseUrl(baseUrl)
      const exp: Record<string, boolean> = {}
      timeline.forEach(p => { exp[p.issueKey] = true })
      setGanttExpanded(exp)
    }).catch(() => {
      setError('Failed to load projects')
    }).finally(() => {
      setLoading(false)
    })
  }, [])

  // Debounced smart search (semantic/substring via board search API)
  useEffect(() => {
    if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current)
    if (search.length < 3) {
      setSearchResult(null)
      setSearchLoading(false)
      return
    }
    setSearchLoading(true)
    searchDebounceRef.current = setTimeout(() => {
      searchBoard(search)
        .then(result => { setSearchResult(result); setSearchLoading(false) })
        .catch(() => { setSearchResult(null); setSearchLoading(false) })
    }, 300)
    return () => { if (searchDebounceRef.current) clearTimeout(searchDebounceRef.current) }
  }, [search])

  // Team data from timeline
  const teamNames = useMemo(() => {
    const names = new Set<string>()
    for (const p of timelineProjects) {
      for (const e of p.epics) {
        if (e.teamName) names.add(e.teamName)
      }
    }
    return Array.from(names).sort()
  }, [timelineProjects])

  const teamColorMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const p of timelineProjects) {
      for (const e of p.epics) {
        if (e.teamName && e.teamColor && !map.has(e.teamName)) {
          map.set(e.teamName, e.teamColor)
        }
      }
    }
    return map
  }, [timelineProjects])

  const projectTeamsMap = useMemo(() => {
    const map = new Map<string, Set<string>>()
    for (const p of timelineProjects) {
      const teams = new Set<string>()
      for (const e of p.epics) {
        if (e.teamName) teams.add(e.teamName)
      }
      map.set(p.issueKey, teams)
    }
    return map
  }, [timelineProjects])

  // Epic key → project key map (for smart search matching)
  const epicToProjectMap = useMemo(() => {
    const map = new Map<string, Set<string>>()
    for (const p of timelineProjects) {
      for (const e of p.epics) {
        if (!map.has(e.epicKey)) map.set(e.epicKey, new Set())
        map.get(e.epicKey)!.add(p.issueKey)
      }
    }
    return map
  }, [timelineProjects])

  // Matched project keys from smart search
  const searchMatchedProjectKeys = useMemo(() => {
    if (!search) return null
    if (search.length >= 3 && searchResult) {
      // Server-side search: find projects whose epic keys are in the result
      const matched = new Set<string>()
      for (const epicKey of searchResult.matchedEpicKeys) {
        const projectKeys = epicToProjectMap.get(epicKey)
        if (projectKeys) projectKeys.forEach(k => matched.add(k))
      }
      // Also match project keys/summaries directly
      const q = search.toLowerCase()
      for (const p of listProjects) {
        if (p.issueKey.toLowerCase().includes(q) || p.summary.toLowerCase().includes(q)) {
          matched.add(p.issueKey)
        }
      }
      return matched
    }
    // Short query: local key+summary search
    const q = search.toLowerCase()
    const matched = new Set<string>()
    for (const p of listProjects) {
      if (p.issueKey.toLowerCase().includes(q) || p.summary.toLowerCase().includes(q)) {
        matched.add(p.issueKey)
      }
    }
    return matched
  }, [search, searchResult, epicToProjectMap, listProjects])

  // Available filter options
  const availablePMs = useMemo(() => Array.from(new Set(
    listProjects.map(p => p.assigneeDisplayName).filter((n): n is string => !!n)
  )).sort(), [listProjects])

  const availableStatuses = useMemo(() => Array.from(new Set(listProjects.map(p => p.status))).sort(), [listProjects])

  const statusColorMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const [status, style] of Object.entries(statusStyles)) {
      if (style.color) map.set(status, style.color)
    }
    return map
  }, [statusStyles])

  // Filter handlers
  const handlePMToggle = (pm: string) => {
    const next = new Set(selectedPMs)
    next.has(pm) ? next.delete(pm) : next.add(pm)
    updateParam('pm', next.size > 0 ? Array.from(next).join(',') : null)
  }
  const handleStatusToggle = (status: string) => {
    const next = new Set(selectedStatuses)
    next.has(status) ? next.delete(status) : next.add(status)
    updateParam('status', next.size > 0 ? Array.from(next).join(',') : null)
  }
  const handleTeamToggle = (team: string) => {
    const next = new Set(selectedTeams)
    next.has(team) ? next.delete(team) : next.add(team)
    updateParam('team', next.size > 0 ? Array.from(next).join(',') : null)
  }
  const clearFilters = () => {
    setSearchParams(prev => {
      const next = new URLSearchParams(prev)
      next.delete('search')
      next.delete('pm')
      next.delete('status')
      next.delete('team')
      return next
    }, { replace: true })
  }

  // Filtered list projects
  const filteredListProjects = useMemo(() => {
    return listProjects.filter(p => {
      if (selectedPMs.size > 0 && (!p.assigneeDisplayName || !selectedPMs.has(p.assigneeDisplayName))) return false
      if (selectedStatuses.size > 0 && !selectedStatuses.has(p.status)) return false
      if (selectedTeams.size > 0) {
        const teams = projectTeamsMap.get(p.issueKey)
        if (!teams || !Array.from(selectedTeams).some(t => teams.has(t))) return false
      }
      if (searchMatchedProjectKeys) {
        if (!searchMatchedProjectKeys.has(p.issueKey)) return false
      }
      return true
    })
  }, [listProjects, selectedPMs, selectedStatuses, selectedTeams, searchMatchedProjectKeys, projectTeamsMap])

  // Sort list
  const sortedListProjects = useMemo(() => {
    if (sortBy === 'default') return filteredListProjects
    const sorted = [...filteredListProjects]
    switch (sortBy) {
      case 'progress-asc':
        sorted.sort((a, b) => a.progressPercent - b.progressPercent)
        break
      case 'progress-desc':
        sorted.sort((a, b) => b.progressPercent - a.progressPercent)
        break
      case 'rice-desc':
        sorted.sort((a, b) => (b.riceNormalizedScore ?? -1) - (a.riceNormalizedScore ?? -1))
        break
      case 'expected-done':
        sorted.sort((a, b) => {
          if (!a.expectedDone && !b.expectedDone) return 0
          if (!a.expectedDone) return 1
          if (!b.expectedDone) return -1
          return a.expectedDone.localeCompare(b.expectedDone)
        })
        break
      case 'epics-desc':
        sorted.sort((a, b) => b.childEpicCount - a.childEpicCount)
        break
    }
    return sorted
  }, [filteredListProjects, sortBy])

  // Filtered timeline projects
  const filteredTimelineProjects = useMemo(() => {
    let result = timelineProjects
    if (selectedPMs.size > 0) {
      result = result.filter(p => p.assigneeDisplayName && selectedPMs.has(p.assigneeDisplayName))
    }
    if (selectedStatuses.size > 0) {
      result = result.filter(p => selectedStatuses.has(p.status))
    }
    if (searchMatchedProjectKeys) {
      result = result.filter(p => searchMatchedProjectKeys.has(p.issueKey))
    }
    if (selectedTeams.size > 0) {
      result = result
        .map(p => {
          const matchingEpics = p.epics.filter(e => e.teamName != null && selectedTeams.has(e.teamName))
          if (matchingEpics.length === 0) return null
          return { ...p, epics: matchingEpics }
        })
        .filter((p): p is ProjectTimelineDto => p !== null)
    }
    return result
  }, [timelineProjects, selectedPMs, selectedStatuses, selectedTeams, searchMatchedProjectKeys])

  // List expand handler (multi-expand)
  const handleToggle = async (issueKey: string) => {
    if (expandedKeys.has(issueKey)) {
      setExpandedKeys(prev => { const next = new Set(prev); next.delete(issueKey); return next })
      return
    }
    setExpandedKeys(prev => new Set(prev).add(issueKey))
    if (!details.has(issueKey)) {
      setDetailsLoading(prev => new Set(prev).add(issueKey))
      try {
        const [data, recs] = await Promise.all([
          projectsApi.getDetail(issueKey),
          projectsApi.getRecommendations(issueKey).catch(() => [] as ProjectRecommendation[]),
        ])
        setDetails(prev => new Map(prev).set(issueKey, data))
        setRecsMap(prev => new Map(prev).set(issueKey, recs))
      } catch {
        // leave empty
      } finally {
        setDetailsLoading(prev => { const next = new Set(prev); next.delete(issueKey); return next })
      }
    }
  }

  // Gantt toggle all
  const toggleAllGantt = () => {
    const allExpanded = filteredTimelineProjects.every(p => ganttExpanded[p.issueKey])
    const next = { ...ganttExpanded }
    filteredTimelineProjects.forEach(p => { next[p.issueKey] = !allExpanded })
    setGanttExpanded(next)
  }

  // List toggle all
  const toggleAllList = async () => {
    const allExpanded = sortedListProjects.every(p => expandedKeys.has(p.issueKey))
    if (allExpanded) {
      setExpandedKeys(new Set())
    } else {
      const keys = new Set(sortedListProjects.map(p => p.issueKey))
      setExpandedKeys(keys)
      // Load details for projects that haven't been loaded yet
      const toLoad = sortedListProjects.filter(p => !details.has(p.issueKey))
      if (toLoad.length > 0) {
        setDetailsLoading(prev => {
          const next = new Set(prev)
          toLoad.forEach(p => next.add(p.issueKey))
          return next
        })
        await Promise.all(toLoad.map(async p => {
          try {
            const [data, recs] = await Promise.all([
              projectsApi.getDetail(p.issueKey),
              projectsApi.getRecommendations(p.issueKey).catch(() => [] as ProjectRecommendation[]),
            ])
            setDetails(prev => new Map(prev).set(p.issueKey, data))
            setRecsMap(prev => new Map(prev).set(p.issueKey, recs))
          } catch {
            // skip
          } finally {
            setDetailsLoading(prev => { const next = new Set(prev); next.delete(p.issueKey); return next })
          }
        }))
      }
    }
  }

  // Filter chips
  const chips = useMemo(() => {
    const result: FilterChip[] = []
    for (const pm of selectedPMs) {
      result.push({ category: 'PM', value: pm, onRemove: () => handlePMToggle(pm) })
    }
    for (const status of selectedStatuses) {
      result.push({ category: 'Status', value: status, color: statusColorMap.get(status), onRemove: () => handleStatusToggle(status) })
    }
    for (const team of selectedTeams) {
      result.push({ category: 'Team', value: team, color: teamColorMap.get(team), onRemove: () => handleTeamToggle(team) })
    }
    if (search) {
      result.push({ category: 'Search', value: `"${search}"`, onRemove: () => updateParam('search', null) })
    }
    return result
  }, [selectedPMs, selectedStatuses, selectedTeams, search, statusColorMap, teamColorMap])

  // Sorted timeline projects (same sort logic as list)
  const sortedTimelineProjects = useMemo(() => {
    if (sortBy === 'default') return filteredTimelineProjects
    const sorted = [...filteredTimelineProjects]
    switch (sortBy) {
      case 'progress-asc':
        sorted.sort((a, b) => a.progressPercent - b.progressPercent)
        break
      case 'progress-desc':
        sorted.sort((a, b) => b.progressPercent - a.progressPercent)
        break
      case 'rice-desc':
        sorted.sort((a, b) => (b.riceNormalizedScore ?? -1) - (a.riceNormalizedScore ?? -1))
        break
      case 'expected-done':
        // no-op for timeline (no expectedDone field)
        break
      case 'epics-desc':
        sorted.sort((a, b) => b.epics.length - a.epics.length)
        break
    }
    return sorted
  }, [filteredTimelineProjects, sortBy])

  // Toggle all handler for current view
  const handleToggleAll = view === 'gantt' ? toggleAllGantt : toggleAllList
  const allExpandedInCurrentView = view === 'gantt'
    ? filteredTimelineProjects.length > 0 && filteredTimelineProjects.every(p => ganttExpanded[p.issueKey])
    : sortedListProjects.length > 0 && sortedListProjects.every(p => expandedKeys.has(p.issueKey))

  const searchBadge = searchResult
    ? { label: searchResult.searchMode === 'semantic' ? 'AI' : 'TXT', variant: (searchResult.searchMode === 'semantic' ? 'ai' : 'text') as 'ai' | 'text' }
    : undefined

  const searchHints = useMemo(() => {
    return listProjects
      .map(p => p.summary)
      .filter(t => t.length >= 5 && t.length <= 50)
      .slice(0, 4)
  }, [listProjects])

  const roleCodes = getRoleCodes()

  if (loading) {
    return (
      <main className="main-content">
        <div style={{ padding: 32, textAlign: 'center', color: '#6B778C' }}>Loading projects...</div>
      </main>
    )
  }

  if (error) {
    return (
      <main className="main-content">
        <div style={{ padding: 32, textAlign: 'center', color: '#DE350B' }}>{error}</div>
      </main>
    )
  }

  if (listProjects.length === 0) {
    return (
      <main className="main-content">
        <div style={{ padding: 32, textAlign: 'center', color: '#6B778C' }}>
          <h2 style={{ margin: '0 0 8px', color: '#172B4D' }}>No Projects Found</h2>
          <p>Configure a PROJECT issue type in Workflow Config and sync from Jira to see projects here.</p>
        </div>
      </main>
    )
  }

  return (
    <StatusStylesProvider value={statusStyles}>
      <main className="main-content" style={view === 'gantt' ? { display: 'flex', flexDirection: 'column' } : undefined}>
        {/* Header */}
        <div style={{ padding: '12px 16px 0', display: 'flex', alignItems: 'center', gap: 12 }}>
          <h2 style={{ margin: 0 }}>Projects</h2>
          <ViewToggle
            value={view}
            onChange={v => updateParam('view', v === 'list' ? null : v)}
          />
        </div>

        {/* FilterBar */}
        <div style={{ padding: '0 16px' }}>
          <FilterBar
            chips={chips}
            onClearAll={clearFilters}
            trailing={
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                {view === 'gantt' && (
                  <>
                    <SingleSelectDropdown
                      label="Zoom"
                      options={[
                        { value: 'day', label: 'Day' },
                        { value: 'week', label: 'Week' },
                        { value: 'month', label: 'Month' },
                      ]}
                      selected={zoom}
                      onChange={v => updateParam('zoom', v === 'week' ? null : v)}
                      allowClear={false}
                    />
                    <div className="pt-legend">
                      {roleCodes.map(code => (
                        <span key={code} className="pt-legend-item" style={{ borderLeft: `3px solid ${lightenColor(getRoleColor(code), 0.5)}` }}>
                          {code}
                        </span>
                      ))}
                      <span className="pt-legend-item pt-legend-today">Today</span>
                      <span className="pt-legend-item pt-legend-rough">Forecast</span>
                    </div>
                  </>
                )}
              </div>
            }
          >
            <SearchInput
              value={search}
              onChange={v => updateParam('search', v || null)}
              placeholder="AI search: key, text, or meaning..."
              loading={searchLoading}
              badge={searchBadge}
              hints={searchHints}
            />

            <MultiSelectDropdown
              label="PM"
              options={availablePMs}
              selected={selectedPMs}
              onToggle={handlePMToggle}
              placeholder="All PM"
            />

            <MultiSelectDropdown
              label="Status"
              options={availableStatuses}
              selected={selectedStatuses}
              onToggle={handleStatusToggle}
              placeholder="All statuses"
              colorMap={statusColorMap}
            />

            {teamNames.length > 0 && (
              <MultiSelectDropdown
                label="Team"
                options={teamNames}
                selected={selectedTeams}
                onToggle={handleTeamToggle}
                placeholder="All teams"
                colorMap={teamColorMap}
              />
            )}

            <SingleSelectDropdown
              label="Sort"
              options={[
                { value: 'default', label: 'Default' },
                { value: 'progress-asc', label: 'Progress \u2191' },
                { value: 'progress-desc', label: 'Progress \u2193' },
                { value: 'rice-desc', label: 'RICE Score \u2193' },
                { value: 'expected-done', label: 'Expected Done' },
                { value: 'epics-desc', label: 'Epics count \u2193' },
              ]}
              selected={sortBy}
              onChange={v => updateParam('sort', v === 'default' ? null : v)}
              placeholder="Sort"
              allowClear={false}
            />

            <button
              className="pt-toggle-btn"
              onClick={handleToggleAll}
            >
              {allExpandedInCurrentView ? 'Collapse all' : 'Expand all'}
            </button>
          </FilterBar>
        </div>

        {/* List View */}
        {view === 'list' && (
          <div style={{ padding: '0 16px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {sortedListProjects.map(p => (
                <div key={p.issueKey}>
                  {/* Project card */}
                  <div
                    onClick={() => handleToggle(p.issueKey)}
                    style={{
                      padding: '14px 20px',
                      background: '#fff',
                      border: '1px solid #DFE1E6',
                      borderRadius: expandedKeys.has(p.issueKey) ? '8px 8px 0 0' : 8,
                      cursor: 'pointer',
                      transition: 'box-shadow 0.15s',
                    }}
                    onMouseEnter={e => (e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.08)')}
                    onMouseLeave={e => (e.currentTarget.style.boxShadow = 'none')}
                  >
                    {/* Top row */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                      <img src={getIssueIcon(p.issueType, getIssueTypeIconUrl(p.issueType))} alt={p.issueType} style={{ width: 16, height: 16, flexShrink: 0 }} />
                      <JiraLink issueKey={p.issueKey} jiraBaseUrl={jiraBaseUrl} />
                      <span style={{ flex: 1, fontSize: 14, color: '#172B4D', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {p.summary}
                      </span>
                      {p.assigneeDisplayName && (
                        <AssigneeBadge name={p.assigneeDisplayName} avatarUrl={p.assigneeAvatarUrl} />
                      )}
                      <RiceScoreBadge score={p.riceScore} normalized={p.riceNormalizedScore} />
                      <StatusBadge status={p.status} />
                      <span style={{
                        fontSize: 16,
                        color: '#6B778C',
                        transform: expandedKeys.has(p.issueKey) ? 'rotate(180deg)' : 'rotate(0)',
                        transition: 'transform 0.2s',
                        flexShrink: 0,
                      }}>
                        &#9660;
                      </span>
                    </div>

                    {/* Bottom row */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                      <ProgressBar percent={p.progressPercent} width={120} />
                      {p.totalEstimateSeconds != null && p.totalEstimateSeconds > 0 ? (
                        <span style={{ fontSize: 12, color: '#42526E', fontWeight: 500 }}>
                          {formatHours(p.totalLoggedSeconds)}/{formatHours(p.totalEstimateSeconds)} ({p.progressPercent}%)
                        </span>
                      ) : (
                        <span style={{ fontSize: 12, color: '#42526E', fontWeight: 500 }}>
                          {p.completedEpicCount}/{p.childEpicCount} epics ({p.progressPercent}%)
                        </span>
                      )}
                      <span style={{ color: '#DFE1E6' }}>|</span>
                      <span style={{ fontSize: 12, color: '#97A0AF' }}>
                        {p.completedEpicCount}/{p.childEpicCount} epics
                      </span>
                      {p.expectedDone && (
                        <>
                          <span style={{ color: '#DFE1E6' }}>|</span>
                          <span style={{ fontSize: 12, color: '#6B778C' }}>
                            Done by <strong style={{ color: '#42526E' }}>{formatDate(p.expectedDone)}</strong>
                          </span>
                        </>
                      )}
                    </div>
                  </div>

                  {/* Expanded detail */}
                  {expandedKeys.has(p.issueKey) && (() => {
                    const det = details.get(p.issueKey)
                    const isLoading = detailsLoading.has(p.issueKey)
                    const recs = recsMap.get(p.issueKey) || []
                    return (
                    <div style={{
                      border: '1px solid #DFE1E6',
                      borderTop: 'none',
                      borderRadius: '0 0 8px 8px',
                      background: '#FAFBFC',
                      padding: '12px 20px',
                    }}>
                      {det?.description && (
                        <div style={{
                          fontSize: 13,
                          color: '#42526E',
                          lineHeight: 1.5,
                          marginBottom: 12,
                          paddingBottom: 12,
                          borderBottom: '1px solid #EBECF0',
                          whiteSpace: 'pre-line',
                        }}>
                          {det.description}
                        </div>
                      )}
                      {isLoading ? (
                        <div style={{ padding: 16, textAlign: 'center', color: '#6B778C', fontSize: 13 }}>
                          Loading epics...
                        </div>
                      ) : det && det.epics.length > 0 ? (
                        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, tableLayout: 'fixed' }}>
                          <colgroup>
                            <col style={{ width: 100 }} />
                            <col />
                            <col style={{ width: 130 }} />
                            <col style={{ width: 120 }} />
                            <col style={{ width: 160 }} />
                            <col style={{ width: 90 }} />
                            <col style={{ width: 70 }} />
                          </colgroup>
                          <thead>
                            <tr style={{ borderBottom: '2px solid #DFE1E6' }}>
                              <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Key</th>
                              <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Summary</th>
                              <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Status</th>
                              <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Team</th>
                              <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Progress</th>
                              <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Done by</th>
                              <th style={{ textAlign: 'center', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Align</th>
                            </tr>
                          </thead>
                          <tbody>
                            {det.epics.map(e => (
                              <tr key={e.issueKey} style={{ borderBottom: '1px solid #EBECF0' }}>
                                <td style={{ padding: '8px' }}>
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                    <img src={getIssueIcon(e.issueType, getIssueTypeIconUrl(e.issueType))} alt={e.issueType} style={{ width: 16, height: 16, flexShrink: 0 }} />
                                    <JiraLink issueKey={e.issueKey} jiraBaseUrl={jiraBaseUrl} />
                                  </div>
                                </td>
                                <td style={{ padding: '8px', color: '#172B4D', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.summary}</td>
                                <td style={{ padding: '8px' }}>
                                  <StatusBadge status={e.status} />
                                </td>
                                <td style={{ padding: '8px' }}>
                                  <TeamBadge name={e.teamName} color={e.teamColor} />
                                </td>
                                <td style={{ padding: '8px' }}>
                                  {e.progressPercent != null ? (
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                      <ProgressBar percent={e.progressPercent} width={60} />
                                      <span style={{ fontSize: 11, color: '#42526E', whiteSpace: 'nowrap' }}>
                                        {e.progressPercent}%
                                      </span>
                                      {e.estimateSeconds != null && e.estimateSeconds > 0 && (
                                        <span style={{ fontSize: 10, color: '#97A0AF', whiteSpace: 'nowrap' }}>
                                          {formatHours(e.loggedSeconds)}/{formatHours(e.estimateSeconds)}
                                        </span>
                                      )}
                                    </div>
                                  ) : (
                                    <span style={{ color: '#97A0AF' }}>{'\u2014'}</span>
                                  )}
                                </td>
                                <td style={{ padding: '8px', fontSize: 12, color: '#42526E', whiteSpace: 'nowrap' }}>
                                  {formatDate(e.expectedDone || e.dueDate)}
                                </td>
                                <td style={{ padding: '8px', textAlign: 'center' }}>
                                  <AlignmentBadge delayDays={e.delayDays} />
                                </td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      ) : (
                        <div style={{ padding: 16, textAlign: 'center', color: '#6B778C', fontSize: 13 }}>
                          No child epics found
                        </div>
                      )}
                      <RecommendationsBlock recommendations={recs} />
                      <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                        <button
                          onClick={e => { e.stopPropagation(); setRiceModalKey(p.issueKey) }}
                          style={{
                            padding: '6px 14px',
                            fontSize: 13,
                            fontWeight: 500,
                            color: '#0052CC',
                            background: '#E9F2FF',
                            border: '1px solid #B3D4FF',
                            borderRadius: 4,
                            cursor: 'pointer',
                          }}
                        >
                          RICE Scoring
                        </button>
                      </div>
                    </div>
                    )
                  })()}
                </div>
              ))}
            </div>
            {riceModalKey && (() => {
              const proj = listProjects.find(pr => pr.issueKey === riceModalKey)
              return (
                <Modal
                  isOpen={true}
                  onClose={() => setRiceModalKey(null)}
                  title="RICE Scoring"
                  maxWidth={680}
                >
                  <div style={{ marginBottom: 16, paddingBottom: 12, borderBottom: '1px solid #EBECF0' }}>
                    <div style={{ fontSize: 15, fontWeight: 600, color: '#172B4D' }}>
                      <span style={{ color: '#0052CC', marginRight: 8 }}>{riceModalKey}</span>
                      {proj?.summary}
                    </div>
                  </div>
                  <RiceForm issueKey={riceModalKey} onSaved={() => {
                    projectsApi.list().then(setListProjects).catch(() => {})
                  }} />
                </Modal>
              )
            })()}
          </div>
        )}

        {/* Gantt View */}
        {view === 'gantt' && sortedTimelineProjects.length > 0 && (
          <ProjectGanttView
            projects={sortedTimelineProjects}
            jiraBaseUrl={jiraBaseUrl}
            zoom={zoom}
            expanded={ganttExpanded}
            onToggleProject={key => setGanttExpanded(prev => ({ ...prev, [key]: !prev[key] }))}
          />
        )}
        {view === 'gantt' && !loading && sortedTimelineProjects.length === 0 && (
          <div style={{ padding: 40, textAlign: 'center', color: '#6B778C', fontSize: 14 }}>
            No projects with timeline data
          </div>
        )}
      </main>
    </StatusStylesProvider>
  )
}
