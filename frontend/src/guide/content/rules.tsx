import { Link } from 'react-router-dom'
import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { GuideSectionData } from '../types'

function RulesContent() {
  const { t } = useGuideLang()
  return (
    <>
      {/* Task Hierarchy */}
      <h3>{t('Иерархия задач', 'Task Hierarchy')}</h3>
      <pre>{`Epic (${t('проект/фича', 'project/feature')})
└── Story / Bug (${t('единица работы', 'work unit')})
    └── Subtask (${t('действие по роли', 'role-specific action')})
        ├── Subtask [${t('Роль A', 'Role A')}]
        ├── Subtask [${t('Роль B', 'Role B')}]
        └── Subtask [${t('Роль C', 'Role C')}]`}</pre>
      <p>
        {t(
          'Роли конвейера настраиваются в ',
          'Pipeline roles are configurable in '
        )}
        <Link to="/workflow">{t('Конфигурации Workflow', 'Workflow Config')}</Link>
        {t(
          '. Пример по умолчанию: SA → DEV → QA.',
          '. Default example: SA → DEV → QA.'
        )}
      </p>

      {/* Estimation Rules */}
      <h3>{t('Правила оценки', 'Estimation Rules')}</h3>
      <table className="guide-table">
        <thead>
          <tr>
            <th>{t('Правило', 'Rule')}</th>
            <th>{t('Описание', 'Description')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('Оценки только в Subtask', 'Estimates only in Subtask')}</td>
            <td>{t('Story и Epic агрегируются из Subtask', 'Story and Epic are aggregated from Subtasks')}</td>
          </tr>
          <tr>
            <td>{t('Subtask = роль', 'Subtask = role')}</td>
            <td>{t('Каждый Subtask привязан к роли конвейера', 'Each Subtask is bound to a pipeline role')}</td>
          </tr>
          <tr>
            <td>{t('Единица измерения', 'Unit')}</td>
            <td>{t('Человеко-дни (1 ч/д = 8 часов)', 'Person-days (1 pd = 8 hours)')}</td>
          </tr>
          <tr>
            <td>{t('Исполнитель только в Subtask', 'Assignee only in Subtask')}</td>
            <td>{t('Устанавливается на Subtask, не на Story/Epic', 'Set on Subtask, not on Story/Epic')}</td>
          </tr>
          <tr>
            <td>{t('Агрегация вверх', 'Upward aggregation')}</td>
            <td>{t('Subtask → Story (сумма по ролям) → Epic (общая сумма)', 'Subtask → Story (sum by role) → Epic (total sum)')}</td>
          </tr>
        </tbody>
      </table>

      {/* Time Logging */}
      <h3>{t('Логирование времени', 'Time Logging')}</h3>
      <table className="guide-table">
        <thead>
          <tr>
            <th>{t('Правило', 'Rule')}</th>
            <th>{t('Описание', 'Description')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('Лог в Subtask', 'Log in Subtask')}</td>
            <td>{t('Worklog записывается в конкретный Subtask', 'Worklog is written to the specific Subtask')}</td>
          </tr>
          <tr>
            <td>{t('Прогресс', 'Progress')}</td>
            <td>{t('залогировано / оценка (максимум 100%)', 'logged / estimate (max 100%)')}</td>
          </tr>
          <tr>
            <td>DSR</td>
            <td>{t(
              'Рабочие дни с начала − дни паузы / оценка. Цель < 1.0',
              'Work days since start − pause days / estimate. Target < 1.0'
            )}</td>
          </tr>
        </tbody>
      </table>

      {/* Grades & Capacity */}
      <h3>{t('Грейды и ёмкость', 'Grades & Capacity')}</h3>
      <p>
        {t(
          'Коэффициент грейда влияет на расчёт ёмкости команды и прогноз сроков:',
          'Grade coefficient affects team capacity calculation and deadline forecast:'
        )}
      </p>
      <table className="guide-table">
        <thead>
          <tr>
            <th>{t('Грейд', 'Grade')}</th>
            <th>{t('Коэффициент', 'Coefficient')}</th>
            <th>{t('Значение', 'Meaning')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Senior</td>
            <td>0.8</td>
            <td>{t('На 20% быстрее', '20% faster')}</td>
          </tr>
          <tr>
            <td>Middle</td>
            <td>1.0</td>
            <td>{t('Базовая линия', 'Baseline')}</td>
          </tr>
          <tr>
            <td>Junior</td>
            <td>1.5</td>
            <td>{t('На 50% больше времени', '50% more time')}</td>
          </tr>
        </tbody>
      </table>

      {/* Rough Estimates */}
      <h3>{t('Грязные оценки (Rough Estimates)', 'Rough Estimates')}</h3>
      <p>
        {t(
          'Даются на уровне Epic, по каждой роли конвейера, в человеко-днях. Используются для раннего прогнозирования сроков до детальной декомпозиции.',
          'Given at Epic level, per pipeline role, in person-days. Used for early deadline forecasting before detailed decomposition.'
        )}
      </p>

      {/* Flags & Pauses */}
      <h3>{t('Флаги и паузы', 'Flags & Pauses')}</h3>
      <p>
        {t(
          'Флаги сигнализируют о блокерах и влияют на приоритизацию:',
          'Flags signal blockers and affect prioritization:'
        )}
      </p>
      <ul>
        <li>
          <strong>{t('Флаг на Epic', 'Epic flag')}</strong>: {t(
            'AutoScore -100. Epic заблокирован.',
            'AutoScore -100. Epic is blocked.'
          )}
        </li>
        <li>
          <strong>{t('Флаг на Story', 'Story flag')}</strong>: {t(
            'AutoScore -200, DSR встаёт на паузу. Story заблокирована, время не считается.',
            'AutoScore -200, DSR pauses. Story is blocked, time stops counting.'
          )}
        </li>
      </ul>

      {/* WIP Limits */}
      <h3>{t('WIP-лимиты', 'WIP Limits')}</h3>
      <p>
        {t(
          'Ограничивают число активных Epic/Story на команду или роль. Не дают переключаться между задачами и терять фокус. Если лимит достигнут, сначала закончите текущие задачи.',
          'Cap the number of active Epics/Stories per team or role. Keep people from jumping between tasks and losing focus. If you hit the limit, finish current tasks first.'
        )}
      </p>
    </>
  )
}

const rulesContent = <RulesContent />

export const rulesSection: GuideSectionData = {
  id: 'rules',
  titleRu: 'Правила процесса',
  titleEn: 'Process Rules',
  contentRu: rulesContent,
  contentEn: rulesContent,
}
