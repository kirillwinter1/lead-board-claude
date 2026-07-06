import { useMemo, useState } from 'react'
import { SearchInput } from '../SearchInput'
import { MultiSelectDropdown } from '../MultiSelectDropdown'
import { EpicCard } from './EpicCard'
import { RoughEstimateConfig } from '../../api/epics'
import { PlanningEpicDto, EpicRemainingDto } from '../../api/quarterlyPlanning'
import { NO_PROJECT_KEY, NO_PROJECT_LABEL } from './constants'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  BG_SUBTLE,
  BG_PAGE,
  BORDER_DEFAULT,
  SEPARATOR,
} from '../../constants/colors'

interface BacklogColumnProps {
  epics: PlanningEpicDto[]
  targetQuarter: string
  // F86: the quarter being planned — used by EpicCard to decide needs-planning.
  currentQuarter: string
  jiraBaseUrl: string
  // F86: per-epic remaining work, keyed by epicKey. Loaded lazily by the page.
  remainingByEpic: Record<string, EpicRemainingDto>
  estimateConfig?: RoughEstimateConfig | null
  onEstimateChange?: (epicKey: string, role: string, days: number | null) => Promise<void>
  onMove: (epicKey: string, toQuarter: string | null) => void
  onBoostChange: (epicKey: string, boost: number) => void
  // F70: when true the parent has already filtered the epic list down to
  // "epics whose project desires this quarter ∪ standalone". The column
  // surfaces a toggle so the tech lead can opt out of the filter.
  onlyDesired: boolean
  onOnlyDesiredChange: (value: boolean) => void
}

interface ProjectGroup {
  projectKey: string | null
  projectSummary: string | null
  epics: PlanningEpicDto[]
}

function projectKeyOrSentinel(key: string | null): string {
  return key ?? NO_PROJECT_KEY
}

export function BacklogColumn({
  epics,
  targetQuarter,
  currentQuarter,
  jiraBaseUrl,
  remainingByEpic,
  estimateConfig,
  onEstimateChange,
  onMove,
  onBoostChange,
  onlyDesired,
  onOnlyDesiredChange,
}: BacklogColumnProps) {
  const [searchQuery, setSearchQuery] = useState('')
  const [projectFilter, setProjectFilter] = useState<Set<string>>(new Set())
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())

  // Build filter option lists from full epics list (so user can always find any project/team).
  // Epics with no parent project are bucketed under a sentinel key/label so filters and sort
  // work without crashing on null. The sentinel group is always sorted last.
  const allProjectOptions = useMemo(() => {
    const map = new Map<string, string>()
    epics.forEach(e => {
      const k = projectKeyOrSentinel(e.projectKey)
      if (!map.has(k)) map.set(k, e.projectSummary ?? NO_PROJECT_LABEL)
    })
    return Array.from(map.entries()).sort(([a], [b]) => {
      if (a === NO_PROJECT_KEY) return 1
      if (b === NO_PROJECT_KEY) return -1
      return a.localeCompare(b)
    })
  }, [epics])

  // Apply local search + project filters. The page already pre-filtered by
  // teamFilter so we do not re-apply it here.
  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    return epics.filter(e => {
      if (q && !e.epicSummary.toLowerCase().includes(q) && !e.epicKey.toLowerCase().includes(q)) return false
      if (projectFilter.size > 0 && !projectFilter.has(projectKeyOrSentinel(e.projectKey))) return false
      return true
    })
  }, [epics, searchQuery, projectFilter])

  const groups: ProjectGroup[] = useMemo(() => {
    const map = new Map<string, ProjectGroup>()
    filtered.forEach(e => {
      const k = projectKeyOrSentinel(e.projectKey)
      let g = map.get(k)
      if (!g) {
        g = { projectKey: e.projectKey, projectSummary: e.projectSummary, epics: [] }
        map.set(k, g)
      }
      g.epics.push(e)
    })
    const arr = Array.from(map.values())
    arr.forEach(g => g.epics.sort((a, b) => b.priorityScore - a.priorityScore))
    arr.sort((a, b) => {
      const aMax = a.epics[0]?.priorityScore ?? 0
      const bMax = b.epics[0]?.priorityScore ?? 0
      return bMax - aMax
    })
    return arr
  }, [filtered])

  const totalCount = epics.length
  const visibleCount = filtered.length
  const hiddenCount = totalCount - visibleCount

  const toggleGroup = (key: string) => {
    setCollapsed(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 16,
        background: BG_PAGE,
        border: `1px solid ${BORDER_DEFAULT}`,
        borderRadius: 8,
        minHeight: 400,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8 }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 18, color: TEXT_PRIMARY }}>Backlog</h2>
          <span style={{ fontSize: 12, color: TEXT_MUTED }}>
            {visibleCount} of {totalCount} epics
            {hiddenCount > 0 && ` (${hiddenCount} hidden)`} · sorted by priority
          </span>
        </div>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, alignItems: 'center' }}>
        <div style={{ flex: '1 1 240px', minWidth: 200 }}>
          <SearchInput value={searchQuery} onChange={setSearchQuery} placeholder="Search epics..." />
        </div>
        <MultiSelectDropdown
          label="Project"
          options={allProjectOptions.map(([k]) => k)}
          selected={projectFilter}
          onToggle={key => setProjectFilter(prev => {
            const next = new Set(prev)
            if (next.has(key)) next.delete(key)
            else next.add(key)
            return next
          })}
          placeholder="All projects"
          renderOption={key => {
            if (key === NO_PROJECT_KEY) return NO_PROJECT_LABEL
            const summary = allProjectOptions.find(([k]) => k === key)?.[1] ?? ''
            return summary ? `${key} — ${summary}` : key
          }}
        />
        <label
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            padding: '6px 10px',
            border: `1px solid ${BORDER_DEFAULT}`,
            borderRadius: 6,
            background: onlyDesired ? BG_SUBTLE : BG_PAGE,
            fontSize: 12,
            color: TEXT_PRIMARY,
            cursor: 'pointer',
            userSelect: 'none',
          }}
          title="Show only epics from projects PMs requested for this quarter, plus standalone epics"
        >
          <input
            type="checkbox"
            checked={onlyDesired}
            onChange={e => onOnlyDesiredChange(e.target.checked)}
            aria-label="Only epics requested for this quarter"
            style={{ cursor: 'pointer' }}
          />
          Only requested for this quarter
        </label>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {groups.length === 0 && (
          <div style={{ padding: 24, textAlign: 'center', color: TEXT_MUTED, background: BG_SUBTLE, borderRadius: 6 }}>
            {epics.length === 0
              ? (onlyDesired
                ? `Nothing is planned for this team in ${targetQuarter}. Uncheck "Only requested for this quarter" to see other epics.`
                : 'No epics in backlog.')
              : 'No epics match your filters.'}
          </div>
        )}
        {groups.map(g => {
          const groupKey = projectKeyOrSentinel(g.projectKey)
          const isCollapsed = collapsed.has(groupKey)
          const displayHeader = g.projectKey ?? NO_PROJECT_LABEL
          const headerSummary = g.projectSummary ?? ''
          return (
            <section key={groupKey} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button
                type="button"
                onClick={() => toggleGroup(groupKey)}
                aria-expanded={!isCollapsed}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 10px',
                  background: BG_SUBTLE,
                  border: `1px solid ${SEPARATOR}`,
                  borderRadius: 6,
                  cursor: 'pointer',
                  textAlign: 'left',
                  color: TEXT_PRIMARY,
                  fontWeight: 600,
                  fontSize: 13,
                }}
              >
                <span style={{ display: 'inline-block', transform: isCollapsed ? 'rotate(-90deg)' : 'none', transition: 'transform 0.15s' }}>
                  ▾
                </span>
                <span>{displayHeader}</span>
                {headerSummary && (
                  <span style={{ color: TEXT_MUTED, fontWeight: 500 }}>{headerSummary}</span>
                )}
                <span style={{ marginLeft: 'auto', color: TEXT_MUTED, fontWeight: 600 }}>
                  {g.epics.length} {g.epics.length === 1 ? 'epic' : 'epics'}
                </span>
              </button>
              {!isCollapsed && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {g.epics.map(epic => (
                    <EpicCard
                      key={epic.epicKey}
                      epic={epic}
                      mode="backlog"
                      targetQuarter={targetQuarter}
                      currentQuarter={currentQuarter}
                      jiraBaseUrl={jiraBaseUrl}
                      remaining={remainingByEpic[epic.epicKey]}
                      showProject={false}
                      estimateConfig={estimateConfig}
                      onEstimateChange={onEstimateChange}
                      onMove={onMove}
                      onBoostChange={onBoostChange}
                    />
                  ))}
                </div>
              )}
            </section>
          )
        })}
      </div>
    </div>
  )
}
