import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { teamsApi, Team, TeamMember, CreateTeamMemberRequest, UpdateTeamMemberRequest } from '../api/teams'
import { Modal } from '../components/Modal'

const ROLES = ['SA', 'DEV', 'QA'] as const
const GRADES = ['JUNIOR', 'MIDDLE', 'SENIOR'] as const

export function TeamMembersPage() {
  const { teamId } = useParams<{ teamId: string }>()
  const [team, setTeam] = useState<Team | null>(null)
  const [members, setMembers] = useState<TeamMember[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingMember, setEditingMember] = useState<TeamMember | null>(null)
  const [formData, setFormData] = useState<CreateTeamMemberRequest>({
    jiraAccountId: '',
    displayName: '',
    role: 'DEV',
    grade: 'MIDDLE',
    hoursPerDay: 6.0,
  })
  const [saving, setSaving] = useState(false)

  const fetchData = () => {
    if (!teamId) return
    setLoading(true)

    Promise.all([
      teamsApi.getById(parseInt(teamId)),
      teamsApi.getMembers(parseInt(teamId)),
    ])
      .then(([teamData, membersData]) => {
        setTeam(teamData)
        setMembers(membersData)
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
      role: 'DEV',
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

  const getRoleBadgeClass = (role: string) => {
    switch (role) {
      case 'SA': return 'role-badge sa'
      case 'DEV': return 'role-badge dev'
      case 'QA': return 'role-badge qa'
      default: return 'role-badge'
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
          <Link to="/teams" className="back-link">&larr; Back to Teams</Link>
          <h2>{team.name}</h2>
          {team.jiraTeamValue && (
            <span className="team-jira-value">{team.jiraTeamValue}</span>
          )}
        </div>
        <button className="btn btn-primary" onClick={openCreateModal}>
          + Add Member
        </button>
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
                <th>HOURS/DAY</th>
                <th>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {members.map(member => (
                <tr key={member.id}>
                  <td className="cell-name">
                    {member.displayName || <span className="cell-muted">Not set</span>}
                  </td>
                  <td className="cell-account-id">{member.jiraAccountId}</td>
                  <td>
                    <span className={getRoleBadgeClass(member.role)}>{member.role}</span>
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
                onChange={e => setFormData({ ...formData, role: e.target.value as typeof ROLES[number] })}
              >
                {ROLES.map(role => (
                  <option key={role} value={role}>{role}</option>
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
                max="12"
                step="0.5"
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
