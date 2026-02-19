import { useEffect, useState } from 'react'
import axios from 'axios'

interface BugSlaConfig {
  id: number
  priority: string
  maxResolutionHours: number
}

// Default order for standard Jira priorities (others will be appended at the end)
const PRIORITY_ORDER = ['Blocker', 'Critical', 'Highest', 'High', 'Medium', 'Low', 'Lowest']

const PRIORITY_COLORS: Record<string, string> = {
  Blocker: '#cc0000',
  Critical: '#de350b',
  Highest: '#de350b',
  High: '#ff5630',
  Medium: '#ffab00',
  Low: '#36b37e',
  Lowest: '#97a0af',
}

function getPriorityColor(priority: string): string {
  return PRIORITY_COLORS[priority] || '#42526e'
}

function sortConfigs(configs: BugSlaConfig[]): BugSlaConfig[] {
  return [...configs].sort((a, b) => {
    const ai = PRIORITY_ORDER.indexOf(a.priority)
    const bi = PRIORITY_ORDER.indexOf(b.priority)
    return (ai === -1 ? 999 : ai) - (bi === -1 ? 999 : bi)
  })
}

export function BugSlaSettingsPage() {
  const [configs, setConfigs] = useState<BugSlaConfig[]>([])
  const [availablePriorities, setAvailablePriorities] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingPriority, setEditingPriority] = useState<string | null>(null)
  const [editValue, setEditValue] = useState('')
  const [saving, setSaving] = useState(false)
  const [showAddForm, setShowAddForm] = useState(false)
  const [newPriority, setNewPriority] = useState('')
  const [newHours, setNewHours] = useState('168')

  useEffect(() => {
    fetchData()
  }, [])

  const fetchData = async () => {
    try {
      setLoading(true)
      const [configsRes, prioritiesRes] = await Promise.all([
        axios.get<BugSlaConfig[]>('/api/bug-sla'),
        axios.get<string[]>('/api/bug-sla/priorities'),
      ])
      setConfigs(sortConfigs(configsRes.data))
      setAvailablePriorities(prioritiesRes.data)
      setError(null)
    } catch {
      setError('Failed to load SLA configuration')
    } finally {
      setLoading(false)
    }
  }

  const startEdit = (config: BugSlaConfig) => {
    setEditingPriority(config.priority)
    setEditValue(String(config.maxResolutionHours))
  }

  const cancelEdit = () => {
    setEditingPriority(null)
    setEditValue('')
  }

  const saveEdit = async (priority: string) => {
    const hours = parseInt(editValue, 10)
    if (isNaN(hours) || hours < 1) return

    try {
      setSaving(true)
      await axios.put(`/api/bug-sla/${encodeURIComponent(priority)}`, { maxResolutionHours: hours })
      setEditingPriority(null)
      setEditValue('')
      await fetchData()
    } catch {
      setError('Failed to update SLA')
    } finally {
      setSaving(false)
    }
  }

  const addSla = async () => {
    const hours = parseInt(newHours, 10)
    if (!newPriority || isNaN(hours) || hours < 1) return

    try {
      setSaving(true)
      await axios.post('/api/bug-sla', { priority: newPriority, maxResolutionHours: hours })
      setShowAddForm(false)
      setNewPriority('')
      setNewHours('168')
      await fetchData()
    } catch {
      setError('Failed to add SLA')
    } finally {
      setSaving(false)
    }
  }

  const deleteSla = async (priority: string) => {
    if (!confirm(`Remove SLA for "${priority}"?`)) return

    try {
      await axios.delete(`/api/bug-sla/${encodeURIComponent(priority)}`)
      await fetchData()
    } catch {
      setError('Failed to delete SLA')
    }
  }

  const formatHours = (hours: number): string => {
    if (hours < 24) return `${hours}h`
    const days = Math.floor(hours / 24)
    const remainingHours = hours % 24
    if (remainingHours === 0) return `${days}d`
    return `${days}d ${remainingHours}h`
  }

  // Priorities that exist in issues but don't have SLA config yet
  const configuredPriorities = new Set(configs.map(c => c.priority))
  const missingPriorities = availablePriorities.filter(p => !configuredPriorities.has(p))

  if (loading) return <div className="page-loading">Loading SLA configuration...</div>

  return (
    <div style={{ padding: '24px', maxWidth: 700 }}>
      <h2 style={{ margin: '0 0 8px 0' }}>Bug SLA Settings</h2>
      <p style={{ color: '#6b778c', margin: '0 0 24px 0' }}>
        Maximum resolution time by priority. Bugs exceeding SLA will trigger a Data Quality error.
      </p>

      {error && (
        <div style={{ background: '#ffebe6', color: '#de350b', padding: '12px 16px', borderRadius: 6, marginBottom: 16 }}>
          {error}
          <button onClick={() => setError(null)} style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', color: '#de350b', fontWeight: 600 }}>x</button>
        </div>
      )}

      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ borderBottom: '2px solid #dfe1e6' }}>
            <th style={{ textAlign: 'left', padding: '8px 12px', color: '#6b778c', fontWeight: 500 }}>Priority</th>
            <th style={{ textAlign: 'left', padding: '8px 12px', color: '#6b778c', fontWeight: 500 }}>Max Resolution Time</th>
            <th style={{ textAlign: 'left', padding: '8px 12px', color: '#6b778c', fontWeight: 500 }}>Hours</th>
            <th style={{ width: 140, padding: '8px 12px' }}></th>
          </tr>
        </thead>
        <tbody>
          {configs.map(config => (
            <tr key={config.priority} style={{ borderBottom: '1px solid #ebecf0' }}>
              <td style={{ padding: '10px 12px' }}>
                <span style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 6,
                  fontWeight: 600,
                  color: getPriorityColor(config.priority),
                }}>
                  {config.priority}
                </span>
              </td>
              <td style={{ padding: '10px 12px', color: '#172b4d' }}>
                {formatHours(config.maxResolutionHours)}
              </td>
              <td style={{ padding: '10px 12px' }}>
                {editingPriority === config.priority ? (
                  <input
                    type="number"
                    min={1}
                    value={editValue}
                    onChange={e => setEditValue(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === 'Enter') saveEdit(config.priority)
                      if (e.key === 'Escape') cancelEdit()
                    }}
                    style={{
                      width: 80,
                      padding: '4px 8px',
                      border: '1px solid #0052cc',
                      borderRadius: 4,
                      outline: 'none',
                    }}
                    autoFocus
                  />
                ) : (
                  <span style={{ color: '#6b778c' }}>{config.maxResolutionHours}h</span>
                )}
              </td>
              <td style={{ padding: '10px 12px', textAlign: 'right' }}>
                {editingPriority === config.priority ? (
                  <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                    <button
                      onClick={() => saveEdit(config.priority)}
                      disabled={saving}
                      style={{
                        padding: '4px 12px',
                        background: '#0052cc',
                        color: '#fff',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 13,
                      }}
                    >
                      Save
                    </button>
                    <button
                      onClick={cancelEdit}
                      style={{
                        padding: '4px 12px',
                        background: '#f4f5f7',
                        color: '#42526e',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 13,
                      }}
                    >
                      Cancel
                    </button>
                  </span>
                ) : (
                  <span style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                    <button
                      onClick={() => startEdit(config)}
                      style={{
                        padding: '4px 12px',
                        background: '#f4f5f7',
                        color: '#42526e',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 13,
                      }}
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => deleteSla(config.priority)}
                      style={{
                        padding: '4px 12px',
                        background: '#f4f5f7',
                        color: '#de350b',
                        border: 'none',
                        borderRadius: 4,
                        cursor: 'pointer',
                        fontSize: 13,
                      }}
                    >
                      Delete
                    </button>
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {missingPriorities.length > 0 && (
        <div style={{ marginTop: 16, padding: '12px 16px', background: '#fffae6', borderRadius: 6, color: '#7a6200', fontSize: 13 }}>
          Priorities found in issues without SLA config: <strong>{missingPriorities.join(', ')}</strong>
        </div>
      )}

      <div style={{ marginTop: 16 }}>
        {showAddForm ? (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            {missingPriorities.length > 0 ? (
              <select
                value={newPriority}
                onChange={e => setNewPriority(e.target.value)}
                style={{ padding: '6px 10px', borderRadius: 4, border: '1px solid #dfe1e6', fontSize: 13 }}
              >
                <option value="">Select priority...</option>
                {missingPriorities.map(p => (
                  <option key={p} value={p}>{p}</option>
                ))}
                <option value="__custom">Custom name...</option>
              </select>
            ) : (
              <input
                type="text"
                placeholder="Priority name"
                value={newPriority}
                onChange={e => setNewPriority(e.target.value)}
                style={{ padding: '6px 10px', borderRadius: 4, border: '1px solid #dfe1e6', fontSize: 13, width: 140 }}
              />
            )}
            {newPriority === '__custom' && (
              <input
                type="text"
                placeholder="Priority name"
                onChange={e => setNewPriority(e.target.value === '' ? '__custom' : e.target.value)}
                style={{ padding: '6px 10px', borderRadius: 4, border: '1px solid #dfe1e6', fontSize: 13, width: 140 }}
                autoFocus
              />
            )}
            <input
              type="number"
              min={1}
              value={newHours}
              onChange={e => setNewHours(e.target.value)}
              placeholder="Hours"
              style={{ padding: '6px 10px', borderRadius: 4, border: '1px solid #dfe1e6', fontSize: 13, width: 80 }}
            />
            <span style={{ color: '#6b778c', fontSize: 13 }}>hours</span>
            <button
              onClick={addSla}
              disabled={saving || !newPriority || newPriority === '__custom'}
              style={{
                padding: '6px 14px',
                background: '#0052cc',
                color: '#fff',
                border: 'none',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 13,
              }}
            >
              Add
            </button>
            <button
              onClick={() => { setShowAddForm(false); setNewPriority(''); setNewHours('168') }}
              style={{
                padding: '6px 14px',
                background: '#f4f5f7',
                color: '#42526e',
                border: 'none',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 13,
              }}
            >
              Cancel
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowAddForm(true)}
            style={{
              padding: '8px 16px',
              background: '#f4f5f7',
              color: '#172b4d',
              border: 'none',
              borderRadius: 4,
              cursor: 'pointer',
              fontSize: 13,
              fontWeight: 500,
            }}
          >
            + Add Priority
          </button>
        )}
      </div>
    </div>
  )
}
