import { EpicRoleChip } from './EpicRoleChip'
import { useWorkflowConfig } from '../../contexts/WorkflowConfigContext'
import { hexToRgba } from '../../constants/colors'
import type { RoleMetrics, RoleChipsProps } from './types'

function RoleChip({ label, metrics, roleColor }: { label: string; metrics: RoleMetrics | null; roleColor: string }) {
  const hasEstimate = metrics && metrics.estimateSeconds > 0
  const progress = hasEstimate ? Math.min(metrics.progress, 100) : 0

  // Generate lighter background from roleColor
  const bgColor = hexToRgba(roleColor, 0.1)
  const fillColor = hexToRgba(roleColor, 0.5)

  return (
    <div
      className={`role-chip ${hasEstimate ? '' : 'disabled'}`}
      style={{ background: bgColor, color: roleColor }}
    >
      <div className="role-chip-fill" style={{ width: `${progress}%`, background: fillColor }} />
      <span className="role-chip-label">{label}</span>
      {hasEstimate && <span className="role-chip-percent">{metrics.progress}%</span>}
    </div>
  )
}

export function RoleChips({ node, config, onRoughEstimateUpdate }: RoleChipsProps) {
  const { getRoleColor, getRoleCodes, isEpic } = useWorkflowConfig()
  const roleProgress = node.roleProgress
  const roleCodes = getRoleCodes()

  // For Epic - use special Epic chips
  if (isEpic(node.issueType)) {
    return (
      <div className="epic-role-chips">
        {roleCodes.map(code => {
          const metrics: RoleMetrics = roleProgress?.[code] ?? {
            estimateSeconds: 0,
            loggedSeconds: 0,
            progress: 0,
            roughEstimateDays: node.roughEstimates?.[code] ?? null
          }

          return (
            <EpicRoleChip
              key={code}
              label={code}
              role={code}
              metrics={metrics}
              epicInTodo={node.epicInTodo}
              epicKey={node.issueKey}
              config={config}
              onUpdate={onRoughEstimateUpdate}
              roleColor={getRoleColor(code)}
              estimateSource={node.estimateSource}
            />
          )
        })}
      </div>
    )
  }

  // For Story/Bug/Subtask - standard chips
  return (
    <div className="role-chips">
      {roleCodes.map(code => {
        return (
          <RoleChip
            key={code}
            label={code}
            metrics={roleProgress?.[code] || null}
            roleColor={getRoleColor(code)}
          />
        )
      })}
    </div>
  )
}
