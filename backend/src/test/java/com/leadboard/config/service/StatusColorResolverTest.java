package com.leadboard.config.service;

import com.leadboard.config.entity.*;
import com.leadboard.status.StatusCategory;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class StatusColorResolverTest {

    private static final Map<String, String> ROLES = Map.of("DEV", "#10b981");

    private StatusMappingEntity m(String role, StatusKind kind, StatusCategory cat, String color) {
        StatusMappingEntity e = new StatusMappingEntity();
        e.setWorkflowRoleCode(role); e.setStatusKind(kind);
        e.setStatusCategory(cat); e.setColor(color);
        return e;
    }

    @Test void manualOverrideWins() {
        assertThat(StatusColorResolver.resolve(
                m("DEV", StatusKind.WORK, StatusCategory.IN_PROGRESS, "#123456"), ROLES))
                .isEqualTo("#123456");
    }

    @Test void workIsLightenedRoleColor() { // lighten(#10b981, 0.65)
        assertThat(StatusColorResolver.resolve(
                m("DEV", StatusKind.WORK, StatusCategory.IN_PROGRESS, null), ROLES))
                .isEqualTo("#abe7d3");
    }

    @Test void nullKindBehavesAsWork() {
        assertThat(StatusColorResolver.resolve(
                m("DEV", null, StatusCategory.IN_PROGRESS, null), ROLES))
                .isEqualTo("#abe7d3");
    }

    @Test void reviewIsRoleColorAsIs() {
        assertThat(StatusColorResolver.resolve(
                m("DEV", StatusKind.REVIEW, StatusCategory.IN_PROGRESS, null), ROLES))
                .isEqualTo("#10b981");
    }

    @Test void waitingIsGreyEvenWithRole() {
        assertThat(StatusColorResolver.resolve(
                m("DEV", StatusKind.WAITING, StatusCategory.IN_PROGRESS, null), ROLES))
                .isEqualTo("#DFE1E6");
    }

    @Test void noRoleFallsBackToCategoryDefaults() {
        assertThat(StatusColorResolver.resolve(
                m(null, null, StatusCategory.IN_PROGRESS, null), ROLES)).isEqualTo("#DEEBFF");
        assertThat(StatusColorResolver.resolve(
                m(null, null, StatusCategory.NEW, null), ROLES)).isEqualTo("#DFE1E6");
        assertThat(StatusColorResolver.resolve(
                m(null, null, StatusCategory.DONE, null), ROLES)).isEqualTo("#E3FCEF");
        assertThat(StatusColorResolver.resolve(
                m(null, null, StatusCategory.REQUIREMENTS, null), ROLES)).isEqualTo("#E6FCFF");
        assertThat(StatusColorResolver.resolve(
                m(null, null, StatusCategory.PLANNED, null), ROLES)).isEqualTo("#EAE6FF");
    }

    @Test void noRoleWaitingIsGrey() {
        assertThat(StatusColorResolver.resolve(
                m(null, StatusKind.WAITING, StatusCategory.IN_PROGRESS, null), ROLES))
                .isEqualTo("#DFE1E6");
    }

    @Test void unknownRoleCodeFallsBackToCategoryDefault() {
        assertThat(StatusColorResolver.resolve(
                m("PM", StatusKind.WORK, StatusCategory.IN_PROGRESS, null), ROLES))
                .isEqualTo("#DEEBFF");
    }
}
