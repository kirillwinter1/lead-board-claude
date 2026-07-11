import type { ReactNode } from 'react'
import { projectsApi, type ProjectDetailDto } from '../../api/projects'
import { HoverInfoCard } from '../HoverInfoCard'
import { TeamBadge } from '../TeamBadge'
import { TooltipIssueHeader } from './TooltipIssueHeader'

// F85 — tooltip проекта: иконка+ключ / название / описание, затем прогресс, дедлайн, команды.
// Команды выводятся из списка эпиков проекта (distinct по имени).
export function ProjectTooltip({ projectKey, children }: { projectKey: string; children: ReactNode }) {
  return (
    <HoverInfoCard<ProjectDetailDto>
      width={340}
      loadData={() => projectsApi.getDetail(projectKey)}
      render={(p) => {
        const teamsMap = new Map<string, string | null>()
        p.epics.forEach((e) => { if (e.teamName) teamsMap.set(e.teamName, e.teamColor) })
        const teams = Array.from(teamsMap, ([name, color]) => ({ name, color }))
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <TooltipIssueHeader issueType={p.issueType} issueKey={p.issueKey} summary={p.summary} />
            {p.description ? (
              <div style={{ color: '#42526e', whiteSpace: 'pre-wrap', maxHeight: 200, overflow: 'hidden', lineHeight: 1.4 }}>
                {p.description}
              </div>
            ) : (
              <div style={{ color: '#97a0af', fontStyle: 'italic' }}>No description</div>
            )}
            <div style={{ display: 'flex', justifyContent: 'space-between', color: '#42526e' }}>
              <span>Progress</span>
              <span style={{ fontWeight: 600, color: '#172b4d' }}>
                {p.progressPercent}% · {p.completedEpicCount}/{p.epics.length} epics
              </span>
            </div>
            {p.expectedDone && (
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#42526e' }}>
                <span>Deadline</span>
                <span style={{ fontWeight: 600, color: '#172b4d' }}>{p.expectedDone}</span>
              </div>
            )}
            {teams.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, alignItems: 'center' }}>
                {teams.map((t) => <TeamBadge key={t.name} name={t.name} color={t.color} />)}
              </div>
            )}
          </div>
        )
      }}
    >
      {children}
    </HoverInfoCard>
  )
}
