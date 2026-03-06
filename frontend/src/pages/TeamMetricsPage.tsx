import { useState, useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import { teamsApi, Team } from '../api/teams'
import { getWipHistory, createWipSnapshot, WipHistoryResponse } from '../api/forecast'
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

// --- WIP History Chart Component ---

interface WipHistoryChartProps {
  teamId: number
}

export function WipHistoryChart({ teamId }: WipHistoryChartProps) {
  const [history, setHistory] = useState<WipHistoryResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [days, setDays] = useState(30)

  const loadHistory = () => {
    setLoading(true)
    setError(null)
    getWipHistory(teamId, days)
      .then(data => {
        setHistory(data)
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load WIP history: ' + err.message)
        setLoading(false)
      })
  }

  useEffect(() => {
    loadHistory()
  }, [teamId, days])

  const handleCreateSnapshot = async () => {
    try {
      await createWipSnapshot(teamId)
      loadHistory() // Reload after creating snapshot
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Unknown error'
      setError('Failed to create snapshot: ' + message)
    }
  }

  const chartData = useMemo(() => {
    if (!history || history.dataPoints.length === 0) return null

    const points = history.dataPoints
    const maxValue = Math.max(
      ...points.map(p => Math.max(
        p.teamCurrent,
        p.teamLimit,
        p.totalEpics ?? 0
      ))
    )

    return { points, maxValue: Math.max(maxValue, 5) }
  }, [history])

  if (loading) {
    return (
      <div className="wip-history-section">
        <div className="wip-history-header">
          <h3>WIP History</h3>
        </div>
        <div className="loading">Loading WIP history...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="wip-history-section">
        <div className="wip-history-header">
          <h3>WIP History</h3>
        </div>
        <div className="error">{error}</div>
      </div>
    )
  }

  return (
    <div className="wip-history-section">
      <div className="wip-history-header">
        <h3>WIP History (Last {days} Days)</h3>
        <div className="wip-history-controls">
          <SingleSelectDropdown
            label="Period"
            options={[
              { value: '7', label: '7 days' },
              { value: '14', label: '14 days' },
              { value: '30', label: '30 days' },
              { value: '60', label: '60 days' },
              { value: '90', label: '90 days' },
            ]}
            selected={String(days)}
            onChange={v => v && setDays(Number(v))}
            allowClear={false}
          />
          <button
            className="btn btn-secondary"
            onClick={handleCreateSnapshot}
            title="Create a snapshot for today"
          >
            + Snapshot
          </button>
        </div>
      </div>

      {!chartData || chartData.points.length === 0 ? (
        <div className="wip-history-empty">
          <p>No WIP history data available.</p>
          <p className="wip-history-hint">
            WIP snapshots are created daily. Click "+ Snapshot" to create one now.
          </p>
        </div>
      ) : (
        <div className="wip-history-chart">
          {/* Y-axis labels */}
          <div className="wip-chart-y-axis">
            {[...Array(6)].map((_, i) => {
              const value = Math.round((chartData.maxValue / 5) * (5 - i))
              return <div key={i} className="wip-chart-y-label">{value}</div>
            })}
          </div>

          {/* Chart area */}
          <div className="wip-chart-area">
            {/* Horizontal grid lines */}
            <div className="wip-chart-grid">
              {[...Array(6)].map((_, i) => (
                <div key={i} className="wip-chart-grid-line" />
              ))}
            </div>

            {/* Data bars */}
            <div className="wip-chart-bars">
              {chartData.points.map((point, i) => {
                const currentHeight = (point.teamCurrent / chartData.maxValue) * 100
                const limitHeight = (point.teamLimit / chartData.maxValue) * 100
                const queueHeight = point.inQueue ? (point.inQueue / chartData.maxValue) * 100 : 0
                const isExceeded = point.teamCurrent > point.teamLimit

                return (
                  <div key={i} className="wip-chart-bar-group" title={`${point.date}\nWIP: ${point.teamCurrent}/${point.teamLimit}\nIn Queue: ${point.inQueue ?? 0}`}>
                    {/* Limit line */}
                    <div
                      className="wip-chart-limit-line"
                      style={{ bottom: `${limitHeight}%` }}
                    />
                    {/* Queue bar (stacked) */}
                    {queueHeight > 0 && (
                      <div
                        className="wip-chart-bar wip-chart-bar-queue"
                        style={{ height: `${queueHeight}%`, bottom: `${currentHeight}%` }}
                      />
                    )}
                    {/* Current WIP bar */}
                    <div
                      className={`wip-chart-bar wip-chart-bar-current ${isExceeded ? 'wip-chart-bar-exceeded' : ''}`}
                      style={{ height: `${currentHeight}%` }}
                    />
                  </div>
                )
              })}
            </div>

            {/* X-axis labels */}
            <div className="wip-chart-x-axis">
              {chartData.points.map((point, i) => {
                // Show every Nth label to avoid crowding
                const interval = chartData.points.length > 14 ? 7 : chartData.points.length > 7 ? 3 : 1
                if (i % interval !== 0 && i !== chartData.points.length - 1) {
                  return <div key={i} className="wip-chart-x-label" />
                }
                const date = new Date(point.date)
                const label = date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
                return <div key={i} className="wip-chart-x-label">{label}</div>
              })}
            </div>
          </div>

          {/* Legend */}
          <div className="wip-chart-legend">
            <span className="wip-legend-item wip-legend-current">Active WIP</span>
            <span className="wip-legend-item wip-legend-queue">In Queue</span>
            <span className="wip-legend-item wip-legend-limit">WIP Limit</span>
            <span className="wip-legend-item wip-legend-exceeded">Exceeded</span>
          </div>
        </div>
      )}
    </div>
  )
}

// --- Main Team Metrics Page ---

export function TeamMetricsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { issueTypeCategories } = useWorkflowConfig()
  const [teams, setTeams] = useState<Team[]>([])
  const [metrics, setMetrics] = useState<TeamMetricsSummary | null>(null)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})

  // Sync teamId with URL (validate to avoid NaN)
  const rawTeamId = searchParams.get('teamId')
  const selectedTeamId = rawTeamId ? (isNaN(Number(rawTeamId)) ? null : Number(rawTeamId)) : null
  const setSelectedTeamId = (id: number | null) => {
    if (id) {
      setSearchParams({ teamId: String(id) })
    } else {
      setSearchParams({})
    }
  }
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
        const urlTeamId = searchParams.get('teamId')
        if (activeTeams.length > 0 && !urlTeamId) {
          setSelectedTeamId(activeTeams[0].id)
        }
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load teams: ' + err.message)
        setLoading(false)
      })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []) // Run once on mount

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
                  subtitle={dsr && dsr.totalEpics > 0 ? `среднее по ${dsr.totalEpics} эпикам` : 'нет данных'}
                  tooltip="DSR — относительная скорость доставки эпика. Формула: (рабочие дни − дни паузы) / оценка в днях. 1.0 — норма, < 1.0 — быстрее, > 1.0 — медленнее. Пауза (флаг) останавливает таймер."
                />
                <DsrGauge
                  value={dsr && dsr.totalEpics > 0 ? dsr.avgDsrForecast : null}
                  title="DSR Forecast"
                  subtitle={dsr && dsr.totalEpics > 0 ? 'точность прогноза' : 'нет данных'}
                  tooltip="Отношение фактической длительности к прогнозу из планирования. Показывает точность прогнозирования."
                />
                <MetricCard
                  title="Throughput"
                  value={metrics.throughput.total}
                  subtitle={`${metrics.throughput.totalStories} stories, ${metrics.throughput.totalEpics} epics${metrics.throughput.totalBugs > 0 ? `, ${metrics.throughput.totalBugs} bugs` : ''}`}
                  tooltip="Количество завершённых задач за выбранный период."
                />
                <MetricCard
                  title="Within Estimate"
                  value={dsr && dsr.totalEpics > 0 ? `${dsr.onTimeRate.toFixed(0)}%` : '—'}
                  subtitle={dsr && dsr.totalEpics > 0 ? `${dsr.onTimeCount} из ${dsr.totalEpics} в рамках оценки` : 'нет данных'}
                  trend={dsr && dsr.totalEpics > 0
                    ? (dsr.onTimeRate >= 80 ? 'up' : dsr.onTimeRate < 50 ? 'down' : 'neutral')
                    : undefined}
                  tooltip="Процент эпиков с DSR ≤ 1.1 — уложившихся в оценку трудозатрат (не про дедлайны)."
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
