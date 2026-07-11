import { useEffect, useState, useCallback, useMemo, useRef, CSSProperties } from 'react'
import {
  quarterlyPlanningApi,
  PlanningEpicDto,
  QuarterlyTeamOverviewDto,
  TeamRef,
  EpicRemainingDto,
  needsPlanning,
} from '../api/quarterlyPlanning'
import { getConfig } from '../api/config'
import { getStatusStyles, StatusStyle } from '../api/board'
import { getRoughEstimateConfig, updateRoughEstimate, RoughEstimateConfig } from '../api/epics'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { CapacityBars } from '../components/planning/CapacityBars'
import { BacklogColumn } from '../components/planning/BacklogColumn'
import { InQuarterColumn } from '../components/planning/InQuarterColumn'
import { PublishToJiraModal, PendingChange, PublishResultItem } from '../components/planning/PublishToJiraModal'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  TEXT_SECONDARY,
  BG_SUBTLE,
  BG_PAGE,
  BORDER_DEFAULT,
  LINK_COLOR,
  ERROR_TEXT,
  ERROR_BG,
  ERROR_BORDER,
} from '../constants/colors'
import './QuarterlyPlanningPage.css'

function currentQuarterLabel(now: Date = new Date()): string {
  return `${now.getFullYear()}Q${Math.floor(now.getMonth() / 3) + 1}`
}

/**
 * Filter the server-returned list to "plannable" quarters — current and future
 * only. Quarter labels are YYYYQn, which sort lexicographically by chronology,
 * so a string comparison against the current quarter is enough.
 */
function plannableQuarters(quarters: string[]): string[] {
  const current = currentQuarterLabel()
  return quarters.filter(q => q >= current)
}

function defaultQuarter(quarters: string[]): string {
  if (quarters.length === 0) return ''
  const currentQ = currentQuarterLabel()
  return quarters.includes(currentQ) ? currentQ : quarters[0]
}

export function QuarterlyPlanningPage() {
  // ==================== Loading state ====================
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // ==================== Static config ====================
  const [availableQuarters, setAvailableQuarters] = useState<string[]>([])
  const [quarter, setQuarter] = useState('')
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')

  // ==================== Server data ====================
  const [epics, setEpics] = useState<PlanningEpicDto[]>([])
  const [teamsOverview, setTeamsOverview] = useState<QuarterlyTeamOverviewDto[]>([])
  // Status colors for the epic StatusBadge — same source as Board/Projects pages.
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  // Rough-estimate editing config (enabled flag, allowed statuses, step/min/max).
  const [estimateConfig, setEstimateConfig] = useState<RoughEstimateConfig | null>(null)
  // F86: per-epic remaining work (now vs at quarter start), keyed by epicKey.
  // Loaded lazily and independently of loadQuarter so the board renders
  // immediately and these numbers stream in.
  const [remainingByEpic, setRemainingByEpic] = useState<Record<string, EpicRemainingDto>>({})
  // baseline of inQuarter and boost values from server — used to compute diff
  const baselineRef = useRef<Map<string, { inQuarter: boolean; quarterLabel: string | null; boost: number }>>(new Map())

  // ==================== UI state ====================
  const [publishModalOpen, setPublishModalOpen] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  // F70: tech-lead-facing filter. true (default) = show only epics whose project
  // desires this quarter, plus standalone (orphan) epics. false = show every
  // epic the tech lead might want to consider (legacy F69 behaviour).
  const [onlyDesired, setOnlyDesired] = useState(true)
  // Page-level team filter — always one team. Initialised lazily from the
  // first team that appears in the overview (see effect below). Page is
  // unusable until at least one team exists, which matches the empty-state
  // guard already present in CapacityBars.
  const [teamFilter, setTeamFilter] = useState<string>('')
  // Monotonic counter used to discard responses from stale loadQuarter calls
  // when the user switches quarters quickly or hits Refresh mid-flight.
  const loadGenerationRef = useRef(0)
  // F86: separate generation counter for the lazy remaining-work fetch so its
  // stale responses are discarded independently of the main list load.
  const remainingGenerationRef = useRef(0)

  // ==================== Initial load ====================
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([
      quarterlyPlanningApi.getAvailableQuarters().catch(() => [] as string[]),
      getConfig().then(c => c.jiraBaseUrl || '').catch(() => ''),
      getStatusStyles().catch(() => ({} as Record<string, StatusStyle>)),
      getRoughEstimateConfig().catch(() => null),
    ]).then(([qs, baseUrl, styles, roughCfg]) => {
      if (cancelled) return
      setStatusStyles(styles)
      setEstimateConfig(roughCfg)
      // Planning is forward-looking: hide past quarters from the dropdown so
      // a user cannot accidentally schedule work into a quarter that already
      // ended. The current quarter is always retained (backend guarantees it).
      const plannable = plannableQuarters(qs)
      setAvailableQuarters(plannable)
      setQuarter(defaultQuarter(plannable))
      setJiraBaseUrl(baseUrl)
      setLoading(false)
    }).catch(err => {
      if (cancelled) return
      setError(err instanceof Error ? err.message : 'Failed to load quarters')
      setLoading(false)
    })
    return () => { cancelled = true }
  }, [])

  // ==================== Data load ====================
  const loadQuarter = useCallback(async (q: string) => {
    if (!q) return
    const generation = ++loadGenerationRef.current
    setRefreshing(true)
    setError(null)
    try {
      const [epicsRes, teamsRes] = await Promise.all([
        // Always fetch the full backlog. The "Only requested for this quarter"
        // filter is applied client-side (see backlogEpics) so toggling it never
        // triggers a refetch that would discard unpublished moves — and it never
        // affects the InQuarter column.
        quarterlyPlanningApi.getEpicsForQuarter(q, false),
        quarterlyPlanningApi.getTeamsOverview(q),
      ])
      // Stale response — a newer loadQuarter has been issued. Discard.
      if (generation !== loadGenerationRef.current) return
      setEpics(epicsRes.epics)
      setTeamsOverview(teamsRes)
      const baseline = new Map<string, { inQuarter: boolean; quarterLabel: string | null; boost: number }>()
      epicsRes.epics.forEach(e => {
        baseline.set(e.epicKey, {
          inQuarter: e.inQuarter,
          quarterLabel: e.quarterLabel,
          boost: e.manualBoost,
        })
      })
      baselineRef.current = baseline
    } catch (err) {
      if (generation !== loadGenerationRef.current) return
      setError(err instanceof Error ? err.message : 'Failed to load planning data')
    } finally {
      if (generation === loadGenerationRef.current) {
        setRefreshing(false)
      }
    }
  }, [])

  useEffect(() => { if (quarter) loadQuarter(quarter) }, [quarter, loadQuarter])

  // F86: lazily load per-epic remaining work whenever the quarter or the
  // selected team changes. Runs independently of loadQuarter — the board is
  // never blocked on it, and any failure is swallowed (numbers just stay
  // absent, cards fall back to "нет оценки"). teamFilter is '' on the very
  // first render (before the initial team is picked) — skip until it's set.
  useEffect(() => {
    if (!quarter || !teamFilter) return
    const teamId = Number(teamFilter)
    if (!Number.isFinite(teamId)) return
    let cancelled = false
    const generation = ++remainingGenerationRef.current
    quarterlyPlanningApi.getRemainingForQuarter(quarter, teamId)
      .then(res => {
        if (cancelled || generation !== remainingGenerationRef.current) return
        setRemainingByEpic(res.epics ?? {})
      })
      .catch(err => {
        if (cancelled || generation !== remainingGenerationRef.current) return
        // Non-fatal: log and clear so cards degrade to "нет оценки".
        console.warn('Failed to load remaining work for quarter', quarter, err)
        setRemainingByEpic({})
      })
    return () => { cancelled = true }
  }, [quarter, teamFilter])

  // L5: keep latest values in refs so the visibilitychange handler can be
  // installed once. Re-subscribing on every state change (quarter/refreshing)
  // adds and removes the global listener many times per session.
  const quarterRef = useRef(quarter)
  const refreshingRef = useRef(refreshing)
  const loadQuarterRef = useRef(loadQuarter)
  quarterRef.current = quarter
  refreshingRef.current = refreshing
  loadQuarterRef.current = loadQuarter

  // Refresh on tab focus
  useEffect(() => {
    const handler = () => {
      if (document.visibilityState === 'visible' && quarterRef.current && !refreshingRef.current) {
        loadQuarterRef.current(quarterRef.current)
      }
    }
    document.addEventListener('visibilitychange', handler)
    return () => document.removeEventListener('visibilitychange', handler)
  }, [])

  // ==================== Optimistic move ====================
  const handleMove = useCallback((epicKey: string, toQuarter: string | null) => {
    setEpics(prev => prev.map(e => {
      if (e.epicKey !== epicKey) return e
      const goingIn = toQuarter !== null
      return {
        ...e,
        inQuarter: goingIn,
        quarterLabel: goingIn ? toQuarter : null,
      }
    }))
    // Recompute teams overview demand optimistically (after state update)
    // We use a microtask to ensure setEpics has been queued; we rely on derivedTeams instead.
  }, [])

  const handleBoostChange = useCallback((epicKey: string, boost: number) => {
    setEpics(prev => prev.map(e => {
      if (e.epicKey !== epicKey) return e
      // Recalculate priority score — clamped 0..150 — using current rice
      const rawPriority = Math.max(0, Math.min(150, e.riceScore + boost))
      return {
        ...e,
        manualBoost: boost,
        priorityScore: rawPriority,
      }
    }))
  }, [])

  // Save a rough estimate (same endpoint as the Board page) and sync the epic's
  // demand figures from the server response, so chips, Σ, group totals and the
  // optimistic capacity math all update without a full reload.
  const handleEstimateChange = useCallback(async (epicKey: string, role: string, days: number | null) => {
    const resp = await updateRoughEstimate(epicKey, role, { days })
    setEpics(prev => prev.map(e => {
      if (e.epicKey !== epicKey) return e
      const demandByRole: Record<string, number> = {}
      for (const [r, v] of Object.entries(resp.roughEstimates)) {
        if (v !== null && v > 0) demandByRole[r] = v
      }
      const totalDemandDays = Object.values(demandByRole).reduce((s, v) => s + v, 0)
      return { ...e, demandByRole, totalDemandDays, hasEstimate: totalDemandDays > 0 }
    }))
  }, [])

  // ==================== Derived data ====================

  const teamsById = useMemo(() => {
    const m = new Map<number, TeamRef>()
    teamsOverview.forEach(t => m.set(t.teamId, { id: t.teamId, name: t.teamName, color: t.teamColor }))
    epics.forEach(e => e.teams.forEach(t => {
      if (!m.has(t.id)) m.set(t.id, { id: t.id, name: t.name, color: t.color })
    }))
    return m
  }, [teamsOverview, epics])

  // Single-select Team dropdown options. Sorted by name for stable order;
  // color is shown as a colored dot inside the dropdown.
  const teamFilterOptions = useMemo(
    () => Array.from(teamsById.values())
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(t => ({ value: String(t.id), label: t.name, color: t.color ?? undefined })),
    [teamsById],
  )

  // Snap teamFilter to a valid option whenever the option list changes:
  //   - empty selection on first load → pick the first team
  //   - currently-selected team disappeared (e.g. switched quarter) → pick first
  // The dropdown has no "All teams" choice so we always keep one selected.
  useEffect(() => {
    if (teamFilterOptions.length === 0) return
    const stillValid = teamFilterOptions.some(o => o.value === teamFilter)
    if (!stillValid) setTeamFilter(teamFilterOptions[0].value)
  }, [teamFilterOptions, teamFilter])

  // Page-level team filter. Applied to both backlog and in-quarter so
  // CapacityBars + columns stay consistent.
  const epicMatchesTeamFilter = useCallback((e: PlanningEpicDto) => {
    if (!teamFilter) return true
    return e.teams.some(t => String(t.id) === teamFilter)
  }, [teamFilter])

  // "Only requested for this quarter" (onlyDesired) is a backlog-only, client-side
  // filter: keep an epic when its parent project desires this quarter, or when it
  // is standalone (mirrors the F70 backend rule). Applied here — never on refetch —
  // so toggling the checkbox is instant and never discards unpublished moves nor
  // touches the InQuarter column.
  // F86: needs-planning epics (active work not committed to the viewed or a
  // future quarter) always pass the onlyDesired filter — otherwise they'd be
  // hidden exactly when the tech lead needs to see and schedule their tail.
  const backlogEpics = useMemo(
    () => epics.filter(e =>
      !e.inQuarter
      && epicMatchesTeamFilter(e)
      && (!onlyDesired || e.isStandalone || e.projectDesiredQuarter === quarter || needsPlanning(e, quarter)),
    ),
    [epics, epicMatchesTeamFilter, onlyDesired, quarter],
  )
  const inQuarterEpics = useMemo(
    () => epics.filter(e => e.inQuarter && epicMatchesTeamFilter(e)),
    [epics, epicMatchesTeamFilter],
  )

  // Visible teams: full overview narrowed to the selected team. The dropdown
  // always keeps one team selected, but during the brief window before the
  // initial team is picked (teamFilter === '') we render the full overview so
  // the page is never empty.
  const visibleTeamsOverview = useMemo<QuarterlyTeamOverviewDto[]>(() => {
    if (!teamFilter) return teamsOverview
    return teamsOverview.filter(t => String(t.teamId) === teamFilter)
  }, [teamsOverview, teamFilter])

  // Optimistically recompute team utilization based on current in-quarter epics
  const derivedTeams = useMemo<QuarterlyTeamOverviewDto[]>(() => {
    // demandByRole and demandDays per team derived from in-quarter epics with estimate + team mapping.
    // For multi-team epics, demand is split EVENLY across all assigned teams to avoid double-counting:
    // an epic on 2 teams would otherwise inflate both teams' utilization with the full epic estimate,
    // producing spurious "overload" red bars in CapacityBars.
    const demandByTeam = new Map<number, { totalDays: number; byRole: Record<string, number> }>()

    inQuarterEpics.forEach(e => {
      if (!e.hasEstimate || !e.hasTeamMapping) return
      const teamCount = e.teams.length
      if (teamCount === 0) return
      const shareTotal = e.totalDemandDays / teamCount
      const shareByRole: Record<string, number> = {}
      Object.entries(e.demandByRole).forEach(([role, days]) => {
        shareByRole[role] = days / teamCount
      })
      e.teams.forEach(t => {
        const existing = demandByTeam.get(t.id) || { totalDays: 0, byRole: {} }
        existing.totalDays += shareTotal
        Object.entries(shareByRole).forEach(([role, days]) => {
          existing.byRole[role] = (existing.byRole[role] || 0) + days
        })
        demandByTeam.set(t.id, existing)
      })
    })

    return visibleTeamsOverview.map(team => {
      const updatedDemand = demandByTeam.get(team.teamId)
      if (!updatedDemand) {
        // No in-quarter epics for this team — demand is 0
        return {
          ...team,
          demandDays: 0,
          demandByRole: {},
          gapDays: team.capacityDays,
          utilization: 0,
          risk: 'low' as const,
        }
      }
      const demandDays = updatedDemand.totalDays
      const gapDays = team.capacityDays - demandDays
      const utilization = team.capacityDays > 0 ? Math.round((demandDays / team.capacityDays) * 100) : 0
      // Risk thresholds aligned with getUtilizationColor (constants/colors.ts):
      // <80% low (green), 80..100% medium (yellow), >100% high (red).
      const risk: 'low' | 'medium' | 'high' =
        utilization < 80
          ? 'low'
          : utilization <= 100
            ? 'medium'
            : 'high'
      return {
        ...team,
        demandDays,
        demandByRole: updatedDemand.byRole,
        gapDays,
        utilization,
        risk,
      }
    })
  }, [visibleTeamsOverview, inQuarterEpics])

  // ==================== Pending changes ====================

  const pendingChanges: PendingChange[] = useMemo(() => {
    const changes: PendingChange[] = []
    epics.forEach(e => {
      const base = baselineRef.current.get(e.epicKey)
      if (!base) return
      // Quarter changes — treated based on inQuarter for THIS quarter
      if (base.inQuarter !== e.inQuarter) {
        if (e.inQuarter) {
          // Adding to this quarter
          if (base.quarterLabel && base.quarterLabel !== quarter) {
            changes.push({
              epicKey: e.epicKey,
              epicSummary: e.epicSummary,
              action: 'move',
              fromQuarter: base.quarterLabel,
              toQuarter: quarter,
            })
          } else {
            changes.push({
              epicKey: e.epicKey,
              epicSummary: e.epicSummary,
              action: 'add',
              fromQuarter: null,
              toQuarter: quarter,
            })
          }
        } else {
          // Removing from this quarter
          changes.push({
            epicKey: e.epicKey,
            epicSummary: e.epicSummary,
            action: 'remove',
            fromQuarter: quarter,
            toQuarter: null,
          })
        }
      }

      // Boost changes
      if (base.boost !== e.manualBoost) {
        changes.push({
          epicKey: e.epicKey,
          epicSummary: e.epicSummary,
          action: 'boost',
          fromBoost: base.boost,
          toBoost: e.manualBoost,
        })
      }
    })
    return changes
  }, [epics, quarter])

  const pendingCount = pendingChanges.length

  // ==================== Publish ====================
  const publishChanges = useCallback(async (): Promise<PublishResultItem[]> => {
    const results: PublishResultItem[] = []
    for (const change of pendingChanges) {
      try {
        if (change.action === 'add' || change.action === 'move') {
          await quarterlyPlanningApi.assignEpicToQuarter(change.epicKey, change.toQuarter ?? quarter)
        } else if (change.action === 'remove') {
          await quarterlyPlanningApi.assignEpicToQuarter(change.epicKey, null)
        } else if (change.action === 'boost') {
          await quarterlyPlanningApi.setEpicBoost(change.epicKey, change.toBoost ?? 0)
        }
        results.push({ change, ok: true })
        // Update baseline for this epic so it's no longer pending
        const epic = epics.find(e => e.epicKey === change.epicKey)
        if (epic) {
          baselineRef.current.set(change.epicKey, {
            inQuarter: epic.inQuarter,
            quarterLabel: epic.inQuarter ? quarter : null,
            boost: epic.manualBoost,
          })
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'Unknown error'
        results.push({ change, ok: false, error: msg })
      }
    }
    // After publishing, refetch to reconcile with Jira
    if (results.some(r => r.ok)) {
      await loadQuarter(quarter)
    }
    return results
  }, [pendingChanges, quarter, epics, loadQuarter])

  // ==================== Render ====================
  if (loading) {
    return (
      <div style={pageStyle}>
        <div style={emptyStyle}>Loading planning data...</div>
      </div>
    )
  }

  if (availableQuarters.length === 0) {
    return (
      <div style={pageStyle}>
        <h1 style={{ color: TEXT_PRIMARY, marginTop: 0 }}>Quarterly Planning</h1>
        <div style={emptyStyle}>
          No quarter labels found. Add labels like "2026Q2" to epics or projects in Jira.
        </div>
      </div>
    )
  }

  const quarterOptions = availableQuarters.map(q => ({ value: q, label: q }))

  return (
    <StatusStylesProvider value={statusStyles}>
    <div style={pageStyle}>
      {/* Header */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <div>
          <h1 style={{ margin: 0, fontSize: 24, color: TEXT_PRIMARY }}>Quarterly Planning</h1>
          <p style={{ margin: '4px 0 0', color: TEXT_SECONDARY, fontSize: 13 }}>
            Decide what fits in <strong>{quarter}</strong> by moving epics between Backlog and In quarter. Publish to write quarter labels to Jira.
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <SingleSelectDropdown
            label="Quarter"
            options={quarterOptions}
            selected={quarter}
            onChange={(value) => { if (value) setQuarter(value) }}
            placeholder="Select quarter"
            allowClear={false}
          />
          <SingleSelectDropdown
            label="Team"
            options={teamFilterOptions}
            selected={teamFilter}
            onChange={(value) => { if (value) setTeamFilter(value) }}
            placeholder="Select team"
            allowClear={false}
          />
          <button
            type="button"
            onClick={() => loadQuarter(quarter)}
            disabled={refreshing}
            title="Reload latest from Jira"
            style={{
              padding: '8px 12px',
              background: BG_PAGE,
              border: `1px solid ${BORDER_DEFAULT}`,
              color: TEXT_PRIMARY,
              borderRadius: 4,
              fontWeight: 600,
              cursor: refreshing ? 'wait' : 'pointer',
            }}
          >
            {refreshing ? 'Refreshing...' : '↻ Refresh'}
          </button>
          <button
            type="button"
            onClick={() => setPublishModalOpen(true)}
            disabled={pendingCount === 0}
            style={{
              padding: '8px 14px',
              background: pendingCount > 0 ? LINK_COLOR : BG_SUBTLE,
              border: `1px solid ${pendingCount > 0 ? LINK_COLOR : BORDER_DEFAULT}`,
              color: pendingCount > 0 ? BG_PAGE : TEXT_MUTED,
              borderRadius: 4,
              fontWeight: 700,
              cursor: pendingCount > 0 ? 'pointer' : 'not-allowed',
            }}
          >
            Publish → Jira{pendingCount > 0 ? ` (${pendingCount})` : ''}
          </button>
        </div>
      </div>

      {error && (
        <div
          role="alert"
          aria-live="polite"
          style={{
            padding: '10px 14px',
            background: ERROR_BG,
            border: `1px solid ${ERROR_BORDER}`,
            color: ERROR_TEXT,
            borderRadius: 6,
            marginBottom: 12,
            fontSize: 13,
          }}
        >
          {error}
        </div>
      )}

      {/* Capacity bars sticky header */}
      <div style={{ position: 'sticky', top: 0, zIndex: 5, background: BG_PAGE, paddingBottom: 12, marginBottom: 12 }}>
        <CapacityBars teams={derivedTeams} />
      </div>

      {/* Two columns — dimmed while refreshing to signal stale data */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)',
          gap: 16,
          alignItems: 'start',
          opacity: refreshing ? 0.6 : 1,
          pointerEvents: refreshing ? 'none' : 'auto',
          transition: 'opacity 0.15s ease',
        }}
        aria-busy={refreshing}
        className="planning-board-grid"
      >
        <BacklogColumn
          epics={backlogEpics}
          targetQuarter={quarter}
          currentQuarter={quarter}
          jiraBaseUrl={jiraBaseUrl}
          remainingByEpic={remainingByEpic}
          estimateConfig={estimateConfig}
          onEstimateChange={handleEstimateChange}
          onMove={handleMove}
          onBoostChange={handleBoostChange}
          onlyDesired={onlyDesired}
          onOnlyDesiredChange={setOnlyDesired}
        />
        <InQuarterColumn
          epics={inQuarterEpics}
          targetQuarter={quarter}
          jiraBaseUrl={jiraBaseUrl}
          estimateConfig={estimateConfig}
          onEstimateChange={handleEstimateChange}
          onMove={handleMove}
          onBoostChange={handleBoostChange}
        />
      </div>

      <PublishToJiraModal
        isOpen={publishModalOpen}
        onClose={() => setPublishModalOpen(false)}
        pendingChanges={pendingChanges}
        onConfirm={publishChanges}
      />
    </div>
    </StatusStylesProvider>
  )
}

// Layout styles kept inline to avoid pulling in legacy CSS
const pageStyle: CSSProperties = {
  maxWidth: 1440,
  margin: '0 auto',
  padding: '24px 28px 48px',
  color: TEXT_PRIMARY,
}

const emptyStyle: CSSProperties = {
  padding: 30,
  textAlign: 'center',
  color: TEXT_MUTED,
  background: BG_SUBTLE,
  border: `1px solid ${BORDER_DEFAULT}`,
  borderRadius: 8,
  fontSize: 14,
}
