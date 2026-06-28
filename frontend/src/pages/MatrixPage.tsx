import { useState, useEffect, useRef, useCallback } from 'react'
import {
  DndContext,
  DragEndEvent,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import { teamsApi, Team } from '../api/teams'
import { getConfig } from '../api/config'
import { getMatrix, triage, getRecommendations, MatrixView, MatrixCard as MatrixCardData, Quadrant, RecommendationView } from '../api/matrixApi'
import { SingleSelectDropdown } from '../components/SingleSelectDropdown'
import { FilterBar } from '../components/FilterBar'
import { MatrixQuadrant } from '../components/matrix/MatrixQuadrant'
import { MatrixUnassigned, UNASSIGNED_ZONE_ID } from '../components/matrix/MatrixUnassigned'
import { MatrixRecommendations } from '../components/matrix/MatrixRecommendations'
import './MatrixPage.css'

const EMPTY_VIEW: MatrixView = { p1: [], p2: [], p3: [], p4: [], unassigned: [] }

// Maps a droppable zone id to its key in MatrixView and the quadrant value sent to the API.
const ZONE_TO_KEY: Record<string, { key: keyof MatrixView; quadrant: Quadrant | null }> = {
  P1: { key: 'p1', quadrant: 'P1' },
  P2: { key: 'p2', quadrant: 'P2' },
  P3: { key: 'p3', quadrant: 'P3' },
  P4: { key: 'p4', quadrant: 'P4' },
  [UNASSIGNED_ZONE_ID]: { key: 'unassigned', quadrant: null },
}

// Finds which zone a card currently lives in.
function findCardZone(view: MatrixView, issueKey: string): keyof MatrixView | null {
  const keys: (keyof MatrixView)[] = ['p1', 'p2', 'p3', 'p4', 'unassigned']
  return keys.find(k => view[k].some(c => c.issueKey === issueKey)) ?? null
}

// Returns a new view with the card moved from its current zone to the target zone.
function moveCard(view: MatrixView, issueKey: string, targetKey: keyof MatrixView, quadrant: Quadrant | null): MatrixView {
  const fromKey = findCardZone(view, issueKey)
  if (!fromKey || fromKey === targetKey) return view
  const card = view[fromKey].find(c => c.issueKey === issueKey)
  if (!card) return view
  const moved: MatrixCardData = { ...card, quadrant }
  return {
    ...view,
    [fromKey]: view[fromKey].filter(c => c.issueKey !== issueKey),
    [targetKey]: [...view[targetKey], moved],
  }
}

export function MatrixPage() {
  const [teams, setTeams] = useState<Team[]>([])
  const [selectedTeamId, setSelectedTeamId] = useState<number | null>(null)
  const [view, setView] = useState<MatrixView>(EMPTY_VIEW)
  const [jiraBaseUrl, setJiraBaseUrl] = useState<string>('')
  const [loading, setLoading] = useState(true)
  const [matrixLoading, setMatrixLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [triageError, setTriageError] = useState<string | null>(null)
  const [recommendations, setRecommendations] = useState<RecommendationView | null>(null)
  const initialTeamIdRef = useRef(selectedTeamId)

  const sensors = useSensors(
    // Small activation distance so clicking the Jira link doesn't start a drag.
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
  )

  // Load Jira base URL for issue links.
  useEffect(() => {
    getConfig()
      .then(config => setJiraBaseUrl(config.jiraBaseUrl))
      .catch(err => console.error('Failed to load config:', err))
  }, [])

  // Load teams once; default to the first active team.
  useEffect(() => {
    teamsApi.getAll()
      .then(data => {
        const activeTeams = data.filter(t => t.active)
        setTeams(activeTeams)
        if (activeTeams.length > 0 && !initialTeamIdRef.current) {
          setSelectedTeamId(activeTeams[0].id)
        }
        setLoading(false)
      })
      .catch(err => {
        setError('Failed to load teams: ' + (err instanceof Error ? err.message : 'Unknown error'))
        setLoading(false)
      })
  }, [])

  // Load matrix when team changes.
  useEffect(() => {
    if (!selectedTeamId) return
    const controller = new AbortController()
    setMatrixLoading(true)
    setError(null)
    setTriageError(null)
    setRecommendations(null)

    getMatrix(selectedTeamId)
      .then(data => {
        if (controller.signal.aborted) return
        setView(data)
        setMatrixLoading(false)
      })
      .catch(err => {
        if (controller.signal.aborted) return
        setError('Failed to load matrix: ' + (err instanceof Error ? err.message : 'Unknown error'))
        setView(EMPTY_VIEW)
        setMatrixLoading(false)
      })

    getRecommendations(selectedTeamId)
      .then(data => {
        if (controller.signal.aborted) return
        setRecommendations(data)
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setRecommendations(null)
      })

    return () => controller.abort()
  }, [selectedTeamId])

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event
    if (!over) return
    const issueKey = String(active.id)
    const target = ZONE_TO_KEY[String(over.id)]
    if (!target) return

    const fromKey = findCardZone(view, issueKey)
    if (!fromKey || fromKey === target.key) return

    const previousView = view
    setTriageError(null)
    setView(current => moveCard(current, issueKey, target.key, target.quadrant))

    triage(issueKey, target.quadrant).catch(err => {
      // Revert optimistic move on failure.
      setView(previousView)
      setTriageError(
        `Не удалось переместить ${issueKey}: ` + (err instanceof Error ? err.message : 'Unknown error'),
      )
    })
  }, [view])

  return (
    <main className="main-content">
      <div className="page-header">
        <h2>Matrix</h2>
      </div>

      <FilterBar>
        <SingleSelectDropdown
          label="Team"
          options={teams.map(t => ({ value: String(t.id), label: t.name, color: t.color || undefined }))}
          selected={selectedTeamId ? String(selectedTeamId) : null}
          onChange={v => setSelectedTeamId(v ? Number(v) : null)}
          placeholder="Select team..."
          allowClear={false}
        />
      </FilterBar>

      {loading && <div className="loading">Loading...</div>}
      {error && <div className="error">{error}</div>}
      {triageError && <div className="error" role="alert">{triageError}</div>}

      {!loading && !error && !selectedTeamId && teams.length === 0 && (
        <div className="empty">No active teams found. Create a team in the Teams section first.</div>
      )}

      {!loading && !error && selectedTeamId && (
        matrixLoading ? (
          <div className="loading">Loading matrix...</div>
        ) : (
          <>
          <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
            <div className="matrix-grid">
              <MatrixQuadrant
                quadrant="P1"
                title="Важно и срочно"
                subtitle="Important & Urgent"
                cards={view.p1}
                jiraBaseUrl={jiraBaseUrl}
              />
              <MatrixQuadrant
                quadrant="P2"
                title="Важно, не срочно"
                subtitle="Important / Not urgent"
                cards={view.p2}
                jiraBaseUrl={jiraBaseUrl}
              />
              <MatrixQuadrant
                quadrant="P3"
                title="Не важно, срочно"
                subtitle="Not important / Urgent"
                cards={view.p3}
                jiraBaseUrl={jiraBaseUrl}
              />
              <MatrixQuadrant
                quadrant="P4"
                title="Не важно, не срочно"
                subtitle="Not important / Not urgent"
                cards={view.p4}
                jiraBaseUrl={jiraBaseUrl}
              />
            </div>
            <MatrixUnassigned cards={view.unassigned} jiraBaseUrl={jiraBaseUrl} />
          </DndContext>
          <MatrixRecommendations data={recommendations} jiraBaseUrl={jiraBaseUrl} />
          </>
        )
      )}
    </main>
  )
}
