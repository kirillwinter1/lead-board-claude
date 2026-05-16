import { useMemo, useState } from 'react'
import { TeamBadge } from '../TeamBadge'
import { EpicCard } from './EpicCard'
import { PlanningEpicDto } from '../../api/quarterlyPlanning'
import {
  TEXT_PRIMARY,
  TEXT_MUTED,
  BG_SUBTLE,
  BORDER_DEFAULT,
  SEPARATOR,
  LINK_COLOR,
} from '../../constants/colors'

type GroupMode = 'project' | 'team'

interface InQuarterColumnProps {
  epics: PlanningEpicDto[]
  targetQuarter: string
  jiraBaseUrl: string
  teamsById: Map<number, { id: number; name: string; color: string | null }>
  onMove: (epicKey: string, toQuarter: string | null) => void
  onBoostChange: (epicKey: string, boost: number) => void
}

interface Group {
  key: string
  title: string
  color: string | null
  epics: PlanningEpicDto[]
  totalEstimated: number
}

const UNASSIGNED_KEY = '__unassigned__'
const NO_PROJECT_KEY = '__no_project__'
const NO_PROJECT_LABEL = 'Без проекта'

export function InQuarterColumn({
  epics,
  targetQuarter,
  jiraBaseUrl,
  teamsById,
  onMove,
  onBoostChange,
}: InQuarterColumnProps) {
  const [groupMode, setGroupMode] = useState<GroupMode>('project')
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())

  const totalEstimatedDays = useMemo(() => {
    return epics.reduce((sum, e) => sum + (e.hasEstimate ? e.totalDemandDays : 0), 0)
  }, [epics])

  const groups: Group[] = useMemo(() => {
    if (groupMode === 'project') {
      const map = new Map<string, Group>()
      epics.forEach(e => {
        const k = e.projectKey ?? NO_PROJECT_KEY
        let g = map.get(k)
        if (!g) {
          const title = e.projectKey == null
            ? NO_PROJECT_LABEL
            : `${e.projectKey} — ${e.projectSummary ?? ''}`.replace(/ — $/, '')
          g = {
            key: k,
            title,
            color: null,
            epics: [],
            totalEstimated: 0,
          }
          map.set(k, g)
        }
        g.epics.push(e)
        if (e.hasEstimate) g.totalEstimated += e.totalDemandDays
      })
      const arr = Array.from(map.values())
      arr.forEach(g => g.epics.sort((a, b) => b.priorityScore - a.priorityScore))
      arr.sort((a, b) => {
        if (a.key === NO_PROJECT_KEY) return 1
        if (b.key === NO_PROJECT_KEY) return -1
        return a.title.localeCompare(b.title)
      })
      return arr
    }

    // Group by team
    const map = new Map<string, Group>()
    epics.forEach(e => {
      if (e.teams.length === 0) {
        let g = map.get(UNASSIGNED_KEY)
        if (!g) {
          g = { key: UNASSIGNED_KEY, title: 'Не назначены', color: null, epics: [], totalEstimated: 0 }
          map.set(UNASSIGNED_KEY, g)
        }
        g.epics.push(e)
        if (e.hasEstimate) g.totalEstimated += e.totalDemandDays
        return
      }
      e.teams.forEach(t => {
        const key = String(t.id)
        let g = map.get(key)
        if (!g) {
          g = { key, title: t.name, color: t.color, epics: [], totalEstimated: 0 }
          map.set(key, g)
        }
        g.epics.push(e)
        if (e.hasEstimate) g.totalEstimated += e.totalDemandDays
      })
    })
    const arr = Array.from(map.values())
    arr.forEach(g => g.epics.sort((a, b) => b.priorityScore - a.priorityScore))
    arr.sort((a, b) => {
      if (a.key === UNASSIGNED_KEY) return 1
      if (b.key === UNASSIGNED_KEY) return -1
      return a.title.localeCompare(b.title)
    })
    return arr
  }, [epics, groupMode])

  const toggleGroup = (key: string) => {
    setCollapsed(prev => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 16,
        background: '#fff',
        border: `1px solid ${BORDER_DEFAULT}`,
        borderRadius: 8,
        minHeight: 400,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 8, flexWrap: 'wrap' }}>
        <div>
          <h2 style={{ margin: 0, fontSize: 18, color: TEXT_PRIMARY }}>
            В квартале <span style={{ color: LINK_COLOR }}>{targetQuarter}</span>
          </h2>
          <span style={{ fontSize: 12, color: TEXT_MUTED }}>
            {epics.length} {epics.length === 1 ? 'epic' : 'epics'} · {Math.round(totalEstimatedDays)} estimated person-days
          </span>
        </div>
        <div role="group" aria-label="Group by" style={{ display: 'inline-flex', border: `1px solid ${BORDER_DEFAULT}`, borderRadius: 6, overflow: 'hidden' }}>
          <button
            type="button"
            onClick={() => setGroupMode('project')}
            style={{
              padding: '6px 12px',
              background: groupMode === 'project' ? LINK_COLOR : '#fff',
              color: groupMode === 'project' ? '#fff' : TEXT_PRIMARY,
              border: 'none',
              cursor: 'pointer',
              fontSize: 12,
              fontWeight: 600,
            }}
          >
            By project
          </button>
          <button
            type="button"
            onClick={() => setGroupMode('team')}
            style={{
              padding: '6px 12px',
              background: groupMode === 'team' ? LINK_COLOR : '#fff',
              color: groupMode === 'team' ? '#fff' : TEXT_PRIMARY,
              border: 'none',
              borderLeft: `1px solid ${BORDER_DEFAULT}`,
              cursor: 'pointer',
              fontSize: 12,
              fontWeight: 600,
            }}
          >
            By team
          </button>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {groups.length === 0 && (
          <div style={{ padding: 24, textAlign: 'center', color: TEXT_MUTED, background: BG_SUBTLE, borderRadius: 6 }}>
            No epics in quarter yet. Move epics from the Backlog to plan {targetQuarter}.
          </div>
        )}
        {groups.map(g => {
          const isCollapsed = collapsed.has(g.key)
          return (
            <section key={g.key} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <button
                type="button"
                onClick={() => toggleGroup(g.key)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 10px',
                  background: BG_SUBTLE,
                  border: `1px solid ${SEPARATOR}`,
                  borderRadius: 6,
                  cursor: 'pointer',
                  textAlign: 'left',
                  color: TEXT_PRIMARY,
                  fontWeight: 600,
                  fontSize: 13,
                }}
              >
                <span style={{ display: 'inline-block', transform: isCollapsed ? 'rotate(-90deg)' : 'none', transition: 'transform 0.15s' }}>
                  ▾
                </span>
                {groupMode === 'team' && g.key !== UNASSIGNED_KEY ? (
                  <TeamBadge name={g.title} color={g.color} />
                ) : (
                  <span>{g.title}</span>
                )}
                <span style={{ marginLeft: 'auto', color: TEXT_MUTED, fontWeight: 600 }}>
                  {g.epics.length} {g.epics.length === 1 ? 'epic' : 'epics'} · {Math.round(g.totalEstimated)}d
                </span>
              </button>
              {!isCollapsed && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {g.epics.map(epic => (
                    <EpicCard
                      key={`${g.key}-${epic.epicKey}`}
                      epic={epic}
                      mode="in-quarter"
                      targetQuarter={targetQuarter}
                      jiraBaseUrl={jiraBaseUrl}
                      teamsById={teamsById}
                      onMove={onMove}
                      onBoostChange={onBoostChange}
                    />
                  ))}
                </div>
              )}
            </section>
          )
        })}
      </div>
    </div>
  )
}
