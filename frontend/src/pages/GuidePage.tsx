import { useCallback, useEffect, useRef, useState } from 'react'
import { GuideLanguageProvider, useGuideLang } from '../components/guide/GuideLanguageContext'
import { GuideSidebar } from '../components/guide/GuideSidebar'
import { GuideSection } from '../components/guide/GuideSection'
import { PipelineVisual } from '../components/guide/PipelineVisual'
import { overviewSection } from '../guide/content/overview'
import { rulesSection } from '../guide/content/rules'
import { stagesSections } from '../guide/content/stages'
import { rolesSection } from '../guide/content/roles'
import { guideSidebarItems } from '../guide/content/navigation'
import './GuidePage.css'

function GuidePageContent() {
  const { lang, toggleLang, t } = useGuideLang()
  const [activeId, setActiveId] = useState('overview')
  const contentRef = useRef<HTMLDivElement>(null)

  // IntersectionObserver to track which section is visible
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id)
            break
          }
        }
      },
      { rootMargin: '-80px 0px -60% 0px' }
    )

    // Collect all section IDs from sidebar items (flatten children)
    const ids: string[] = []
    guideSidebarItems.forEach(item => {
      ids.push(item.id)
      item.children?.forEach(child => ids.push(child.id))
    })

    ids.forEach(id => {
      const el = document.getElementById(id)
      if (el) observer.observe(el)
    })

    return () => observer.disconnect()
  }, [])

  // Scroll to hash on initial load
  useEffect(() => {
    if (window.location.hash) {
      const id = window.location.hash.slice(1)
      const el = document.getElementById(id)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' })
        setActiveId(id)
      }
    }
  }, [])

  const handleNavigate = useCallback((id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
    window.history.replaceState(null, '', '#' + id)
  }, [])

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
        <GuideSidebar items={guideSidebarItems} activeId={activeId} onNavigate={handleNavigate} />
      </div>
      <div className="guide-content-area" ref={contentRef}>
        {/* Overview */}
        <GuideSection id={overviewSection.id} titleRu={overviewSection.titleRu} titleEn={overviewSection.titleEn}>
          {lang === 'ru' ? overviewSection.contentRu : overviewSection.contentEn}
        </GuideSection>

        {/* Rules */}
        <GuideSection id={rulesSection.id} titleRu={rulesSection.titleRu} titleEn={rulesSection.titleEn}>
          {lang === 'ru' ? rulesSection.contentRu : rulesSection.contentEn}
        </GuideSection>

        {/* Pipeline wrapper with id="pipeline" */}
        <div id="pipeline" style={{ scrollMarginTop: 72 }}>
          <PipelineVisual activeStageId={activeId} onStageClick={handleNavigate} />

          {stagesSections.map(stage => (
            <GuideSection key={stage.id} id={stage.id} titleRu={stage.titleRu} titleEn={stage.titleEn}>
              {lang === 'ru' ? stage.contentRu : stage.contentEn}
            </GuideSection>
          ))}
        </div>

        {/* Roles */}
        <GuideSection id={rolesSection.id} titleRu={rolesSection.titleRu} titleEn={rolesSection.titleEn}>
          {lang === 'ru' ? rolesSection.contentRu : rolesSection.contentEn}
        </GuideSection>
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
