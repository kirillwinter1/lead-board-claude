package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ответ API с информацией о загрузке команды по ролям.
 * Показывает capacity, assigned hours и utilization для каждой роли (SA/DEV/QA).
 */
public record RoleLoadResponse(
        Long teamId,
        LocalDate planningDate,
        int periodDays,
        RoleLoadInfo sa,
        RoleLoadInfo dev,
        RoleLoadInfo qa,
        List<RoleLoadAlert> alerts
) {

    /**
     * Информация о загрузке для одной роли.
     */
    public record RoleLoadInfo(
            int memberCount,
            BigDecimal totalCapacityHours,
            BigDecimal totalAssignedHours,
            BigDecimal utilizationPercent,
            UtilizationStatus status
    ) {
        public static RoleLoadInfo empty() {
            return new RoleLoadInfo(
                    0,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    UtilizationStatus.NO_CAPACITY
            );
        }
    }

    /**
     * Статус утилизации роли.
     */
    public enum UtilizationStatus {
        OVERLOAD,    // >100%
        NORMAL,      // 50-100%
        IDLE,        // <50%
        NO_CAPACITY  // нет людей в роли
    }

    /**
     * Алерт о проблеме с загрузкой.
     */
    public record RoleLoadAlert(
            AlertType type,
            String role,
            String message
    ) {}

    /**
     * Типы алертов.
     */
    public enum AlertType {
        ROLE_OVERLOAD,  // Роль перегружена (>100%)
        ROLE_IDLE,      // Роль простаивает (<50%)
        IMBALANCE,      // Дисбаланс между ролями (>40% разница)
        NO_CAPACITY     // Нет людей в роли
    }
}
