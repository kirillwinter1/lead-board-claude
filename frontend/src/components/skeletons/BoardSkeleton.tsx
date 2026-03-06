import { Skeleton } from '../Skeleton'

const GRID = '40px minmax(280px, 1fr) 170px 80px 130px 110px 260px 200px 70px'
const ROWS = 6

export function BoardSkeleton() {
  return (
    <div className="board-table-container" role="status" aria-label="Loading board">
      <div className="board-grid">
        {/* Header */}
        <div className="board-header" style={{ display: 'grid', gridTemplateColumns: GRID }}>
          <div className="cell" />
          <div className="cell"><Skeleton width={40} height={10} /></div>
          <div className="cell"><Skeleton width={40} height={10} /></div>
          <div className="cell"><Skeleton width={50} height={10} /></div>
          <div className="cell"><Skeleton width={60} height={10} /></div>
          <div className="cell"><Skeleton width={30} height={10} /></div>
          <div className="cell"><Skeleton width={50} height={10} /></div>
          <div className="cell"><Skeleton width={45} height={10} /></div>
          <div className="cell"><Skeleton width={35} height={10} /></div>
        </div>

        {/* Rows */}
        <div className="board-body">
          {Array.from({ length: ROWS }).map((_, i) => {
            const delay = i * 80
            return (
              <div
                key={i}
                className="board-row level-0"
                style={{ display: 'grid', gridTemplateColumns: GRID }}
              >
                {/* Expander */}
                <div style={{ padding: '12px 8px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Skeleton width={16} height={16} borderRadius={3} delay={delay} />
                </div>

                {/* Name: icon + key + title */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center', gap: 8 }}>
                  <Skeleton width={20} height={20} borderRadius={4} delay={delay} />
                  <Skeleton width={60} height={14} delay={delay} />
                  <Skeleton width={120 + (i % 3) * 40} height={14} delay={delay} />
                </div>

                {/* Team */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center' }}>
                  <Skeleton width={80} height={22} borderRadius={11} delay={delay} />
                </div>

                {/* Priority */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Skeleton width={20} height={20} borderRadius={4} delay={delay} />
                </div>

                {/* Progress */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Skeleton width={80} height={6} borderRadius={3} delay={delay} />
                  <Skeleton width={30} height={12} delay={delay} />
                </div>

                {/* RICE */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center' }}>
                  <Skeleton width={36} height={22} borderRadius={11} delay={delay} />
                </div>

                {/* Roles */}
                <div style={{ padding: '12px 16px', display: 'flex', alignItems: 'center', gap: 6 }}>
                  <Skeleton width={55} height={24} borderRadius={4} delay={delay} />
                  <Skeleton width={55} height={24} borderRadius={4} delay={delay} />
                  <Skeleton width={55} height={24} borderRadius={4} delay={delay} />
                </div>

                {/* Status */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center' }}>
                  <Skeleton width={90 + (i % 2) * 20} height={22} borderRadius={4} delay={delay} />
                </div>

                {/* Alerts */}
                <div style={{ padding: '12px 24px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  {i % 3 === 0 && <Skeleton width={18} height={18} borderRadius="50%" delay={delay} />}
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
