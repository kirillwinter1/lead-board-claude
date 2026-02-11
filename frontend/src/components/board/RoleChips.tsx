import { isEpic } from './helpers'
import { EpicRoleChip } from './EpicRoleChip'
import type { RoleMetrics, RoleChipsProps } from './types'

function RoleChip({ label, metrics }: { label: string; metrics: RoleMetrics | null }) {
  const hasEstimate = metrics && metrics.estimateSeconds > 0
  const progress = hasEstimate ? Math.min(metrics.progress, 100) : 0
  const roleClass = label.toLowerCase()

  return (
    <div className={`role-chip ${roleClass} ${hasEstimate ? '' : 'disabled'}`}>
      <div className="role-chip-fill" style={{ width: `${progress}%` }} />
      <span className="role-chip-label">{label}</span>
      {hasEstimate && <span className="role-chip-percent">{metrics.progress}%</span>}
    </div>
  )
}

export function RoleChips({ node, config, onRoughEstimateUpdate }: RoleChipsProps) {
  const roleProgress = node.roleProgress

  // For Epic - use special Epic chips
  if (isEpic(node.issueType)) {
    const saMetrics: RoleMetrics = roleProgress?.analytics ?? {
      estimateSeconds: 0,
      loggedSeconds: 0,
      progress: 0,
      roughEstimateDays: node.roughEstimateSaDays
    }
    const devMetrics: RoleMetrics = roleProgress?.development ?? {
      estimateSeconds: 0,
      loggedSeconds: 0,
      progress: 0,
      roughEstimateDays: node.roughEstimateDevDays
    }
    const qaMetrics: RoleMetrics = roleProgress?.testing ?? {
      estimateSeconds: 0,
      loggedSeconds: 0,
      progress: 0,
      roughEstimateDays: node.roughEstimateQaDays
    }

    return (
      <div className="epic-role-chips">
        <EpicRoleChip
          label="SA"
          role="sa"
          metrics={saMetrics}
          epicInTodo={node.epicInTodo}
          epicKey={node.issueKey}
          config={config}
          onUpdate={onRoughEstimateUpdate}
        />
        <EpicRoleChip
          label="DEV"
          role="dev"
          metrics={devMetrics}
          epicInTodo={node.epicInTodo}
          epicKey={node.issueKey}
          config={config}
          onUpdate={onRoughEstimateUpdate}
        />
        <EpicRoleChip
          label="QA"
          role="qa"
          metrics={qaMetrics}
          epicInTodo={node.epicInTodo}
          epicKey={node.issueKey}
          config={config}
          onUpdate={onRoughEstimateUpdate}
        />
      </div>
    )
  }

  // For Story/Bug/Subtask - standard chips
  return (
    <div className="role-chips">
      <RoleChip label="SA" metrics={roleProgress?.analytics || null} />
      <RoleChip label="DEV" metrics={roleProgress?.development || null} />
      <RoleChip label="QA" metrics={roleProgress?.testing || null} />
    </div>
  )
}
