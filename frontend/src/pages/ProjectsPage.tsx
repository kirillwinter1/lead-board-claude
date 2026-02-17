import { useEffect, useState } from 'react'
import { projectsApi, ProjectDto, ProjectDetailDto } from '../api/projects'
import { getStatusStyles, StatusStyle } from '../api/board'
import { StatusStylesProvider } from '../components/board/StatusStylesContext'
import { StatusBadge } from '../components/board/StatusBadge'
import { RiceForm } from '../components/rice/RiceForm'
import { RiceScoreBadge } from '../components/rice/RiceScoreBadge'

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

export function ProjectsPage() {
  const [projects, setProjects] = useState<ProjectDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [expandedKey, setExpandedKey] = useState<string | null>(null)
  const [detail, setDetail] = useState<ProjectDetailDto | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [statusStyles, setStatusStyles] = useState<Record<string, StatusStyle>>({})

  useEffect(() => {
    projectsApi.list()
      .then(setProjects)
      .catch(() => setError('Failed to load projects'))
      .finally(() => setLoading(false))
    getStatusStyles().then(setStatusStyles).catch(() => {})
  }, [])

  const handleToggle = async (issueKey: string) => {
    if (expandedKey === issueKey) {
      setExpandedKey(null)
      setDetail(null)
      return
    }
    setExpandedKey(issueKey)
    setDetail(null)
    setDetailLoading(true)
    try {
      const data = await projectsApi.getDetail(issueKey)
      setDetail(data)
    } catch {
      setDetail(null)
    } finally {
      setDetailLoading(false)
    }
  }

  if (loading) {
    return (
      <div style={{ padding: 32, textAlign: 'center', color: '#6B778C' }}>
        Loading projects...
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ padding: 32, textAlign: 'center', color: '#DE350B' }}>
        {error}
      </div>
    )
  }

  if (projects.length === 0) {
    return (
      <div style={{ padding: 32, textAlign: 'center', color: '#6B778C' }}>
        <h2 style={{ margin: '0 0 8px', color: '#172B4D' }}>No Projects Found</h2>
        <p>Configure a PROJECT issue type in Workflow Config and sync from Jira to see projects here.</p>
      </div>
    )
  }

  return (
    <StatusStylesProvider value={statusStyles}>
      <div style={{ padding: '24px 32px', maxWidth: 960, margin: '0 auto' }}>
        <h1 style={{ margin: '0 0 24px', fontSize: 20, color: '#172B4D' }}>
          Projects ({projects.length})
        </h1>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {projects.map(p => (
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
                {/* Top row: key + summary + status + arrow */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
                  <span style={{ fontSize: 13, color: '#0052CC', fontWeight: 600 }}>
                    {p.issueKey}
                  </span>
                  <span style={{ flex: 1, fontSize: 14, color: '#172B4D', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {p.summary}
                  </span>
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
                {/* Bottom row: progress bar + count + expected done */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <ProgressBar percent={p.progressPercent} width={140} />
                  <span style={{ fontSize: 12, color: '#42526E', fontWeight: 500 }}>
                    {p.completedEpicCount}/{p.childEpicCount} epics
                  </span>
                  <span style={{ fontSize: 12, color: '#6B778C' }}>
                    ({p.progressPercent}%)
                  </span>
                  {p.expectedDone && (
                    <>
                      <span style={{ color: '#DFE1E6' }}>|</span>
                      <span style={{ fontSize: 12, color: '#6B778C' }}>
                        Done by <strong style={{ color: '#42526E' }}>{formatDate(p.expectedDone)}</strong>
                      </span>
                    </>
                  )}
                  {p.assigneeDisplayName && (
                    <>
                      <span style={{ color: '#DFE1E6' }}>|</span>
                      <span style={{ fontSize: 12, color: '#6B778C' }}>
                        {p.assigneeDisplayName}
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
                  {detailLoading ? (
                    <div style={{ padding: 16, textAlign: 'center', color: '#6B778C', fontSize: 13 }}>
                      Loading epics...
                    </div>
                  ) : detail && detail.epics.length > 0 ? (
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, tableLayout: 'fixed' }}>
                      <colgroup>
                        <col style={{ width: 70 }} />
                        <col />
                        <col style={{ width: 120 }} />
                        <col style={{ width: 110 }} />
                        <col style={{ width: 130 }} />
                        <col style={{ width: 80 }} />
                      </colgroup>
                      <thead>
                        <tr style={{ borderBottom: '2px solid #DFE1E6' }}>
                          <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Key</th>
                          <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Summary</th>
                          <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Status</th>
                          <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Team</th>
                          <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Progress</th>
                          <th style={{ textAlign: 'left', padding: '6px 8px', color: '#6B778C', fontWeight: 500, fontSize: 11, textTransform: 'uppercase' }}>Done by</th>
                        </tr>
                      </thead>
                      <tbody>
                        {detail.epics.map(e => (
                          <tr key={e.issueKey} style={{ borderBottom: '1px solid #EBECF0' }}>
                            <td style={{ padding: '8px', color: '#0052CC', fontWeight: 500 }}>{e.issueKey}</td>
                            <td style={{ padding: '8px', color: '#172B4D', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{e.summary}</td>
                            <td style={{ padding: '8px' }}>
                              <StatusBadge status={e.status} />
                            </td>
                            <td style={{ padding: '8px', color: '#6B778C', fontSize: 12 }}>{e.teamName || '—'}</td>
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
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  ) : (
                    <div style={{ padding: 16, textAlign: 'center', color: '#6B778C', fontSize: 13 }}>
                      No child epics found
                    </div>
                  )}
                  <RiceForm issueKey={p.issueKey} onSaved={() => {
                    // Refresh project list to update RICE badge
                    projectsApi.list().then(setProjects).catch(() => {})
                  }} />
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </StatusStylesProvider>
  )
}
