import { useEffect, useState, useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { MetricCard } from '../components/metrics/MetricCard'
import { StatusBadge } from '../components/board/StatusBadge'
import { fetchBugMetrics, BugMetricsResponse } from '../api/metrics'
import { getPriorityColor } from '../helpers/priorityColors'
import { FilterBar } from '../components/FilterBar'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { FilterChip } from '../components/FilterChips'
import './BugMetricsPage.css'

interface Team {
  id: number
  name: string
  color: string | null
}

function formatHours(hours: number): string {
  if (hours < 24) return `${hours}h`
  const days = Math.floor(hours / 24)
  const h = hours % 24
  return h > 0 ? `${days}d ${h}h` : `${days}d`
}

export function BugMetricsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [data, setData] = useState<BugMetricsResponse | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const selectedTeamId = searchParams.get('teamId') ? Number(searchParams.get('teamId')) : undefined

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await fetchBugMetrics(selectedTeamId)
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load bug metrics')
    } finally {
      setLoading(false)
    }
  }, [selectedTeamId])

  const fetchTeams = useCallback(async () => {
    try {
      const response = await axios.get<Team[]>('/api/teams')
      setTeams(response.data)
    } catch (err) {
      console.error('Failed to load teams:', err)
    }
  }, [])

  useEffect(() => { fetchTeams() }, [fetchTeams])
  useEffect(() => { fetchData() }, [fetchData])

  const teamOptions = useMemo(() =>
    teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined })),
    [teams]
  )

  const chips = useMemo(() => {
    const result: FilterChip[] = []
    if (selectedTeamId) {
      const team = teams.find(t => t.id === selectedTeamId)
      if (team) {
        result.push({
          category: 'Team',
          value: team.name,
          color: team.color || undefined,
          onRemove: () => setSearchParams({}),
        })
      }
    }
    return result
  }, [selectedTeamId, teams, setSearchParams])

  if (loading) {
    return <div className="bug-metrics-page"><div className="loading">Loading bug metrics...</div></div>
  }

  if (error) {
    return <div className="bug-metrics-page"><div className="error-message">{error}</div></div>
  }

  if (!data) return null

  return (
    <div className="bug-metrics-page">
      <div className="bug-metrics-header">
        <h2>Bug Metrics</h2>
      </div>

      <FilterBar
        chips={chips}
        onClearAll={() => setSearchParams({})}
      >
        <SingleSelectDropdown
          label="Team"
          options={teamOptions}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={v => {
            if (v) setSearchParams({ teamId: v })
            else setSearchParams({})
          }}
          placeholder="All Teams"
        />
      </FilterBar>

      <div className="metrics-summary-cards">
        <MetricCard
          title="Open Bugs"
          value={data.openBugs}
          trend={data.openBugs > 0 ? 'down' : 'neutral'}
          tooltip="Total number of unresolved bugs"
        />
        <MetricCard
          title="SLA Compliance"
          value={`${data.slaCompliancePercent}%`}
          trend={data.slaCompliancePercent >= 90 ? 'up' : data.slaCompliancePercent >= 70 ? 'neutral' : 'down'}
          tooltip="Percentage of resolved bugs that met their SLA target"
        />
        <MetricCard
          title="Avg Resolution"
          value={formatHours(data.avgResolutionHours)}
          tooltip="Average time to resolve a bug from creation to done"
        />
        <MetricCard
          title="Stale Bugs"
          value={data.staleBugs}
          trend={data.staleBugs > 0 ? 'down' : 'up'}
          tooltip="Open bugs with no updates for more than 14 days"
        />
      </div>

      {data.byPriority.length > 0 && (
        <div className="bug-metrics-section">
          <h3>By Priority</h3>
          <table className="bug-metrics-table">
            <thead>
              <tr>
                <th>Priority</th>
                <th>Open</th>
                <th>Resolved</th>
                <th>Avg Resolution</th>
                <th>SLA Limit</th>
                <th>Compliance</th>
              </tr>
            </thead>
            <tbody>
              {data.byPriority.map(p => (
                <tr key={p.priority}>
                  <td>
                    <span className="priority-dot" style={{ backgroundColor: getPriorityColor(p.priority) }} />
                    {p.priority}
                  </td>
                  <td>{p.openCount}</td>
                  <td>{p.resolvedCount}</td>
                  <td>{p.resolvedCount > 0 ? formatHours(p.avgResolutionHours) : '—'}</td>
                  <td>{p.slaLimitHours != null ? formatHours(p.slaLimitHours) : '—'}</td>
                  <td>
                    {p.resolvedCount > 0 ? (
                      <span className={`compliance-badge ${p.slaCompliancePercent >= 90 ? 'good' : p.slaCompliancePercent >= 70 ? 'warn' : 'bad'}`}>
                        {p.slaCompliancePercent}%
                      </span>
                    ) : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data.openBugList.length > 0 && (
        <div className="bug-metrics-section">
          <h3>Open Bugs ({data.openBugList.length})</h3>
          <table className="bug-metrics-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Summary</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Age</th>
                <th>SLA</th>
              </tr>
            </thead>
            <tbody>
              {data.openBugList.map(bug => (
                <tr key={bug.issueKey}>
                  <td>
                    <a href={bug.jiraUrl} target="_blank" rel="noopener noreferrer" className="bug-key-link">
                      {bug.issueKey}
                    </a>
                  </td>
                  <td className="bug-summary-cell">{bug.summary}</td>
                  <td>
                    <span className="priority-dot" style={{ backgroundColor: getPriorityColor(bug.priority) }} />
                    {bug.priority}
                  </td>
                  <td><StatusBadge status={bug.status} /></td>
                  <td>{bug.ageDays}d</td>
                  <td>
                    <span className={`sla-badge ${bug.slaBreach ? 'breach' : 'ok'}`}>
                      {bug.slaBreach ? 'Breach' : 'OK'}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data.openBugs === 0 && data.resolvedBugs === 0 && (
        <div className="empty-state">No bugs found. Configure bug types in Workflow Config.</div>
      )}
    </div>
  )
}
