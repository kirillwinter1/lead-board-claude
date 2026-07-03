import type { ReactNode } from 'react'
import { projectsApi, type ProjectDetailDto } from '../../api/projects'
import { HoverInfoCard } from '../HoverInfoCard'
import { TeamBadge } from '../TeamBadge'
import { getIssueIcon } from './helpers'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'

// F85 — tooltip проекта: иконка типа, имя, прогресс, дедлайн, команды.
// Команды выводятся из списка эпиков проекта (distinct по имени).
export function ProjectTooltip({ projectKey, children }: { projectKey: string; children: ReactNode }) {
  const { getIssueTypeIconUrl, getIssueTypeCategory } = useWorkflowConfig()
  return (
    <HoverInfoCard<ProjectDetailDto>
      title={projectKey}
      width={320}
      loadData={() => projectsApi.getDetail(projectKey)}
      render={(p) => {
        const teamsMap = new Map<string, string | null>()
        p.epics.forEach((e) => { if (e.teamName) teamsMap.set(e.teamName, e.teamColor) })
        const teams = Array.from(teamsMap, ([name, color]) => ({ name, color }))
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#172b4d' }}>
              <img
                src={getIssueIcon(p.issueType, getIssueTypeIconUrl(p.issueType), getIssueTypeCategory(p.issueType))}
                alt={p.issueType}
                width={16}
                height={16}
                style={{ flexShrink: 0 }}
              />
              <span>{p.summary}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', color: '#42526e' }}>
              <span>Прогресс</span>
              <span style={{ fontWeight: 600, color: '#172b4d' }}>
                {p.progressPercent}% · {p.completedEpicCount}/{p.epics.length} эпиков
              </span>
            </div>
            {p.expectedDone && (
              <div style={{ display: 'flex', justifyContent: 'space-between', color: '#42526e' }}>
                <span>Дедлайн</span>
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
