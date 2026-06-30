package com.leadboard.insight;

import java.util.List;

/**
 * Результат брифинга готовности команды (F80, движок инсайтов).
 *
 * <p>Цифры детерминированы; LLM (через MCP) озвучивает их человеческим языком.
 * 4 линзы в порядке приоритета тимлида: планирование → загрузка → качество данных → поток.</p>
 */
public record TeamReadiness(
        Long teamId,
        Lens planning,
        Lens load,
        Lens dataQuality,
        Lens flow
) {
    /**
     * Одна линза анализа.
     *
     * @param level    уровень: {@code GREEN | YELLOW | RED}
     * @param headline краткий человекочитаемый итог
     * @param details  пояснения (почему такой уровень, на что смотреть)
     * @param issueKeys ключи задач-примеров (для ссылок), уже ограничены анти-шумом
     */
    public record Lens(
            String level,
            String headline,
            List<String> details,
            List<String> issueKeys
    ) {}
}
