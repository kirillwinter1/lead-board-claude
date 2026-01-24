package com.leadboard.planning.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Ответ с историей WIP для построения графика.
 */
public record WipHistoryResponse(
        Long teamId,
        LocalDate from,
        LocalDate to,
        List<WipDataPoint> dataPoints
) {
    /**
     * Точка данных WIP на определённую дату.
     */
    public record WipDataPoint(
            LocalDate date,
            Integer teamLimit,
            Integer teamCurrent,
            Integer saLimit,
            Integer saCurrent,
            Integer devLimit,
            Integer devCurrent,
            Integer qaLimit,
            Integer qaCurrent,
            Integer inQueue,
            Integer totalEpics
    ) {
        /**
         * Возвращает процент утилизации командного WIP.
         */
        public int teamUtilizationPercent() {
            if (teamLimit == null || teamLimit == 0) return 0;
            return Math.round((float) teamCurrent / teamLimit * 100);
        }
    }
}
