import { useEffect, useState } from 'react'
import axios from 'axios'
import './App.css'

interface BoardNode {
  issueKey: string
  title: string
  status: string
  issueType: string
  jiraUrl: string
  children: BoardNode[]
}

interface BoardResponse {
  items: BoardNode[]
  total: number
}

function App() {
  const [status, setStatus] = useState<string>('loading...')
  const [board, setBoard] = useState<BoardNode[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    axios.get('/api/health')
      .then(response => {
        setStatus(response.data.status)
      })
      .catch(() => {
        setStatus('error')
      })

    axios.get<BoardResponse>('/api/board')
      .then(response => {
        setBoard(response.data.items)
        setLoading(false)
      })
      .catch(err => {
        setError(err.message)
        setLoading(false)
      })
  }, [])

  return (
    <div className="app">
      <header className="header">
        <h1>Lead Board</h1>
        <span className={`status-badge ${status}`}>{status}</span>
      </header>

      <main className="board">
        {loading && <p className="loading">Loading board...</p>}
        {error && <p className="error">Error: {error}</p>}
        {!loading && !error && board.length === 0 && (
          <p className="empty">No epics found. Configure JIRA_* environment variables.</p>
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

            {epic.children.length > 0 && (
              <div className="stories">
                {epic.children.map(story => (
                  <div key={story.issueKey} className="story-row">
                    <a href={story.jiraUrl} target="_blank" rel="noopener noreferrer" className="issue-key">
                      {story.issueKey}
                    </a>
                    <span className="issue-title">{story.title}</span>
                    <span className={`status-tag ${story.status.toLowerCase().replace(/\s+/g, '-')}`}>
                      {story.status}
                    </span>
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
