import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import axios from 'axios'
import './SettingsPage.css'

interface User {
  id: number
  accountId: string
  displayName: string
  email: string
  avatarUrl: string | null
  role: string
}

const ROLES = ['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'VIEWER'] as const

export function SettingsPage() {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [updating, setUpdating] = useState<number | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [syncResult, setSyncResult] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [changelogMonths, setChangelogMonths] = useState(6)
  const [changelogCount, setChangelogCount] = useState<{ issueCount: number; totalIssues: number } | null>(null)
  const [countingChangelogs, setCountingChangelogs] = useState(false)
  const [importingChangelogs, setImportingChangelogs] = useState(false)
  const [changelogResult, setChangelogResult] = useState<{ type: 'success' | 'error'; message: string } | null>(null)

  useEffect(() => {
    fetchUsers()
  }, [])

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
        <h2 className="settings-section-title">Workflow Configuration</h2>
        <p className="settings-section-description">
          Configure issue types, statuses, roles and link type mappings.
        </p>
        <Link to="/board/workflow" className="role-select" style={{ display: 'inline-block', textDecoration: 'none', textAlign: 'center', padding: '8px 16px', background: '#F4F5F7', borderRadius: 4, color: '#172B4D', fontWeight: 500 }}>
          Open Workflow Configuration
        </Link>
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Bug SLA</h2>
        <p className="settings-section-description">
          Configure maximum resolution time by priority for bugs. Bugs exceeding SLA will trigger a Data Quality error.
        </p>
        <Link to="/board/bug-sla" className="role-select" style={{ display: 'inline-block', textDecoration: 'none', textAlign: 'center', padding: '8px 16px', background: '#F4F5F7', borderRadius: 4, color: '#172B4D', fontWeight: 500 }}>
          Open Bug SLA Settings
        </Link>
      </section>

      <section className="settings-section">
        <h2 className="settings-section-title">Jira Sync</h2>
        <p className="settings-section-description">
          Sync issues from Jira. Runs automatically on schedule, but you can trigger manually.
        </p>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button
            className="changelog-check-btn"
            disabled={syncing}
            onClick={async () => {
              try {
                setSyncing(true)
                setSyncResult(null)
                await axios.post('/api/sync/trigger')
                setSyncResult({ type: 'success', message: 'Sync started successfully. It will run in the background.' })
              } catch {
                setSyncResult({ type: 'error', message: 'Failed to start sync' })
              } finally {
                setSyncing(false)
              }
            }}
          >
            {syncing ? 'Starting...' : 'Sync Now'}
          </button>
          {syncResult && (
            <span className={syncResult.type === 'success' ? 'changelog-result-success' : 'changelog-result-error'}>
              {syncResult.message}
            </span>
          )}
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
