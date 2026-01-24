import { useEffect, useState, useCallback, useMemo } from 'react'
import axios from 'axios'

interface ViolationDto {
  rule: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  message: string
}

interface IssueViolations {
  issueKey: string
  issueType: string
  summary: string
  status: string
  jiraUrl: string
  violations: ViolationDto[]
}

interface Summary {
  totalIssues: number
  issuesWithErrors: number
  issuesWithWarnings: number
  issuesWithInfo: number
  byRule: Record<string, number>
  bySeverity: Record<string, number>
}

interface DataQualityResponse {
  generatedAt: string
  teamId: number | null
  summary: Summary
  violations: IssueViolations[]
}

interface Team {
  id: number
  name: string
}

// Severity badge colors
const severityColors: Record<string, { bg: string; text: string; border: string }> = {
  ERROR: { bg: '#fee2e2', text: '#dc2626', border: '#fca5a5' },
  WARNING: { bg: '#fef3c7', text: '#d97706', border: '#fcd34d' },
  INFO: { bg: '#f3f4f6', text: '#6b7280', border: '#d1d5db' }
}

function SeverityBadge({ severity }: { severity: string }) {
  const colors = severityColors[severity] || severityColors.INFO
  return (
    <span
      className="severity-badge"
      style={{
        backgroundColor: colors.bg,
        color: colors.text,
        border: `1px solid ${colors.border}`,
        padding: '2px 8px',
        borderRadius: '4px',
        fontSize: '11px',
        fontWeight: 500
      }}
    >
      {severity}
    </span>
  )
}

function SummaryCard({ title, value, color }: { title: string; value: number; color: string }) {
  return (
    <div className="summary-card" style={{ borderLeftColor: color }}>
      <div className="summary-card-value" style={{ color }}>{value}</div>
      <div className="summary-card-title">{title}</div>
    </div>
  )
}

function ViolationRow({ issue }: { issue: IssueViolations }) {
  const [expanded, setExpanded] = useState(false)
  const maxSeverity = issue.violations.reduce((max, v) => {
    const order = { ERROR: 0, WARNING: 1, INFO: 2 }
    return order[v.severity] < order[max] ? v.severity : max
  }, 'INFO' as 'ERROR' | 'WARNING' | 'INFO')

  return (
    <>
      <tr
        className={`violation-row severity-${maxSeverity.toLowerCase()}`}
        onClick={() => setExpanded(!expanded)}
      >
        <td className="cell-expand">
          <button className="expander-btn">
            <span className={`chevron ${expanded ? 'expanded' : ''}`}>›</span>
          </button>
        </td>
        <td className="cell-key">
          <a href={issue.jiraUrl} target="_blank" rel="noopener noreferrer" onClick={e => e.stopPropagation()}>
            {issue.issueKey}
          </a>
        </td>
        <td className="cell-type">{issue.issueType}</td>
        <td className="cell-summary">{issue.summary}</td>
        <td className="cell-status">{issue.status}</td>
        <td className="cell-severity">
          <SeverityBadge severity={maxSeverity} />
        </td>
        <td className="cell-count">{issue.violations.length}</td>
      </tr>
      {expanded && issue.violations.map((v, i) => (
        <tr key={i} className="violation-detail-row">
          <td></td>
          <td colSpan={6}>
            <div className="violation-detail">
              <SeverityBadge severity={v.severity} />
              <span className="violation-rule">{v.rule}</span>
              <span className="violation-message">{v.message}</span>
            </div>
          </td>
        </tr>
      ))}
    </>
  )
}

export function DataQualityPage() {
  const [data, setData] = useState<DataQualityResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [severityFilter, setSeverityFilter] = useState<Set<string>>(new Set(['ERROR', 'WARNING', 'INFO']))
  const [ruleFilter, setRuleFilter] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const url = selectedTeamId
        ? `/api/data-quality?teamId=${selectedTeamId}`
        : '/api/data-quality'
      const response = await axios.get<DataQualityResponse>(url)
      setData(response.data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load data quality report')
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

  useEffect(() => {
    fetchTeams()
  }, [fetchTeams])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const filteredViolations = useMemo(() => {
    if (!data) return []
    return data.violations.filter(issue => {
      // Filter by severity
      const hasMatchingSeverity = issue.violations.some(v => severityFilter.has(v.severity))
      if (!hasMatchingSeverity) return false

      // Filter by rule
      if (ruleFilter) {
        const hasMatchingRule = issue.violations.some(v => v.rule === ruleFilter)
        if (!hasMatchingRule) return false
      }

      return true
    })
  }, [data, severityFilter, ruleFilter])

  const allRules = useMemo(() => {
    if (!data) return []
    return Object.keys(data.summary.byRule).sort()
  }, [data])

  const toggleSeverity = (severity: string) => {
    setSeverityFilter(prev => {
      const next = new Set(prev)
      if (next.has(severity)) {
        next.delete(severity)
      } else {
        next.add(severity)
      }
      return next
    })
  }

  const formatDate = (isoString: string): string => {
    const date = new Date(isoString)
    return date.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  return (
    <div className="data-quality-page">
      <div className="filter-panel">
        <div className="filter-group">
          <label className="filter-label">Team</label>
          <select
            value={selectedTeamId || ''}
            onChange={e => setSelectedTeamId(e.target.value ? Number(e.target.value) : null)}
            className="filter-select"
          >
            <option value="">All teams</option>
            {teams.map(team => (
              <option key={team.id} value={team.id}>{team.name}</option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label className="filter-label">Severity</label>
          <div className="filter-checkboxes">
            {['ERROR', 'WARNING', 'INFO'].map(severity => (
              <label key={severity} className="filter-checkbox">
                <input
                  type="checkbox"
                  checked={severityFilter.has(severity)}
                  onChange={() => toggleSeverity(severity)}
                />
                <SeverityBadge severity={severity} />
              </label>
            ))}
          </div>
        </div>

        <div className="filter-group">
          <label className="filter-label">Rule</label>
          <select
            value={ruleFilter || ''}
            onChange={e => setRuleFilter(e.target.value || null)}
            className="filter-select"
          >
            <option value="">All rules</option>
            {allRules.map(rule => (
              <option key={rule} value={rule}>{rule}</option>
            ))}
          </select>
        </div>

        <button className="btn btn-primary btn-refresh" onClick={fetchData} disabled={loading}>
          {loading ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      <main className="main-content">
        {loading && <div className="loading">Loading data quality report...</div>}
        {error && <div className="error">Error: {error}</div>}

        {!loading && !error && data && (
          <>
            <div className="summary-cards">
              <SummaryCard title="Total Issues" value={data.summary.totalIssues} color="#6b7280" />
              <SummaryCard title="Errors" value={data.summary.issuesWithErrors} color="#dc2626" />
              <SummaryCard title="Warnings" value={data.summary.issuesWithWarnings} color="#d97706" />
              <SummaryCard title="Info" value={data.summary.issuesWithInfo} color="#9ca3af" />
            </div>

            <div className="report-meta">
              Generated: {formatDate(data.generatedAt)}
              {' | '}
              Showing {filteredViolations.length} of {data.violations.length} issues
            </div>

            {filteredViolations.length === 0 ? (
              <div className="empty">
                {data.violations.length === 0
                  ? 'No data quality issues found!'
                  : 'No issues match the current filters'}
              </div>
            ) : (
              <div className="violations-table-container">
                <table className="violations-table">
                  <thead>
                    <tr>
                      <th className="th-expand"></th>
                      <th className="th-key">KEY</th>
                      <th className="th-type">TYPE</th>
                      <th className="th-summary">SUMMARY</th>
                      <th className="th-status">STATUS</th>
                      <th className="th-severity">SEVERITY</th>
                      <th className="th-count">ISSUES</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredViolations.map(issue => (
                      <ViolationRow key={issue.issueKey} issue={issue} />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  )
}
