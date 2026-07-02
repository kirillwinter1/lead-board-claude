import type { ReactNode } from 'react'
import { getEpicDetail, type EpicDetail } from '../../api/epics'
import { HoverInfoCard } from '../HoverInfoCard'

// F85 — tooltip эпика: полное название + описание (того, чего нет на строке).
export function EpicTooltip({ epicKey, children }: { epicKey: string; children: ReactNode }) {
  return (
    <HoverInfoCard<EpicDetail>
      title={epicKey}
      width={340}
      loadData={(signal) => getEpicDetail(epicKey, signal)}
      render={(d) => (
        <>
          <div style={{ fontWeight: 600, color: '#172b4d', marginBottom: 6 }}>{d.summary}</div>
          {d.description ? (
            <div style={{ color: '#42526e', whiteSpace: 'pre-wrap', maxHeight: 260, overflow: 'hidden', lineHeight: 1.4 }}>
              {d.description}
            </div>
          ) : (
            <div style={{ color: '#97a0af', fontStyle: 'italic' }}>Без описания</div>
          )}
        </>
      )}
    >
      {children}
    </HoverInfoCard>
  )
}
