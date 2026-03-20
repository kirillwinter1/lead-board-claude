import { useGuideLang } from '../../components/guide/GuideLanguageContext'
import { RoleCardCompact } from '../../components/guide/RoleCard'
import { StageChecklist } from '../../components/guide/StageChecklist'
import { AntiPatterns, ScreenLinks } from '../../components/guide/GuideSection'
import { GuideSectionData } from '../types'

/* ────────────────── Stage 1: Idea ────────────────── */

function StageIdea() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Зафиксировать бизнес-потребность, оценить ценность, принять решение go/no-go.',
          'Capture business need, assess value, decide go/no-go.'
        )}
      </p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Product Owner"
          nameEn="Product Owner"
          actionsRu="Формулирует идею, описывает бизнес-ценность, создаёт Epic в Jira, заполняет RICE"
          actionsEn="Formulates idea, describes business value, creates Epic in Jira, fills RICE"
        />
        <RoleCardCompact
          nameRu="Delivery Manager"
          nameEn="Delivery Manager"
          actionsRu="Проверяет соответствие roadmap, оценивает приоритет в контексте других задач"
          actionsEn="Checks roadmap fit, assesses priority in the context of other tasks"
        />
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Оценивает техническую реализуемость на верхнем уровне"
          actionsEn="Assesses high-level technical feasibility"
        />
      </div>

      <ScreenLinks screens={[
        { label: 'Board', path: '/', descriptionRu: 'Epic в статусе NEW', descriptionEn: 'Epic in NEW status' },
      ]} />

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'Epic создан в Jira с описанием бизнес-ценности', textEn: 'Epic created in Jira with business value description' },
          { textRu: 'RICE-оценка заполнена', textEn: 'RICE assessment filled' },
          { textRu: 'Решение go/no-go принято', textEn: 'Go/no-go decision made' },
        ]}
      />

      <AntiPatterns items={[
        { ru: 'Начинать разработку без сформулированной цели', en: 'Starting development without a formulated goal' },
        { ru: 'Epic без описания — только заголовок', en: 'Epic without description — title only' },
      ]} />

      <p>
        <strong>{t('Контроль качества:', 'Quality control:')}</strong>{' '}
        {t(
          'Data Quality проверяет наличие описания и заполненность RICE.',
          'Data Quality checks description presence and RICE completeness.'
        )}
      </p>
    </>
  )
}

/* ────────────────── Stage 2: BRD ────────────────── */

function StageBrd() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Формализовать требования, зафиксировать scope и критерии приёмки.',
          'Formalize requirements, fix scope and acceptance criteria.'
        )}
      </p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Product Owner"
          nameEn="Product Owner"
          actionsRu="Пишет бизнес-требования (BRD), определяет критерии приёмки (AC)"
          actionsEn="Writes business requirements (BRD), defines acceptance criteria (AC)"
        />
        <RoleCardCompact
          nameRu="Представитель роли SA"
          nameEn="SA role representative"
          actionsRu="Участвует в проработке, уточняет технические детали"
          actionsEn="Participates in elaboration, clarifies technical details"
        />
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Подтверждает техническую реализуемость"
          actionsEn="Confirms technical feasibility"
        />
      </div>

      <ScreenLinks screens={[
        { label: 'Board', path: '/', descriptionRu: 'Epic в статусе Requirements', descriptionEn: 'Epic in Requirements status' },
      ]} />

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'BRD написан и согласован', textEn: 'BRD written and approved' },
          { textRu: 'Критерии приёмки (AC) определены', textEn: 'Acceptance criteria (AC) defined' },
          { textRu: 'TL подтвердил техническую реализуемость', textEn: 'TL confirmed technical feasibility' },
        ]}
      />

      <AntiPatterns items={[
        { ru: '«Допишу потом» — требования остаются пустыми неделями', en: '"I\'ll write it later" — requirements stay empty for weeks' },
        { ru: 'Требования без критериев приёмки', en: 'Requirements without acceptance criteria' },
      ]} />

      <p>
        <strong>{t('Контроль качества:', 'Quality control:')}</strong>{' '}
        {t(
          'Data Quality проверяет наличие описания.',
          'Data Quality checks description presence.'
        )}
      </p>
    </>
  )
}

/* ────────────────── Stage 3: Rough Estimates ────────────────── */

function StageRoughEstimates() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Понять объём работ до детальной декомпозиции, получить ранний прогноз сроков.',
          'Understand work volume before detailed decomposition, get early deadline forecast.'
        )}
      </p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Координирует оценку, собирает грязные оценки от представителей ролей"
          actionsEn="Coordinates estimation, collects rough estimates from role representatives"
        />
        <RoleCardCompact
          nameRu="Представители ролей"
          nameEn="Role representatives"
          actionsRu="Дают грязные оценки в человеко-днях по своей роли конвейера"
          actionsEn="Give rough estimates in person-days for their pipeline role"
        />
        <RoleCardCompact
          nameRu="Product Owner"
          nameEn="Product Owner"
          actionsRu="Проверяет прогноз, принимает решение о приоритете"
          actionsEn="Reviews forecast, makes priority decision"
        />
      </div>

      <ScreenLinks screens={[
        { label: 'Board', path: '/', descriptionRu: 'Карточка Epic → грязные оценки', descriptionEn: 'Epic card → rough estimates' },
        { label: 'Timeline', path: '/?view=timeline', descriptionRu: 'Прогноз на основе грязных оценок', descriptionEn: 'Forecast based on rough estimates' },
        { label: 'Projects', path: '/projects', descriptionRu: 'Обзор проектов', descriptionEn: 'Projects overview' },
      ]} />

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'Грязная оценка заполнена по каждой роли', textEn: 'Rough estimate filled for each role' },
          { textRu: 'PO проверил прогноз сроков', textEn: 'PO reviewed the deadline forecast' },
        ]}
      />

      <AntiPatterns items={[
        { ru: 'Одно число на весь Epic без разбивки по ролям', en: 'Single number for the entire Epic without role breakdown' },
        { ru: 'Пропуск грязных оценок — сразу в декомпозицию', en: 'Skipping rough estimates — jumping straight to decomposition' },
        { ru: 'Оценка без участия реальных исполнителей', en: 'Estimation without actual doers involved' },
      ]} />

      <p>
        <strong>{t('Контроль качества:', 'Quality control:')}</strong>{' '}
        {t(
          'Data Quality проверяет заполненность оценок. Timeline показывает реалистичность прогноза.',
          'Data Quality checks estimate completeness. Timeline shows forecast realism.'
        )}
      </p>
    </>
  )
}

/* ────────────────── Stage 4: Planning ────────────────── */

function StagePlanning() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Декомпозировать Epic → Stories → Subtasks, дать точные оценки, сформировать очередь работ.',
          'Decompose Epic → Stories → Subtasks, give precise estimates, form the work queue.'
        )}
      </p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Декомпозирует Epic на Stories и Subtask, контролирует полноту"
          actionsEn="Decomposes Epic into Stories and Subtasks, controls completeness"
        />
        <RoleCardCompact
          nameRu="Представители ролей"
          nameEn="Role representatives"
          actionsRu="Оценивают через Planning Poker, создают Subtask по своей роли"
          actionsEn="Estimate via Planning Poker, create Subtasks for their role"
        />
        <RoleCardCompact
          nameRu="Product Owner"
          nameEn="Product Owner"
          actionsRu="Приоритизирует Stories по бизнес-ценности"
          actionsEn="Prioritizes Stories by business value"
        />
        <RoleCardCompact
          nameRu="Delivery Manager"
          nameEn="Delivery Manager"
          actionsRu="Контролирует WIP-лимиты, баланс нагрузки"
          actionsEn="Controls WIP limits, workload balance"
        />
      </div>

      <h4>{t('Дерево декомпозиции', 'Decomposition tree')}</h4>
      <pre>{`Epic
├── Story A
│   ├── Subtask [SA]   — 2 ${t('ч/д', 'pd')}
│   ├── Subtask [DEV]  — 5 ${t('ч/д', 'pd')}
│   └── Subtask [QA]   — 3 ${t('ч/д', 'pd')}
└── Story B
    ├── Subtask [DEV]  — 3 ${t('ч/д', 'pd')}
    └── Subtask [QA]   — 2 ${t('ч/д', 'pd')}`}</pre>

      <h4>{t('Порядок оценки', 'Estimation flow')}</h4>
      <ol>
        <li>{t('Декомпозиция Epic → Stories → Subtasks', 'Decompose Epic → Stories → Subtasks')}</li>
        <li>{t('Оценка через Planning Poker', 'Estimate via Planning Poker')}</li>
        <li>{t('Агрегация: Subtask → Story → Epic', 'Aggregate: Subtask → Story → Epic')}</li>
        <li>{t('Пересчёт прогноза', 'Recalculate forecast')}</li>
      </ol>

      <ScreenLinks screens={[
        { label: 'Board', path: '/', descriptionRu: 'Доска с декомпозицией', descriptionEn: 'Board with decomposition' },
        { label: 'Data Quality', path: '/data-quality', descriptionRu: 'Проверка полноты оценок', descriptionEn: 'Estimate completeness check' },
        { label: 'Planning Poker', path: '/poker', descriptionRu: 'Командная оценка задач', descriptionEn: 'Team estimation sessions' },
        { label: 'Quarterly Planning', path: '/quarterly-planning', descriptionRu: 'Квартальное планирование', descriptionEn: 'Quarterly planning' },
      ]} />

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'Полная декомпозиция: Epic → Stories → Subtasks', textEn: 'Full decomposition: Epic → Stories → Subtasks' },
          { textRu: 'Оценки проставлены во всех Subtask', textEn: 'Estimates set in all Subtasks' },
          { textRu: 'Data Quality — без критичных проблем', textEn: 'Data Quality — no critical issues' },
          { textRu: 'WIP-лимиты не превышены', textEn: 'WIP limits not exceeded' },
        ]}
      />

      <AntiPatterns items={[
        { ru: 'Stories без Subtask — невозможно отследить по ролям', en: 'Stories without Subtasks — impossible to track by role' },
        { ru: 'Subtask без оценок — ломает прогноз', en: 'Subtasks without estimates — breaks forecast' },
        { ru: 'Игнорирование WIP-лимитов — перегрузка команды', en: 'Ignoring WIP limits — team overload' },
        { ru: 'Manual Boost без причины — искажает приоритизацию', en: 'Manual Boost without reason — distorts prioritization' },
      ]} />

      <p>
        <strong>{t('Контроль качества:', 'Quality control:')}</strong>{' '}
        {t(
          'Data Quality, Board — WIP, Forecast accuracy.',
          'Data Quality, Board — WIP, Forecast accuracy.'
        )}
      </p>
    </>
  )
}

/* ────────────────── Stage 5: Development ────────────────── */

function StageDevelopment() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Провести задачу через ролевой конвейер до Done.',
          'Move the task through the role pipeline to Done.'
        )}
      </p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Исполнители"
          nameEn="Executors"
          actionsRu="Берут верхнюю задачу по AutoScore, двигают статусы, логируют время"
          actionsEn="Take top task by AutoScore, move statuses, log time"
        />
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Мониторит DSR, блокеры, помогает команде"
          actionsEn="Monitors DSR, blockers, helps the team"
        />
        <RoleCardCompact
          nameRu="Delivery Manager"
          nameEn="Delivery Manager"
          actionsRu="Контролирует WIP, throughput, точность прогноза"
          actionsEn="Controls WIP, throughput, forecast accuracy"
        />
      </div>

      <h4>{t('Ежедневный поток', 'Daily flow')}</h4>
      <ol>
        <li>{t('Board → найти свои задачи', 'Board → find your tasks')}</li>
        <li>{t('Взять верхнюю по AutoScore', 'Take the top one by AutoScore')}</li>
        <li>{t('Перевести статус', 'Move status')}</li>
        <li>{t('Работать + логировать время', 'Work + log time')}</li>
        <li>{t('Завершить → следующая фаза конвейера', 'Complete → next pipeline phase')}</li>
      </ol>

      <h4>{t('Пример потока Story', 'Example Story flow')}</h4>
      <p style={{ fontFamily: 'monospace', fontSize: 13, overflowX: 'auto' }}>
        [NEW] → [Analysis] → [Analysis Review] → [Development] → [Code Review] → [Testing] → [Test Review] → [DONE]
      </p>

      <h4>{t('Сигналы качества', 'Quality signals')}</h4>
      <table className="guide-table">
        <thead>
          <tr>
            <th>{t('Сигнал', 'Signal')}</th>
            <th>{t('Значение', 'Meaning')}</th>
            <th>{t('Действие', 'Action')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>DSR &gt; 1.0</td>
            <td>{t('Превышение оценки', 'Estimate exceeded')}</td>
            <td>{t('Расследовать причину', 'Investigate cause')}</td>
          </tr>
          <tr>
            <td>{t('Нет логов > 2 дней', 'No logs > 2 days')}</td>
            <td>{t('Задача застряла', 'Task is stuck')}</td>
            <td>{t('Проверить статус', 'Check status')}</td>
          </tr>
          <tr>
            <td>{t('WIP на лимите', 'WIP at limit')}</td>
            <td>{t('Перегрузка', 'Overload')}</td>
            <td>{t('Завершить текущие', 'Finish current tasks')}</td>
          </tr>
          <tr>
            <td>{t('Throughput падает', 'Throughput dropping')}</td>
            <td>{t('Замедление', 'Slowdown')}</td>
            <td>{t('Найти блокеры', 'Find blockers')}</td>
          </tr>
        </tbody>
      </table>

      <ScreenLinks screens={[
        { label: 'Board', path: '/', descriptionRu: 'Основная рабочая доска', descriptionEn: 'Main work board' },
        { label: 'Teams', path: '/teams', descriptionRu: 'Команды → профиль участника — нагрузка, задачи', descriptionEn: 'Teams → member profile — workload, tasks' },
        { label: 'Timeline', path: '/?view=timeline', descriptionRu: 'Прогноз и отслеживание сроков', descriptionEn: 'Forecast and deadline tracking' },
        { label: 'Metrics', path: '/metrics', descriptionRu: 'DSR, throughput, velocity', descriptionEn: 'DSR, throughput, velocity' },
      ]} />

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'Все Subtask в статусе Done', textEn: 'All Subtasks in Done status' },
          { textRu: 'Время залогировано', textEn: 'Time logged' },
        ]}
      />

      <AntiPatterns items={[
        { ru: 'Не логировать время — искажает метрики и прогнозы', en: 'Not logging time — distorts metrics and forecasts' },
        { ru: 'Брать новые задачи при превышении WIP', en: 'Taking new tasks when WIP is exceeded' },
        { ru: 'Работать над задачей с флагом (блокером)', en: 'Working on a flagged (blocked) task' },
        { ru: 'Не переводить статусы — доска не отражает реальность', en: 'Not moving statuses — board does not reflect reality' },
      ]} />
    </>
  )
}

/* ────────────────── Stage 6: E2E (Optional) ────────────────── */

function StageE2E() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Сквозная проверка всей фичи целиком.',
          'End-to-end verification of the entire feature.'
        )}
      </p>
      <p><em>{t('Этап опциональный.', 'This stage is optional.')}</em></p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Представитель роли QA"
          nameEn="QA role representative"
          actionsRu="Проводит E2E-тестирование всей фичи"
          actionsEn="Conducts E2E testing of the entire feature"
        />
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Оценивает готовность, подготавливает тестовое окружение"
          actionsEn="Assesses readiness, prepares test environment"
        />
      </div>

      <h4>{t('Когда нужен', 'When needed')}</h4>
      <ul>
        <li>{t('Фича затрагивает несколько компонентов', 'Feature spans multiple components')}</li>
        <li>{t('Есть внешние интеграции', 'External integrations involved')}</li>
        <li>{t('Высокий риск', 'High risk')}</li>
      </ul>

      <h4>{t('Когда НЕ нужен', 'When NOT needed')}</h4>
      <ul>
        <li>{t('Изолированное изменение', 'Isolated change')}</li>
        <li>{t('Уже покрыто QA-фазой конвейера', 'Already covered by pipeline QA phase')}</li>
      </ul>

      <p>
        <em>{t(
          'В текущей версии Lead Board нет отдельного статуса E2E. TL координирует этап вне доски.',
          'Current Lead Board has no separate E2E status. TL coordinates the stage outside the board.'
        )}</em>
      </p>

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'E2E пройден без критичных багов (или этап осознанно пропущен)', textEn: 'E2E passed without critical bugs (or stage was consciously skipped)' },
        ]}
      />

      <p><strong>{t('Контроль качества:', 'Quality control:')}</strong></p>
      <ScreenLinks screens={[
        { label: 'Bug Metrics', path: '/bug-metrics', descriptionRu: 'Метрики багов', descriptionEn: 'Bug metrics' },
      ]} />
    </>
  )
}

/* ────────────────── Stage 7: Acceptance (Optional) ────────────────── */

function StageAcceptance() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'PO подтверждает, что результат соответствует BRD.',
          'PO confirms the result matches the BRD.'
        )}
      </p>
      <p><em>{t('Этап опциональный.', 'This stage is optional.')}</em></p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Product Owner"
          nameEn="Product Owner"
          actionsRu="Проверяет результат по критериям приёмки"
          actionsEn="Verifies result against acceptance criteria"
        />
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Демонстрирует результат"
          actionsEn="Demonstrates the result"
        />
        <RoleCardCompact
          nameRu="Delivery Manager"
          nameEn="Delivery Manager"
          actionsRu="Координирует демо"
          actionsEn="Coordinates the demo"
        />
      </div>

      <h4>{t('Формат', 'Format')}</h4>
      <p>
        {t(
          'Демо или асинхронная проверка на staging.',
          'Demo or async check on staging.'
        )}
      </p>

      <h4>{t('Результат', 'Result')}</h4>
      <ul>
        <li><strong>{t('Принято', 'Accepted')}</strong> → Done</li>
        <li><strong>{t('Замечания', 'Remarks')}</strong> → {t('возврат в Development', 'return to Development')}</li>
      </ul>

      <p>
        <em>{t(
          'В текущей версии Lead Board нет отдельного статуса Acceptance.',
          'Current Lead Board has no separate Acceptance status.'
        )}</em>
      </p>

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'PO подтвердил (или этап осознанно пропущен)', textEn: 'PO confirmed (or stage was consciously skipped)' },
        ]}
      />
    </>
  )
}

/* ────────────────── Stage 8: Done ────────────────── */

function StageDone() {
  const { t } = useGuideLang()
  return (
    <>
      <p>
        <strong>{t('Цель:', 'Goal:')}</strong>{' '}
        {t(
          'Финализировать, задеплоить, собрать данные для ретроспективы.',
          'Finalize, deploy, collect retrospective data.'
        )}
      </p>

      <h4>{t('Роли', 'Roles')}</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <RoleCardCompact
          nameRu="Team Lead"
          nameEn="Team Lead"
          actionsRu="Переводит Epic в Done, проверяет полноту"
          actionsEn="Moves Epic to Done, verifies completeness"
        />
        <RoleCardCompact
          nameRu="Delivery Manager"
          nameEn="Delivery Manager"
          actionsRu="Фиксирует факт vs прогноз, проводит ретроспективу"
          actionsEn="Records actual vs forecast, conducts retrospective"
        />
        <RoleCardCompact
          nameRu="DevOps"
          nameEn="DevOps"
          actionsRu="Деплоит, мониторит, откатывает при необходимости"
          actionsEn="Deploys, monitors, rolls back if needed"
        />
      </div>

      <ScreenLinks screens={[
        { label: 'Board', path: '/', descriptionRu: 'Epic в статусе Done', descriptionEn: 'Epic in Done status' },
        { label: 'Timeline', path: '/?view=timeline', descriptionRu: 'Факт vs прогноз', descriptionEn: 'Actual vs forecast' },
        { label: 'Metrics', path: '/metrics', descriptionRu: 'DSR, throughput, velocity', descriptionEn: 'DSR, throughput, velocity' },
      ]} />

      <h4>{t('Данные для ретроспективы', 'Retrospective data')}</h4>
      <table className="guide-table">
        <thead>
          <tr>
            <th>{t('Вопрос', 'Question')}</th>
            <th>{t('Где смотреть', 'Where to look')}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>{t('Уложились в прогноз?', 'Met the forecast?')}</td>
            <td>Timeline</td>
          </tr>
          <tr>
            <td>{t('Точность оценок?', 'Estimate accuracy?')}</td>
            <td>Metrics</td>
          </tr>
          <tr>
            <td>{t('Где застревали?', 'Where did we get stuck?')}</td>
            <td>{t('Board: DSR > 1.0', 'Board: DSR > 1.0')}</td>
          </tr>
          <tr>
            <td>{t('Баги после релиза?', 'Post-release bugs?')}</td>
            <td>Bug Metrics</td>
          </tr>
          <tr>
            <td>{t('Команда перегружена?', 'Team overloaded?')}</td>
            <td>{t('WIP, throughput', 'WIP, throughput')}</td>
          </tr>
        </tbody>
      </table>

      <h4>{t('Ретроспектива', 'Retrospective')}</h4>
      <ul>
        <li>{t('2–3 action items → задачи в Jira', '2–3 action items → Jira tasks')}</li>
        <li>{t('Калибровка оценок', 'Calibrate estimates')}</li>
        <li>{t('Обновление процесса', 'Update process')}</li>
      </ul>

      <StageChecklist
        titleRu="Критерии перехода"
        titleEn="Transition criteria"
        items={[
          { textRu: 'Все Stories в статусе Done', textEn: 'All Stories in Done status' },
          { textRu: 'Задеплоено в продакшен', textEn: 'Deployed to production' },
          { textRu: 'Ретроспектива проведена', textEn: 'Retrospective held' },
        ]}
      />
    </>
  )
}

/* ────────────────── Export all 8 stages ────────────────── */

const ideaContent = <StageIdea />
const brdContent = <StageBrd />
const roughEstimatesContent = <StageRoughEstimates />
const planningContent = <StagePlanning />
const developmentContent = <StageDevelopment />
const e2eContent = <StageE2E />
const acceptanceContent = <StageAcceptance />
const doneContent = <StageDone />

export const stagesSections: GuideSectionData[] = [
  {
    id: 'pipeline-idea',
    titleRu: '1. Идея',
    titleEn: '1. Idea',
    contentRu: ideaContent,
    contentEn: ideaContent,
  },
  {
    id: 'pipeline-brd',
    titleRu: '2. Бизнес-требования (БТ)',
    titleEn: '2. Business Requirements (BRD)',
    contentRu: brdContent,
    contentEn: brdContent,
  },
  {
    id: 'pipeline-rough-estimates',
    titleRu: '3. Грязные оценки',
    titleEn: '3. Rough Estimates',
    contentRu: roughEstimatesContent,
    contentEn: roughEstimatesContent,
  },
  {
    id: 'pipeline-planning',
    titleRu: '4. Планирование',
    titleEn: '4. Planning',
    contentRu: planningContent,
    contentEn: planningContent,
  },
  {
    id: 'pipeline-development',
    titleRu: '5. Разработка',
    titleEn: '5. Development',
    contentRu: developmentContent,
    contentEn: developmentContent,
  },
  {
    id: 'pipeline-e2e',
    titleRu: '6. E2E (опц.)',
    titleEn: '6. E2E (opt.)',
    contentRu: e2eContent,
    contentEn: e2eContent,
  },
  {
    id: 'pipeline-acceptance',
    titleRu: '7. Приёмка (опц.)',
    titleEn: '7. Acceptance (opt.)',
    contentRu: acceptanceContent,
    contentEn: acceptanceContent,
  },
  {
    id: 'pipeline-done',
    titleRu: '8. Готово',
    titleEn: '8. Done',
    contentRu: doneContent,
    contentEn: doneContent,
  },
]
