import { useEffect, useState, useCallback, useRef } from 'react'
import { Link } from 'react-router-dom'
import { teamsApi, Team, CreateTeamRequest, TeamsConfig, TeamSyncStatus } from '../api/teams'
import { TEAM_PALETTE } from '../constants/teamColors'
import { Modal } from '../components/Modal'
import './TeamsPage.css'

export function TeamsPage() {
  const [teams, setTeams] = useState<Team[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingTeam, setEditingTeam] = useState<Team | null>(null)
  const [formData, setFormData] = useState<CreateTeamRequest>({ name: '', jiraTeamValue: '', color: undefined })
  const [saving, setSaving] = useState(false)

  const [config, setConfig] = useState<TeamsConfig | null>(null)
  const [syncStatus, setSyncStatus] = useState<TeamSyncStatus | null>(null)
  const [colorPickerTeamId, setColorPickerTeamId] = useState<number | null>(null)
  const colorPickerRef = useRef<HTMLDivElement>(null)

  const fetchTeams = useCallback(() => {
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
  }, [])

  const fetchConfig = useCallback(() => {
    teamsApi.getConfig()
      .then(setConfig)
      .catch(() => setConfig({ manualTeamManagement: true, organizationId: '' }))
  }, [])

  const fetchSyncStatus = useCallback(() => {
    teamsApi.getSyncStatus()
      .then(status => {
        setSyncStatus(status)
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    fetchConfig()
    fetchTeams()
    fetchSyncStatus()
  }, [fetchConfig, fetchTeams, fetchSyncStatus])

  const openCreateModal = () => {
    setEditingTeam(null)
    setFormData({ name: '', jiraTeamValue: '', color: undefined })
    setIsModalOpen(true)
  }

  const openEditModal = (team: Team) => {
    setEditingTeam(team)
    setFormData({ name: team.name, jiraTeamValue: team.jiraTeamValue || '', color: team.color || undefined })
    setIsModalOpen(true)
  }

  const closeModal = () => {
    setIsModalOpen(false)
    setEditingTeam(null)
    setFormData({ name: '', jiraTeamValue: '', color: undefined })
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

  const formatSyncTime = (isoString: string | null): string => {
    if (!isoString) return 'Never'
    const date = new Date(isoString)
    return date.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  // Close color picker on click outside
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (colorPickerRef.current && !colorPickerRef.current.contains(e.target as Node)) {
        setColorPickerTeamId(null)
      }
    }
    if (colorPickerTeamId !== null) {
      document.addEventListener('mousedown', handleClickOutside)
      return () => document.removeEventListener('mousedown', handleClickOutside)
    }
  }, [colorPickerTeamId])

  const handleColorChange = (teamId: number, color: string) => {
    teamsApi.update(teamId, { color })
      .then(() => {
        setTeams(prev => prev.map(t => t.id === teamId ? { ...t, color } : t))
        setColorPickerTeamId(null)
      })
      .catch(err => alert(err.response?.data?.error || 'Failed to update color'))
  }

  const canManageTeams = config?.manualTeamManagement ?? false
  const canSync = config?.organizationId && config.organizationId.length > 0

  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Teams</h2>
        <div className="page-header-actions">
          {canSync && syncStatus && (
            <div className="sync-status">
              <span className="sync-info">
                Last sync: {formatSyncTime(syncStatus.lastSyncTime)}
                {syncStatus.error && <span className="sync-error"> (Error)</span>}
              </span>
            </div>
          )}
          {canManageTeams && (
            <button className="btn btn-primary" onClick={openCreateModal}>
              + Add Team
            </button>
          )}
        </div>
      </div>

      {loading && <div className="loading">Loading teams...</div>}
      {error && <div className="error">Error: {error}</div>}

      {!loading && !error && teams.length === 0 && (
        <div className="empty">
          {canSync
            ? 'No teams yet. Click "Sync from Atlassian" to import teams.'
            : 'No teams yet. Create your first team!'}
        </div>
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
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, position: 'relative' }}>
                      <span
                        onClick={() => setColorPickerTeamId(colorPickerTeamId === team.id ? null : team.id)}
                        title="Click to change color"
                        style={{
                          display: 'inline-block',
                          width: 16,
                          height: 16,
                          borderRadius: '50%',
                          backgroundColor: team.color || '#ccc',
                          flexShrink: 0,
                          cursor: 'pointer',
                          border: '2px solid rgba(0,0,0,0.1)',
                          transition: 'transform 0.15s',
                        }}
                        onMouseEnter={e => (e.currentTarget.style.transform = 'scale(1.2)')}
                        onMouseLeave={e => (e.currentTarget.style.transform = 'scale(1)')}
                      />
                      {colorPickerTeamId === team.id && (
                        <div
                          ref={colorPickerRef}
                          style={{
                            position: 'absolute',
                            top: '100%',
                            left: 0,
                            zIndex: 100,
                            background: '#fff',
                            border: '1px solid #dfe1e6',
                            borderRadius: 8,
                            padding: 8,
                            display: 'grid',
                            gridTemplateColumns: 'repeat(6, 1fr)',
                            gap: 6,
                            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                            marginTop: 4,
                          }}
                        >
                          {TEAM_PALETTE.map(c => (
                            <span
                              key={c}
                              onClick={() => handleColorChange(team.id, c)}
                              style={{
                                width: 28,
                                height: 28,
                                borderRadius: '50%',
                                backgroundColor: c,
                                cursor: 'pointer',
                                border: team.color === c ? '3px solid #172B4D' : '2px solid transparent',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                              }}
                            >
                              {team.color === c && (
                                <svg width="12" height="12" viewBox="0 0 14 14" fill="none">
                                  <path d="M2.5 7L5.5 10L11.5 4" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                                </svg>
                              )}
                            </span>
                          ))}
                        </div>
                      )}
                      <Link to={`/board/teams/${team.id}`} className="team-name-link">
                        {team.name}
                      </Link>
                    </div>
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
                      {canManageTeams && (
                        <>
                          <button className="btn btn-small btn-secondary" onClick={() => openEditModal(team)}>
                            Edit
                          </button>
                          <button className="btn btn-small btn-danger" onClick={() => handleDelete(team)}>
                            Delete
                          </button>
                        </>
                      )}
                      {!canManageTeams && (
                        <span className="cell-muted">--</span>
                      )}
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
          <div className="form-group">
            <label>Team Color</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 4 }}>
              {TEAM_PALETTE.map(color => (
                <button
                  key={color}
                  type="button"
                  onClick={() => setFormData({ ...formData, color })}
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    backgroundColor: color,
                    border: formData.color === color ? '3px solid #172B4D' : '2px solid transparent',
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    padding: 0,
                    outline: formData.color === color ? '2px solid #fff' : 'none',
                    outlineOffset: -4,
                  }}
                >
                  {formData.color === color && (
                    <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                      <path d="M2.5 7L5.5 10L11.5 4" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                    </svg>
                  )}
                </button>
              ))}
            </div>
            <span className="form-hint">Auto-assigned if not selected</span>
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
