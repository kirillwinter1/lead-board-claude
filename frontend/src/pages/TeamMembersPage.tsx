import { useEffect, useState, useMemo } from 'react'
import { useParams, Link } from 'react-router-dom'
import { teamsApi, Team, TeamMember, CreateTeamMemberRequest, UpdateTeamMemberRequest, PlanningConfig } from '../api/teams'
import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { Modal } from '../components/Modal'
import './TeamsPage.css'

const GRADES = ['JUNIOR', 'MIDDLE', 'SENIOR'] as const

const DEFAULT_PLANNING_CONFIG: PlanningConfig = {
  gradeCoefficients: { senior: 0.8, middle: 1.0, junior: 1.5 },
  riskBuffer: 0.2,
  wipLimits: { team: 6, roleLimits: { SA: 2, DEV: 3, QA: 2 } },
  storyDuration: { roleDurations: { SA: 2, DEV: 2, QA: 2 } }
}

export function TeamMembersPage() {
  const { teamId } = useParams<{ teamId: string }>()
  const { getRoleCodes, getRoleColor, getRoleDisplayName } = useWorkflowConfig()
  const roles = getRoleCodes()

  const [team, setTeam] = useState<Team | null>(null)
  const [members, setMembers] = useState<TeamMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingMember, setEditingMember] = useState<TeamMember | null>(null)
  const [formData, setFormData] = useState<CreateTeamMemberRequest>({
    jiraAccountId: '',
    displayName: '',
    role: roles[0] || 'DEV',
    grade: 'MIDDLE',
    hoursPerDay: 6.0,
  })
  const [saving, setSaving] = useState(false)

  // Planning config state
  const [planningConfig, setPlanningConfig] = useState<PlanningConfig>(DEFAULT_PLANNING_CONFIG)
  const [showPlanningConfig, setShowPlanningConfig] = useState(false)
  const [savingConfig, setSavingConfig] = useState(false)

  const fetchData = () => {
    if (!teamId) return
    setLoading(true)

    Promise.all([
      teamsApi.getById(parseInt(teamId)),
      teamsApi.getMembers(parseInt(teamId)),
      teamsApi.getPlanningConfig(parseInt(teamId)),
    ])
      .then(([teamData, membersData, configData]) => {
        setTeam(teamData)
        setMembers(membersData)
        setPlanningConfig(configData)
        setError(null)
      })
      .catch(err => {
        setError(err.response?.data?.error || err.message)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchData()
  }, [teamId])

  const openCreateModal = () => {
    setEditingMember(null)
    setFormData({
      jiraAccountId: '',
      displayName: '',
      role: roles[0] || 'DEV',
      grade: 'MIDDLE',
      hoursPerDay: 6.0,
    })
    setIsModalOpen(true)
  }

  const openEditModal = (member: TeamMember) => {
    setEditingMember(member)
    setFormData({
      jiraAccountId: member.jiraAccountId,
      displayName: member.displayName || '',
      role: member.role,
      grade: member.grade,
      hoursPerDay: member.hoursPerDay,
    })
    setIsModalOpen(true)
  }

  const closeModal = () => {
    setIsModalOpen(false)
    setEditingMember(null)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!teamId) return
    setSaving(true)

    const id = parseInt(teamId)

    const request = editingMember
      ? teamsApi.updateMember(id, editingMember.id, formData as UpdateTeamMemberRequest)
      : teamsApi.addMember(id, formData)

    request
      .then(() => {
        closeModal()
        fetchData()
      })
      .catch(err => {
        alert(err.response?.data?.error || 'Failed to save member')
      })
      .finally(() => setSaving(false))
  }

  const handleDeactivate = (member: TeamMember) => {
    if (!teamId) return
    if (!confirm(`Are you sure you want to deactivate "${member.displayName || member.jiraAccountId}"?`)) return

    teamsApi.deactivateMember(parseInt(teamId), member.id)
      .then(() => fetchData())
      .catch(err => {
        alert(err.response?.data?.error || 'Failed to deactivate member')
      })
  }

  const getRoleBadgeStyle = (role: string): React.CSSProperties => {
    const color = getRoleColor(role)
    return {
      backgroundColor: color + '20',
      color: color,
      padding: '2px 8px',
      borderRadius: '4px',
      fontSize: '12px',
      fontWeight: 600,
      display: 'inline-block',
    }
  }

  const getGradeBadgeClass = (grade: string) => {
    switch (grade) {
      case 'JUNIOR': return 'grade-badge junior'
      case 'MIDDLE': return 'grade-badge middle'
      case 'SENIOR': return 'grade-badge senior'
      default: return 'grade-badge'
    }
  }

  const handleSavePlanningConfig = () => {
    if (!teamId) return
    setSavingConfig(true)

    teamsApi.updatePlanningConfig(parseInt(teamId), planningConfig)
      .then(updatedConfig => {
        setPlanningConfig(updatedConfig)
      })
      .catch(err => {
        alert(err.response?.data?.error || 'Failed to save planning config')
      })
      .finally(() => setSavingConfig(false))
  }

  const updateGradeCoefficient = (grade: 'senior' | 'middle' | 'junior', value: number) => {
    setPlanningConfig(prev => ({
      ...prev,
      gradeCoefficients: { ...prev.gradeCoefficients, [grade]: value }
    }))
  }

  const updateTeamWipLimit = (value: number) => {
    setPlanningConfig(prev => ({
      ...prev,
      wipLimits: { ...prev.wipLimits, team: value }
    }))
  }

  const updateRoleWipLimit = (role: string, value: number) => {
    setPlanningConfig(prev => ({
      ...prev,
      wipLimits: {
        ...prev.wipLimits,
        roleLimits: { ...(prev.wipLimits.roleLimits || {}), [role]: value }
      }
    }))
  }

  const updateStoryDuration = (role: string, value: number) => {
    setPlanningConfig(prev => ({
      ...prev,
      storyDuration: {
        roleDurations: { ...(prev.storyDuration?.roleDurations || {}), [role]: value }
      }
    }))
  }

  // Calculate recommended WIP limits based on team composition
  const recommendedWip = useMemo(() => {
    const totalCount = members.length
    const roleRecommendations: Record<string, number> = {}

    // Build role counts dynamically
    const roleCodes = roles.length > 0 ? roles : Object.keys(planningConfig.wipLimits.roleLimits || {})
    for (const role of roleCodes) {
      const count = members.filter(m => m.role === role).length
      roleRecommendations[role] = Math.ceil(count * 1.5)
    }

    return {
      team: Math.ceil(totalCount / Math.max(roleCodes.length, 1)),
      roles: roleRecommendations,
    }
  }, [members, roles, planningConfig.wipLimits.roleLimits])

  // Get WIP status color: green if <= recommended, yellow if <= 150%, red if > 150%
  const getWipStatus = (value: number, recommended: number): 'good' | 'warning' | 'danger' => {
    if (recommended === 0) return 'good' // No members of this role
    if (value <= recommended) return 'good'
    if (value <= recommended * 1.5) return 'warning'
    return 'danger'
  }

  // Get the role limits to display - merge config with known roles
  const roleWipEntries = useMemo(() => {
    const roleLimits = planningConfig.wipLimits.roleLimits || {}
    const allRoles = new Set([...roles, ...Object.keys(roleLimits)])
    return Array.from(allRoles).map(role => ({
      role,
      limit: roleLimits[role] ?? 2,
    }))
  }, [roles, planningConfig.wipLimits.roleLimits])

  // Get the story duration entries to display
  const storyDurationEntries = useMemo(() => {
    const roleDurations = planningConfig.storyDuration?.roleDurations || {}
    const allRoles = new Set([...roles, ...Object.keys(roleDurations)])
    return Array.from(allRoles).map(role => ({
      role,
      duration: roleDurations[role] ?? 2,
    }))
  }, [roles, planningConfig.storyDuration?.roleDurations])

  if (loading) {
    return <main className="main-content"><div className="loading">Loading...</div></main>
  }

  if (error) {
    return <main className="main-content"><div className="error">Error: {error}</div></main>
  }

  if (!team) {
    return <main className="main-content"><div className="error">Team not found</div></main>
  }

  return (
    <main className="main-content">
      <div className="page-header">
        <div className="page-header-left">
          <Link to="/board/teams" className="back-link">&larr; Back to Teams</Link>
          <h2>{team.name}</h2>
          {team.jiraTeamValue && (
            <span className="team-jira-value">{team.jiraTeamValue}</span>
          )}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to={`/board/teams/${teamId}/competency`} className="btn btn-secondary"
            style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}>
            Competency Matrix
          </Link>
          <button className="btn btn-primary" onClick={openCreateModal}>
            + Add Member
          </button>
        </div>
      </div>

      {members.length === 0 ? (
        <div className="empty">No members in this team yet. Add your first member!</div>
      ) : (
        <div className="members-table-container">
          <table className="members-table">
            <thead>
              <tr>
                <th>NAME</th>
                <th>JIRA ACCOUNT ID</th>
                <th>ROLE</th>
                <th>GRADE</th>
                <th>HOURS PER DAY</th>
                <th>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {members.map(member => (
                <tr key={member.id}>
                  <td className="cell-name">
                    <Link to={`/board/teams/${teamId}/member/${member.id}`} className="team-name-link">
                      {member.displayName || <span className="cell-muted">Not set</span>}
                    </Link>
                  </td>
                  <td className="cell-account-id">{member.jiraAccountId}</td>
                  <td>
                    <span style={getRoleBadgeStyle(member.role)}>
                      {getRoleDisplayName(member.role)}
                    </span>
                  </td>
                  <td>
                    <span className={getGradeBadgeClass(member.grade)}>{member.grade}</span>
                  </td>
                  <td className="cell-hours">{member.hoursPerDay}h</td>
                  <td>
                    <div className="actions">
                      <button className="btn btn-small btn-secondary" onClick={() => openEditModal(member)}>
                        Edit
                      </button>
                      <button className="btn btn-small btn-danger" onClick={() => handleDeactivate(member)}>
                        Deactivate
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Planning Configuration Section */}
      <div className="planning-config-section">
        <div
          className="planning-config-header"
          onClick={() => setShowPlanningConfig(!showPlanningConfig)}
        >
          <span className={`chevron ${showPlanningConfig ? 'expanded' : ''}`}>›</span>
          <h3>Настройки планирования</h3>
        </div>

        {showPlanningConfig && (
          <div className="planning-config-content">
            <div className="config-group">
              <div className="config-title-row">
                <h4>Коэффициенты грейдов</h4>
                <span className="config-info" title="Влияют на расчёт скорости выполнения задач. Чем меньше коэффициент — тем быстрее работает специалист.">?</span>
              </div>
              <p className="config-hint">
                Senior (0.8) выполняет 1 чел-день за 0.8 дня, Junior (1.5) — за 1.5 дня
              </p>
              <div className="config-row">
                <div className="config-field">
                  <label>Senior</label>
                  <input
                    type="number"
                    min="0.1"
                    max="3"
                    step="0.1"
                    value={planningConfig.gradeCoefficients.senior}
                    onChange={e => updateGradeCoefficient('senior', parseFloat(e.target.value) || 0.8)}
                  />
                </div>
                <div className="config-field">
                  <label>Middle</label>
                  <input
                    type="number"
                    min="0.1"
                    max="3"
                    step="0.1"
                    value={planningConfig.gradeCoefficients.middle}
                    onChange={e => updateGradeCoefficient('middle', parseFloat(e.target.value) || 1.0)}
                  />
                </div>
                <div className="config-field">
                  <label>Junior</label>
                  <input
                    type="number"
                    min="0.1"
                    max="3"
                    step="0.1"
                    value={planningConfig.gradeCoefficients.junior}
                    onChange={e => updateGradeCoefficient('junior', parseFloat(e.target.value) || 1.5)}
                  />
                </div>
              </div>
            </div>

            <div className="config-group">
              <div className="config-title-row">
                <h4>Буфер рисков</h4>
                <span className="config-info" title="Запас времени на непредвиденные обстоятельства: болезни, блокеры, техдолг. Добавляется к прогнозным датам.">?</span>
              </div>
              <p className="config-hint">
                Рекомендуется 15-25% для большинства команд
              </p>
              <div className="config-row">
                <div className="config-field config-field-percent">
                  <label>Буфер</label>
                  <div className="input-with-suffix">
                    <input
                      type="number"
                      min="0"
                      max="100"
                      step="5"
                      value={Math.round(planningConfig.riskBuffer * 100)}
                      onChange={e => setPlanningConfig(prev => ({
                        ...prev,
                        riskBuffer: (parseFloat(e.target.value) || 0) / 100
                      }))}
                    />
                    <span className="input-suffix">%</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="config-group">
              <div className="config-title-row">
                <h4>Длительность стори по ролям</h4>
                <span className="config-info" title="Среднее время работы над одной сторёй для каждой роли. Используется для расчёта параллельной работы: следующая роль начинает после завершения первой стори предыдущей.">?</span>
              </div>
              <p className="config-hint">
                Влияет на перекрытие фаз в timeline. Меньше значение — больше параллелизм.
              </p>
              <div className="config-row">
                {storyDurationEntries.map(({ role, duration }) => (
                  <div className="config-field" key={role}>
                    <label>{getRoleDisplayName(role)} (дней)</label>
                    <input
                      type="number"
                      min="0.5"
                      max="10"
                      step="0.5"
                      value={duration}
                      onChange={e => updateStoryDuration(role, parseFloat(e.target.value) || 2)}
                    />
                  </div>
                ))}
              </div>
            </div>

            <div className="config-group">
              <div className="config-title-row">
                <h4>WIP лимиты (рекомендательные)</h4>
                <span className="config-info" title="Рекомендуемые ограничения количества эпиков в работе. НЕ влияют на автопланирование — используются только для визуализации и метрик.">?</span>
              </div>
              <p className="config-hint wip-notice">
                Эти значения НЕ ограничивают планирование. Алгоритм планирует все эпики на основе реальной capacity команды.
              </p>
              <p className="config-hint">
                Рекомендация: команда = участники / кол-во ролей, на роль = участников x 1.5
              </p>
              <div className="config-row wip-row">
                <div className={`config-field wip-field ${getWipStatus(planningConfig.wipLimits.team, recommendedWip.team)}`}>
                  <label>Команда</label>
                  <input
                    type="number"
                    min="1"
                    max="20"
                    step="1"
                    value={planningConfig.wipLimits.team}
                    onChange={e => updateTeamWipLimit(parseInt(e.target.value) || 1)}
                  />
                  <span className="wip-recommendation">
                    рек. {recommendedWip.team || 1}
                  </span>
                </div>
                {roleWipEntries.map(({ role, limit }) => {
                  const roleCount = members.filter(m => m.role === role).length
                  const recommended = recommendedWip.roles[role] || 0
                  return (
                    <div
                      key={role}
                      className={`config-field wip-field ${getWipStatus(limit, recommended)}`}
                    >
                      <label>{getRoleDisplayName(role)} ({roleCount})</label>
                      <input
                        type="number"
                        min="1"
                        max="10"
                        step="1"
                        value={limit}
                        onChange={e => updateRoleWipLimit(role, parseInt(e.target.value) || 1)}
                      />
                      <span className="wip-recommendation">
                        рек. {recommended || 1}
                      </span>
                    </div>
                  )
                })}
              </div>
              <div className="wip-legend">
                <span className="wip-legend-item good">● Оптимально</span>
                <span className="wip-legend-item warning">● Допустимо</span>
                <span className="wip-legend-item danger">● Перегрузка</span>
              </div>
            </div>

            <div className="config-actions">
              <button
                className="btn btn-primary"
                onClick={handleSavePlanningConfig}
                disabled={savingConfig}
              >
                {savingConfig ? 'Сохранение...' : 'Сохранить настройки'}
              </button>
            </div>
          </div>
        )}
      </div>

      <Modal
        isOpen={isModalOpen}
        onClose={closeModal}
        title={editingMember ? 'Edit Member' : 'Add Member'}
      >
        <form onSubmit={handleSubmit} className="modal-form">
          <div className="form-group">
            <label htmlFor="jiraAccountId">Jira Account ID *</label>
            <input
              id="jiraAccountId"
              type="text"
              value={formData.jiraAccountId}
              onChange={e => setFormData({ ...formData, jiraAccountId: e.target.value })}
              placeholder="e.g. 5b10ac8d82e05b22cc7d4ef5"
              required
              disabled={!!editingMember}
              autoFocus={!editingMember}
            />
          </div>
          <div className="form-group">
            <label htmlFor="displayName">Display Name</label>
            <input
              id="displayName"
              type="text"
              value={formData.displayName || ''}
              onChange={e => setFormData({ ...formData, displayName: e.target.value })}
              placeholder="e.g. John Doe"
              autoFocus={!!editingMember}
            />
          </div>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="role">Role</label>
              <select
                id="role"
                value={formData.role}
                onChange={e => setFormData({ ...formData, role: e.target.value })}
              >
                {(roles.length > 0 ? roles : ['SA', 'DEV', 'QA']).map(role => (
                  <option key={role} value={role}>{getRoleDisplayName(role)}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="grade">Grade</label>
              <select
                id="grade"
                value={formData.grade}
                onChange={e => setFormData({ ...formData, grade: e.target.value as typeof GRADES[number] })}
              >
                {GRADES.map(grade => (
                  <option key={grade} value={grade}>{grade}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="hoursPerDay">Hours/Day</label>
              <input
                id="hoursPerDay"
                type="number"
                min="0.1"
                max="24"
                step="0.1"
                value={formData.hoursPerDay}
                onChange={e => setFormData({ ...formData, hoursPerDay: parseFloat(e.target.value) || 6 })}
              />
            </div>
          </div>
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={closeModal}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving...' : (editingMember ? 'Save' : 'Add')}
            </button>
          </div>
        </form>
      </Modal>
    </main>
  )
}
