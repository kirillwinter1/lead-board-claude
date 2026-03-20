import { useGuideLang } from './GuideLanguageContext'
import { GuideChecklistItem } from '../../guide/types'
import './StageChecklist.css'

interface Props {
  titleRu?: string
  titleEn?: string
  items: GuideChecklistItem[]
}

export function StageChecklist({ titleRu, titleEn, items }: Props) {
  const { t } = useGuideLang()
  return (
    <div className="stage-checklist">
      {titleRu && <h4 className="stage-checklist-title">{t(titleRu, titleEn || titleRu)}</h4>}
      <ul className="stage-checklist-list">
        {items.map((item, i) => (
          <li key={i} className="stage-checklist-item">
            <span className="stage-checklist-box">☐</span>
            <span>{t(item.textRu, item.textEn)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
