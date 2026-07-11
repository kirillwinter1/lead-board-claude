import { getIssueIcon } from './helpers'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { TEXT_PRIMARY, LINK_COLOR } from '../../constants/colors'

// F85 — shared tooltip header: icon + key on line 1, name (summary) on line 2.
export function TooltipIssueHeader({ issueType, issueKey, summary }: { issueType: string; issueKey: string; summary: string }) {
  const { getIssueTypeIconUrl, getIssueTypeCategory } = useWorkflowConfig()
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <img
          src={getIssueIcon(issueType, getIssueTypeIconUrl(issueType), getIssueTypeCategory(issueType))}
          alt={issueType}
          width={16}
          height={16}
          style={{ flexShrink: 0 }}
        />
        <span style={{ fontSize: 12, fontWeight: 600, color: LINK_COLOR }}>{issueKey}</span>
      </div>
      <div style={{ fontWeight: 600, color: TEXT_PRIMARY, marginTop: 4 }}>{summary}</div>
    </div>
  )
}
