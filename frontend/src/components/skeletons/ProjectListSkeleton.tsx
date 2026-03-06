import { Skeleton } from '../Skeleton'

const CARDS = 5

export function ProjectListSkeleton() {
  return (
    <div role="status" aria-label="Loading projects" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {Array.from({ length: CARDS }).map((_, i) => {
        const delay = i * 80
        return (
          <div
            key={i}
            style={{
              background: 'white',
              borderRadius: 8,
              border: '1px solid #dfe1e6',
              padding: '14px 18px',
            }}
          >
            {/* Top row: icon + key + summary + assignee + RICE + status */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
              <Skeleton width={20} height={20} borderRadius={4} delay={delay} />
              <Skeleton width={65} height={14} delay={delay} />
              <Skeleton width={140 + (i % 3) * 40} height={14} delay={delay} />
              <div style={{ flex: 1 }} />
              <Skeleton width={70} height={22} borderRadius={11} delay={delay} />
              <Skeleton width={36} height={22} borderRadius={11} delay={delay} />
              <Skeleton width={80} height={22} borderRadius={4} delay={delay} />
            </div>

            {/* Bottom row: progress + text + epics count */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Skeleton width={120} height={6} borderRadius={3} delay={delay} />
              <Skeleton width={40} height={12} delay={delay} />
              <div style={{ flex: 1 }} />
              <Skeleton width={60} height={12} delay={delay} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
