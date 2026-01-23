import { useEffect, useState, useCallback } from 'react'
import axios from 'axios'
import './App.css'

interface RoleMetrics {
  estimateSeconds: number
  loggedSeconds: number
  progress: number
}

interface RoleProgress {
  analytics: RoleMetrics
  development: RoleMetrics
  testing: RoleMetrics
}

interface BoardNode {
  issueKey: string
  title: string
  status: string
  issueType: string
  jiraUrl: string
  role: string | null
  estimateSeconds: number | null
  loggedSeconds: number | null
  progress: number | null
  roleProgress: RoleProgress | null
  children: BoardNode[]
}

interface BoardResponse {
  items: BoardNode[]
  total: number
}

function formatTime(seconds: number | null): string {
  if (!seconds) return '-'
  const hours = Math.floor(seconds / 3600)
  if (hours < 8) return `${hours}h`
  const days = (hours / 8).toFixed(1)
  return `${days}d`
}

function ProgressBar({ progress, label }: { progress: number; label?: string }) {
  return (
    <div className="progress-bar-container">
      {label && <span className="progress-label">{label}</span>}
      <div className="progress-bar">
        <div
          className={`progress-fill ${progress >= 100 ? 'complete' : ''}`}
          style={{ width: `${Math.min(progress, 100)}%` }}
        />
      </div>
      <span className="progress-text">{progress}%</span>
    </div>
  )
}

function RoleProgressBars({ roleProgress }: { roleProgress: RoleProgress }) {
  const hasData = roleProgress.analytics.estimateSeconds > 0 ||
                  roleProgress.development.estimateSeconds > 0 ||
                  roleProgress.testing.estimateSeconds > 0

  if (!hasData) return null

  return (
    <div className="role-progress">
      {roleProgress.analytics.estimateSeconds > 0 && (
        <ProgressBar progress={roleProgress.analytics.progress} label="SA" />
      )}
      {roleProgress.development.estimateSeconds > 0 && (
        <ProgressBar progress={roleProgress.development.progress} label="DEV" />
      )}
      {roleProgress.testing.estimateSeconds > 0 && (
        <ProgressBar progress={roleProgress.testing.progress} label="QA" />
      )}
    </div>
  )
}

function App() {
  const [status, setStatus] = useState<string>('loading...')
  const [board, setBoard] = useState<BoardNode[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [searchInput, setSearchInput] = useState('')

  const fetchBoard = useCallback((searchQuery?: string) => {
    setLoading(true)
    const params = new URLSearchParams()
    if (searchQuery) params.append('query', searchQuery)

    axios.get<BoardResponse>(`/api/board?${params.toString()}`)
      .then(response => {
        setBoard(response.data.items)
        setLoading(false)
      })
      .catch(err => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    axios.get('/api/health')
      .then(response => setStatus(response.data.status))
      .catch(() => setStatus('error'))

    fetchBoard()
  }, [fetchBoard])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setQuery(searchInput)
    fetchBoard(searchInput)
  }

  const clearSearch = () => {
    setSearchInput('')
    setQuery('')
    fetchBoard()
  }

  return (
    <div className="app">
      <header className="header">
        <h1>Lead Board</h1>
        <span className={`status-badge ${status}`}>{status}</span>
      </header>

      <div className="filters">
        <form onSubmit={handleSearch} className="search-form">
          <input
            type="text"
            placeholder="Search by key or title..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            className="search-input"
          />
          <button type="submit" className="search-btn">Search</button>
          {query && (
            <button type="button" onClick={clearSearch} className="clear-btn">Clear</button>
          )}
        </form>
      </div>

      <main className="board">
        {loading && <p className="loading">Loading board...</p>}
        {error && <p className="error">Error: {error}</p>}
        {!loading && !error && board.length === 0 && (
          <p className="empty">
            {query ? `No results for "${query}"` : 'No epics found. Configure JIRA_* environment variables.'}
          </p>
        )}

        {board.map(epic => (
          <div key={epic.issueKey} className="epic-card">
            <div className="epic-header">
              <a href={epic.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
                {epic.issueKey}
              </a>
              <span className="issue-title">{epic.title}</span>
              <span className={`status-tag ${epic.status.toLowerCase().replace(/\s+/g, '-')}`}>
                {epic.status}
              </span>
            </div>

            <div className="epic-metrics">
              <div className="time-info">
                <span className="metric">
                  <span className="metric-label">Est:</span> {formatTime(epic.estimateSeconds)}
                </span>
                <span className="metric">
                  <span className="metric-label">Log:</span> {formatTime(epic.loggedSeconds)}
                </span>
              </div>
              {epic.progress !== null && epic.progress > 0 && (
                <ProgressBar progress={epic.progress} />
              )}
              {epic.roleProgress && <RoleProgressBars roleProgress={epic.roleProgress} />}
            </div>

            {epic.children.length > 0 && (
              <div className="stories">
                {epic.children.map(story => (
                  <div key={story.issueKey} className="story-item">
                    <div className="story-row">
                      <a href={story.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
                        {story.issueKey}
                      </a>
                      <span className="issue-title">{story.title}</span>
                      <div className="story-metrics">
                        {story.estimateSeconds && (
                          <span className="metric small">{formatTime(story.estimateSeconds)}</span>
                        )}
                        {story.progress !== null && story.progress > 0 && (
                          <div className="mini-progress">
                            <div
                              className="mini-progress-fill"
                              style={{ width: `${Math.min(story.progress, 100)}%` }}
                            />
                          </div>
                        )}
                      </div>
                      <span className={`status-tag ${story.status.toLowerCase().replace(/\s+/g, '-')}`}>
                        {story.status}
                      </span>
                    </div>

                    {story.children.length > 0 && (
                      <div className="subtasks">
                        {story.children.map(subtask => (
                          <div key={subtask.issueKey} className="subtask-row">
                            <span className={`role-badge ${subtask.role?.toLowerCase() || 'unknown'}`}>
                              {subtask.role === 'ANALYTICS' ? 'SA' :
                               subtask.role === 'DEVELOPMENT' ? 'DEV' :
                               subtask.role === 'TESTING' ? 'QA' : '?'}
                            </span>
                            <a href={subtask.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key small">
                              {subtask.issueKey}
                            </a>
                            <span className="issue-title small">{subtask.title}</span>
                            {subtask.estimateSeconds && (
                              <span className="metric small">{formatTime(subtask.estimateSeconds)}</span>
                            )}
                            <span className={`status-tag small ${subtask.status.toLowerCase().replace(/\s+/g, '-')}`}>
                              {subtask.status}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </main>
    </div>
  )
}

export default App
