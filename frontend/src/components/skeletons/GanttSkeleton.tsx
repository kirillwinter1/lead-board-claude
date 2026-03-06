import { Skeleton } from '../Skeleton'

const ROWS = 6
const BAR_CONFIGS = [
  { left: '10%', width: '35%' },
  { left: '5%', width: '50%' },
  { left: '20%', width: '30%' },
  { left: '15%', width: '45%' },
  { left: '8%', width: '55%' },
  { left: '25%', width: '25%' },
]

export function GanttSkeleton() {
  return (
    <div role="status" aria-label="Loading timeline" style={{
      display: 'flex',
      background: 'white',
      borderRadius: 8,
      border: '1px solid #dfe1e6',
      overflow: 'hidden',
    }}>
      {/* Labels panel */}
      <div style={{
        flexShrink: 0,
        width: 380,
        borderRight: '1px solid #dfe1e6',
        background: '#fafbfc',
      }}>
        {/* Header */}
        <div style={{
          minHeight: 64,
          padding: '10px 16px',
          background: '#f4f5f7',
          borderBottom: '1px solid #dfe1e6',
          display: 'flex',
          alignItems: 'flex-end',
          paddingBottom: 8,
        }}>
          <Skeleton width={40} height={10} />
        </div>

        {/* Label rows */}
        {Array.from({ length: ROWS }).map((_, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              minHeight: 48,
              padding: '6px 12px',
              marginBottom: 8,
            }}
          >
            <Skeleton width={20} height={20} borderRadius={4} delay={i * 80} />
            <Skeleton width={55} height={13} delay={i * 80} />
            <Skeleton width={100 + (i % 3) * 30} height={13} delay={i * 80} />
          </div>
        ))}
      </div>

      {/* Chart area */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        {/* Header with date columns */}
        <div style={{
          minHeight: 64,
          background: '#f4f5f7',
          borderBottom: '1px solid #dfe1e6',
          display: 'flex',
          alignItems: 'flex-end',
          gap: 40,
          padding: '10px 20px',
          paddingBottom: 8,
        }}>
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} width={50} height={10} />
          ))}
        </div>

        {/* Bars */}
        {Array.from({ length: ROWS }).map((_, i) => (
          <div
            key={i}
            style={{
              position: 'relative',
              minHeight: 48,
              marginBottom: 8,
              padding: '6px 0',
              display: 'flex',
              alignItems: 'center',
            }}
          >
            <div style={{
              position: 'absolute',
              left: BAR_CONFIGS[i].left,
              width: BAR_CONFIGS[i].width,
              height: 24,
            }}>
              <Skeleton width="100%" height={24} borderRadius={4} delay={i * 80} />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
