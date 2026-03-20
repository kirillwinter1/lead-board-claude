import { GuideLanguageProvider, useGuideLang } from '../components/guide/GuideLanguageContext'
import './GuidePage.css'

function GuidePageContent() {
  const { lang, toggleLang, t } = useGuideLang()

  return (
    <div className="guide-page">
      <div className="guide-sidebar-area">
        <div className="guide-sidebar-header">
          <h2>{t('Delivery Guide', 'Delivery Guide')}</h2>
          <div className="guide-lang-toggle">
            <button
              className={`guide-lang-btn ${lang === 'ru' ? 'active' : ''}`}
              onClick={() => lang !== 'ru' && toggleLang()}
            >
              RU
            </button>
            <span className="guide-lang-sep">|</span>
            <button
              className={`guide-lang-btn ${lang === 'en' ? 'active' : ''}`}
              onClick={() => lang !== 'en' && toggleLang()}
            >
              EN
            </button>
          </div>
        </div>
      </div>
      <div className="guide-content-area">
        <p>{t('Гайд в разработке...', 'Guide is under construction...')}</p>
      </div>
    </div>
  )
}

export function GuidePage() {
  return (
    <GuideLanguageProvider>
      <GuidePageContent />
    </GuideLanguageProvider>
  )
}
