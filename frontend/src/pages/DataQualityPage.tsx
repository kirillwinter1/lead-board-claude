import { useEffect, useState, useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../components/board/helpers'
import { getStatusStyles, type StatusStyle } from '../api/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { StatusBadge } from '../components/board/StatusBadge'
import { SeverityBadge, SEVERITY_COLORS } from '../components/SeverityBadge'
import { FilterBar } from '../components/FilterBar'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { FilterChip } from '../components/FilterChips'
import './DataQualityPage.css'

interface ViolationDto {
  rule: string
  severity: 'ERROR' | 'WARNING' | 'INFO'
  message: string
  label: string
  category: string
  categoryLabel: string
}

interface IssueViolations {
  issueKey: string
  issueType: string
  summary: string
  status: string
  jiraUrl: string
  violations: ViolationDto[]
}

// Catalog entry for ALL rules (not only violated ones) — drives filter options
interface RuleCatalogEntry {
  name: string
  label: string
  category: string
  categoryLabel: string
  severity: string
}

interface Summary {
  totalIssues: number
  issuesWithErrors: number
  issuesWithWarnings: number
  issuesWithInfo: number
  byRule: Record<string, number>
  bySeverity: Record<string, number>
  byCategory: Record<string, number>
}

interface DataQualityResponse {
  generatedAt: string
  teamId: number | null
  summary: Summary
  rules: RuleCatalogEntry[]
  violations: IssueViolations[]
}

interface Team {
  id: number
  name: string
  color: string | null
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
  const { getIssueTypeIconUrl, getIssueTypeCategory } = useWorkflowConfig()
  const iconUrl = getIssueTypeIconUrl(issue.issueType)
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
        <td className="cell-type">
          <span className="issue-type-cell">
            <img src={getIssueIcon(issue.issueType, iconUrl, getIssueTypeCategory(issue.issueType))} alt={issue.issueType} className="issue-type-icon" />
            {issue.issueType}
          </span>
        </td>
        <td className="cell-summary">{issue.summary}</td>
        <td className="cell-status"><StatusBadge status={issue.status} /></td>
        <td className="cell-severity">
          <SeverityBadge severity={maxSeverity} />
        </td>
        <td className="cell-count">{issue.violations.length}</td>
      </tr>
      {expanded && issue.violations.map((v) => (
        <tr key={`${issue.issueKey}-${v.rule}-${v.severity}`} className="violation-detail-row">
          <td></td>
          <td colSpan={6}>
            <div className="violation-detail">
              <SeverityBadge severity={v.severity} />
              {v.categoryLabel && <span className="violation-category">{v.categoryLabel}</span>}
              <span className="violation-rule">{v.label}</span>
            </div>
          </td>
        </tr>
      ))}
    </>
  )
}

export function DataQualityPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [data, setData] = useState<DataQualityResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [teams, setTeams] = useState<Team[]>([])
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [severityFilter, setSeverityFilter] = useState<Set<string>>(new Set(['ERROR', 'WARNING', 'INFO']))

  // Sync teamId with URL
  const selectedTeamId = searchParams.get('teamId') ? Number(searchParams.get('teamId')) : null
  const setSelectedTeamId = (id: number | null) => {
    if (id) {
      setSearchParams({ teamId: String(id) })
    } else {
      setSearchParams({})
    }
  }
  const [ruleFilter, setRuleFilter] = useState<string | null>(null)
  const [categoryFilter, setCategoryFilter] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const url = selectedTeamId
        ? `/api/data-quality?teamId=${selectedTeamId}`
        : '/api/data-quality'
      const response = await axios.get<DataQualityResponse>(url)
      setData(response.data)
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setError(`Failed to load data quality report: ${err.response?.status} ${err.response?.statusText || err.message}`)
      } else {
        setError(err instanceof Error ? err.message : 'Failed to load data quality report')
      }
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
    getStatusStyles().then(setStatusStyles).catch(() => {})
  }, [])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  const filteredViolations = useMemo(() => {
    if (!data) return []
    return data.violations.filter(issue => {
      // Filter by severity
      const hasMatchingSeverity = issue.violations.some(v => severityFilter.has(v.severity))
      if (!hasMatchingSeverity) return false

      // Filter by category
      if (categoryFilter) {
        const hasMatchingCategory = issue.violations.some(v => v.category === categoryFilter)
        if (!hasMatchingCategory) return false
      }

      // Filter by rule
      if (ruleFilter) {
        const hasMatchingRule = issue.violations.some(v => v.rule === ruleFilter)
        if (!hasMatchingRule) return false
      }

      return true
    })
  }, [data, severityFilter, ruleFilter, categoryFilter])

  // Lookup: rule name -> catalog entry (for narrowing / reset logic)
  const rulesByName = useMemo(() => {
    const m = new Map<string, RuleCatalogEntry>()
    for (const r of data?.rules || []) m.set(r.name, r)
    return m
  }, [data])

  // Lookup: category code -> human-readable label
  const categoryLabelByCode = useMemo(() => {
    const m: Record<string, string> = {}
    for (const r of data?.rules || []) m[r.category] = r.categoryLabel
    return m
  }, [data])

  // Selecting a category filters the table AND narrows the rule dropdown.
  // If the current rule filter is not part of the chosen category, reset it.
  const handleCategoryChange = useCallback((cat: string | null) => {
    setCategoryFilter(cat)
    if (cat) {
      setRuleFilter(prev => {
        if (!prev) return prev
        const entry = rulesByName.get(prev)
        return entry && entry.category !== cat ? null : prev
      })
    }
  }, [rulesByName])

  const toggleCategory = useCallback((cat: string) => {
    handleCategoryChange(categoryFilter === cat ? null : cat)
  }, [categoryFilter, handleCategoryChange])

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
    return date.toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  }

  const teamOptions = useMemo(() =>
    teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined })),
    [teams]
  )

  // Category options: unique categories from the rules catalog, sorted by label
  const categoryOptions = useMemo(() => {
    if (!data) return []
    const seen = new Map<string, string>()
    for (const r of data.rules) {
      if (!seen.has(r.category)) seen.set(r.category, r.categoryLabel)
    }
    return Array.from(seen.entries())
      .map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label))
  }, [data])

  // Rule options: rules that have violations (present in summary.byRule),
  // narrowed to the selected category, labelled from the catalog, sorted by label
  const ruleOptions = useMemo(() => {
    if (!data) return []
    const byRule = data.summary.byRule
    return data.rules
      .filter(r => r.name in byRule)
      .filter(r => !categoryFilter || r.category === categoryFilter)
      .map(r => ({ value: r.name, label: r.label }))
      .sort((a, b) => a.label.localeCompare(b.label))
  }, [data, categoryFilter])

  // Category summary: chips "CategoryLabel N" from summary.byCategory, sorted by count desc
  const categorySummary = useMemo(() => {
    if (!data) return []
    return Object.entries(data.summary.byCategory || {})
      .map(([code, count]) => ({ code, count, label: categoryLabelByCode[code] || code }))
      .sort((a, b) => b.count - a.count)
  }, [data, categoryLabelByCode])

  const chips = useMemo(() => {
    const result: FilterChip[] = []
    if (selectedTeamId) {
      const team = teams.find(t => t.id === selectedTeamId)
      if (team) {
        result.push({
          category: 'Team',
          value: team.name,
          color: team.color || undefined,
          onRemove: () => setSelectedTeamId(null),
        })
      }
    }
    if (categoryFilter) {
      result.push({
        category: 'Category',
        value: categoryLabelByCode[categoryFilter] || categoryFilter,
        onRemove: () => handleCategoryChange(null),
      })
    }
    if (ruleFilter) {
      result.push({
        category: 'Rule',
        value: rulesByName.get(ruleFilter)?.label || ruleFilter,
        onRemove: () => setRuleFilter(null),
      })
    }
    for (const s of ['ERROR', 'WARNING', 'INFO']) {
      if (!severityFilter.has(s)) {
        result.push({
          category: 'Hidden',
          value: `Hide ${s}`,
          color: SEVERITY_COLORS[s]?.text,
          onRemove: () => toggleSeverity(s),
        })
      }
    }
    return result
  }, [selectedTeamId, ruleFilter, categoryFilter, severityFilter, teams, categoryLabelByCode, rulesByName, handleCategoryChange])

  const clearAllFilters = () => {
    setSelectedTeamId(null)
    setRuleFilter(null)
    setCategoryFilter(null)
    setSeverityFilter(new Set(['ERROR', 'WARNING', 'INFO']))
  }

  return (
    <StatusStylesProvider value={statusStyles}>
    <div className="data-quality-page">
      <FilterBar
        chips={chips}
        onClearAll={clearAllFilters}
        trailing={
          <button className="btn btn-primary btn-refresh" onClick={fetchData} disabled={loading}>
            {loading ? 'Loading...' : 'Refresh'}
          </button>
        }
      >
        <SingleSelectDropdown
          label="Team"
          options={teamOptions}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={v => setSelectedTeamId(v ? Number(v) : null)}
          placeholder="All teams"
        />

        <div className="filter-checkboxes" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {['ERROR', 'WARNING', 'INFO'].map(severity => (
            <button
              key={severity}
              className={`btn btn-sm btn-toggle ${severityFilter.has(severity) ? 'btn-toggle-active' : ''}`}
              onClick={() => toggleSeverity(severity)}
              style={severityFilter.has(severity) ? {
                backgroundColor: SEVERITY_COLORS[severity]?.bg,
                borderColor: SEVERITY_COLORS[severity]?.border,
                color: SEVERITY_COLORS[severity]?.text,
              } : undefined}
            >
              {severity}
            </button>
          ))}
        </div>

        <SingleSelectDropdown
          label="Category"
          options={categoryOptions}
          selected={categoryFilter}
          onChange={handleCategoryChange}
          placeholder="All categories"
        />

        <SingleSelectDropdown
          label="Rule"
          options={ruleOptions}
          selected={ruleFilter}
          onChange={v => setRuleFilter(v)}
          placeholder="All rules"
        />
      </FilterBar>

      <main className="main-content">
        {loading && <div className="loading">Loading report...</div>}
        {error && <div className="error">Error: {error}</div>}

        {!loading && !error && data && (
          <>
            <div className="summary-cards">
              <SummaryCard title="Total Issues" value={data.summary.totalIssues} color="#6b7280" />
              <SummaryCard title="Errors" value={data.summary.issuesWithErrors} color="#dc2626" />
              <SummaryCard title="Warnings" value={data.summary.issuesWithWarnings} color="#d97706" />
              <SummaryCard title="Info" value={data.summary.issuesWithInfo} color="#9ca3af" />
            </div>

            {categorySummary.length > 0 && (
              <div className="category-summary-row">
                {categorySummary.map(cat => (
                  <button
                    key={cat.code}
                    type="button"
                    className={`category-chip ${categoryFilter === cat.code ? 'active' : ''}`}
                    onClick={() => toggleCategory(cat.code)}
                    aria-pressed={categoryFilter === cat.code}
                  >
                    <span className="category-chip-label">{cat.label}</span>
                    <span className="category-chip-count">{cat.count}</span>
                  </button>
                ))}
              </div>
            )}

            <div className="report-meta">
              Generated: {formatDate(data.generatedAt)}
              {' | '}
              Showing {filteredViolations.length} of {data.violations.length} issues
            </div>

            {filteredViolations.length === 0 ? (
              <div className="empty">
                {data.violations.length === 0
                  ? 'No data quality issues found!'
                  : 'No issues matching filters'}
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
    </StatusStylesProvider>
  )
}
