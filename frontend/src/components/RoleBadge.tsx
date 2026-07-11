import { useWorkflowConfig } from '../contexts/WorkflowConfigContext'
import { hexToRgba } from '../constants/colors'

interface RoleBadgeProps {
  role: string
}

/**
 * Unified member-role pill (SA/DEV/QA and any custom role). Colour comes from the
 * workflow config via getRoleColor — never hardcoded. Replaces per-page .role-badge
 * CSS classes and the inline `color + '20'` alpha hack.
 */
export function RoleBadge({ role }: RoleBadgeProps) {
  const { getRoleColor, getRoleDisplayName } = useWorkflowConfig()
  const color = getRoleColor(role)
  return (
    <span
      style={{
        background: hexToRgba(color, 0.125),
        color,
        padding: '2px 8px',
        borderRadius: 4,
        fontSize: 12,
        fontWeight: 600,
        display: 'inline-block',
      }}
    >
      {getRoleDisplayName(role)}
    </span>
  )
}
