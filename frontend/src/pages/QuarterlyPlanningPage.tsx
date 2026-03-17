import { useEffect, useState, useCallback, useMemo } from 'react'
import { TeamBadge } from '../components/TeamBadge'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import {
  quarterlyPlanningApi,
  QuarterlyProjectsResponse,
  QuarterlyProjectOverviewDto,
  QuarterlyTeamOverviewDto,
} from '../api/quarterlyPlanning'
import './QuarterlyPlanningPage.css'

type PlanningTab = 'projects' | 'readiness' | 'teams'
type ProjectFilter = 'all' | 'in-quarter' | 'blocked' | 'not-added'

export function QuarterlyPlanningPage() {
  return <QuarterlyPlanningLivePage />
}

function QuarterlyPlanningLivePage() {
  const { getRoleColor, getRoleCodes } = useWorkflowConfig()

  const [availableQuarters, setAvailableQuarters] = useState<string[]>([])
  const [quarter, setQuarter] = useState('')
  const [activeTab, setActiveTab] = useState<PlanningTab>('projects')
  const [filter, setFilter] = useState<ProjectFilter>('all')
  const [selectedProjectKey, setSelectedProjectKey] = useState<string | null>(null)
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)

  const [projectsData, setProjectsData] = useState<QuarterlyProjectsResponse | null>(null)
  const [teamsData, setTeamsData] = useState<QuarterlyTeamOverviewDto[]>([])

  const [loading, setLoading] = useState(true)
  const [dataLoading, setDataLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // ==================== Init ====================

  useEffect(() => {
    quarterlyPlanningApi.getAvailableQuarters().then(quarters => {
      setAvailableQuarters(quarters)
      if (quarters.length > 0) {
        const now = new Date()
        const currentQ = `${now.getFullYear()}Q${Math.floor(now.getMonth() / 3) + 1}`
        setQuarter(quarters.includes(currentQ) ? currentQ : quarters[0])
      }
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [])

  // ==================== Data loading ====================

  const loadData = useCallback(async () => {
    if (!quarter) return
    setError(null)
    setDataLoading(true)
    try {
      const [projRes, teamsRes] = await Promise.all([
        quarterlyPlanningApi.getProjectsOverview(quarter),
        quarterlyPlanningApi.getTeamsOverview(quarter),
      ])
      setProjectsData(projRes)
      setTeamsData(teamsRes)

      // Auto-select first in-quarter project
      if (!selectedProjectKey) {
        const firstInQ = projRes.projects.find(p => p.inQuarter)
        if (firstInQ) setSelectedProjectKey(firstInQ.projectKey)
      }
      // Auto-select first team
      if (!selectedTeamId && teamsRes.length > 0) {
        setSelectedTeamId(teamsRes[0].teamId)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load data')
    } finally {
      setDataLoading(false)
    }
  }, [quarter])

  useEffect(() => { loadData() }, [loadData])

  // ==================== Derived data ====================

  const filteredProjects = useMemo(() => {
    if (!projectsData) return []
    return projectsData.projects.filter(p => {
      if (filter === 'all') return true
      if (filter === 'in-quarter') return p.inQuarter
      if (filter === 'blocked') return p.planningStatus === 'blocked' || p.planningStatus === 'partial'
      return p.planningStatus === 'not-added'
    })
  }, [projectsData, filter])

  const selectedProject = projectsData?.projects.find(p => p.projectKey === selectedProjectKey) ?? null
  const selectedTeam = teamsData.find(t => t.teamId === selectedTeamId) ?? teamsData[0] ?? null

  const readinessIssues = useMemo(() => {
    if (!projectsData) return []
    const inQuarter = projectsData.projects.filter(p => p.inQuarter)
    const epicsWithoutRough = inQuarter.reduce((sum, p) => {
      return sum + p.epics.filter(e => !e.roughEstimated).length
    }, 0)
    const epicsWithoutTeams = inQuarter.reduce((sum, p) => {
      return sum + p.epics.filter(e => !e.teamMapped).length
    }, 0)
    const partiallyReady = inQuarter.filter(p => p.planningStatus === 'partial').length
    return [
      { title: 'Epics without rough estimates', count: epicsWithoutRough, action: 'Open blocked epics', tone: epicsWithoutRough > 0 ? 'critical' : 'ok' },
      { title: 'Epics without team mapping', count: epicsWithoutTeams, action: 'Review routing', tone: epicsWithoutTeams > 0 ? 'warning' : 'ok' },
      { title: 'Projects partially ready', count: partiallyReady, action: 'Filter projects', tone: partiallyReady > 0 ? 'warning' : 'ok' },
    ]
  }, [projectsData])

  const handleBoostChange = async (projectKey: string, boost: number) => {
    try {
      await quarterlyPlanningApi.updateProjectBoost(projectKey, boost)
      loadData()
    } catch (err) {
      console.error('Failed to update boost:', err)
    }
  }

  // ==================== Render ====================

  if (loading) {
    return <div className="qpp-page"><div className="qpp-empty-state">Loading...</div></div>
  }

  const summary = projectsData

  return (
    <div className="qpp-page">
      <div className="qpp-hero">
        <div>
          <h1>Quarterly Planning</h1>
          <p>
            {summary
              ? `${summary.inQuarterCount} project${summary.inQuarterCount !== 1 ? 's' : ''} in quarter, ${summary.readyCount} ready to plan.`
              : 'Select a quarter to view planning overview.'}
          </p>
        </div>
        <div className="qpp-hero-actions">
          <label className="qpp-select">
            <span>Quarter</span>
            <select value={quarter} onChange={e => { setQuarter(e.target.value); setSelectedProjectKey(null); setSelectedTeamId(null) }}>
              {availableQuarters.map(q => <option key={q} value={q}>{q}</option>)}
            </select>
          </label>
        </div>
      </div>

      <div className="qpp-steps">
        <StepCard index="01" title="Projects in quarter" body="PM includes projects by setting the Jira quarter label." active={activeTab === 'projects'} />
        <StepCard index="02" title="Readiness check" body="Planning only becomes reliable when rough estimates and team mapping are complete." active={activeTab === 'readiness'} />
        <StepCard index="03" title="Team impact" body="Only then does capacity vs demand make sense as a decision layer." active={activeTab === 'teams'} />
      </div>

      {summary && (
        <div className="qpp-summary-grid">
          <SummaryMetricCard title="Projects in quarter" value={String(summary.inQuarterCount)} hint="Projects labeled with the selected quarter in Jira" />
          <SummaryMetricCard title="Ready to plan" value={String(summary.readyCount)} hint={`${summary.partialCount} partial \u00B7 ${summary.blockedCount} blocked`} tone={summary.blockedCount > 0 ? 'warning' : 'neutral'} />
          <SummaryMetricCard title="Epics coverage" value={`${summary.roughCoveragePct}%`} hint={`${summary.totalEpics} epics checked for rough estimates`} tone={summary.roughCoveragePct < 100 ? 'warning' : 'good'} />
          <SummaryMetricCard title="Teams involved" value={String(summary.teamsInvolved)} hint="Cross-team load only matters after readiness is green" />
        </div>
      )}

      <div className="qpp-tabs">
        <button className={activeTab === 'projects' ? 'active' : ''} onClick={() => setActiveTab('projects')}>Projects</button>
        <button className={activeTab === 'readiness' ? 'active' : ''} onClick={() => setActiveTab('readiness')}>Readiness</button>
        <button className={activeTab === 'teams' ? 'active' : ''} onClick={() => setActiveTab('teams')}>Teams</button>
      </div>

      {error && <div style={{ color: '#ba3526', padding: '12px 0' }}>{error}</div>}
      {dataLoading && <div className="qpp-empty-state">Loading data...</div>}

      {availableQuarters.length === 0 && !loading && (
        <div className="qpp-empty-state">No quarter labels found. Add labels like "2026Q2" to epics or projects in Jira.</div>
      )}

      {!dataLoading && activeTab === 'projects' && projectsData && (
        <ProjectsTab
          projects={filteredProjects}
          filter={filter}
          onFilterChange={setFilter}
          selectedProject={selectedProject}
          onSelectProject={setSelectedProjectKey}
          quarter={quarter}
          onBoostChange={handleBoostChange}
        />
      )}

      {!dataLoading && activeTab === 'readiness' && projectsData && (
        <ReadinessTab
          issues={readinessIssues}
          projects={projectsData.projects.filter(p => p.inQuarter)}
          quarter={quarter}
        />
      )}

      {!dataLoading && activeTab === 'teams' && teamsData.length > 0 && (
        <TeamsTab
          teams={teamsData}
          selectedTeam={selectedTeam}
          onSelectTeam={setSelectedTeamId}
          getRoleColor={getRoleColor}
          getRoleCodes={getRoleCodes}
        />
      )}
    </div>
  )
}

// ==================== Projects Tab ====================

function ProjectsTab({ projects, filter, onFilterChange, selectedProject, onSelectProject, quarter, onBoostChange }: {
  projects: QuarterlyProjectOverviewDto[]
  filter: ProjectFilter
  onFilterChange: (f: ProjectFilter) => void
  selectedProject: QuarterlyProjectOverviewDto | null
  onSelectProject: (key: string) => void
  quarter: string
  onBoostChange: (key: string, boost: number) => void
}) {
  return (
    <div className="qpp-panel">
      <div className="qpp-panel-header">
        <div>
          <h2>Projects in Quarter</h2>
          <p>Source of truth: Jira label like <code>{quarter}</code>. Add/remove labels directly in Jira.</p>
        </div>
        <div className="qpp-filter-bar">
          <FilterChip active={filter === 'all'} onClick={() => onFilterChange('all')}>All</FilterChip>
          <FilterChip active={filter === 'in-quarter'} onClick={() => onFilterChange('in-quarter')}>In quarter</FilterChip>
          <FilterChip active={filter === 'blocked'} onClick={() => onFilterChange('blocked')}>Needs planning work</FilterChip>
          <FilterChip active={filter === 'not-added'} onClick={() => onFilterChange('not-added')}>Not added</FilterChip>
        </div>
      </div>

      <div className="qpp-project-layout">
        <div className="qpp-table-card">
          <table className="qpp-table">
            <thead>
              <tr>
                <th>Project</th>
                <th>Quarter</th>
                <th>Teams</th>
                <th>Epics</th>
                <th>Rough est.</th>
                <th>Status</th>
                <th>Forecast</th>
              </tr>
            </thead>
            <tbody>
              {projects.map(p => (
                <tr
                  key={p.projectKey}
                  className={selectedProject?.projectKey === p.projectKey ? 'selected' : ''}
                  onClick={() => onSelectProject(p.projectKey)}
                >
                  <td>
                    <div className="qpp-project-cell">
                      <strong>{p.projectKey}</strong>
                      <span>{p.summary}</span>
                    </div>
                  </td>
                  <td>
                    <span className={`qpp-quarter-pill ${p.inQuarter ? 'active' : ''}`}>
                      {p.inQuarter ? quarter : 'Not set'}
                    </span>
                  </td>
                  <td>
                    <div className="qpp-team-stack">
                      {p.teams.map(t => (
                        <TeamBadge key={t.id} name={t.name} color={t.color || '#666'} />
                      ))}
                      {p.teams.length === 0 && <span style={{ color: '#60708f', fontSize: 13 }}>No teams</span>}
                    </div>
                  </td>
                  <td>{p.epicCount}</td>
                  <td>{p.roughEstimateCoverage}%</td>
                  <td><StatusPill status={p.planningStatus}>{statusLabel(p.planningStatus)}</StatusPill></td>
                  <td>{p.forecastLabel}</td>
                </tr>
              ))}
              {projects.length === 0 && (
                <tr><td colSpan={7} style={{ textAlign: 'center', color: '#60708f', padding: 24 }}>No projects match the selected filter.</td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="qpp-detail-card">
          {selectedProject ? (
            <ProjectDetail project={selectedProject} onBoostChange={onBoostChange} />
          ) : (
            <div className="qpp-empty-state">Select a project to inspect quarter label, readiness, and blockers.</div>
          )}
        </div>
      </div>
    </div>
  )
}

function ProjectDetail({ project, onBoostChange }: {
  project: QuarterlyProjectOverviewDto
  onBoostChange: (key: string, boost: number) => void
}) {
  return (
    <>
      <div className="qpp-detail-head">
        <div>
          <div className="qpp-detail-key">{project.projectKey}</div>
          <h3>{project.summary}</h3>
        </div>
        <StatusPill status={project.planningStatus}>{statusLabel(project.planningStatus)}</StatusPill>
      </div>
      <div className="qpp-detail-metrics">
        <MetricMini label="Priority" value={String(Math.round(project.priorityScore))} />
        <MetricMini label="RICE" value={String(Math.round(project.riceNormalizedScore))} />
        <BoostMini currentBoost={project.manualBoost ?? 0} onBoostChange={b => onBoostChange(project.projectKey, b)} />
        <MetricMini label="Risk" value={riskLabel(project.risk)} />
      </div>
      <div className="qpp-checklist">
        <ChecklistItem label="Project has quarter label" ok={project.inQuarter} />
        <ChecklistItem label="Epics mapped to teams" ok={project.teamMappingCoverage === 100} note={`${project.teamMappingCoverage}% coverage`} />
        <ChecklistItem label="Rough estimates complete" ok={project.roughEstimateCoverage === 100} note={`${project.roughEstimateCoverage}% coverage`} />
      </div>
      <div className="qpp-blockers">
        <h4>Blockers</h4>
        {project.blockers.length > 0 ? (
          <ul>{project.blockers.map(b => <li key={b}>{b}</li>)}</ul>
        ) : (
          <p>Project is ready for planning. PM can now validate team capacity impact.</p>
        )}
      </div>
      <div className="qpp-epics">
        <h4>Epics ({project.epics.length})</h4>
        {project.epics.map(epic => (
          <div key={epic.key} className="qpp-epic-row">
            <div>
              <strong>{epic.key}</strong>
              <span>{epic.summary}</span>
            </div>
            <div className="qpp-epic-meta">
              <span>{epic.teams.length > 0 ? epic.teams.map(t => t.name).join(' + ') : 'Unassigned'}</span>
              <span className={epic.roughEstimated ? 'ok' : 'bad'}>{epic.roughEstimated ? 'Rough est. ready' : 'No rough estimate'}</span>
            </div>
          </div>
        ))}
      </div>
    </>
  )
}

// ==================== Readiness Tab ====================

function ReadinessTab({ issues, projects, quarter }: {
  issues: { title: string; count: number; action: string; tone: string }[]
  projects: QuarterlyProjectOverviewDto[]
  quarter: string
}) {
  return (
    <div className="qpp-panel">
      <div className="qpp-panel-header">
        <div>
          <h2>Planning Readiness</h2>
          <p>This screen separates "project is in the quarter" from "project can be planned reliably".</p>
        </div>
      </div>

      <div className="qpp-readiness-grid">
        {issues.map(issue => (
          <div key={issue.title} className={`qpp-issue-card ${issue.tone}`}>
            <span className="qpp-issue-count">{issue.count}</span>
            <div>
              <h3>{issue.title}</h3>
              <p>{issue.action}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="qpp-table-card">
        <table className="qpp-table">
          <thead>
            <tr>
              <th>Project</th>
              <th>Quarter label</th>
              <th>Team mapping</th>
              <th>Rough estimates</th>
              <th>Ready to plan</th>
              <th>Why blocked</th>
            </tr>
          </thead>
          <tbody>
            {projects.map(p => (
              <tr key={p.projectKey}>
                <td>
                  <div className="qpp-project-cell">
                    <strong>{p.projectKey}</strong>
                    <span>{p.summary}</span>
                  </div>
                </td>
                <td>{quarter}</td>
                <td>{p.teamMappingCoverage}%</td>
                <td>{p.roughEstimateCoverage}%</td>
                <td><StatusPill status={p.planningStatus}>{p.planningStatus === 'ready' ? 'Yes' : 'No'}</StatusPill></td>
                <td>{p.blockers.length > 0 ? p.blockers.join(', ') : 'No blocking issues'}</td>
              </tr>
            ))}
            {projects.length === 0 && (
              <tr><td colSpan={6} style={{ textAlign: 'center', color: '#60708f', padding: 24 }}>No projects in this quarter yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ==================== Teams Tab ====================

function TeamsTab({ teams, selectedTeam, onSelectTeam, getRoleColor, getRoleCodes }: {
  teams: QuarterlyTeamOverviewDto[]
  selectedTeam: QuarterlyTeamOverviewDto | null
  onSelectTeam: (id: number) => void
  getRoleColor: (code: string) => string
  getRoleCodes: () => string[]
}) {
  return (
    <div className="qpp-panel">
      <div className="qpp-panel-header">
        <div>
          <h2>Team Impact</h2>
          <p>Capacity is only meaningful after quarter composition and readiness are already clear.</p>
        </div>
      </div>

      <div className="qpp-team-layout">
        <div className="qpp-table-card">
          <table className="qpp-table">
            <thead>
              <tr>
                <th>Team</th>
                <th>Capacity</th>
                <th>Demand</th>
                <th>Gap</th>
                <th>Utilization</th>
                <th>Overloaded epics</th>
                <th>Risk</th>
              </tr>
            </thead>
            <tbody>
              {teams.map(t => (
                <tr
                  key={t.teamId}
                  className={selectedTeam?.teamId === t.teamId ? 'selected' : ''}
                  onClick={() => onSelectTeam(t.teamId)}
                >
                  <td><TeamBadge name={t.teamName} color={t.teamColor || '#666'} /></td>
                  <td>{Math.round(t.capacityDays)}d</td>
                  <td>{Math.round(t.demandDays)}d</td>
                  <td className={t.gapDays < 0 ? 'bad' : 'ok'}>
                    {t.gapDays > 0 ? '+' : ''}{Math.round(t.gapDays)}d
                  </td>
                  <td>{t.utilization}%</td>
                  <td>{t.overloadedEpics}</td>
                  <td><RiskPill risk={t.risk}>{riskLabel(t.risk)}</RiskPill></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="qpp-detail-card">
          {selectedTeam ? (
            <TeamDetail team={selectedTeam} getRoleColor={getRoleColor} getRoleCodes={getRoleCodes} />
          ) : (
            <div className="qpp-empty-state">No team data available for this quarter.</div>
          )}
        </div>
      </div>
    </div>
  )
}

function TeamDetail({ team, getRoleColor, getRoleCodes }: {
  team: QuarterlyTeamOverviewDto
  getRoleColor: (code: string) => string
  getRoleCodes: () => string[]
}) {
  const allRoles = useMemo(() => {
    const set = new Set([...Object.keys(team.capacityByRole), ...Object.keys(team.demandByRole)])
    const configOrder = getRoleCodes()
    const sorted = configOrder.filter(r => set.has(r))
    set.forEach(r => { if (!configOrder.includes(r)) sorted.push(r) })
    return sorted
  }, [team, getRoleCodes])

  return (
    <>
      <div className="qpp-detail-head">
        <div>
          <div className="qpp-detail-key">Team focus</div>
          <h3>{team.teamName}</h3>
        </div>
        <RiskPill risk={team.risk}>{team.utilization}% utilized</RiskPill>
      </div>

      <div className="qpp-role-bars">
        {allRoles.map(role => {
          const cap = team.capacityByRole[role] || 0
          const demand = team.demandByRole[role] || 0
          const util = cap > 0 ? Math.round((demand / cap) * 100) : 0
          return (
            <div key={role} className="qpp-role-row">
              <span style={{ color: getRoleColor(role) }}>{role}</span>
              <div className="qpp-role-track">
                <div className={`qpp-role-fill ${util > 100 ? 'over' : ''}`} style={{ width: `${Math.min(util, 100)}%` }} />
              </div>
              <strong>{Math.round(demand)}/{Math.round(cap)}d</strong>
            </div>
          )
        })}
      </div>

      <div className="qpp-impact-projects">
        <h4>Projects impacting this team</h4>
        {team.impactingProjects.map(p => (
          <div key={p.key} className="qpp-impact-row">
            <div>
              <strong>{p.key}</strong>
              <span>{p.name}</span>
            </div>
            <div>
              <StatusPill status={p.planningStatus as 'ready' | 'partial' | 'blocked' | 'not-added'}>
                {statusLabel(p.planningStatus as 'ready' | 'partial' | 'blocked' | 'not-added')}
              </StatusPill>
            </div>
          </div>
        ))}
        {team.impactingProjects.length === 0 && (
          <p style={{ color: '#60708f' }}>No projects assigned to this team for the quarter.</p>
        )}
      </div>
    </>
  )
}

// ==================== Shared Components ====================

function StepCard({ index, title, body, active }: { index: string; title: string; body: string; active?: boolean }) {
  return (
    <div className={`qpp-step-card ${active ? 'active' : ''}`}>
      <span>{index}</span>
      <h3>{title}</h3>
      <p>{body}</p>
    </div>
  )
}

function SummaryMetricCard({ title, value, hint, tone = 'neutral' }: { title: string; value: string; hint: string; tone?: 'neutral' | 'warning' | 'good' }) {
  return (
    <div className={`qpp-metric-card ${tone}`}>
      <span>{title}</span>
      <strong>{value}</strong>
      <small>{hint}</small>
    </div>
  )
}

function MetricMini({ label, value }: { label: string; value: string }) {
  return (
    <div className="qpp-mini-metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function BoostMini({ currentBoost, onBoostChange }: { currentBoost: number; onBoostChange: (b: number) => void }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(String(currentBoost))

  const handleSave = () => {
    const num = parseInt(value, 10)
    if (!isNaN(num)) onBoostChange(Math.max(-50, Math.min(50, num)))
    setEditing(false)
  }

  if (editing) {
    return (
      <div className="qpp-mini-metric">
        <span>Boost</span>
        <input
          type="number"
          value={value}
          onChange={e => setValue(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') handleSave(); if (e.key === 'Escape') setEditing(false) }}
          onBlur={handleSave}
          min={-50} max={50}
          autoFocus
          style={{ width: 60, fontSize: 18, fontWeight: 700, border: '1px solid #bfd3ff', borderRadius: 8, padding: '2px 6px', textAlign: 'center' }}
        />
      </div>
    )
  }

  return (
    <div className="qpp-mini-metric" onClick={() => { setValue(String(currentBoost)); setEditing(true) }} style={{ cursor: 'pointer' }} title="Click to edit boost (-50 to +50)">
      <span>Boost</span>
      <strong>{currentBoost > 0 ? `+${currentBoost}` : String(currentBoost)}</strong>
    </div>
  )
}

function ChecklistItem({ label, ok, note }: { label: string; ok: boolean; note?: string }) {
  return (
    <div className={`qpp-check-row ${ok ? 'ok' : 'bad'}`}>
      <span>{ok ? 'Ready' : 'Needs work'}</span>
      <div>
        <strong>{label}</strong>
        {note && <small>{note}</small>}
      </div>
    </div>
  )
}

function FilterChip({ children, active, onClick }: { children: string; active: boolean; onClick: () => void }) {
  return <button className={`qpp-chip ${active ? 'active' : ''}`} onClick={onClick}>{children}</button>
}

function StatusPill({ status, children }: { status: string; children: string }) {
  return <span className={`qpp-status-pill ${status}`}>{children}</span>
}

function RiskPill({ risk, children }: { risk: string; children: React.ReactNode }) {
  return <span className={`qpp-risk-pill ${risk}`}>{children}</span>
}

// ==================== Helpers ====================

function statusLabel(status: string): string {
  if (status === 'ready') return 'Ready'
  if (status === 'partial') return 'Partial'
  if (status === 'blocked') return 'Blocked'
  return 'Not added'
}

function riskLabel(risk: string): string {
  return risk.charAt(0).toUpperCase() + risk.slice(1)
}
