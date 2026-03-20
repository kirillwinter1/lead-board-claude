import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { GuideSectionData } from '../types'

function OverviewContent() {
  const { t } = useGuideLang()
  return (
    <>
      <h3>{t('Что это и для кого', 'What it is and who it is for')}</h3>
      <p>
        {t(
          'Этот гайд описывает процесс delivery — от идеи до продакшена. Он охватывает все этапы жизненного цикла задачи, роли участников и инструменты контроля.',
          'This guide describes the delivery process — from idea to production. It covers all stages of the task lifecycle, participant roles, and control instruments.'
        )}
      </p>
      <p>
        {t(
          'Lead Board — инструмент, который поддерживает этот процесс: визуализирует доску, метрики, прогнозы и качество данных.',
          'Lead Board is the tool that supports this process: it visualizes the board, metrics, forecasts, and data quality.'
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
          <strong>{t('Непрерывный поток', 'Continuous flow')}</strong> — {t(
            'задачи движутся по конвейеру без привязки к спринтам. Нет итераций — есть поток.',
            'tasks move through the pipeline without sprint boundaries. No iterations — just flow.'
          )}
        </li>
        <li>
          <strong>{t('WIP-лимиты', 'WIP limits')}</strong> — {t(
            'ограничение количества одновременно активных задач. Предотвращает перегрузку и переключение контекста.',
            'limiting the number of concurrently active tasks. Prevents overload and context switching.'
          )}
        </li>
        <li>
          <strong>{t('Визуализация', 'Visualization')}</strong> — {t(
            'доска, таймлайн, метрики — всё наглядно. Проблемы видны сразу.',
            'board, timeline, metrics — everything is visual. Problems are visible immediately.'
          )}
        </li>
        <li>
          <strong>{t('Метрики потока', 'Flow metrics')}</strong> — {t(
            'DSR, throughput, cycle time, forecast accuracy — количественный контроль процесса.',
            'DSR, throughput, cycle time, forecast accuracy — quantitative process control.'
          )}
        </li>
      </ul>

      <h3>{t('Конвейер', 'The Pipeline')}</h3>
      <p>
        {t(
          'Центральная концепция — конвейер из 8 этапов. Каждая задача (Epic) проходит все обязательные этапы последовательно:',
          'The central concept is a pipeline of 8 stages. Each task (Epic) passes through all mandatory stages sequentially:'
        )}
      </p>
      <ol>
        <li><strong>{t('Идея', 'Idea')}</strong> — {t('фиксация бизнес-потребности', 'capturing business need')}</li>
        <li><strong>{t('БТ (BRD)', 'BRD')}</strong> — {t('формализация требований', 'formalizing requirements')}</li>
        <li><strong>{t('Грязные оценки', 'Rough Estimates')}</strong> — {t('ранняя оценка объёма', 'early volume estimation')}</li>
        <li><strong>{t('Планирование', 'Planning')}</strong> — {t('декомпозиция и точные оценки', 'decomposition and precise estimates')}</li>
        <li><strong>{t('Разработка', 'Development')}</strong> — {t('выполнение работы', 'executing the work')}</li>
        <li><strong>{t('E2E', 'E2E')}</strong> <em>({t('опц.', 'opt.')})</em> — {t('сквозная проверка', 'end-to-end verification')}</li>
        <li><strong>{t('Приёмка', 'Acceptance')}</strong> <em>({t('опц.', 'opt.')})</em> — {t('подтверждение PO', 'PO confirmation')}</li>
        <li><strong>{t('Готово', 'Done')}</strong> — {t('деплой, ретроспектива', 'deployment, retrospective')}</li>
      </ol>
      <p>
        {t(
          'На каждом этапе — определённые роли и экраны Lead Board. Контроль качества (Data Quality, метрики, WIP, DSR) — сквозной, работает на всех этапах.',
          'Each stage has defined roles and Lead Board screens. Quality control (Data Quality, metrics, WIP, DSR) is cross-cutting, operating at every stage.'
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
