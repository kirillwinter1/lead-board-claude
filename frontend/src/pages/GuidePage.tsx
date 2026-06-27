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
        <GuideSection id="pipeline" titleRu="Типы задач" titleEn="Task Types">
          <p>
            {t(
              'Задачи выстраиваются в иерархию: от крупных инициатив до конкретных действий исполнителя. Каждый уровень отвечает за свой масштаб и имеет свои правила.',
              'Tasks form a hierarchy: from large initiatives down to specific actions by an individual. Each level has its own scale and rules.'
            )}
          </p>

          <h3>Project</h3>
          <p>
            {t(
              'Во многих компаниях запускаются большие инициативы, которые требуют участия нескольких команд. Заказчик (PO, PM) описывает бизнес-требования, совместно с архитектором определяет какие команды будут задействованы.',
              'Many companies launch large initiatives that require multiple teams. The customer (PO, PM) describes business requirements and, together with the architect, determines which teams will be involved.'
            )}
          </p>

          <h3>Epic</h3>
          <p>
            {t(
              'Epic - конкретная, измеримая ценность, доставляемая в рамках одной команды. Один эпик = одна команда. Над одним эпиком может работать только одна команда, но команда может вести несколько эпиков параллельно. Воркфлоу эпика похож на проект, за исключением этапа проверки идеи - эпик получает уже валидированную задачу от проекта.',
              'An Epic is a concrete, measurable value delivered by a single team. One epic = one team. Only one team can work on a given epic, but a team can run multiple epics in parallel. The epic workflow is similar to a project, except for the idea validation stage - an epic receives an already validated task from the project.'
            )}
          </p>

          <h3>Story</h3>
          <p>
            {t(
              'Story - задача, над которой работают одна или несколько ролей (как правило SA, DEV, QA). Результат выполнения Story - поставленный код в продакшен. Stories объединяются в эпики, а сами делятся на Subtask по ролям.',
              'A Story is a task worked on by one or more roles (typically SA, DEV, QA). The result of completing a Story is code shipped to production. Stories are grouped into epics and split into Subtasks by role.'
            )}
          </p>

          <h3>Subtask</h3>
          <p>
            {t(
              'Subtask - базовая, неделимая единица выполнения работы. Subtask привязывается к конкретному исполнителю с конкретной ролью. Именно в Subtask ставится оценка в человеко-днях и логируется время.',
              'A Subtask is the basic, indivisible unit of work. A Subtask is assigned to a specific person with a specific role. Estimates in person-days and time logging happen at the Subtask level.'
            )}
          </p>
        </GuideSection>

        <GuideSection id="conveyor" titleRu="Конвейер" titleEn="Pipeline">
          <p>
            {t(
              'Каждый тип задачи имеет свой жизненный цикл. На разных этапах подключаются разные участники. В разных компаниях процессы отличаются - мы постарались взять лучшие практики управления потоком.',
              'Each task type has its own lifecycle. Different participants join at different stages. Processes vary across companies - we tried to adopt the best flow management practices.'
            )}
          </p>
          <PipelineVisual activeStageId={activeId} onStageClick={handleNavigate} />
        </GuideSection>

        {stagesSections.map(stage => (
          <GuideSection key={stage.id} id={stage.id} titleRu={stage.titleRu} titleEn={stage.titleEn}>
            {lang === 'ru' ? stage.contentRu : stage.contentEn}
          </GuideSection>
        ))}

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
