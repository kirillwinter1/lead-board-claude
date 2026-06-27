import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { GuideSectionData } from '../types'

function OverviewContent() {
  const { t } = useGuideLang()
  return (
    <>
      <h3>{t('Что такое Lead Board', 'What is Lead Board')}</h3>
      <p>
        {t(
          'Lead Board - инструмент управления поставкой задач. Помогает видеть поток работы, расставлять приоритеты, планировать на операционном и стратегическом уровне.',
          'Lead Board is a task delivery management tool. It helps you see the flow of work, set priorities, and plan at both operational and strategic level.'
        )}
      </p>
      <p>
        {t(
          'Каждая задача проходит полный цикл: от зарождения идеи до реализации в продакшене. Lead Board помогает определить приоритет выполнения, спланировать работу на уровне эпиков и проектов, понять кто и что должен делать в каждый момент времени, и предсказать дату завершения.',
          'Each task goes through the full cycle: from the birth of an idea to its release in production. Lead Board helps determine execution priority, plan work at both epic and project level, understand who should be doing what at any given moment, and predict completion dates.'
        )}
      </p>

      <h3>{t('Для кого', 'Who it is for')}</h3>
      <p>
        {t(
          'Инструмент рассчитан на разные роли в команде:',
          'The tool is designed for different team roles:'
        )}
      </p>
      <ul>
        <li><strong>Team Lead</strong> - {t('декомпозиция, контроль потока, работа с блокерами', 'decomposition, flow control, handling blockers')}</li>
        <li><strong>Product Owner</strong> - {t('приоритизация, приёмка результата, контроль scope', 'prioritization, result acceptance, scope control')}</li>
        <li><strong>Delivery / Project Manager</strong> - {t('метрики, прогнозы, ретроспективы, координация между командами', 'metrics, forecasts, retrospectives, cross-team coordination')}</li>
        <li><strong>{t('Исполнители (SA, DEV, QA и др.)', 'Executors (SA, DEV, QA, etc.)')}</strong> - {t('очередь задач, логирование времени, статусы', 'task queue, time logging, statuses')}</li>
      </ul>

      <h3>{t('Конфигурация', 'Configuration')}</h3>
      <p>
        {t(
          'У Lead Board свой внутренний движок с гибкой конфигурацией. Процессы в разных командах отличаются, поэтому роли конвейера, типы задач и статусы настраиваются под конкретную команду.',
          'Lead Board has its own engine with flexible configuration. Processes differ across teams, so pipeline roles, task types, and statuses are configurable per team.'
        )}
      </p>
      <p>
        {t(
          'В этом гайде используем дефолтную конфигурацию: роли SA → DEV → QA, стандартный workflow и иерархию задач. Ваша команда может работать иначе - Lead Board это поддержит.',
          'This guide uses the default configuration: SA → DEV → QA roles, standard workflow and task hierarchy. Your team may work differently - Lead Board supports that.'
        )}
      </p>
      <p>
        {t(
          'У системы есть набор правил, по которым она работает: иерархия задач, оценки по ролям, маппинг статусов из Jira. Эти правила описаны в следующем разделе.',
          'The system has a set of rules it operates by: task hierarchy, estimates per role, status mapping from Jira. These rules are described in the next section.'
        )}
      </p>

    </>
  )
}

const overviewContent = <OverviewContent />

export const overviewSection: GuideSectionData = {
  id: 'overview',
  titleRu: 'Обзор',
  titleEn: 'Overview',
  contentRu: overviewContent,
  contentEn: overviewContent,
}
