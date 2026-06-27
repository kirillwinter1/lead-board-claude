import { useState, useEffect, useRef, useMemo } from 'react'
import { teamsApi, Team } from '../api/teams'
import { getMetricsSummary, TeamMetricsSummary, getForecastAccuracy, ForecastAccuracyResponse, getDsr, DsrResponse, DeliveryHealth, getThroughput } from '../api/metrics'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { getConfig } from '../api/config'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { THROUGHPUT_EPIC, THROUGHPUT_STORY, THROUGHPUT_TOTAL } from '../constants/colors'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { FilterBar } from '../components/FilterBar'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { MetricsFilterProvider, useMetricsFilter } from '../contexts/MetricsFilterContext'
import { DateRangePicker } from '../components/metrics/DateRangePicker'
import { DataStatusBar } from '../components/metrics/DataStatusBar'
import { ExecutiveSummaryRow } from '../components/metrics/ExecutiveSummaryRow'
import { DeliveryHealthBadge } from '../components/metrics/DeliveryHealthBadge'
import { AlertStrip } from '../components/metrics/AlertStrip'
import { MetricsSection } from '../components/metrics/MetricsSection'
import { ThroughputChart, ThroughputSeries, ThroughputModeOption } from '../components/metrics/ThroughputChart'
import { TimeInStatusTable } from '../components/metrics/TimeInStatusTable'
import { AssigneeTable } from '../components/metrics/AssigneeTable'
import { ForecastAccuracyChart } from '../components/metrics/ForecastAccuracyChart'
import { VelocityChart } from '../components/metrics/VelocityChart'
import { EpicBurndownChart } from '../components/metrics/EpicBurndownChart'
import { RoleLoadBlock } from '../components/metrics/RoleLoadBlock'
import { DsrTrendChart } from '../components/metrics/DsrTrendChart'
import { DsrBreakdownChart } from '../components/metrics/DsrBreakdownChart'
import { WorklogTimeline } from '../components/WorklogTimeline'
import './TeamMetricsPage.css'

export function TeamMetricsPage() {
  return (
    <MetricsFilterProvider>
      <TeamMetricsPageContent />
    </MetricsFilterProvider>
  )
}

// Throughput selector modes that are not concrete issue types.
const THROUGHPUT_MODE_EPICS_STORIES = 'epics-stories'
const THROUGHPUT_MODE_ALL = 'all'

function TeamMetricsPageContent() {
  const { teamId: selectedTeamId, from, to, setTeamId: setSelectedTeamId } = useMetricsFilter()
  const { issueTypeCategories } = useWorkflowConfig()
  const [teams, setTeams] = useState<Team[]>([])
  const [metrics, setMetrics] = useState<TeamMetricsSummary | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [forecastAccuracy, setForecastAccuracy] = useState<ForecastAccuracyResponse | null>(null)
  const [dsr, setDsr] = useState<DsrResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [metricsLoading, setMetricsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [jiraBaseUrl, setJiraBaseUrl] = useState<string>('')
  const [alerts, setAlerts] = useState<DeliveryHealth['alerts']>([])
  const initialTeamIdRef = useRef(selectedTeamId)

  // ---- Throughput chart: issue-type selector + resolved series ----
  const [throughputMode, setThroughputMode] = useState<string>(THROUGHPUT_MODE_EPICS_STORIES)
  const [throughputSeries, setThroughputSeries] = useState<ThroughputSeries[]>([])
  const [throughputLoading, setThroughputLoading] = useState(false)

  // Resolve epic/story type names from categories (localization-agnostic).
  const epicTypeName = useMemo(
    () => Object.keys(issueTypeCategories).find(name => issueTypeCategories[name] === 'EPIC'),
    [issueTypeCategories]
  )
  const storyTypeName = useMemo(
    () => Object.keys(issueTypeCategories).find(name => issueTypeCategories[name] === 'STORY'),
    [issueTypeCategories]
  )

  const throughputModeOptions = useMemo<ThroughputModeOption[]>(() => [
    { value: THROUGHPUT_MODE_EPICS_STORIES, label: 'Epics & Stories' },
    { value: THROUGHPUT_MODE_ALL, label: 'All (total)' },
    // Only meaningful work-unit types for throughput; hide PROJECT and SUBTASK.
    ...Object.keys(issueTypeCategories)
      .filter(name => issueTypeCategories[name] !== 'PROJECT' && issueTypeCategories[name] !== 'SUBTASK')
      .map(name => ({ value: name, label: name })),
  ], [issueTypeCategories])

  // "all" mode reuses the already-loaded summary — no extra request.
  const allModeSeries = useMemo<ThroughputSeries[]>(() => {
    if (!metrics) return []
    return [{
      key: 'total',
      name: 'Total',
      color: THROUGHPUT_TOTAL,
      points: metrics.throughput.byPeriod.map(p => ({ periodStart: p.periodStart, value: p.total })),
    }]
  }, [metrics])

  // Load config for Jira URL + status styles
  useEffect(() => {
    getConfig()
      .then(config => setJiraBaseUrl(config.jiraBaseUrl))
      .catch(err => console.error('Failed to load config:', err))
    getStatusStyles().then(setStatusStyles).catch(() => {})
  }, [])

  // Load teams (once on mount)
  useEffect(() => {
    teamsApi.getAll()
      .then(data => {
        const activeTeams = data.filter(t => t.active)
        setTeams(activeTeams)
        if (activeTeams.length > 0 && !initialTeamIdRef.current) {
          setSelectedTeamId(activeTeams[0].id)
        }
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load teams: ' + err.message)
        setLoading(false)
      })
  }, [setSelectedTeamId])

  // Load metrics when team or filters change
  useEffect(() => {
    if (!selectedTeamId) return

    const controller = new AbortController()
    setMetricsLoading(true)
    setError(null)

    Promise.all([
      getMetricsSummary(selectedTeamId, from, to),
      getForecastAccuracy(selectedTeamId, from, to),
      getDsr(selectedTeamId, from, to)
    ])
      .then(([metricsData, accuracyData, dsrData]) => {
        if (controller.signal.aborted) return
        setMetrics(metricsData)
        setForecastAccuracy(accuracyData)
        setDsr(dsrData)
        setMetricsLoading(false)
      })
      .catch(err => {
        if (controller.signal.aborted) return
        setError('Failed to load metrics: ' + (err instanceof Error ? err.message : 'Unknown error'))
        setMetrics(null)
        setForecastAccuracy(null)
        setDsr(null)
        setMetricsLoading(false)
      })

    return () => controller.abort()
  }, [selectedTeamId, from, to])

  // Fetch throughput series for issue-type and epics-stories modes. "all" mode
  // is derived from the summary (allModeSeries) and needs no request here.
  useEffect(() => {
    if (!selectedTeamId || throughputMode === THROUGHPUT_MODE_ALL) return

    const controller = new AbortController()
    setThroughputLoading(true)

    const toSeries = (key: string, name: string, color: string, byPeriod: { periodStart: string; total: number }[]): ThroughputSeries => ({
      key,
      name,
      color,
      points: byPeriod.map(p => ({ periodStart: p.periodStart, value: p.total })),
    })

    let request: Promise<ThroughputSeries[]>
    if (throughputMode === THROUGHPUT_MODE_EPICS_STORIES) {
      if (!epicTypeName || !storyTypeName) {
        setThroughputSeries([])
        setThroughputLoading(false)
        return () => controller.abort()
      }
      request = Promise.all([
        getThroughput(selectedTeamId, from, to, epicTypeName),
        getThroughput(selectedTeamId, from, to, storyTypeName),
      ]).then(([epicRes, storyRes]) => [
        toSeries('epics', epicTypeName, THROUGHPUT_EPIC, epicRes.byPeriod),
        toSeries('stories', storyTypeName, THROUGHPUT_STORY, storyRes.byPeriod),
      ])
    } else {
      const typeName = throughputMode
      request = getThroughput(selectedTeamId, from, to, typeName)
        .then(res => [toSeries('type', typeName, THROUGHPUT_TOTAL, res.byPeriod)])
    }

    request
      .then(resolved => {
        if (controller.signal.aborted) return
        setThroughputSeries(resolved)
        setThroughputLoading(false)
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setThroughputSeries([])
        setThroughputLoading(false)
      })

    return () => controller.abort()
  }, [throughputMode, selectedTeamId, from, to, epicTypeName, storyTypeName])

  const displayedThroughputSeries = throughputMode === THROUGHPUT_MODE_ALL ? allModeSeries : throughputSeries
  const displayedThroughputMA = throughputMode === THROUGHPUT_MODE_ALL ? metrics?.throughput.movingAverage : undefined

  return (
    <StatusStylesProvider value={statusStyles}>
    <main className="main-content">
      <div className="page-header">
        <h2>Team Metrics</h2>
      </div>

      <FilterBar trailing={<DateRangePicker />}>
        <SingleSelectDropdown
          label="Team"
          options={teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined }))}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={v => setSelectedTeamId(v ? Number(v) : null)}
          placeholder="Select team..."
          allowClear={false}
        />
      </FilterBar>

      {selectedTeamId && <DataStatusBar />}

      {selectedTeamId && alerts.length > 0 && (
        <div style={{ marginTop: 8 }}>
          <AlertStrip alerts={alerts} />
        </div>
      )}

      {loading && <div className="loading">Loading...</div>}
      {error && <div className="error">{error}</div>}

      {!loading && !error && selectedTeamId && (
        <div className="metrics-content">
          {metricsLoading ? (
            <div className="loading">Loading metrics...</div>
          ) : metrics ? (
            <>
              {/* Executive Summary Section */}
              <MetricsSection id="executive" title="Executive Summary" defaultExpanded={true}>
                <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
                  <DeliveryHealthBadge
                    teamId={selectedTeamId}
                    from={from}
                    to={to}
                    onAlerts={setAlerts}
                  />
                  <div style={{ flex: 1 }}>
                    <ExecutiveSummaryRow teamId={selectedTeamId} from={from} to={to} />
                  </div>
                </div>
              </MetricsSection>

              {/* Diagnostics Section */}
              <MetricsSection id="diagnostics" title="Diagnostics" defaultExpanded={true}>
                <ThroughputChart
                  series={displayedThroughputSeries}
                  movingAverage={displayedThroughputMA}
                  mode={throughputMode}
                  modeOptions={throughputModeOptions}
                  onModeChange={setThroughputMode}
                  loading={throughputLoading}
                />
                <DsrTrendChart teamId={selectedTeamId} />
                <TimeInStatusTable data={metrics.timeInStatuses} />
                <RoleLoadBlock teamId={selectedTeamId} />
              </MetricsSection>

              {/* Drilldown Section */}
              <MetricsSection id="drilldown" title="Drilldown" defaultExpanded={false}>
                {dsr && dsr.epics.length > 0 && (
                  <DsrBreakdownChart epics={dsr.epics} jiraBaseUrl={jiraBaseUrl} />
                )}
                {forecastAccuracy && (
                  <ForecastAccuracyChart data={forecastAccuracy} jiraBaseUrl={jiraBaseUrl} />
                )}
                <AssigneeTable data={metrics.byAssignee} />
                <VelocityChart
                  teamId={selectedTeamId}
                  from={from}
                  to={to}
                />
                <div className="velocity-section">
                  <h3>Worklog Timeline</h3>
                  <p className="velocity-description">
                    Daily logged hours per team member. Shows who logged what and when.
                  </p>
                  <WorklogTimeline teamId={selectedTeamId} />
                </div>
                <EpicBurndownChart teamId={selectedTeamId} />
              </MetricsSection>
            </>
          ) : (
            <div className="chart-empty">No metrics data available for this period</div>
          )}
        </div>
      )}

      {!loading && !error && !selectedTeamId && teams.length === 0 && (
        <div className="empty">No active teams found. Create a team in the Teams section first.</div>
      )}
    </main>
    </StatusStylesProvider>
  )
}
