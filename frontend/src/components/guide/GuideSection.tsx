import { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useGuideLang } from './GuideLanguageContext'
import './GuideSection.css'

interface Props {
  id: string
  titleRu: string
  titleEn: string
  children?: ReactNode
}

export function GuideSection({ id, titleRu, titleEn, children }: Props) {
  const { t } = useGuideLang()
  return (
    <section id={id} className="guide-section">
      <h2 className="guide-section-title">{t(titleRu, titleEn)}</h2>
      <div className="guide-section-body">{children}</div>
    </section>
  )
}

interface AntiPatternsProps {
  items: { ru: string; en: string }[]
}

export function AntiPatterns({ items }: AntiPatternsProps) {
  const { t } = useGuideLang()
  return (
    <div className="guide-antipatterns">
      <h4 className="guide-antipatterns-title">{t('Антипаттерны', 'Anti-patterns')}</h4>
      <ul>
        {items.map((item, i) => (
          <li key={i}>{t(item.ru, item.en)}</li>
        ))}
      </ul>
    </div>
  )
}

interface ScreenLinksProps {
  screens: { label: string; path: string; descriptionRu: string; descriptionEn: string }[]
}

export function ScreenLinks({ screens }: ScreenLinksProps) {
  const { t } = useGuideLang()
  return (
    <div className="guide-screen-links">
      <h4>{t('В Lead Board', 'In Lead Board')}</h4>
      <ul>
        {screens.map((s, i) => (
          <li key={i}>
            <Link to={s.path} className="guide-screen-link">{s.label}</Link>
            {' — '}{t(s.descriptionRu, s.descriptionEn)}
          </li>
        ))}
      </ul>
    </div>
  )
}
