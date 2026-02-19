import { useEffect, useState } from 'react'
import { projectsApi, ProjectDto, ProjectDetailDto, ProjectRecommendation } from '../api/projects'
import { getStatusStyles, StatusStyle } from '../api/board'
import { getConfig } from '../api/config'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { StatusBadge } from '../components/board/StatusBadge'
import { MultiSelectDropdown } from '../components/MultiSelectDropdown'
import { TeamBadge } from '../components/TeamBadge'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { getIssueIcon } from '../components/board/helpers'
import { RiceForm } from '../components/rice/RiceForm'
import { RiceScoreBadge } from '../components/rice/RiceScoreBadge'
import { Modal } from '../components/Modal'

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  return d.toLocaleDateString('ru-RU', { day: '2-digit', month: 'short' })
}

function formatHours(seconds: number | null): string {
  if (!seconds || seconds <= 0) return '0h'
  const hours = Math.round(seconds / 3600)
  if (hours < 8) return `${hours}h`
  const days = Math.round(hours / 8)
  return `${days}d`
}


function ProgressBar({ percent, width = 100 }: { percent: number; width?: number }) {
  const clampedPercent = Math.max(0, Math.min(percent, 100))
  const color = clampedPercent >= 100 ? '#36B37E' : '#0065FF'
  return (
    <div style={{
      width,
      height: 6,
      background: '#DFE1E6',
      borderRadius: 3,
      overflow: 'hidden',
      flexShrink: 0,
    }}>
      {clampedPercent > 0 && (
        <div style={{
          width: `${clampedPercent}%`,
          minWidth: 2,
          height: '100%',
          background: color,
          borderRadius: 3,
        }} />
      )}
    </div>
  )
}

function AlignmentBadge({ delayDays }: { delayDays: number | null }) {
  if (delayDays == null) {
    return <span style={{ color: '#97A0AF' }}>—</span>
  }
  if (delayDays > 2) {
    return (
      <span title={`${delayDays}d behind average`} style={{
        display: 'inline-flex', alignItems: 'center', gap: 3,
        color: '#FF8B00', fontWeight: 600, fontSize: 12,
      }}>
        &#9888; +{delayDays}d
      </span>
    )
  }
  return (
    <span title="On track" style={{ color: '#36B37E', fontWeight: 600, fontSize: 12 }}>
      &#10003;
    </span>
  )
}

function JiraLink({ issueKey, jiraBaseUrl }: { issueKey: string; jiraBaseUrl: string }) {
  if (!jiraBaseUrl) {
    return <span style={{ fontSize: 13, color: '#0052CC', fontWeight: 600 }}>{issueKey}</span>
  }
  return (
    <a
      href={`${jiraBaseUrl}${issueKey}`}
      target="_blank"
      rel="noopener noreferrer"
      onClick={e => e.stopPropagation()}
      style={{ fontSize: 13, color: '#0052CC', fontWeight: 600, textDecoration: 'none' }}
      onMouseEnter={e => (e.currentTarget.style.textDecoration = 'underline')}
      onMouseLeave={e => (e.currentTarget.style.textDecoration = 'none')}
    >
      {issueKey}
    </a>
  )
}

function AssigneeBadge({ name, avatarUrl }: { name: string; avatarUrl: string | null }) {
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 5,
      fontSize: 12,
      color: '#42526E',
      background: '#F4F5F7',
      padding: '2px 8px 2px 3px',
      borderRadius: 12,
      whiteSpace: 'nowrap',
      flexShrink: 0,
    }}>
      {avatarUrl ? (
        <img
          src={avatarUrl}
          alt={name}
          style={{ width: 20, height: 20, borderRadius: '50%', flexShrink: 0 }}
        />
      ) : (
        <span style={{
          width: 20,
          height: 20,
          borderRadius: '50%',
          background: '#0052CC',
          color: '#fff',
          fontSize: 10,
          fontWeight: 600,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}>
          {name.charAt(0).toUpperCase()}
        </span>
      )}
      {name}
    </span>
  )
}

function RecommendationsBlock({ recommendations }: { recommendations: ProjectRecommendation[] }) {
  if (recommendations.length === 0) return null
  return (
    <div style={{
      margin: '12px 0 4px',
      padding: '10px 14px',
      background: '#FFFAE6',
      border: '1px solid #FFE380',
      borderRadius: 6,
      fontSize: 13,
    }}>
      {recommendations.map((r, i) => (
        <div key={i} style={{ display: 'flex', gap: 6, marginBottom: i < recommendations.length - 1 ? 6 : 0 }}>
          <span style={{ flexShrink: 0 }}>
            {r.severity === 'WARNING' ? '\u26A0\uFE0F' : '\u2139\uFE0F'}
          </span>
          <span style={{ color: '#172B4D' }}>{r.message}</span>
        </div>
      ))}
    </div>
  )
}

function SearchIcon() {
  return (
    <svg className="search-icon" width="16" height="16" viewBox="0 0 16 16" fill="none">
      <path d="M7 12C9.76142 12 12 9.76142 12 7C12 4.23858 9.76142 2 7 2C4.23858 2 2 4.23858 2 7C2 9.76142 4.23858 12 7 12Z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M14 14L10.5 10.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function ProjectsPage() {
  const [projects, setProjects] = useState<ProjectDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [expandedKey, setExpandedKey] = useState<string | null>(null)
  const [detail, setDetail] = useState<ProjectDetailDto | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})
  const [recommendations, setRecommendations] = useState<ProjectRecommendation[]>([])
  const [riceModalKey, setRiceModalKey] = useState<string | null>(null)
  const { getIssueTypeIconUrl } = useWorkflowConfig()
  const [jiraBaseUrl, setJiraBaseUrl] = useState('')
  const [search, setSearch] = useState('')
  const [selectedPMs, setSelectedPMs] = useState<Set<string>>(new Set())
  const [selectedStatuses, setSelectedStatuses] = useState<Set<string>>(new Set())

  useEffect(() => {
    projectsApi.list()
      .then(setProjects)
      .catch(() => setError('Failed to load projects'))
      .finally(() => setLoading(false))
    getStatusStyles().then(setStatusStyles).catch(() => {})
    getConfig().then(c => setJiraBaseUrl(c.jiraBaseUrl || '')).catch(() => {})
  }, [])

  const handleToggle = async (issueKey: string) => {
    if (expandedKey === issueKey) {
      setExpandedKey(null)
      setDetail(null)
      setRecommendations([])
      return
    }
    setExpandedKey(issueKey)
    setDetail(null)
    setRecommendations([])
    setDetailLoading(true)
    try {
      const [data, recs] = await Promise.all([
        projectsApi.getDetail(issueKey),
        projectsApi.getRecommendations(issueKey).catch(() => [] as ProjectRecommendation[]),
      ])
      setDetail(data)
      setRecommendations(recs)
    } catch {
      setDetail(null)
    } finally {
      setDetailLoading(false)
    }
  }

  const availablePMs = Array.from(new Set(
    projects.map(p => p.assigneeDisplayName).filter((n): n is string => !!n)
  )).sort()
  const availableStatuses = Array.from(new Set(projects.map(p => p.status))).sort()

  const handlePMToggle = (pm: string) => {
    setSelectedPMs(prev => {
      const next = new Set(prev)
      next.has(pm) ? next.delete(pm) : next.add(pm)
      return next
    })
  }
  const handleStatusToggle = (status: string) => {
    setSelectedStatuses(prev => {
      const next = new Set(prev)
      next.has(status) ? next.delete(status) : next.add(status)
      return next
    })
  }
  const clearFilters = () => {
    setSearch('')
    setSelectedPMs(new Set())
    setSelectedStatuses(new Set())
  }
  const hasActiveFilters = search || selectedPMs.size > 0 || selectedStatuses.size > 0

  const filteredProjects = projects.filter(p => {
    if (selectedPMs.size > 0 && (!p.assigneeDisplayName || !selectedPMs.has(p.assigneeDisplayName))) return false
    if (selectedStatuses.size > 0 && !selectedStatuses.has(p.status)) return false
    if (search) {
      const q = search.toLowerCase()
      if (!p.issueKey.toLowerCase().includes(q) && !p.summary.toLowerCase().includes(q)) return false
    }
    return true
  })

  if (loading) {
    return (
      <main className="main-content">
        <div style={{ padding: 32, textAlign: 'center', color: '#6B778C' }}>
          Loading projects...
        </div>
      </main>
    )
  }

  if (error) {
    return (
      <main className="main-content">
        <div style={{ padding: 32, textAlign: 'center', color: '#DE350B' }}>
          {error}
        </div>
      </main>
    )
  }

  if (projects.length === 0) {
    return (
      <main className="main-content">
        <div style={{ padding: 32, textAlign: 'center', color: '#6B778C' }}>
          <h2 style={{ margin: '0 0 8px', color: '#172B4D' }}>No Projects Found</h2>
          <p>Configure a PROJECT issue type in Workflow Config and sync from Jira to see projects here.</p>
        </div>
      </main>
    )
  }

  return (
    <StatusStylesProvider value={statusStyles}>
      <main className="main-content">
        <div style={{ padding: '16px 24px' }}>
          <div className="page-header" style={{ marginBottom: 16 }}>
            <h2>Projects ({filteredProjects.length})</h2>
          </div>

          <div className="filter-panel">
            <div className="filter-group filter-search">
              <SearchIcon />
              <input
                type="text"
                placeholder="Search by key..."
                value={search}
                onChange={e => setSearch(e.target.value)}
                className="filter-input"
              />
            </div>

            <MultiSelectDropdown
              label="PM"
              options={availablePMs}
              selected={selectedPMs}
              onToggle={handlePMToggle}
              placeholder="All PM"
            />

            <MultiSelectDropdown
              label="Status"
              options={availableStatuses}
              selected={selectedStatuses}
              onToggle={handleStatusToggle}
              placeholder="All statuses"
            />

            {hasActiveFilters && (
              <button className="btn btn-secondary btn-clear" onClick={clearFilters}>
                Clear
              </button>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {filteredProjects.map(p => (
              <div key={p.issueKey}>
                {/* Project card */}
                <div
                  onClick={() => handleToggle(p.issueKey)}
                  style={{
                    padding: '14px 20px',
                    background: '#fff',
                    border: '1px solid #DFE1E6',
                    borderRadius: expandedKey === p.issueKey ? '8px 8px 0 0' : 8,
                    cursor: 'pointer',
                    transition: 'box-shadow 0.15s',
                  }}
                  onMouseEnter={e => (e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.08)')}
                  onMouseLeave={e => (e.currentTarget.style.boxShadow = 'none')}
                >
                  {/* Top row: icon + key + summary + assignee + status + rice + arrow */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                    <img src={getIssueIcon(p.issueType, getIssueTypeIconUrl(p.issueType))} alt={p.issueType} style={{ width: 16, height: 16, flexShrink: 0 }} />
                    <JiraLink issueKey={p.issueKey} jiraBaseUrl={jiraBaseUrl} />
                    <span style={{ flex: 1, fontSize: 14, color: '#172B4D', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {p.summary}
                    </span>
                    {p.assigneeDisplayName && (
                      <AssigneeBadge name={p.assigneeDisplayName} avatarUrl={p.assigneeAvatarUrl} />
                    )}
                    <RiceScoreBadge score={p.riceScore} normalized={p.riceNormalizedScore} />
                    <StatusBadge status={p.status} />
                    <span style={{
                      fontSize: 16,
                      color: '#6B778C',
                      transform: expandedKey === p.issueKey ? 'rotate(180deg)' : 'rotate(0)',
                      transition: 'transform 0.2s',
                      flexShrink: 0,
                    }}>
                      &#9660;
                    </span>
                  </div>

                  {/* Bottom row: time progress + epics count + expected done */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                    <ProgressBar percent={p.progressPercent} width={120} />
                    {p.totalEstimateSeconds != null && p.totalEstimateSeconds > 0 ? (
                      <span style={{ fontSize: 12, color: '#42526E', fontWeight: 500 }}>
                        {formatHours(p.totalLoggedSeconds)}/{formatHours(p.totalEstimateSeconds)} ({p.progressPercent}%)
                      </span>
                    ) : (
                      <span style={{ fontSize: 12, color: '#42526E', fontWeight: 500 }}>
                        {p.completedEpicCount}/{p.childEpicCount} epics ({p.progressPercent}%)
                      </span>
                    )}
                    <span style={{ color: '#DFE1E6' }}>|</span>
                    <span style={{ fontSize: 12, color: '#97A0AF' }}>
                      {p.completedEpicCount}/{p.childEpicCount} epics
                    </span>
                    {p.expectedDone && (
                      <>
                        <span style={{ color: '#DFE1E6' }}>|</span>
                        <span style={{ fontSize: 12, color: '#6B778C' }}>
                          Done by <strong style={{ color: '#42526E' }}>{formatDate(p.expectedDone)}</strong>
                        </span>
                      </>
                    )}
                  </div>
                </div>

                {/* Expanded detail */}
                {expandedKey === p.issueKey && (
                  <div style={{
                    border: '1px solid #DFE1E6',
                    borderTop: 'none',
                    borderRadius: '0 0 8px 8px',
                    background: '#FAFBFC',
                    padding: '12px 20px',
                  }}>
                    {detail?.description && (
                      <div style={{
                        fontSize: 13,
                        color: '#42526E',
                        lineHeight: 1.5,
                        marginBottom: 12,
                        paddingBottom: 12,
                        borderBottom: '1px solid #EBECF0',
                        whiteSpace: 'pre-line',
                      }}>
                        {detail.description}
                      </div>
                    )}
                    {detailLoading ? (
                      <div style={{ padding: 16, textAlign: 'center', color: '#6B778C', fontSize: 13 }}>
                        Loading epics...
                      </div>
                    ) : detail && detail.epics.length > 0 ? (
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, tableLayout: 'fixed' }}>
                        <colgroup>
                          <col style={{ width: 100 }} />
                          <col />
                          <col style={{ width: 130 }} />
                          <col style={{ width: 120 }} />
                          <col style={{ width: 160 }} />
                          <col style={{ width: 90 }} />
                          <col style={{ width: 70 }} />
                        </colgroup>
                        <thead>
                          <tr style={{ borderBottom: '2px solid #DFE1E6' }}>
                            <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Key</th>
                            <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Summary</th>
                            <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Status</th>
                            <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Team</th>
                            <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Progress</th>
                            <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Done by</th>
                            <th style={{ textAlign: 'center', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Align</th>
                          </tr>
                        </thead>
                        <tbody>
                          {detail.epics.map(e => (
                            <tr key={e.issueKey} style={{ borderBottom: '1px solid #EBECF0' }}>
                              <td style={{ padding: '8px' }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                  <img src={getIssueIcon(e.issueType, getIssueTypeIconUrl(e.issueType))} alt={e.issueType} style={{ width: 16, height: 16, flexShrink: 0 }} />
                                  <JiraLink issueKey={e.issueKey} jiraBaseUrl={jiraBaseUrl} />
                                </div>
                              </td>
                              <td style={{ padding: '8px', color: '#172B4D', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.summary}</td>
                              <td style={{ padding: '8px' }}>
                                <StatusBadge status={e.status} />
                              </td>
                              <td style={{ padding: '8px' }}>
                                <TeamBadge name={e.teamName} color={e.teamColor} />
                              </td>
                              <td style={{ padding: '8px' }}>
                                {e.progressPercent != null ? (
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                    <ProgressBar percent={e.progressPercent} width={60} />
                                    <span style={{ fontSize: 11, color: '#42526E', whiteSpace: 'nowrap' }}>
                                      {e.progressPercent}%
                                    </span>
                                    {e.estimateSeconds != null && e.estimateSeconds > 0 && (
                                      <span style={{ fontSize: 10, color: '#97A0AF', whiteSpace: 'nowrap' }}>
                                        {formatHours(e.loggedSeconds)}/{formatHours(e.estimateSeconds)}
                                      </span>
                                    )}
                                  </div>
                                ) : (
                                  <span style={{ color: '#97A0AF' }}>—</span>
                                )}
                              </td>
                              <td style={{ padding: '8px', fontSize: 12, color: '#42526E', whiteSpace: 'nowrap' }}>
                                {formatDate(e.expectedDone || e.dueDate)}
                              </td>
                              <td style={{ padding: '8px', textAlign: 'center' }}>
                                <AlignmentBadge delayDays={e.delayDays} />
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    ) : (
                      <div style={{ padding: 16, textAlign: 'center', color: '#6B778C', fontSize: 13 }}>
                        No child epics found
                      </div>
                    )}
                    <RecommendationsBlock recommendations={recommendations} />
                    <div style={{ marginTop: 12, display: 'flex', justifyContent: 'flex-end' }}>
                      <button
                        onClick={e => { e.stopPropagation(); setRiceModalKey(p.issueKey) }}
                        style={{
                          padding: '6px 14px',
                          fontSize: 13,
                          fontWeight: 500,
                          color: '#0052CC',
                          background: '#E9F2FF',
                          border: '1px solid #B3D4FF',
                          borderRadius: 4,
                          cursor: 'pointer',
                        }}
                      >
                        RICE Scoring
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
          {riceModalKey && (() => {
            const proj = projects.find(pr => pr.issueKey === riceModalKey)
            return (
              <Modal
                isOpen={true}
                onClose={() => setRiceModalKey(null)}
                title="RICE Scoring"
                maxWidth={680}
              >
                <div style={{ marginBottom: 16, paddingBottom: 12, borderBottom: '1px solid #EBECF0' }}>
                  <div style={{ fontSize: 15, fontWeight: 600, color: '#172B4D' }}>
                    <span style={{ color: '#0052CC', marginRight: 8 }}>{riceModalKey}</span>
                    {proj?.summary}
                  </div>
                </div>
                <RiceForm issueKey={riceModalKey} onSaved={() => {
                  projectsApi.list().then(setProjects).catch(() => {})
                }} />
              </Modal>
            )
          })()}
        </div>
      </main>
    </StatusStylesProvider>
  )
}
