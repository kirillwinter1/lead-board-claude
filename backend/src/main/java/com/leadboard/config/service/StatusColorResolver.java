package com.leadboard.config.service;

import com.leadboard.config.entity.StatusKind;
import com.leadboard.config.entity.StatusMappingEntity;
import com.leadboard.status.StatusCategory;
import java.util.EnumMap;
import java.util.Map;

/**
 * F92 — единственный источник цвета статуса.
 * color(override) > роль+kind > категорийный дефолт. Формулы см. спеку F92.
 */
public final class StatusColorResolver {

    private static final double REVIEW_TINT = 0.65;      // = TIMELINE_PHASE_TINT на фронте
    private static final String WAITING_GREY = "#DFE1E6";
    private static final Map<StatusCategory, String> CATEGORY_DEFAULTS = new EnumMap<>(Map.of(
            StatusCategory.NEW, "#DFE1E6",
            StatusCategory.IN_PROGRESS, "#DEEBFF",
            StatusCategory.DONE, "#E3FCEF",
            StatusCategory.REQUIREMENTS, "#E6FCFF",
            StatusCategory.PLANNED, "#EAE6FF",
            StatusCategory.DEV_DONE, "#FFF0B3"));

    private StatusColorResolver() {}

    public static String resolve(StatusMappingEntity m, Map<String, String> roleColorsByCode) {
        if (m.getColor() != null && !m.getColor().isBlank()) return m.getColor();
        // kind (и ролевой цвет) осмыслен только у IN_PROGRESS-статусов; NEW/DONE и прочие
        // категории всегда берут категорийный дефолт — иначе NEW-статус с ролью красился
        // бы как активная работа.
        if (m.getStatusCategory() == StatusCategory.IN_PROGRESS) {
            if (m.getStatusKind() == StatusKind.WAITING) return WAITING_GREY;
            String roleColor = m.getWorkflowRoleCode() == null ? null
                    : roleColorsByCode.get(m.getWorkflowRoleCode());
            if (roleColor != null) {
                // Активная работа — насыщенный цвет роли; ревью — светлый тон (решение 22.07).
                return m.getStatusKind() == StatusKind.REVIEW ? lighten(roleColor, REVIEW_TINT) : roleColor;
            }
        }
        return CATEGORY_DEFAULTS.getOrDefault(m.getStatusCategory(), "#DFE1E6");
    }

    static String lighten(String hex, double factor) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return String.format("#%02x%02x%02x",
                (int) Math.round(r + (255 - r) * factor),
                (int) Math.round(g + (255 - g) * factor),
                (int) Math.round(b + (255 - b) * factor));
    }
}
