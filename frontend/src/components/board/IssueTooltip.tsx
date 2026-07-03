import type { ReactNode } from 'react'
import { getIssueDetail, type IssueDetail } from '../../api/issues'
import { HoverInfoCard } from '../HoverInfoCard'
import { TooltipIssueHeader } from './TooltipIssueHeader'

// F85 — hover tooltip for any board issue (epic/story/sub-task):
// icon + key on line 1, name on line 2, description below.
export function IssueTooltip({ issueKey, children }: { issueKey: string; children: ReactNode }) {
  return (
    <HoverInfoCard<IssueDetail>
      width={340}
      loadData={(signal) => getIssueDetail(issueKey, signal)}
      render={(d) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          <TooltipIssueHeader issueType={d.issueType} issueKey={d.issueKey} summary={d.summary} />
          {d.description ? (
            <div style={{ color: '#42526e', whiteSpace: 'pre-wrap', maxHeight: 260, overflow: 'hidden', lineHeight: 1.4 }}>
              {d.description}
            </div>
          ) : (
            <div style={{ color: '#97a0af', fontStyle: 'italic' }}>Без описания</div>
          )}
        </div>
      )}
    >
      {children}
    </HoverInfoCard>
  )
}
