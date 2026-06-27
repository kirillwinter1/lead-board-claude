import { useGuideLang } from './GuideLanguageContext'
import './PipelineVisual.css'

interface PipelineStage {
  id: string
  labelRu: string
  labelEn: string
  optional?: boolean
}

const STAGES: PipelineStage[] = [
  { id: 'pipeline-idea', labelRu: 'Идея', labelEn: 'Idea' },
  { id: 'pipeline-brd', labelRu: 'БТ', labelEn: 'BRD' },
  { id: 'pipeline-rough-estimates', labelRu: 'Грязные\nоценки', labelEn: 'Rough\nEstimates' },
  { id: 'pipeline-planning', labelRu: 'Планирование', labelEn: 'Planning' },
  { id: 'pipeline-development', labelRu: 'Разработка', labelEn: 'Development' },
  { id: 'pipeline-e2e', labelRu: 'E2E', labelEn: 'E2E', optional: true },
  { id: 'pipeline-acceptance', labelRu: 'Приёмка', labelEn: 'Acceptance', optional: true },
  { id: 'pipeline-done', labelRu: 'Готово', labelEn: 'Done' },
]

interface Props {
  activeStageId?: string
  onStageClick: (id: string) => void
}

export function PipelineVisual({ activeStageId, onStageClick }: Props) {
  const { t } = useGuideLang()

  return (
    <div className="pipeline-visual">
      <div className="pipeline-stages">
        {STAGES.map((stage, i) => (
          <div key={stage.id} className="pipeline-stage-wrapper">
            <button
              className={`pipeline-stage-box ${stage.optional ? 'optional' : ''} ${activeStageId === stage.id ? 'active' : ''}`}
              onClick={() => onStageClick(stage.id)}
            >
              <span className="pipeline-stage-number">{i + 1}</span>
              <span className="pipeline-stage-label">{t(stage.labelRu, stage.labelEn)}</span>
              {stage.optional && <span className="pipeline-stage-opt">{t('опц.', 'opt.')}</span>}
            </button>
            {i < STAGES.length - 1 && <span className="pipeline-arrow">&#9654;</span>}
          </div>
        ))}
      </div>
      <div className="pipeline-qc-bar">
        <span className="pipeline-qc-label">{t('КОНТРОЛЬ КАЧЕСТВА (сквозной)', 'QUALITY CONTROL (cross-cutting)')}</span>
        <span className="pipeline-qc-items">
          Data Quality · Metrics · WIP · DSR · Forecast Accuracy · Throughput
        </span>
      </div>
    </div>
  )
}
