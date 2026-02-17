-- Seed: Business template
INSERT INTO rice_templates (id, name, code, strategic_weight) VALUES (1, 'Business', 'business', 1.0);
-- Seed: Technical template
INSERT INTO rice_templates (id, name, code, strategic_weight) VALUES (2, 'Technical', 'technical', 0.8);

-- ============================================================
-- BUSINESS: Reach подкритерии
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (1, 1, 'REACH', 'Тип фичи', 'Переиспользуемая или специфичная', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(1, 'Продуктовая (переиспользуемая)', 3, 1),
(1, 'Специфичная', 1, 2);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (2, 1, 'REACH', 'Кол-во пользователей', 'Сколько пользователей затронет', 'SINGLE', 2);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(2, '< 100', 1, 1),
(2, '100-500', 3, 2),
(2, '500-3000', 5, 3),
(2, '3000+', 7, 4);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (3, 1, 'REACH', 'Кол-во клиентов/команд', 'Сколько клиентов или команд затронет', 'SINGLE', 3);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(3, '< 5', 1, 1),
(3, '5-20', 2, 2),
(3, '20-50', 3, 3),
(3, '50+', 5, 4);

-- ============================================================
-- BUSINESS: Impact подкритерии
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (4, 1, 'IMPACT', 'Инициатор запроса', 'Кто инициировал запрос', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(4, 'Внешний клиент', 3, 1),
(4, 'Внутренний', 1, 2);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (5, 1, 'IMPACT', 'Тип задачи', 'Какой категории задача', 'MULTI', 2);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(5, 'Регуляторное требование', 5, 1),
(5, 'Импортозамещение', 5, 2),
(5, 'Развитие продукта', 1, 3),
(5, 'Снижение рисков', 3, 4);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (6, 1, 'IMPACT', 'Соответствие целям', 'Совпадение с целями команды и компании', 'MULTI', 3);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(6, 'Цели команды', 1, 1),
(6, 'Цели компании', 5, 2);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (7, 1, 'IMPACT', 'Экономия FTE (руб/мес)', 'Экономия в рублях в месяц', 'SINGLE', 4);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(7, 'Нет', 0, 1),
(7, '< 100K', 1, 2),
(7, '100-500K', 2, 3),
(7, '500K-1M', 3, 4),
(7, '1-3M', 4, 5),
(7, '> 3M', 5, 6);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (8, 1, 'IMPACT', 'Финансовые потери при неисполнении', 'Потенциальные потери при невыполнении', 'SINGLE', 5);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(8, 'Нет', 0, 1),
(8, '< 100K', 1, 2),
(8, '100-500K', 2, 3),
(8, '500K-1M', 3, 4),
(8, '1-3M', 4, 5),
(8, '> 3M', 5, 6);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (9, 1, 'IMPACT', 'Лояльность пользователей', 'Как влияет на лояльность', 'MULTI', 6);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(9, 'Удобство использования', 5, 1),
(9, 'Ускорение процессов', 2, 2),
(9, 'Сокращение ошибок', 2, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (10, 1, 'IMPACT', 'Окупаемость', 'Срок окупаемости', 'SINGLE', 7);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(10, '> 1 года', 1, 1),
(10, '< 1 года', 3, 2),
(10, '< 6 мес', 5, 3);

-- ============================================================
-- BUSINESS: Confidence
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (11, 1, 'CONFIDENCE', 'Уверенность в оценке', 'Насколько надёжны данные для оценки', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(11, 'High — есть данные, метрики подтверждены', 1.0, 1),
(11, 'Medium — есть данные, но не по всем критериям', 0.8, 2),
(11, 'Low — оценки предположительные', 0.6, 3),
(11, 'Very Low — данные отсутствуют или неподтверждены', 0.4, 4);

-- ============================================================
-- BUSINESS: Effort
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (12, 1, 'EFFORT', 'Размер (T-shirt)', 'Ориентировочный размер задачи. После появления реальной оценки обновится автоматически.', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(12, 'S (Small)', 1, 1),
(12, 'M (Medium)', 2, 2),
(12, 'L (Large)', 4, 3),
(12, 'XL (Extra Large)', 8, 4);

-- ============================================================
-- TECHNICAL: Reach подкритерии
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (13, 2, 'REACH', 'Scope влияния', 'Масштаб затронутых систем', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(13, 'Один сервис', 1, 1),
(13, 'Несколько сервисов', 3, 2),
(13, 'Вся система', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (14, 2, 'REACH', 'Частота проблемы', 'Как часто проявляется проблема', 'SINGLE', 2);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(14, 'Редко', 1, 1),
(14, 'Еженедельно', 3, 2),
(14, 'Ежедневно', 5, 3);

-- ============================================================
-- TECHNICAL: Impact подкритерии
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (15, 2, 'IMPACT', 'Влияние на стабильность', 'Как влияет на стабильность системы', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(15, 'Низкое', 1, 1),
(15, 'Среднее', 3, 2),
(15, 'Высокое', 5, 3),
(15, 'Критичное', 10, 4);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (16, 2, 'IMPACT', 'Влияние на производительность', 'Как влияет на перформанс', 'SINGLE', 2);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(16, 'Нет', 0, 1),
(16, 'Небольшое', 2, 2),
(16, 'Существенное', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (17, 2, 'IMPACT', 'Ускорение разработки', 'Экономия времени разработчиков', 'SINGLE', 3);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(17, 'Нет', 0, 1),
(17, 'Немного', 2, 2),
(17, 'Существенно', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (18, 2, 'IMPACT', 'Устранение техдолга', 'Уровень техдолга', 'SINGLE', 4);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(18, 'Косметический', 1, 1),
(18, 'Архитектурный', 3, 2),
(18, 'Критичный', 5, 3);

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (19, 2, 'IMPACT', 'Риск безопасности', 'Влияние на безопасность', 'SINGLE', 5);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(19, 'Нет', 0, 1),
(19, 'Низкий', 3, 2),
(19, 'Высокий', 5, 3);

-- ============================================================
-- TECHNICAL: Confidence (same structure as Business)
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (20, 2, 'CONFIDENCE', 'Уверенность в оценке', 'Насколько надёжны данные для оценки', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(20, 'High — есть данные, метрики подтверждены', 1.0, 1),
(20, 'Medium — есть данные, но не по всем критериям', 0.8, 2),
(20, 'Low — оценки предположительные', 0.6, 3),
(20, 'Very Low — данные отсутствуют или неподтверждены', 0.4, 4);

-- ============================================================
-- TECHNICAL: Effort (same T-shirt structure)
-- ============================================================

INSERT INTO rice_criteria (id, template_id, parameter, name, description, selection_type, sort_order)
VALUES (21, 2, 'EFFORT', 'Размер (T-shirt)', 'Ориентировочный размер задачи. После появления реальной оценки обновится автоматически.', 'SINGLE', 1);

INSERT INTO rice_criteria_options (criteria_id, label, score, sort_order) VALUES
(21, 'S (Small)', 1, 1),
(21, 'M (Medium)', 2, 2),
(21, 'L (Large)', 4, 3),
(21, 'XL (Extra Large)', 8, 4);

-- Reset sequences
SELECT setval('rice_templates_id_seq', (SELECT MAX(id) FROM rice_templates));
SELECT setval('rice_criteria_id_seq', (SELECT MAX(id) FROM rice_criteria));
SELECT setval('rice_criteria_options_id_seq', (SELECT MAX(id) FROM rice_criteria_options));
