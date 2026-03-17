import { useState, useEffect, useRef } from 'react'
import { teamsApi, Team } from '../api/teams'
import { getMetricsSummary, TeamMetricsSummary, getForecastAccuracy, ForecastAccuracyResponse, getDsr, DsrResponse, DeliveryHealth } from '../api/metrics'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { getConfig } from '../api/config'
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
import { ThroughputChart } from '../components/metrics/ThroughputChart'
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

function TeamMetricsPageContent() {
  const { teamId: selectedTeamId, from, to, setTeamId: setSelectedTeamId } = useMetricsFilter()
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
                  data={metrics.throughput.byPeriod}
                  movingAverage={metrics.throughput.movingAverage}
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
