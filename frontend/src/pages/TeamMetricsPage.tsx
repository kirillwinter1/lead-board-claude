import { useState, useEffect, useMemo, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getMetricsSummary, TeamMetricsSummary, getForecastAccuracy, ForecastAccuracyResponse, getDsr, DsrResponse } from '../api/metrics'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { getConfig } from '../api/config'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { FilterBar } from '../components/FilterBar'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import './TeamMetricsPage.css'
import { MetricCard } from '../components/metrics/MetricCard'
import { DsrGauge } from '../components/metrics/DsrGauge'
import { ThroughputChart } from '../components/metrics/ThroughputChart'
import { TimeInStatusChart } from '../components/metrics/TimeInStatusChart'
import { AssigneeTable } from '../components/metrics/AssigneeTable'
import { ForecastAccuracyChart } from '../components/metrics/ForecastAccuracyChart'
import { VelocityChart } from '../components/metrics/VelocityChart'
import { EpicBurndownChart } from '../components/metrics/EpicBurndownChart'
import { RoleLoadBlock } from '../components/metrics/RoleLoadBlock'
import { DsrBreakdownChart } from '../components/metrics/DsrBreakdownChart'

export function TeamMetricsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { issueTypeCategories } = useWorkflowConfig()
  const [teams, setTeams] = useState<Team[]>([])
  const [metrics, setMetrics] = useState<TeamMetricsSummary | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})

  // Sync teamId with URL (validate to avoid NaN)
  const rawTeamId = searchParams.get('teamId')
  const selectedTeamId = rawTeamId ? (isNaN(Number(rawTeamId)) ? null : Number(rawTeamId)) : null
  const setSelectedTeamId = useCallback((id: number | null) => {
    if (id) {
      setSearchParams({ teamId: String(id) })
    } else {
      setSearchParams({})
    }
  }, [setSearchParams])
  const initialTeamIdRef = useRef(rawTeamId)
  const [forecastAccuracy, setForecastAccuracy] = useState<ForecastAccuracyResponse | null>(null)
  const [dsr, setDsr] = useState<DsrResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [metricsLoading, setMetricsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState(30) // days
  const [issueType, setIssueType] = useState<string>('')
  const [jiraBaseUrl, setJiraBaseUrl] = useState<string>('')

  // Computed date range
  const dateRange = useMemo(() => {
    const to = new Date()
    const from = new Date()
    from.setDate(from.getDate() - period)
    return {
      from: from.toISOString().split('T')[0],
      to: to.toISOString().split('T')[0]
    }
  }, [period])

  // Build issue type options from workflow config
  const issueTypeOptions = useMemo(() => {
    const categoryLabels: Record<string, string> = {
      EPIC: 'Epics', STORY: 'Stories', SUBTASK: 'Sub-tasks', BUG: 'Bugs'
    }
    const seen = new Set<string>()
    const options: { value: string; label: string }[] = []
    for (const [typeName, category] of Object.entries(issueTypeCategories)) {
      if (!seen.has(typeName) && categoryLabels[category]) {
        seen.add(typeName)
        options.push({ value: typeName, label: `${typeName} (${categoryLabels[category]})` })
      }
    }
    return options
  }, [issueTypeCategories])

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
        // If no team in URL and teams available, select first one
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

    // Load metrics, forecast accuracy, and DSR in parallel
    Promise.all([
      getMetricsSummary(
        selectedTeamId,
        dateRange.from,
        dateRange.to,
        issueType || undefined
      ),
      getForecastAccuracy(selectedTeamId, dateRange.from, dateRange.to),
      getDsr(selectedTeamId, dateRange.from, dateRange.to)
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
  }, [selectedTeamId, dateRange.from, dateRange.to, issueType])

  return (
    <StatusStylesProvider value={statusStyles}>
    <main className="main-content">
      <div className="page-header">
        <h2>Team Metrics</h2>
      </div>

      <FilterBar>
        <SingleSelectDropdown
          label="Team"
          options={teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined }))}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={v => setSelectedTeamId(v ? Number(v) : null)}
          placeholder="Select team..."
          allowClear={false}
        />
        <SingleSelectDropdown
          label="Period"
          options={[
            { value: '7', label: 'Last 7 days' },
            { value: '14', label: 'Last 14 days' },
            { value: '30', label: 'Last 30 days' },
            { value: '60', label: 'Last 60 days' },
            { value: '90', label: 'Last 90 days' },
          ]}
          selected={String(period)}
          onChange={v => v && setPeriod(Number(v))}
          placeholder="Period"
          allowClear={false}
        />
        <SingleSelectDropdown
          label="Issue Type"
          options={issueTypeOptions.map(o => ({ value: o.value, label: o.label }))}
          selected={issueType || null}
          onChange={v => setIssueType(v || '')}
          placeholder="All"
        />
      </FilterBar>

      {loading && <div className="loading">Loading...</div>}
      {error && <div className="error">{error}</div>}

      {!loading && !error && selectedTeamId && (
        <div className="metrics-content">
          {/* Summary Cards */}
          {metricsLoading ? (
            <div className="loading">Loading metrics...</div>
          ) : metrics ? (
            <>
              <div className="metrics-summary-cards">
                <DsrGauge
                  value={dsr && dsr.totalEpics > 0 ? dsr.avgDsrActual : null}
                  title="DSR Actual"
                  subtitle={dsr && dsr.totalEpics > 0 ? `avg of ${dsr.totalEpics} epics` : 'no data'}
                  tooltip="DSR — delivery speed ratio for an epic. Formula: (working days − pause days) / estimate in days. 1.0 = on target, < 1.0 = faster, > 1.0 = slower. Pause (flag) stops the timer."
                />
                <DsrGauge
                  value={dsr && dsr.totalEpics > 0 ? dsr.avgDsrForecast : null}
                  title="DSR Forecast"
                  subtitle={dsr && dsr.totalEpics > 0 ? 'forecast accuracy' : 'no data'}
                  tooltip="Ratio of actual duration to forecast from planning. Shows forecast accuracy."
                />
                <MetricCard
                  title="Throughput"
                  value={metrics.throughput.total}
                  subtitle={`${metrics.throughput.totalStories} stories, ${metrics.throughput.totalEpics} epics${metrics.throughput.totalBugs > 0 ? `, ${metrics.throughput.totalBugs} bugs` : ''}`}
                  tooltip="Number of completed tasks for the selected period."
                />
                <MetricCard
                  title="Within Estimate"
                  value={dsr && dsr.totalEpics > 0 ? `${dsr.onTimeRate.toFixed(0)}%` : '—'}
                  subtitle={dsr && dsr.totalEpics > 0 ? `${dsr.onTimeCount} of ${dsr.totalEpics} within estimate` : 'no data'}
                  trend={dsr && dsr.totalEpics > 0
                    ? (dsr.onTimeRate >= 80 ? 'up' : dsr.onTimeRate < 50 ? 'down' : 'neutral')
                    : undefined}
                  tooltip="Percentage of epics with DSR ≤ 1.1 — completed within effort estimate (not about deadlines)."
                />
              </div>

              {/* Role Load Block */}
              <RoleLoadBlock teamId={selectedTeamId} />

              {/* DSR Epic Breakdown Chart */}
              {dsr && dsr.epics.length > 0 && (
                <DsrBreakdownChart epics={dsr.epics} jiraBaseUrl={jiraBaseUrl} />
              )}

              {/* Forecast Accuracy */}
              {forecastAccuracy && (
                <ForecastAccuracyChart data={forecastAccuracy} jiraBaseUrl={jiraBaseUrl} />
              )}

              {/* Throughput Chart */}
              <ThroughputChart
                data={metrics.throughput.byPeriod}
                movingAverage={metrics.throughput.movingAverage}
              />

              {/* Time in Status Chart */}
              <TimeInStatusChart data={metrics.timeInStatuses} />

              {/* By Assignee Table */}
              <AssigneeTable data={metrics.byAssignee} />

              {/* Team Velocity Chart */}
              <VelocityChart
                teamId={selectedTeamId}
                from={dateRange.from}
                to={dateRange.to}
              />

              {/* Epic Burndown Chart */}
              <EpicBurndownChart teamId={selectedTeamId} />
            </>
          ) : (
            <div className="chart-empty">No metrics data available for this period</div>
          )}

          {/* WIP History — disabled */}
        </div>
      )}

      {!loading && !error && !selectedTeamId && teams.length === 0 && (
        <div className="empty">No active teams found. Create a team in the Teams section first.</div>
      )}
    </main>
    </StatusStylesProvider>
  )
}
