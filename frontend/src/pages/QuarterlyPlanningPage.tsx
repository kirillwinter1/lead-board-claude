import { useEffect, useState, useCallback, useRef } from 'react'
import { useSearchParams } from 'react-router-dom'
import axios from 'axios'
import { MetricCard } from '../components/metrics/MetricCard'
import { StatusBadge } from '../components/board/StatusBadge'
import { TeamBadge } from '../components/TeamBadge'

import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import {
  quarterlyPlanningApi,
  QuarterlyDemandDto,
  QuarterlySummaryDto,
  ProjectViewDto,
  EpicDemandDto,
} from '../api/quarterlyPlanning'
import { ProjectDto } from '../api/projects'
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
  const { getRoleColor, getRoleCodes } = useWorkflowConfig()

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
      axios.get<ProjectDto[]>('/api/projects'),
      quarterlyPlanningApi.getAvailableQuarters(),
    ]).then(([teamsRes, projectsRes, quarters]) => {
      setTeams(teamsRes.data)
      setProjects(projectsRes.data.map(p => ({ issueKey: p.issueKey, summary: p.summary })))
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

  const handleTeamChange = (value: string | null) => {
    const params: Record<string, string> = {}
    if (value) params.teamId = value
    if (selectedProjectKey) params.projectKey = selectedProjectKey
    setSearchParams(params)
  }

  const handleProjectChange = (value: string | null) => {
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
          <SingleSelectDropdown
            label="Quarter"
            options={availableQuarters.map(q => ({ value: q, label: q }))}
            selected={quarter}
            onChange={v => v && setQuarter(v)}
            allowClear={false}
          />
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
          getRoleCodes={getRoleCodes}
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
          getRoleCodes={getRoleCodes}
          onBoostChange={handleBoostChange}
        />
      )}
    </div>
  )
}

// ==================== Helpers ====================

/** Sort roles by pipeline order from WorkflowConfig, unknown roles at end */
function sortRoles(roles: string[], getRoleCodes: () => string[]): string[] {
  const configOrder = getRoleCodes()
  const sorted = configOrder.filter(r => roles.includes(r))
  roles.forEach(r => { if (!configOrder.includes(r)) sorted.push(r) })
  return sorted
}

/** Collect all role keys from capacity + demand maps */
function collectAllRoles(
  capacity: Record<string, number>,
  demand: Record<string, number>,
  getRoleCodes: () => string[]
): string[] {
  const all = new Set([...Object.keys(capacity), ...Object.keys(demand)])
  return sortRoles([...all], getRoleCodes)
}

// ==================== Team View ====================

function TeamView({
  teams, selectedTeamId, onTeamChange, teamDemand, summary, quarter, getRoleColor, getRoleCodes, onBoostChange
}: {
  teams: Team[]
  selectedTeamId: number | null
  onTeamChange: (value: string | null) => void
  teamDemand: QuarterlyDemandDto | null
  summary: QuarterlySummaryDto | null
  quarter: string
  getRoleColor: (code: string) => string
  getRoleCodes: () => string[]
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

  // Chart data — use capacity from teamDemand directly (more reliable than summary snapshot)
  const chartRoles = teamDemand?.capacity
    ? collectAllRoles(
        teamDemand.capacity.capacityByRole,
        (() => {
          const dem: Record<string, number> = {}
          for (const p of teamDemand.projects) {
            for (const [k, v] of Object.entries(p.totalDemandByRole)) {
              dem[k] = (dem[k] || 0) + v
            }
          }
          return dem
        })(),
        getRoleCodes
      )
    : []
  const demandByRole: Record<string, number> = {}
  if (teamDemand) {
    for (const p of teamDemand.projects) {
      for (const [k, v] of Object.entries(p.totalDemandByRole)) {
        demandByRole[k] = (demandByRole[k] || 0) + v
      }
    }
    for (const e of teamDemand.unassignedEpics) {
      for (const [k, v] of Object.entries(e.demandByRole)) {
        demandByRole[k] = (demandByRole[k] || 0) + v
      }
    }
  }
  const chartData = chartRoles.map(role => ({
    role,
    Capacity: teamDemand?.capacity.capacityByRole[role] || 0,
    Demand: demandByRole[role] || 0,
    color: getRoleColor(role),
  }))

  return (
    <div className="qp-team-view">
      <div className="qp-filter-row">
        <SingleSelectDropdown
          label="Team"
          options={teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined }))}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={onTeamChange}
          placeholder="Select team..."
        />
      </div>

      {selectedTeamId && teamDemand && (
        <>
          <div className="qp-metrics-row">
            <MetricCard title="Capacity" value={`${totalCapacity.toFixed(0)} days`} tooltip="Total effective days for the quarter (adjusted by grade and hours)" />
            <MetricCard title="Demand" value={`${totalDemand.toFixed(0)} days`} tooltip="Total demand from all epics (with risk buffer)" />
            <MetricCard
              title="Utilization"
              value={`${utilization}%`}
              trend={utilization > 100 ? 'down' : utilization > 80 ? 'neutral' : 'up'}
              tooltip="Demand / Capacity"
            />
            <MetricCard title="Overcommit" value={overcommitCount} tooltip="Number of epics exceeding remaining capacity" />
          </div>

          {chartData.length > 0 && (
            <div className="qp-chart-container">
              <h3>Capacity vs Demand by Role</h3>
              <div className="qp-chart-bars">
                {chartData.map(item => {
                  const max = Math.max(...chartData.map(d => Math.max(d.Capacity, d.Demand)), 1)
                  const capPct = (item.Capacity / max) * 100
                  const demPct = (item.Demand / max) * 100
                  const over = item.Demand > item.Capacity
                  return (
                    <div key={item.role} className="qp-chart-bar-group">
                      <div className="qp-chart-bar-pair">
                        <div className="qp-chart-bar-wrapper">
                          <div
                            className="qp-chart-bar"
                            style={{ height: `${capPct}%`, backgroundColor: item.color, opacity: 0.3 }}
                            title={`Capacity: ${item.Capacity.toFixed(1)}d`}
                          />
                          <span className="qp-chart-bar-val">{item.Capacity.toFixed(0)}d</span>
                        </div>
                        <div className="qp-chart-bar-wrapper">
                          <div
                            className={`qp-chart-bar ${over ? 'over' : ''}`}
                            style={{ height: `${demPct}%`, backgroundColor: over ? '#d32f2f' : item.color }}
                            title={`Demand: ${item.Demand.toFixed(1)}d`}
                          />
                          <span className="qp-chart-bar-val">{item.Demand.toFixed(0)}d</span>
                        </div>
                      </div>
                      <span className="qp-chart-bar-label" style={{ color: item.color }}>{item.role}</span>
                    </div>
                  )
                })}
              </div>
              <div className="qp-chart-legend">
                <span className="qp-chart-legend-item"><span className="qp-chart-legend-swatch" style={{ opacity: 0.3 }} /> Capacity</span>
                <span className="qp-chart-legend-item"><span className="qp-chart-legend-swatch" style={{ opacity: 1 }} /> Demand</span>
              </div>
            </div>
          )}

          {teamDemand.projects.map(project => (
            <div key={project.projectKey} className={`qp-project-group ${!project.fitsInCapacity ? 'qp-project-over' : ''}`}>
              <div className="qp-project-header" onClick={() => toggleProject(project.projectKey)}>
                <span className="qp-collapse-icon">{collapsedProjects.has(project.projectKey) ? '▸' : '▾'}</span>
                <span className="qp-project-key">{project.projectKey}</span>
                <span className="qp-project-name">{project.summary}</span>
                {project.riceNormalizedScore != null && project.riceNormalizedScore > 0 && (
                  <span className="qp-rice-badge" title={`RICE: ${project.riceNormalizedScore.toFixed(0)}/100`}>
                    RICE: {project.riceNormalizedScore.toFixed(0)}
                  </span>
                )}
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
                <EpicTable epics={project.epics} getRoleColor={getRoleColor} getRoleCodes={getRoleCodes}
                  capacityByRole={teamDemand.capacity.capacityByRole} />
              )}
            </div>
          ))}

          {teamDemand.unassignedEpics.length > 0 && (
            <div className="qp-project-group">
              <div className="qp-project-header">
                <span className="qp-project-name" style={{ fontStyle: 'italic', color: '#888' }}>Unassigned Epics</span>
              </div>
              <EpicTable epics={teamDemand.unassignedEpics} getRoleColor={getRoleColor} getRoleCodes={getRoleCodes} />
            </div>
          )}
        </>
      )}

      {!selectedTeamId && summary && (
        <div className="qp-summary-grid">
          <h3>All Teams — {quarter}</h3>
          {summary.teams.map(team => (
            <SummaryCard key={team.teamId} team={team} getRoleColor={getRoleColor} getRoleCodes={getRoleCodes} />
          ))}
        </div>
      )}
    </div>
  )
}

// ==================== Project View ====================

function ProjectView({
  projects, selectedProjectKey, onProjectChange, projectView, quarter, getRoleColor, getRoleCodes, onBoostChange
}: {
  projects: ProjectOption[]
  selectedProjectKey: string | null
  onProjectChange: (value: string | null) => void
  projectView: ProjectViewDto | null
  quarter: string
  getRoleColor: (code: string) => string
  getRoleCodes: () => string[]
  onBoostChange: (key: string, boost: number) => void
}) {
  return (
    <div className="qp-project-view">
      <div className="qp-filter-row">
        <SingleSelectDropdown
          label="Project"
          options={projects.map(p => ({ value: p.issueKey, label: p.summary || p.issueKey }))}
          selected={selectedProjectKey}
          onChange={onProjectChange}
          placeholder="Select project..."
        />
      </div>

      {!selectedProjectKey && (
        <div className="qp-empty">Select a project to see team allocation and epic demand.</div>
      )}

      {projectView && (
        <>
          <div className="qp-project-info">
            <span className="qp-project-key-lg">{projectView.projectKey}</span>
            <h3>{projectView.summary}</h3>
            <span className="qp-priority-badge">P: {projectView.priorityScore?.toFixed(0)}</span>
            <BoostControl
              currentBoost={projectView.manualBoost || 0}
              onBoostChange={(boost) => onBoostChange(projectView.projectKey, boost)}
            />
          </div>

          {projectView.teams.map(team => {
            const roles = collectAllRoles(team.teamCapacity, team.projectDemand, getRoleCodes)
            return (
              <div key={team.teamId} className={`qp-team-allocation ${team.overloaded ? 'qp-team-over' : ''}`}>
                <div className="qp-team-allocation-header">
                  <TeamBadge name={team.teamName} color={team.teamColor || '#666'} />
                  {team.overloaded && <span className="qp-overcommit-badge">Over Capacity</span>}
                </div>

                <div className="qp-capacity-bars">
                  {roles.map(role => {
                    const cap = team.teamCapacity[role] || 0
                    const dem = team.projectDemand[role] || 0
                    const pct = cap > 0 ? (dem / cap) * 100 : 0
                    const over = dem > cap
                    return (
                      <div key={role} className="qp-capacity-bar-item">
                        <span className="qp-bar-label" style={{ color: getRoleColor(role) }}>{role}</span>
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

                <EpicTable epics={team.epics} getRoleColor={getRoleColor} getRoleCodes={getRoleCodes}
                  capacityByRole={team.teamCapacity} />
              </div>
            )
          })}

          {projectView.teams.length === 0 && (
            <div className="qp-empty">No epics found for this project in {quarter}</div>
          )}
        </>
      )}
    </div>
  )
}

// ==================== Shared Components ====================

function EpicTable({ epics, getRoleColor, getRoleCodes, capacityByRole }: {
  epics: EpicDemandDto[]
  getRoleColor: (code: string) => string
  getRoleCodes: () => string[]
  capacityByRole?: Record<string, number>
}) {
  if (epics.length === 0) return null

  // Use all roles from capacity + epic demands, sorted by pipeline order
  const epicRoles = new Set(epics.flatMap(e => Object.keys(e.demandByRole)))
  const capRoles = capacityByRole ? Object.keys(capacityByRole) : []
  const allRolesSet = new Set([...capRoles, ...epicRoles])
  const allRoles = sortRoles([...allRolesSet], getRoleCodes)

  return (
    <table className="qp-epic-table">
      <thead>
        <tr>
          <th>Epic</th>
          <th>Status</th>
          {allRoles.map(role => (
            <th key={role} className="qp-role-header" style={{ color: getRoleColor(role) }}>{role}</th>
          ))}
          <th></th>
        </tr>
      </thead>
      <tbody>
        {epics.map(epic => (
          <tr key={epic.epicKey} className={epic.overCapacity ? 'qp-over-capacity' : ''}>
            <td className="qp-epic-name-cell">
              <a className="qp-epic-key" href={`/?epicKey=${epic.epicKey}`} title={epic.summary}>{epic.epicKey}</a>
              <span className="qp-epic-summary">{epic.summary}</span>
            </td>
            <td><StatusBadge status={epic.status} /></td>
            {allRoles.map(role => (
              <td key={role} className="qp-demand-cell">
                {epic.demandByRole[role] != null
                  ? <span className="qp-demand-value">{epic.demandByRole[role].toFixed(1)}</span>
                  : <span className="qp-demand-empty">—</span>}
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
  team, getRoleColor, getRoleCodes
}: {
  team: { teamId: number, teamName: string, teamColor: string | null, capacityByRole: Record<string, number>, demandByRole: Record<string, number>, utilizationPctByRole: Record<string, number>, overloaded: boolean }
  getRoleColor: (code: string) => string
  getRoleCodes: () => string[]
}) {
  const roles = collectAllRoles(team.capacityByRole, team.demandByRole, getRoleCodes)
  const totalCap = Object.values(team.capacityByRole).reduce((a, b) => a + b, 0)
  const totalDem = Object.values(team.demandByRole).reduce((a, b) => a + b, 0)
  const totalUtil = totalCap > 0 ? Math.round((totalDem / totalCap) * 100) : 0

  return (
    <div className={`qp-summary-card ${team.overloaded ? 'overloaded' : ''}`}>
      <div className="qp-summary-card-header">
        <TeamBadge name={team.teamName} color={team.teamColor || '#666'} />
        <span className={`qp-util-total ${totalUtil > 100 ? 'over' : totalUtil > 80 ? 'warn' : ''}`}>
          {totalUtil}%
        </span>
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
