import { Link } from 'react-router-dom'
import { useGuideLang } from './GuideLanguageContext'
import { GuideScreenLink } from '../../guide/types'
import './RoleCard.css'

interface RoleCardCompactProps {
  nameRu: string
  nameEn: string
  actionsRu: string
  actionsEn: string
}

export function RoleCardCompact({ nameRu, nameEn, actionsRu, actionsEn }: RoleCardCompactProps) {
  const { t } = useGuideLang()
  return (
    <div className="role-card">
      <div className="role-card-name">{t(nameRu, nameEn)}</div>
      <div className="role-card-actions">{t(actionsRu, actionsEn)}</div>
    </div>
  )
}

interface RoleCardFullProps {
  id: string
  nameRu: string
  nameEn: string
  responsibilityRu: string
  responsibilityEn: string
  stages: { stageRu: string; stageEn: string; actionsRu: string; actionsEn: string }[]
  keyScreens: GuideScreenLink[]
}

export function RoleCardFull({ id, nameRu, nameEn, responsibilityRu, responsibilityEn, stages, keyScreens }: RoleCardFullProps) {
  const { t } = useGuideLang()
  return (
    <div id={id} className="role-card-full">
      <h3 className="role-card-full-name">{t(nameRu, nameEn)}</h3>
      <p className="role-card-full-responsibility">{t(responsibilityRu, responsibilityEn)}</p>
      <table className="role-card-stages-table">
        <thead>
          <tr>
            <th>{t('Этап', 'Stage')}</th>
            <th>{t('Действия', 'Actions')}</th>
          </tr>
        </thead>
        <tbody>
          {stages.map((s, i) => (
            <tr key={i}>
              <td>{t(s.stageRu, s.stageEn)}</td>
              <td>{t(s.actionsRu, s.actionsEn)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {keyScreens.length > 0 && (
        <div className="role-card-screens">
          <strong>{t('Ключевые экраны:', 'Key screens:')}</strong>{' '}
          {keyScreens.map((s, i) => (
            <span key={i}>
              {i > 0 && ', '}
              <Link to={s.path}>{s.label}</Link>
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
