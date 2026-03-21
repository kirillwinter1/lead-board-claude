import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { GuideSectionData } from '../types'

function OverviewContent() {
  const { t } = useGuideLang()
  return (
    <>
      <h3>{t('Что это и для кого', 'What it is and who it is for')}</h3>
      <p>
        {t(
          'Гайд по процессу delivery: как задача проходит путь от идеи до продакшена, кто за что отвечает и какие инструменты помогают контролировать процесс.',
          'A guide to the delivery process: how a task travels from idea to production, who is responsible for what, and which tools help control the process.'
        )}
      </p>
      <p>
        {t(
          'Lead Board поддерживает этот процесс: показывает доску, метрики, прогнозы и качество данных.',
          'Lead Board supports this process: it shows the board, metrics, forecasts, and data quality.'
        )}
      </p>
      <p>
        <strong>{t('Целевая аудитория:', 'Target audience:')}</strong>{' '}
        {t(
          'Delivery Managers, Team Leads, Engineering Managers.',
          'Delivery Managers, Team Leads, Engineering Managers.'
        )}
      </p>

      <h3>{t('Методология: Kanban / Flow', 'Methodology: Kanban / Flow')}</h3>
      <p>
        {t(
          'Процесс основан на принципах Kanban и непрерывного потока (Flow):',
          'The process is based on Kanban and continuous Flow principles:'
        )}
      </p>
      <ul>
        <li>
          <strong>{t('Непрерывный поток', 'Continuous flow')}</strong>: {t(
            'задачи идут по конвейеру без привязки к спринтам. Никаких итераций, только поток.',
            'tasks go through the pipeline without sprint boundaries. No iterations, just flow.'
          )}
        </li>
        <li>
          <strong>{t('WIP-лимиты', 'WIP limits')}</strong>: {t(
            'ограничение числа задач в работе одновременно. Не даёт команде перегружаться и прыгать между контекстами.',
            'cap on how many tasks are in progress at once. Keeps the team from overloading and jumping between contexts.'
          )}
        </li>
        <li>
          <strong>{t('Визуализация', 'Visualization')}</strong>: {t(
            'доска, таймлайн, метрики - всё наглядно. Проблемы видны сразу.',
            'board, timeline, metrics - everything is visual. Problems are visible right away.'
          )}
        </li>
        <li>
          <strong>{t('Метрики потока', 'Flow metrics')}</strong>: {t(
            'DSR, throughput, cycle time, forecast accuracy - контроль процесса в цифрах.',
            'DSR, throughput, cycle time, forecast accuracy - process control in numbers.'
          )}
        </li>
      </ul>

      <h3>{t('Конвейер', 'The Pipeline')}</h3>
      <p>
        {t(
          'Задача (Epic) проходит конвейер из 8 этапов по порядку:',
          'Each task (Epic) goes through a pipeline of 8 stages in order:'
        )}
      </p>
      <ol>
        <li><strong>{t('Идея', 'Idea')}</strong>: {t('фиксация бизнес-потребности', 'capture business need')}</li>
        <li><strong>{t('БТ (BRD)', 'BRD')}</strong>: {t('формализация требований', 'formalize requirements')}</li>
        <li><strong>{t('Грязные оценки', 'Rough Estimates')}</strong>: {t('ранняя оценка объёма', 'early volume estimation')}</li>
        <li><strong>{t('Планирование', 'Planning')}</strong>: {t('декомпозиция и точные оценки', 'decomposition and precise estimates')}</li>
        <li><strong>{t('Разработка', 'Development')}</strong>: {t('выполнение работы', 'execute the work')}</li>
        <li><strong>{t('E2E', 'E2E')}</strong> <em>({t('опц.', 'opt.')})</em>: {t('сквозная проверка', 'end-to-end verification')}</li>
        <li><strong>{t('Приёмка', 'Acceptance')}</strong> <em>({t('опц.', 'opt.')})</em>: {t('подтверждение PO', 'PO confirmation')}</li>
        <li><strong>{t('Готово', 'Done')}</strong>: {t('деплой, ретроспектива', 'deployment, retrospective')}</li>
      </ol>
      <p>
        {t(
          'На каждом этапе свои роли и экраны Lead Board. Контроль качества (Data Quality, метрики, WIP, DSR) работает сквозной на всех этапах.',
          'Each stage has its own roles and Lead Board screens. Quality control (Data Quality, metrics, WIP, DSR) runs across all stages.'
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
