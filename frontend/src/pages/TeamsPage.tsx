import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { teamsApi, Team, CreateTeamRequest } from '../api/teams'
import { Modal } from '../components/Modal'

export function TeamsPage() {
  const [teams, setTeams] = useState<Team[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingTeam, setEditingTeam] = useState<Team | null>(null)
  const [formData, setFormData] = useState<CreateTeamRequest>({ name: '', jiraTeamValue: '' })
  const [saving, setSaving] = useState(false)

  const fetchTeams = () => {
    setLoading(true)
    teamsApi.getAll()
      .then(data => {
        setTeams(data)
        setError(null)
      })
      .catch(err => {
        setError(err.response?.data?.error || err.message)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchTeams()
  }, [])

  const openCreateModal = () => {
    setEditingTeam(null)
    setFormData({ name: '', jiraTeamValue: '' })
    setIsModalOpen(true)
  }

  const openEditModal = (team: Team) => {
    setEditingTeam(team)
    setFormData({ name: team.name, jiraTeamValue: team.jiraTeamValue || '' })
    setIsModalOpen(true)
  }

  const closeModal = () => {
    setIsModalOpen(false)
    setEditingTeam(null)
    setFormData({ name: '', jiraTeamValue: '' })
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setSaving(true)

    const request = editingTeam
      ? teamsApi.update(editingTeam.id, formData)
      : teamsApi.create(formData)

    request
      .then(() => {
        closeModal()
        fetchTeams()
      })
      .catch(err => {
        alert(err.response?.data?.error || 'Failed to save team')
      })
      .finally(() => setSaving(false))
  }

  const handleDelete = (team: Team) => {
    if (!confirm(`Are you sure you want to deactivate team "${team.name}"?`)) return

    teamsApi.delete(team.id)
      .then(() => fetchTeams())
      .catch(err => {
        alert(err.response?.data?.error || 'Failed to deactivate team')
      })
  }

  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Teams</h2>
        <button className="btn btn-primary" onClick={openCreateModal}>
          + Add Team
        </button>
      </div>

      {loading && <div className="loading">Loading teams...</div>}
      {error && <div className="error">Error: {error}</div>}

      {!loading && !error && teams.length === 0 && (
        <div className="empty">No teams yet. Create your first team!</div>
      )}

      {!loading && !error && teams.length > 0 && (
        <div className="teams-table-container">
          <table className="teams-table">
            <thead>
              <tr>
                <th>NAME</th>
                <th>JIRA TEAM VALUE</th>
                <th>MEMBERS</th>
                <th>CREATED</th>
                <th>ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {teams.map(team => (
                <tr key={team.id}>
                  <td>
                    <Link to={`/teams/${team.id}`} className="team-name-link">
                      {team.name}
                    </Link>
                  </td>
                  <td className="cell-muted">{team.jiraTeamValue || '--'}</td>
                  <td>
                    <span className="member-count">{team.memberCount}</span>
                  </td>
                  <td className="cell-muted">
                    {new Date(team.createdAt).toLocaleDateString()}
                  </td>
                  <td>
                    <div className="actions">
                      <button className="btn btn-small btn-secondary" onClick={() => openEditModal(team)}>
                        Edit
                      </button>
                      <button className="btn btn-small btn-danger" onClick={() => handleDelete(team)}>
                        Delete
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
        title={editingTeam ? 'Edit Team' : 'Create Team'}
      >
        <form onSubmit={handleSubmit} className="modal-form">
          <div className="form-group">
            <label htmlFor="name">Team Name *</label>
            <input
              id="name"
              type="text"
              value={formData.name}
              onChange={e => setFormData({ ...formData, name: e.target.value })}
              placeholder="e.g. Backend Team"
              required
              autoFocus
            />
          </div>
          <div className="form-group">
            <label htmlFor="jiraTeamValue">Jira Team Value</label>
            <input
              id="jiraTeamValue"
              type="text"
              value={formData.jiraTeamValue || ''}
              onChange={e => setFormData({ ...formData, jiraTeamValue: e.target.value })}
              placeholder="e.g. backend-team"
            />
            <span className="form-hint">Used to map Epics from Jira Team field</span>
          </div>
          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={closeModal}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Saving...' : (editingTeam ? 'Save' : 'Create')}
            </button>
          </div>
        </form>
      </Modal>
    </main>
  )
}
