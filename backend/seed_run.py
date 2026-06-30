#!/usr/bin/env python3
"""Generate a realistic backlog in Jira project LB across both teams.

Structure per team: 3 epics x 3 stories x 3 role-subtasks (SA/DEV/QA, estimated),
plus 3 orphan stories (with role-subtasks) and 3 orphan bugs. Statuses are spread:
~1/3 of stories+subtasks Done, ~1/3 in-progress, ~1/3 New.

Every issue carries label seed-2026-06-28. Created keys are appended to
/tmp/seed_keys.txt as we go so a partial run is still cleanable.
"""
import seed_lib as L

KEYLOG = '/tmp/seed_keys.txt'
keyfile = open(KEYLOG, 'a')
def log_key(key, kind):
    keyfile.write(f"{key}\t{kind}\n"); keyfile.flush()

DONE = {'Готово'}
ROLE_SUBTASKS = [('Аналитика', 'SA', '8h'), ('Разработка', 'DEV', '16h'), ('Тестирование', 'QA', '8h')]

EPIC_THEMES = {
  'Команда победителей': [
    ('Онбординг новых пользователей', ['Мастер первого входа', 'Импорт данных из CSV', 'Подсказки в интерфейсе']),
    ('Биллинг и подписки', ['Тарифные планы', 'Интеграция с платёжкой', 'История платежей']),
    ('Мобильное приложение', ['Авторизация по биометрии', 'Офлайн-режим', 'Push-уведомления']),
  ],
  'Красивые': [
    ('Аналитический дашборд', ['Виджеты метрик', 'Экспорт в Excel', 'Настраиваемые отчёты']),
    ('Поиск и фильтры', ['Полнотекстовый поиск', 'Сохранённые фильтры', 'Автодополнение']),
    ('Уведомления', ['Email-дайджесты', 'Настройки подписок', 'Шаблоны писем']),
  ],
}
ORPHAN_STORIES = {
  'Команда победителей': ['Рефакторинг модуля авторизации', 'Миграция логов на новый формат', 'Оптимизация SQL-запросов дашборда'],
  'Красивые': ['Удаление мёртвого кода в API', 'Обновление зависимостей фронтенда', 'Покрытие тестами модуля отчётов'],
}
ORPHAN_BUGS = {
  'Команда победителей': ['Падает экспорт при пустом периоде', 'Неверная сортировка по дате', 'Утечка памяти в воркере sync'],
  'Красивые': ['Дублируются строки в отчёте', 'Фильтр сбрасывается при перезагрузке', '500 при загрузке аватара > 5MB'],
}

created = {'Эпик':0,'История':0,'subtask':0,'Баг':0}
def mk(*a, **kw):
    s, d = L.create(*a, **kw)
    if s >= 400:
        print('  ERR', s, str(d)[:300]); return None
    return d['key']

for team, themes in EPIC_THEMES.items():
    print(f"\n=== TEAM: {team} ===")
    for ei, (epic_name, stories) in enumerate(themes):
        ek = mk('Эпик', epic_name, f'Эпик: {epic_name}', team_name=team)
        if not ek: continue
        created['Эпик']+=1; log_key(ek,'epic'); print(f"epic {ek}: {epic_name}")
        # status policy per epic index: 0 -> done, 1 -> in progress, 2 -> new
        policy = ['done','progress','new'][ei % 3]
        for si, story_name in enumerate(stories):
            sk = mk('История', story_name, f'История: {story_name}', team_name=team, parent=ek)
            if not sk: continue
            created['История']+=1; log_key(sk,'story')
            subkeys = []
            for (stype, role, est) in ROLE_SUBTASKS:
                subk = mk(stype, f'{role}: {story_name}', f'{stype} по «{story_name}»', parent=sk, estimate=est)
                if subk: created['subtask']+=1; log_key(subk,'subtask'); subkeys.append(subk)
            # apply statuses
            if policy == 'done':
                for k in subkeys: L.transition_to(k, DONE)
                L.transition_to(sk, DONE)
            elif policy == 'progress':
                # first subtask done, second in progress, story moved forward once
                if subkeys: L.transition_to(subkeys[0], DONE)
                if len(subkeys) > 1: L.one_forward(subkeys[1])
                L.one_forward(sk)
            print(f"  story {sk} [{policy}] +{len(subkeys)} subtasks")
    # orphan stories with role-subtasks (left New -> for the matrix)
    for story_name in ORPHAN_STORIES[team]:
        sk = mk('История', story_name, f'Сирота: {story_name}', team_name=team)
        if not sk: continue
        created['История']+=1; log_key(sk,'orphan-story'); print(f"orphan story {sk}: {story_name}")
        for (stype, role, est) in ROLE_SUBTASKS:
            subk = mk(stype, f'{role}: {story_name}', f'{stype} по «{story_name}»', parent=sk, estimate=est)
            if subk: created['subtask']+=1; log_key(subk,'subtask')
    # orphan bugs (for Zero Bug Policy) -> left New (open)
    for bug_name in ORPHAN_BUGS[team]:
        bk = mk('Баг', bug_name, f'Баг: {bug_name}', team_name=team)
        if bk: created['Баг']+=1; log_key(bk,'orphan-bug'); print(f"orphan bug {bk}: {bug_name}")

keyfile.close()
total = sum(created.values())
print(f"\n=== DONE. Created {total} issues: {created} ===")
print(f"Keys logged to {KEYLOG}")
