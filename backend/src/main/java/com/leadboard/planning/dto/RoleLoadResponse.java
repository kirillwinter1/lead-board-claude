package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Ответ API с информацией о загрузке команды по ролям.
 */
public record RoleLoadResponse(
        Long teamId,
        LocalDate planningDate,
        int periodDays,
        Map<String, RoleLoadInfo> roles,
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
        OVERLOAD,
        NORMAL,
        IDLE,
        NO_CAPACITY
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
        ROLE_OVERLOAD,
        ROLE_IDLE,
        IMBALANCE,
        NO_CAPACITY
    }
}
