import { useEffect, useState, useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../components/board/helpers'
import { FilterBar } from '../components/FilterBar'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { FilterChip } from '../components/FilterChips'
import './DataQualityPage.css'

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
  color: string | null
}

// Human-readable Russian names for rules
const ruleLabels: Record<string, string> = {
  TIME_LOGGED_WRONG_EPIC_STATUS: 'Списание времени при неверном статусе эпика',
  TIME_LOGGED_NOT_IN_SUBTASK: 'Время списано не в подзадачу',
  CHILD_IN_PROGRESS_EPIC_NOT: 'Дочерняя задача в работе, эпик — нет',
  SUBTASK_IN_PROGRESS_STORY_NOT: 'Подзадача в работе, стори — нет',
  EPIC_NO_ESTIMATE: 'Эпик без оценки',
  SUBTASK_NO_ESTIMATE: 'Подзадача без оценки',
  SUBTASK_WORK_NO_ESTIMATE: 'Списано время без оценки',
  SUBTASK_OVERRUN: 'Превышение оценки подзадачи',
  EPIC_NO_TEAM: 'Эпик без команды',
  EPIC_TEAM_NO_MEMBERS: 'Команда эпика без участников',
  EPIC_NO_DUE_DATE: 'Эпик без дедлайна',
  EPIC_OVERDUE: 'Эпик просрочен',
  EPIC_FORECAST_LATE: 'Прогноз позже дедлайна',
  EPIC_DONE_OPEN_CHILDREN: 'Эпик закрыт, есть открытые дочерние',
  STORY_DONE_OPEN_CHILDREN: 'Стори закрыта, есть открытые подзадачи',
  EPIC_IN_PROGRESS_NO_STORIES: 'Эпик в работе без сторей',
  STORY_IN_PROGRESS_NO_SUBTASKS: 'Стори в работе без подзадач',
  STORY_NO_SUBTASK_ESTIMATES: 'Стори без оценок в подзадачах',
  STORY_BLOCKED_BY_MISSING: 'Блокировщик не найден',
  STORY_CIRCULAR_DEPENDENCY: 'Циклическая зависимость',
  STORY_BLOCKED_NO_PROGRESS: 'Блокировка без прогресса >30 дней',
  SUBTASK_DONE_NO_TIME_LOGGED: 'Подзадача закрыта без списания времени',
  SUBTASK_TIME_LOGGED_BUT_TODO: 'Списано время, но подзадача в TODO',
  BUG_SLA_BREACH: 'Баг превысил SLA',
  BUG_STALE: 'Баг без обновлений >14 дней',
  STORY_FULLY_LOGGED_NOT_DONE: 'Всё время списано, но эпик не закрыт',
}

function getRuleLabel(rule: string): string {
  return ruleLabels[rule] || rule
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
  const { getIssueTypeIconUrl } = useWorkflowConfig()
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
            <img src={getIssueIcon(issue.issueType, iconUrl)} alt={issue.issueType} className="issue-type-icon" />
            {issue.issueType}
          </span>
        </td>
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
              <span className="violation-rule">{getRuleLabel(v.rule)}</span>
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

  const teamOptions = useMemo(() =>
    teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined })),
    [teams]
  )

  const ruleOptions = useMemo(() =>
    allRules.map(r => ({ value: r, label: getRuleLabel(r) })),
    [allRules]
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
          onRemove: () => setSelectedTeamId(null),
        })
      }
    }
    if (ruleFilter) {
      result.push({
        category: 'Rule',
        value: getRuleLabel(ruleFilter),
        onRemove: () => setRuleFilter(null),
      })
    }
    for (const s of ['ERROR', 'WARNING', 'INFO']) {
      if (!severityFilter.has(s)) {
        result.push({
          category: 'Hidden',
          value: `Hide ${s}`,
          color: severityColors[s]?.text,
          onRemove: () => toggleSeverity(s),
        })
      }
    }
    return result
  }, [selectedTeamId, ruleFilter, severityFilter, teams])

  const clearAllFilters = () => {
    setSelectedTeamId(null)
    setRuleFilter(null)
    setSeverityFilter(new Set(['ERROR', 'WARNING', 'INFO']))
  }

  return (
    <div className="data-quality-page">
      <FilterBar
        chips={chips}
        onClearAll={clearAllFilters}
        trailing={
          <button className="btn btn-primary btn-refresh" onClick={fetchData} disabled={loading}>
            {loading ? 'Загрузка...' : 'Обновить'}
          </button>
        }
      >
        <SingleSelectDropdown
          label="Команда"
          options={teamOptions}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={v => setSelectedTeamId(v ? Number(v) : null)}
          placeholder="Все команды"
        />

        <div className="filter-checkboxes" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {['ERROR', 'WARNING', 'INFO'].map(severity => (
            <button
              key={severity}
              className={`btn btn-sm btn-toggle ${severityFilter.has(severity) ? 'btn-toggle-active' : ''}`}
              onClick={() => toggleSeverity(severity)}
              style={severityFilter.has(severity) ? {
                backgroundColor: severityColors[severity]?.bg,
                borderColor: severityColors[severity]?.border,
                color: severityColors[severity]?.text,
              } : undefined}
            >
              {severity}
            </button>
          ))}
        </div>

        <SingleSelectDropdown
          label="Правило"
          options={ruleOptions}
          selected={ruleFilter}
          onChange={v => setRuleFilter(v)}
          placeholder="Все правила"
        />
      </FilterBar>

      <main className="main-content">
        {loading && <div className="loading">Загрузка отчёта...</div>}
        {error && <div className="error">Ошибка: {error}</div>}

        {!loading && !error && data && (
          <>
            <div className="summary-cards">
              <SummaryCard title="Всего задач" value={data.summary.totalIssues} color="#6b7280" />
              <SummaryCard title="Ошибки" value={data.summary.issuesWithErrors} color="#dc2626" />
              <SummaryCard title="Предупреждения" value={data.summary.issuesWithWarnings} color="#d97706" />
              <SummaryCard title="Информация" value={data.summary.issuesWithInfo} color="#9ca3af" />
            </div>

            <div className="report-meta">
              Сформировано: {formatDate(data.generatedAt)}
              {' | '}
              Показано {filteredViolations.length} из {data.violations.length} задач
            </div>

            {filteredViolations.length === 0 ? (
              <div className="empty">
                {data.violations.length === 0
                  ? 'Проблем с качеством данных не найдено!'
                  : 'Нет задач, соответствующих фильтрам'}
              </div>
            ) : (
              <div className="violations-table-container">
                <table className="violations-table">
                  <thead>
                    <tr>
                      <th className="th-expand"></th>
                      <th className="th-key">КЛЮЧ</th>
                      <th className="th-type">ТИП</th>
                      <th className="th-summary">НАЗВАНИЕ</th>
                      <th className="th-status">СТАТУС</th>
                      <th className="th-severity">КРИТИЧНОСТЬ</th>
                      <th className="th-count">ПРОБЛЕМЫ</th>
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
