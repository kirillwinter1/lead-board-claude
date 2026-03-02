import { useEffect, useState, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Cell } from 'recharts'
import { MetricCard } from '../components/metrics/MetricCard'
import { StatusBadge } from '../components/board/StatusBadge'
import { TeamBadge } from '../components/TeamBadge'
import { RiceScoreBadge } from '../components/rice/RiceScoreBadge'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import {
  quarterlyPlanningApi,
  QuarterlyDemandDto,
  QuarterlySummaryDto,
  ProjectViewDto,
  EpicDemandDto,
} from '../api/quarterlyPlanning'
import './QuarterlyPlanningPage.css'

interface Team {
  id: number
  name: string
  color: string | null
}

interface ProjectOption {
  issueKey: string
  summary: string
}

type ViewTab = 'teams' | 'projects'

export function QuarterlyPlanningPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { getRoleColor } = useWorkflowConfig()

  const [teams, setTeams] = useState<Team[]>([])
  const [projects, setProjects] = useState<ProjectOption[]>([])
  const [availableQuarters, setAvailableQuarters] = useState<string[]>([])
  const [quarter, setQuarter] = useState<string>('')
  const [activeTab, setActiveTab] = useState<ViewTab>('teams')

  // Team view state
  const [teamDemand, setTeamDemand] = useState<QuarterlyDemandDto | null>(null)
  const [summary, setSummary] = useState<QuarterlySummaryDto | null>(null)

  // Project view state
  const [projectView, setProjectView] = useState<ProjectViewDto | null>(null)

  const [loading, setLoading] = useState(true)
  const [dataLoading, setDataLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selectedTeamId = searchParams.get('teamId') ? Number(searchParams.get('teamId')) : null
  const selectedProjectKey = searchParams.get('projectKey') || null
  const teamAbortRef = useRef<AbortController | null>(null)
  const projectAbortRef = useRef<AbortController | null>(null)

  // ==================== Init ====================

  useEffect(() => {
    Promise.all([
      axios.get<Team[]>('/api/teams'),
      axios.get<ProjectOption[]>('/api/projects'),
      quarterlyPlanningApi.getAvailableQuarters(),
    ]).then(([teamsRes, projectsRes, quarters]) => {
      setTeams(teamsRes.data)
      setProjects(projectsRes.data.map(p => ({ issueKey: (p as any).issueKey || (p as any).key, summary: (p as any).summary || (p as any).name })))
      setAvailableQuarters(quarters)
      if (!quarter && quarters.length > 0) {
        // Default to current quarter or first available
        const now = new Date()
        const currentQ = `${now.getFullYear()}Q${Math.floor(now.getMonth() / 3) + 1}`
        setQuarter(quarters.includes(currentQ) ? currentQ : quarters[0])
      }
      setLoading(false)
    }).catch(() => {
      setLoading(false)
    })
  }, [])

  // ==================== Data loading ====================

  const loadTeamView = useCallback(async () => {
    if (!quarter) return
    teamAbortRef.current?.abort()
    const controller = new AbortController()
    teamAbortRef.current = controller
    setError(null)
    setDataLoading(true)
    try {
      if (selectedTeamId) {
        const demand = await quarterlyPlanningApi.getDemand(selectedTeamId, quarter)
        if (controller.signal.aborted) return
        setTeamDemand(demand)
      }
      const sum = await quarterlyPlanningApi.getSummary(quarter)
      if (controller.signal.aborted) return
      setSummary(sum)
    } catch (err) {
      if (controller.signal.aborted) return
      setError(err instanceof Error ? err.message : 'Failed to load data')
    } finally {
      if (!controller.signal.aborted) setDataLoading(false)
    }
  }, [selectedTeamId, quarter])

  const loadProjectView = useCallback(async () => {
    if (!quarter || !selectedProjectKey) return
    projectAbortRef.current?.abort()
    const controller = new AbortController()
    projectAbortRef.current = controller
    setError(null)
    setDataLoading(true)
    try {
      const view = await quarterlyPlanningApi.getProjectView(selectedProjectKey, quarter)
      if (controller.signal.aborted) return
      setProjectView(view)
    } catch (err) {
      if (controller.signal.aborted) return
      setError(err instanceof Error ? err.message : 'Failed to load project view')
    } finally {
      if (!controller.signal.aborted) setDataLoading(false)
    }
  }, [selectedProjectKey, quarter])

  useEffect(() => {
    if (activeTab === 'teams') loadTeamView()
  }, [activeTab, loadTeamView])

  useEffect(() => {
    if (activeTab === 'projects') loadProjectView()
  }, [activeTab, loadProjectView])

  // ==================== Handlers ====================

  const handleTeamChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value
    const params: Record<string, string> = {}
    if (value) params.teamId = value
    if (selectedProjectKey) params.projectKey = selectedProjectKey
    setSearchParams(params)
  }

  const handleProjectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value
    const params: Record<string, string> = {}
    if (selectedTeamId) params.teamId = String(selectedTeamId)
    if (value) params.projectKey = value
    setSearchParams(params)
  }

  const navigateQuarter = (direction: -1 | 1) => {
    const idx = availableQuarters.indexOf(quarter)
    const newIdx = idx + direction
    if (newIdx >= 0 && newIdx < availableQuarters.length) {
      setQuarter(availableQuarters[newIdx])
    }
  }

  const handleBoostChange = async (projectKey: string, boost: number) => {
    try {
      await quarterlyPlanningApi.updateProjectBoost(projectKey, boost)
      if (activeTab === 'teams') loadTeamView()
      else loadProjectView()
    } catch (err) {
      console.error('Failed to update boost:', err)
    }
  }

  // ==================== Render ====================

  if (loading) {
    return <div className="qp-page"><div className="loading">Loading...</div></div>
  }

  return (
    <div className="qp-page">
      <div className="qp-header">
        <h2>Quarterly Planning</h2>
        <div className="qp-quarter-nav">
          <button
            className="qp-nav-btn"
            onClick={() => navigateQuarter(-1)}
            disabled={availableQuarters.indexOf(quarter) <= 0}
          >
            &larr;
          </button>
          <select
            className="qp-quarter-select"
            value={quarter}
            onChange={e => setQuarter(e.target.value)}
          >
            {availableQuarters.map(q => (
              <option key={q} value={q}>{q}</option>
            ))}
          </select>
          <button
            className="qp-nav-btn"
            onClick={() => navigateQuarter(1)}
            disabled={availableQuarters.indexOf(quarter) >= availableQuarters.length - 1}
          >
            &rarr;
          </button>
        </div>
      </div>

      <div className="qp-tabs">
        <button
          className={`qp-tab ${activeTab === 'teams' ? 'active' : ''}`}
          onClick={() => setActiveTab('teams')}
        >
          By Teams
        </button>
        <button
          className={`qp-tab ${activeTab === 'projects' ? 'active' : ''}`}
          onClick={() => setActiveTab('projects')}
        >
          By Projects
        </button>
      </div>

      {error && <div className="error-message">{error}</div>}
      {dataLoading && <div className="loading" style={{ padding: '8px 0' }}>Loading data...</div>}

      {availableQuarters.length === 0 && !loading && (
        <div className="qp-empty">No quarter labels found. Add labels like "2026Q2" to epics or projects in Jira.</div>
      )}

      {activeTab === 'teams' ? (
        <TeamView
          teams={teams}
          selectedTeamId={selectedTeamId}
          onTeamChange={handleTeamChange}
          teamDemand={teamDemand}
          summary={summary}
          quarter={quarter}
          getRoleColor={getRoleColor}
          onBoostChange={handleBoostChange}
        />
      ) : (
        <ProjectView
          projects={projects}
          selectedProjectKey={selectedProjectKey}
          onProjectChange={handleProjectChange}
          projectView={projectView}
          quarter={quarter}
          getRoleColor={getRoleColor}
          onBoostChange={handleBoostChange}
        />
      )}
    </div>
  )
}

// ==================== Team View ====================

function TeamView({
  teams, selectedTeamId, onTeamChange, teamDemand, summary, quarter, getRoleColor, onBoostChange
}: {
  teams: Team[]
  selectedTeamId: number | null
  onTeamChange: (e: React.ChangeEvent<HTMLSelectElement>) => void
  teamDemand: QuarterlyDemandDto | null
  summary: QuarterlySummaryDto | null
  quarter: string
  getRoleColor: (code: string) => string
  onBoostChange: (key: string, boost: number) => void
}) {
  const [collapsedProjects, setCollapsedProjects] = useState<Set<string>>(new Set())

  const toggleProject = (key: string) => {
    setCollapsedProjects(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  // Summary cards from summary data for selected team
  const teamSnapshot = summary?.teams.find(t => t.teamId === selectedTeamId)

  const totalCapacity = teamDemand?.capacity
    ? Object.values(teamDemand.capacity.capacityByRole).reduce((a, b) => a + b, 0)
    : 0
  const totalDemand = teamDemand
    ? (() => {
        let sum = 0
        for (const p of teamDemand.projects) {
          sum += Object.values(p.totalDemandByRole).reduce((a: number, b: number) => a + b, 0)
        }
        for (const e of teamDemand.unassignedEpics) {
          sum += Object.values(e.demandByRole).reduce((a: number, b: number) => a + b, 0)
        }
        return sum
      })()
    : 0
  const utilization = totalCapacity > 0 ? Math.round((totalDemand / totalCapacity) * 100) : 0
  const overcommitCount = teamDemand
    ? teamDemand.projects.flatMap(p => p.epics).filter(e => e.overCapacity).length +
      teamDemand.unassignedEpics.filter(e => e.overCapacity).length
    : 0

  // Chart data
  const roles = teamSnapshot ? Object.keys(teamSnapshot.capacityByRole) : []
  const chartData = roles.map(role => ({
    role,
    Capacity: teamSnapshot?.capacityByRole[role] || 0,
    Demand: teamSnapshot?.demandByRole[role] || 0,
    color: getRoleColor(role),
  }))

  return (
    <div className="qp-team-view">
      <div className="qp-filter-row">
        <select className="qp-team-select" value={selectedTeamId ?? ''} onChange={onTeamChange}>
          <option value="">Select team...</option>
          {teams.map(t => (
            <option key={t.id} value={t.id}>{t.name}</option>
          ))}
        </select>
      </div>

      {selectedTeamId && teamDemand && (
        <>
          <div className="qp-metrics-row">
            <MetricCard title="Capacity" value={`${totalCapacity.toFixed(0)} days`} tooltip="Total effective days for the quarter" />
            <MetricCard title="Demand" value={`${totalDemand.toFixed(0)} days`} tooltip="Total demand from all epics (with risk buffer)" />
            <MetricCard
              title="Utilization"
              value={`${utilization}%`}
              trend={utilization > 100 ? 'down' : utilization > 80 ? 'neutral' : 'up'}
              tooltip="Demand / Capacity"
            />
            <MetricCard title="Overcommit" value={overcommitCount} tooltip="Number of epics exceeding capacity" />
          </div>

          {chartData.length > 0 && (
            <div className="qp-chart-container">
              <h3>Capacity vs Demand by Role</h3>
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={chartData} barGap={4}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="role" />
                  <YAxis label={{ value: 'Days', angle: -90, position: 'insideLeft' }} />
                  <Tooltip />
                  <Legend />
                  <Bar dataKey="Capacity" fill="#8884d8" opacity={0.6}>
                    {chartData.map((entry, idx) => (
                      <Cell key={idx} fill={entry.color} opacity={0.4} />
                    ))}
                  </Bar>
                  <Bar dataKey="Demand" fill="#82ca9d">
                    {chartData.map((entry, idx) => (
                      <Cell key={idx} fill={entry.color} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}

          {teamDemand.projects.map(project => (
            <div key={project.projectKey} className="qp-project-group">
              <div className="qp-project-header" onClick={() => toggleProject(project.projectKey)}>
                <span className="qp-collapse-icon">{collapsedProjects.has(project.projectKey) ? '▸' : '▾'}</span>
                <span className="qp-project-name">{project.summary}</span>
                <RiceScoreBadge score={null} normalized={project.riceNormalizedScore} />
                <span className="qp-priority-badge" title="Priority score">
                  P: {project.priorityScore?.toFixed(0) ?? '—'}
                </span>
                <BoostControl
                  currentBoost={project.manualBoost || 0}
                  onBoostChange={(boost) => onBoostChange(project.projectKey, boost)}
                />
                {!project.fitsInCapacity && <span className="qp-overcommit-badge">Over Capacity</span>}
              </div>
              {!collapsedProjects.has(project.projectKey) && (
                <EpicTable epics={project.epics} getRoleColor={getRoleColor} />
              )}
            </div>
          ))}

          {teamDemand.unassignedEpics.length > 0 && (
            <div className="qp-project-group">
              <div className="qp-project-header">
                <span className="qp-project-name" style={{ fontStyle: 'italic' }}>Unassigned Epics</span>
              </div>
              <EpicTable epics={teamDemand.unassignedEpics} getRoleColor={getRoleColor} />
            </div>
          )}
        </>
      )}

      {!selectedTeamId && summary && (
        <div className="qp-summary-grid">
          <h3>All Teams — {quarter}</h3>
          {summary.teams.map(team => (
            <SummaryCard key={team.teamId} team={team} getRoleColor={getRoleColor} />
          ))}
        </div>
      )}
    </div>
  )
}

// ==================== Project View ====================

function ProjectView({
  projects, selectedProjectKey, onProjectChange, projectView, quarter, getRoleColor, onBoostChange
}: {
  projects: ProjectOption[]
  selectedProjectKey: string | null
  onProjectChange: (e: React.ChangeEvent<HTMLSelectElement>) => void
  projectView: ProjectViewDto | null
  quarter: string
  getRoleColor: (code: string) => string
  onBoostChange: (key: string, boost: number) => void
}) {
  return (
    <div className="qp-project-view">
      <div className="qp-filter-row">
        <select className="qp-team-select" value={selectedProjectKey ?? ''} onChange={onProjectChange}>
          <option value="">Select project...</option>
          {projects.map(p => (
            <option key={p.issueKey} value={p.issueKey}>{p.summary || p.issueKey}</option>
          ))}
        </select>
      </div>

      {projectView && (
        <>
          <div className="qp-project-info">
            <h3>{projectView.summary}</h3>
            <span className="qp-priority-badge">Priority: {projectView.priorityScore?.toFixed(0)}</span>
            <BoostControl
              currentBoost={projectView.manualBoost || 0}
              onBoostChange={(boost) => onBoostChange(projectView.projectKey, boost)}
            />
          </div>

          {projectView.teams.map(team => (
            <div key={team.teamId} className="qp-team-allocation">
              <div className="qp-team-allocation-header">
                <TeamBadge name={team.teamName} color={team.teamColor || '#666'} />
                {team.overloaded && <span className="qp-overcommit-badge">Over Capacity</span>}
              </div>

              <div className="qp-capacity-bars">
                {Object.keys(team.teamCapacity).map(role => {
                  const cap = team.teamCapacity[role] || 0
                  const dem = team.projectDemand[role] || 0
                  const pct = cap > 0 ? Math.min((dem / cap) * 100, 100) : 0
                  const over = dem > cap
                  return (
                    <div key={role} className="qp-capacity-bar-item">
                      <span className="qp-bar-label">{role}</span>
                      <div className="qp-bar-track">
                        <div
                          className={`qp-bar-fill ${over ? 'over' : ''}`}
                          style={{
                            width: `${Math.min(pct, 100)}%`,
                            backgroundColor: getRoleColor(role),
                          }}
                        />
                      </div>
                      <span className="qp-bar-value">{dem.toFixed(0)}/{cap.toFixed(0)}d</span>
                    </div>
                  )
                })}
              </div>

              <EpicTable epics={team.epics} getRoleColor={getRoleColor} />
            </div>
          ))}

          {projectView.teams.length === 0 && (
            <div className="qp-empty">No epics found for this project in {quarter}</div>
          )}
        </>
      )}
    </div>
  )
}

// ==================== Shared Components ====================

function EpicTable({ epics, getRoleColor }: { epics: EpicDemandDto[], getRoleColor: (code: string) => string }) {
  if (epics.length === 0) return null

  const allRoles = [...new Set(epics.flatMap(e => Object.keys(e.demandByRole)))]

  return (
    <table className="qp-epic-table">
      <thead>
        <tr>
          <th>Epic</th>
          <th>Status</th>
          {allRoles.map(role => (
            <th key={role} style={{ color: getRoleColor(role) }}>{role}</th>
          ))}
          <th></th>
        </tr>
      </thead>
      <tbody>
        {epics.map(epic => (
          <tr key={epic.epicKey} className={epic.overCapacity ? 'qp-over-capacity' : ''}>
            <td>
              <span className="qp-epic-key" title={epic.epicKey}>{epic.epicKey}</span>
              <span className="qp-epic-summary">{epic.summary}</span>
            </td>
            <td><StatusBadge status={epic.status} /></td>
            {allRoles.map(role => (
              <td key={role} className="qp-demand-cell">
                {epic.demandByRole[role]?.toFixed(1) || '—'}
              </td>
            ))}
            <td>
              {epic.overCapacity && <span className="qp-over-badge" title="Exceeds remaining capacity">!</span>}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function SummaryCard({
  team, getRoleColor
}: {
  team: { teamId: number, teamName: string, teamColor: string | null, capacityByRole: Record<string, number>, demandByRole: Record<string, number>, utilizationPctByRole: Record<string, number>, overloaded: boolean }
  getRoleColor: (code: string) => string
}) {
  const roles = Object.keys(team.capacityByRole)

  return (
    <div className={`qp-summary-card ${team.overloaded ? 'overloaded' : ''}`}>
      <div className="qp-summary-card-header">
        <TeamBadge name={team.teamName} color={team.teamColor || '#666'} />
        {team.overloaded && <span className="qp-overcommit-badge">Overloaded</span>}
      </div>
      <div className="qp-summary-bars">
        {roles.map(role => {
          const cap = team.capacityByRole[role] || 0
          const dem = team.demandByRole[role] || 0
          const util = team.utilizationPctByRole[role] || 0
          return (
            <div key={role} className="qp-summary-bar-row">
              <span className="qp-bar-label" style={{ color: getRoleColor(role) }}>{role}</span>
              <div className="qp-bar-track">
                <div
                  className={`qp-bar-fill ${util > 100 ? 'over' : ''}`}
                  style={{
                    width: `${Math.min(util, 100)}%`,
                    backgroundColor: getRoleColor(role),
                  }}
                />
              </div>
              <span className="qp-bar-value">{dem.toFixed(0)}/{cap.toFixed(0)}d ({util.toFixed(0)}%)</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function BoostControl({
  currentBoost, onBoostChange
}: {
  currentBoost: number
  onBoostChange: (boost: number) => void
}) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState(String(currentBoost))

  const handleSave = () => {
    const num = parseInt(value, 10)
    if (!isNaN(num)) {
      onBoostChange(Math.max(-50, Math.min(50, num)))
    }
    setEditing(false)
  }

  if (editing) {
    return (
      <span className="qp-boost-control">
        <input
          type="number"
          className="qp-boost-input"
          value={value}
          onChange={e => setValue(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') handleSave(); if (e.key === 'Escape') setEditing(false) }}
          onBlur={handleSave}
          min={-50}
          max={50}
          autoFocus
        />
      </span>
    )
  }

  return (
    <span className="qp-boost-control" onClick={() => { setValue(String(currentBoost)); setEditing(true) }} title="Manual priority boost (-50 to +50)">
      {currentBoost !== 0 && (
        <span className={`qp-boost-value ${currentBoost > 0 ? 'positive' : 'negative'}`}>
          {currentBoost > 0 ? '+' : ''}{currentBoost}
        </span>
      )}
      <span className="qp-boost-icon">⚡</span>
    </span>
  )
}
