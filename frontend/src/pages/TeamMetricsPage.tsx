import { useState, useEffect, useMemo } from 'react'
import { teamsApi, Team } from '../api/teams'
import { getWipHistory, createWipSnapshot, WipHistoryResponse } from '../api/forecast'
import { getMetricsSummary, TeamMetricsSummary, getForecastAccuracy, ForecastAccuracyResponse, getLtc, LtcResponse } from '../api/metrics'
import { getConfig } from '../api/config'
import './TeamMetricsPage.css'
import { MetricCard } from '../components/metrics/MetricCard'
import { LtcGauge } from '../components/metrics/LtcGauge'
import { ThroughputChart } from '../components/metrics/ThroughputChart'
import { TimeInStatusChart } from '../components/metrics/TimeInStatusChart'
import { AssigneeTable } from '../components/metrics/AssigneeTable'
import { ForecastAccuracyChart } from '../components/metrics/ForecastAccuracyChart'

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
          <select
            className="filter-input"
            value={days}
            onChange={e => setDays(Number(e.target.value))}
          >
            <option value={7}>7 days</option>
            <option value={14}>14 days</option>
            <option value={30}>30 days</option>
            <option value={60}>60 days</option>
            <option value={90}>90 days</option>
          </select>
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
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [metrics, setMetrics] = useState<TeamMetricsSummary | null>(null)
  const [forecastAccuracy, setForecastAccuracy] = useState<ForecastAccuracyResponse | null>(null)
  const [ltc, setLtc] = useState<LtcResponse | null>(null)
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

  // Load config for Jira URL
  useEffect(() => {
    getConfig()
      .then(config => setJiraBaseUrl(config.jiraBaseUrl))
      .catch(err => console.error('Failed to load config:', err))
  }, [])

  // Load teams
  useEffect(() => {
    teamsApi.getAll()
      .then(data => {
        const activeTeams = data.filter(t => t.active)
        setTeams(activeTeams)
        if (activeTeams.length > 0 && !selectedTeamId) {
          setSelectedTeamId(activeTeams[0].id)
        }
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load teams: ' + err.message)
        setLoading(false)
      })
  }, [])

  // Load metrics when team or filters change
  useEffect(() => {
    if (!selectedTeamId) return

    setMetricsLoading(true)

    // Load metrics, forecast accuracy, and LTC in parallel
    Promise.all([
      getMetricsSummary(
        selectedTeamId,
        dateRange.from,
        dateRange.to,
        issueType || undefined
      ),
      getForecastAccuracy(selectedTeamId, dateRange.from, dateRange.to),
      getLtc(selectedTeamId, dateRange.from, dateRange.to)
    ])
      .then(([metricsData, accuracyData, ltcData]) => {
        setMetrics(metricsData)
        setForecastAccuracy(accuracyData)
        setLtc(ltcData)
        setMetricsLoading(false)
      })
      .catch(err => {
        console.error('Failed to load metrics:', err)
        setMetrics(null)
        setForecastAccuracy(null)
        setLtc(null)
        setMetricsLoading(false)
      })
  }, [selectedTeamId, dateRange.from, dateRange.to, issueType])

  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Team Metrics</h2>
      </div>

      <div className="metrics-controls">
        <div className="filter-group">
          <label className="filter-label">Team</label>
          <select
            className="filter-input"
            value={selectedTeamId ?? ''}
            onChange={e => setSelectedTeamId(Number(e.target.value))}
          >
            <option value="" disabled>Select team...</option>
            {teams.map(team => (
              <option key={team.id} value={team.id}>{team.name}</option>
            ))}
          </select>
        </div>
        <div className="filter-group">
          <label className="filter-label">Period</label>
          <select
            className="filter-input"
            value={period}
            onChange={e => setPeriod(Number(e.target.value))}
          >
            <option value={7}>Last 7 days</option>
            <option value={14}>Last 14 days</option>
            <option value={30}>Last 30 days</option>
            <option value={60}>Last 60 days</option>
            <option value={90}>Last 90 days</option>
          </select>
        </div>
        <div className="filter-group">
          <label className="filter-label">Issue Type</label>
          <select
            className="filter-input"
            value={issueType}
            onChange={e => setIssueType(e.target.value)}
          >
            <option value="">All</option>
            <option value="Epic">Epics</option>
            <option value="Story">Stories</option>
            <option value="Sub-task">Sub-tasks</option>
          </select>
        </div>
      </div>

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
                <LtcGauge
                  value={ltc && ltc.totalEpics > 0 ? ltc.avgLtcActual : null}
                  title="LTC Actual"
                  subtitle={ltc && ltc.totalEpics > 0 ? `среднее по ${ltc.totalEpics} эпикам` : 'нет данных'}
                  tooltip="Отношение фактической длительности эпика (рабочие дни) к оценке (человеко-дни). ≤1.1 — отлично, 1.1–1.5 — допустимо, >1.5 — проблема."
                />
                <LtcGauge
                  value={ltc && ltc.totalEpics > 0 ? ltc.avgLtcForecast : null}
                  title="LTC Forecast"
                  subtitle={ltc && ltc.totalEpics > 0 ? 'точность прогноза' : 'нет данных'}
                  tooltip="Отношение фактической длительности к прогнозу из планирования. Показывает точность прогнозирования."
                />
                <MetricCard
                  title="Throughput"
                  value={metrics.throughput.total}
                  subtitle={`${metrics.throughput.totalStories} stories, ${metrics.throughput.totalEpics} epics`}
                  tooltip="Количество завершённых задач за выбранный период."
                />
                <MetricCard
                  title="On-Time Rate"
                  value={ltc && ltc.totalEpics > 0 ? `${ltc.onTimeRate.toFixed(0)}%` : '—'}
                  subtitle={ltc && ltc.totalEpics > 0 ? `${ltc.onTimeCount} из ${ltc.totalEpics} вовремя` : 'нет данных'}
                  trend={ltc && ltc.totalEpics > 0
                    ? (ltc.onTimeRate >= 80 ? 'up' : ltc.onTimeRate < 50 ? 'down' : 'neutral')
                    : undefined}
                  tooltip="Процент эпиков с LTC ≤ 1.1 — завершённых в рамках оценки."
                />
              </div>

              {/* Forecast Accuracy */}
              {forecastAccuracy && (
                <ForecastAccuracyChart data={forecastAccuracy} jiraBaseUrl={jiraBaseUrl} />
              )}

              {/* Throughput Chart */}
              <ThroughputChart data={metrics.throughput.byPeriod} />

              {/* Time in Status Chart */}
              <TimeInStatusChart data={metrics.timeInStatuses} />

              {/* By Assignee Table */}
              <AssigneeTable data={metrics.byAssignee} />
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
  )
}
