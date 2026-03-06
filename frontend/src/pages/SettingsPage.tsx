import { useEffect, useState, useRef, useCallback } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import { BugSlaSettingsPage } from './BugSlaSettingsPage'
import {
  type JiraProject,
  type ProjectSyncStatus,
  listJiraProjects,
  createJiraProject,
  updateJiraProject,
  deleteJiraProject,
  getPerProjectSyncStatus,
} from '../api/jiraProjects'
import './SettingsPage.css'

interface User {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: string
}

interface WorklogProgress {
  inProgress: boolean
  processed: number
  total: number
  imported: number
}

interface SyncStatusResponse {
  syncInProgress: boolean
  lastSyncStartedAt: string | null
  lastSyncCompletedAt: string | null
  issuesCount: number
  error: string | null
  setupCompleted: boolean
  worklogProgress: WorklogProgress | null
}

const ROLES = ['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'VIEWER'] as const

export function SettingsPage() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [updating, setUpdating] = useState<number | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [syncStatus, setSyncStatus] = useState<SyncStatusResponse | null>(null)
  const [syncResult, setSyncResult] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const [changelogMonths, setChangelogMonths] = useState(6)
  const [changelogCount, setChangelogCount] = useState<{ issueCount: number; totalIssues: number } | null>(null)
  const [countingChangelogs, setCountingChangelogs] = useState(false)
  const [importingChangelogs, setImportingChangelogs] = useState(false)
  const [changelogResult, setChangelogResult] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  // Project management
  const [projects, setProjects] = useState<JiraProject[]>([])
  const [projectSyncStatuses, setProjectSyncStatuses] = useState<ProjectSyncStatus[]>([])
  const [newProjectKey, setNewProjectKey] = useState('')
  const [addingProject, setAddingProject] = useState(false)

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }, [])

  const startPolling = useCallback(() => {
    stopPolling()
    const poll = async () => {
      try {
        const res = await axios.get<SyncStatusResponse>('/api/sync/status')
        setSyncStatus(res.data)
        const wp = res.data.worklogProgress
        const busy = res.data.syncInProgress || (wp?.inProgress ?? false)
        if (!busy) {
          stopPolling()
          setSyncing(false)
          const msg = `Sync completed. ${res.data.issuesCount} issues synced` +
            (wp && wp.imported > 0 ? `, ${wp.imported} worklogs imported.` : '.')
          setSyncResult({ type: 'success', message: msg })
        }
      } catch {
        // ignore polling errors
      }
    }
    poll()
    pollRef.current = setInterval(poll, 2000)
  }, [stopPolling])

  const fetchProjects = useCallback(async () => {
    try {
      const [projs, statuses] = await Promise.all([listJiraProjects(), getPerProjectSyncStatus()])
      setProjects(projs)
      setProjectSyncStatuses(statuses)
    } catch {
      // ignore — user may not have admin role yet
    }
  }, [])

  useEffect(() => {
    fetchUsers()
    fetchProjects()
    return () => stopPolling()
  }, [stopPolling, fetchProjects])

  const fetchUsers = async () => {
    try {
      setLoading(true)
      const response = await axios.get<User[]>('/api/admin/users')
      setUsers(response.data)
      setError(null)
    } catch (err) {
      setError('Failed to load users')
      console.error('Failed to fetch users:', err)
    } finally {
      setLoading(false)
    }
  }

  const updateRole = async (userId: number, newRole: string) => {
    try {
      setUpdating(userId)
      const response = await axios.patch<User>(`/api/admin/users/${userId}/role`, {
        role: newRole
      })
      setUsers(users.map(u => u.id === userId ? response.data : u))
    } catch (err) {
      console.error('Failed to update role:', err)
      alert('Failed to update role')
    } finally {
      setUpdating(null)
    }
  }

  const checkChangelogCount = async () => {
    try {
      setCountingChangelogs(true)
      setChangelogCount(null)
      setChangelogResult(null)
      const params = changelogMonths > 0 ? { months: changelogMonths } : {}
      const response = await axios.get<{ issueCount: number; totalIssues: number }>('/api/sync/import-changelogs/count', { params })
      setChangelogCount(response.data)
    } catch (err) {
      setChangelogResult({ type: 'error', message: 'Failed to count issues' })
      console.error('Failed to count issues:', err)
    } finally {
      setCountingChangelogs(false)
    }
  }

  const importChangelogs = async () => {
    try {
      setImportingChangelogs(true)
      setChangelogResult(null)
      const params = changelogMonths > 0 ? { months: changelogMonths } : {}
      const response = await axios.post<{ status: string; message: string }>('/api/sync/import-changelogs', null, { params })
      setChangelogResult({ type: 'success', message: response.data.message })
      setChangelogCount(null)
    } catch (err) {
      setChangelogResult({ type: 'error', message: 'Failed to start changelog import' })
      console.error('Failed to import changelogs:', err)
    } finally {
      setImportingChangelogs(false)
    }
  }

  if (loading) {
    return (
      <div className="settings-page">
        <div className="settings-loading">Loading users...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="settings-page">
        <div className="settings-error">{error}</div>
      </div>
    )
  }

  return (
    <div className="settings-page">
      <h1 className="settings-title">Settings</h1>

      <section className="settings-section">
        <h2 className="settings-section-title">User Management</h2>
        <p className="settings-section-description">
          Manage user roles and permissions. Changes take effect immediately.
        </p>

        <table className="users-table">
          <thead>
            <tr>
              <th>User</th>
              <th>Email</th>
              <th>Role</th>
            </tr>
          </thead>
          <tbody>
            {users.map(user => (
              <tr key={user.id}>
                <td className="user-cell">
                  {user.avatarUrl && (
                    <img
                      src={user.avatarUrl}
                      alt=""
                      className="user-avatar-small"
                    />
                  )}
                  <span className="user-name">{user.displayName || 'Unknown'}</span>
                </td>
                <td className="email-cell">{user.email || '-'}</td>
                <td className="role-cell">
                  <select
                    value={user.role}
                    onChange={(e) => updateRole(user.id, e.target.value)}
                    disabled={updating === user.id}
                    className="role-select"
                  >
                    {ROLES.map(role => (
                      <option key={role} value={role}>
                        {role}
                      </option>
                    ))}
                  </select>
                  {updating === user.id && (
                    <span className="updating-indicator">...</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {users.length === 0 && (
          <div className="no-users">No users found</div>
        )}
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Jira Projects</h2>
        <p className="settings-section-description">
          Manage Jira projects to sync. Add multiple project keys to sync issues from several Jira projects.
        </p>

        <table className="users-table" style={{ marginBottom: 12 }}>
          <thead>
            <tr>
              <th>Key</th>
              <th>Name</th>
              <th>Active</th>
              <th>Sync</th>
              <th>Issues</th>
              <th>Last Sync</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {projects.map(proj => {
              const syncStatus = projectSyncStatuses.find(s => s.projectKey === proj.projectKey)
              return (
                <tr key={proj.id}>
                  <td style={{ fontWeight: 600, fontFamily: 'monospace' }}>
                    <Link to={`/workflow?project=${proj.projectKey}`} style={{ color: '#0052CC', textDecoration: 'none' }}>
                      {proj.projectKey}
                    </Link>
                  </td>
                  <td>
                    <input
                      type="text"
                      defaultValue={proj.displayName}
                      className="changelog-months-input"
                      style={{ width: 140 }}
                      onBlur={async (e) => {
                        if (e.target.value !== proj.displayName) {
                          await updateJiraProject(proj.id, { displayName: e.target.value })
                          fetchProjects()
                        }
                      }}
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      checked={proj.active}
                      onChange={async () => {
                        await updateJiraProject(proj.id, { active: !proj.active })
                        fetchProjects()
                      }}
                    />
                  </td>
                  <td>
                    <input
                      type="checkbox"
                      checked={proj.syncEnabled}
                      onChange={async () => {
                        await updateJiraProject(proj.id, { syncEnabled: !proj.syncEnabled })
                        fetchProjects()
                      }}
                    />
                  </td>
                  <td>{syncStatus?.issuesCount ?? '-'}</td>
                  <td style={{ fontSize: '0.85em' }}>
                    {syncStatus?.lastSyncCompletedAt
                      ? new Date(syncStatus.lastSyncCompletedAt).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
                      : 'Never'}
                    {syncStatus?.syncInProgress && <span style={{ marginLeft: 4, color: '#0052CC' }}>syncing...</span>}
                  </td>
                  <td>
                    <button
                      className="changelog-check-btn"
                      style={{ padding: '2px 8px', fontSize: '0.85em' }}
                      onClick={async () => {
                        if (confirm(`Delete project ${proj.projectKey}?`)) {
                          await deleteJiraProject(proj.id)
                          fetchProjects()
                        }
                      }}
                    >
                      Remove
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>

        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input
            type="text"
            placeholder="PROJECT_KEY"
            value={newProjectKey}
            onChange={e => setNewProjectKey(e.target.value.toUpperCase())}
            className="changelog-months-input"
            style={{ width: 140, textTransform: 'uppercase' }}
          />
          <button
            className="changelog-check-btn"
            disabled={addingProject || !newProjectKey.trim()}
            onClick={async () => {
              try {
                setAddingProject(true)
                await createJiraProject(newProjectKey.trim())
                setNewProjectKey('')
                fetchProjects()
              } catch (err) {
                alert('Failed to add project')
              } finally {
                setAddingProject(false)
              }
            }}
          >
            {addingProject ? 'Adding...' : 'Add Project'}
          </button>
        </div>
      </section>

      <section className="settings-section">
        <BugSlaSettingsPage />
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Jira Sync</h2>
        <p className="settings-section-description">
          Sync issues from Jira. Runs automatically on schedule, but you can trigger manually.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              className="changelog-check-btn"
              disabled={syncing}
              onClick={async () => {
                try {
                  setSyncing(true)
                  setSyncResult(null)
                  setSyncStatus(null)
                  await axios.post('/api/sync/trigger')
                  startPolling()
                } catch {
                  setSyncResult({ type: 'error', message: 'Failed to start sync' })
                  setSyncing(false)
                }
              }}
            >
              {syncing ? 'Syncing...' : 'Sync Now'}
            </button>
            {!syncing && syncResult && (
              <span className={syncResult.type === 'success' ? 'changelog-result-success' : 'changelog-result-error'}>
                {syncResult.message}
              </span>
            )}
          </div>
          {syncing && syncStatus && (() => {
            const wp = syncStatus.worklogProgress
            const worklogActive = wp?.inProgress && wp.total > 0
            return (
              <div className="sync-progress-container">
                {syncStatus.syncInProgress && !worklogActive && (
                  <div className="sync-progress-text">Syncing issues...</div>
                )}
                {!syncStatus.syncInProgress && worklogActive && (
                  <div className="sync-progress-text">Issues synced: {syncStatus.issuesCount}</div>
                )}
                {worklogActive && wp && (
                  <>
                    <div className="sync-progress-text">
                      Importing worklogs: {wp.processed}/{wp.total}
                      {wp.total > 0 && ` (${Math.round(wp.processed / wp.total * 100)}%)`}
                    </div>
                    <div className="sync-progress-bar-track">
                      <div
                        className="sync-progress-bar-fill"
                        style={{ width: `${wp.total > 0 ? (wp.processed / wp.total * 100) : 0}%` }}
                      />
                    </div>
                  </>
                )}
                {syncStatus.syncInProgress && !worklogActive && (
                  <div className="sync-progress-bar-track">
                    <div className="sync-progress-bar-fill sync-progress-bar-indeterminate" />
                  </div>
                )}
              </div>
            )
          })()}
        </div>
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Jira Changelog Import</h2>
        <p className="settings-section-description">
          Import full status transition history from Jira.
          Fixes started_at/done_at dates and enables accurate metrics (DSR, cycle time).
          Each issue requires a separate Jira API call (~100ms between calls).
        </p>

        <div className="changelog-controls">
          <div className="changelog-row">
            <label className="changelog-label">
              Period (months):
              <input
                type="number"
                min={0}
                max={120}
                value={changelogMonths}
                onChange={(e) => {
                  setChangelogMonths(parseInt(e.target.value) || 0)
                  setChangelogCount(null)
                }}
                className="changelog-months-input"
              />
              <span className="changelog-hint">
                {changelogMonths > 0 ? `Issues updated in last ${changelogMonths} months` : 'All issues (no filter)'}
              </span>
            </label>
          </div>

          <div className="changelog-row">
            <button
              onClick={checkChangelogCount}
              disabled={countingChangelogs}
              className="changelog-check-btn"
            >
              {countingChangelogs ? 'Counting...' : 'Check Count'}
            </button>

            {changelogCount && (
              <span className="changelog-count-info">
                {changelogCount.issueCount} issues to process (out of {changelogCount.totalIssues} total)
                {' '}— ~{Math.ceil(changelogCount.issueCount * 0.15)} sec
              </span>
            )}
          </div>

          {changelogCount && changelogCount.issueCount > 0 && (
            <div className="changelog-row changelog-confirm">
              <button
                onClick={importChangelogs}
                disabled={importingChangelogs}
                className="changelog-import-btn"
              >
                {importingChangelogs ? 'Starting...' : `Import ${changelogCount.issueCount} issues`}
              </button>
              <span className="changelog-warn">
                Runs in background. Will make {changelogCount.issueCount} Jira API calls.
              </span>
            </div>
          )}

          {changelogCount && changelogCount.issueCount === 0 && (
            <div className="changelog-row">
              <span className="changelog-hint">No issues found for the selected period.</span>
            </div>
          )}

          {changelogResult && (
            <div className="changelog-row">
              <span className={changelogResult.type === 'success' ? 'changelog-result-success' : 'changelog-result-error'}>
                {changelogResult.message}
              </span>
            </div>
          )}
        </div>
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Role Permissions</h2>
        <div className="permissions-table-wrapper">
          <table className="permissions-table">
            <thead>
              <tr>
                <th>Permission</th>
                <th>Admin</th>
                <th>PM</th>
                <th>Team Lead</th>
                <th>Member</th>
                <th>Viewer</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>View Board/Timeline</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
              </tr>
              <tr>
                <td>Manage Projects/RICE</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Planning Poker</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Manage Teams</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-partial">Own team</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Edit Priorities</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Sync Trigger</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
              <tr>
                <td>Settings/Admin</td>
                <td className="permission-yes">Yes</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
                <td className="permission-no">No</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
