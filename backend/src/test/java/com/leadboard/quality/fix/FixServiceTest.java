package com.leadboard.quality.fix;

import com.leadboard.quality.DataQualityRule;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixRequest;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixServiceTest {

    @Mock private FixSupport support;
    @Mock private SyncService syncService;

    private JiraIssueEntity issue;

    @BeforeEach
    void setUp() {
        issue = new JiraIssueEntity();
        issue.setIssueKey("LB-1");
        lenient().when(support.load("LB-1")).thenReturn(Optional.of(issue));
        lenient().when(support.sync()).thenReturn(syncService);
    }

    private FixHandler handler(DataQualityRule rule, boolean local, FixResult applyResult) {
        FixHandler h = mock(FixHandler.class);
        when(h.rule()).thenReturn(rule);
        lenient().when(h.local()).thenReturn(local);
        lenient().when(h.preview(any())).thenReturn(
                FixPreview.builder("LB-1", rule, "TRANSITION", "t").build());
        lenient().when(h.apply(any(), any(), any())).thenReturn(applyResult);
        return h;
    }

    @Test
    void isFixableCoversHandlersAndRice() {
        FixHandler h = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, null);
        FixService service = new FixService(List.of(h), support);

        assertTrue(service.isFixable(DataQualityRule.EPIC_NO_DUE_DATE));
        assertTrue(service.isFixable(DataQualityRule.RICE_MISSING_ASSESSMENT)); // no handler, still fixable
        assertFalse(service.isFixable(DataQualityRule.EPIC_OVERDUE));
        assertFalse(service.isFixable(null));
    }

    @Test
    void duplicateHandlerRulesAreRejected() {
        FixHandler h1 = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, null);
        FixHandler h2 = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, null);
        assertThrows(IllegalStateException.class, () -> new FixService(List.of(h1, h2), support));
    }

    @Test
    void previewReturnsRiceFormWithoutHandler() {
        FixService service = new FixService(List.of(), support);
        FixPreview preview = service.preview("LB-1", DataQualityRule.RICE_MISSING_ASSESSMENT);
        assertEquals("RICE_FORM", preview.fixType());
        assertTrue(preview.applicable());
    }

    @Test
    void previewThrowsForUnknownRule() {
        FixService service = new FixService(List.of(), support);
        assertThrows(IllegalArgumentException.class,
                () -> service.preview("LB-1", DataQualityRule.EPIC_OVERDUE));
    }

    @Test
    void previewMarksNotApplicableWhenViolationGone() {
        FixHandler h = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, null);
        when(support.stillViolated(issue, DataQualityRule.EPIC_NO_DUE_DATE)).thenReturn(false);
        FixService service = new FixService(List.of(h), support);

        FixPreview preview = service.preview("LB-1", DataQualityRule.EPIC_NO_DUE_DATE);
        assertFalse(preview.applicable());
        assertNotNull(preview.notApplicableReason());
    }

    @Test
    void applyDispatchesAndReSyncsUpdatedIssues() {
        FixResult result = FixResult.ok("done", List.of("LB-1"));
        FixHandler h = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, result);
        when(support.stillViolated(issue, DataQualityRule.EPIC_NO_DUE_DATE)).thenReturn(true);
        FixService service = new FixService(List.of(h), support);

        FixResult out = service.apply(new FixRequest("LB-1", "EPIC_NO_DUE_DATE", null, Map.of()));

        assertTrue(out.success());
        verify(h).apply(eq(issue), any(), any());
        verify(syncService).syncSingleIssue("LB-1");
    }

    @Test
    void applyDoesNotSyncLocalFixes() {
        FixResult result = FixResult.ok("done", List.of("LB-1"));
        FixHandler h = handler(DataQualityRule.EPIC_NO_TEAM, true, result);
        when(support.stillViolated(issue, DataQualityRule.EPIC_NO_TEAM)).thenReturn(true);
        FixService service = new FixService(List.of(h), support);

        service.apply(new FixRequest("LB-1", "EPIC_NO_TEAM", null, Map.of()));

        verify(syncService, never()).syncSingleIssue(any());
    }

    @Test
    void applyThrowsConflictWhenViolationGone() {
        FixHandler h = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, null);
        when(support.stillViolated(issue, DataQualityRule.EPIC_NO_DUE_DATE)).thenReturn(false);
        FixService service = new FixService(List.of(h), support);

        assertThrows(FixConflictException.class,
                () -> service.apply(new FixRequest("LB-1", "EPIC_NO_DUE_DATE", null, Map.of())));
        verify(h, never()).apply(any(), any(), any());
    }

    @Test
    void applyThrowsForUnknownIssue() {
        when(support.load("LB-404")).thenReturn(Optional.empty());
        FixHandler h = handler(DataQualityRule.EPIC_NO_DUE_DATE, false, null);
        FixService service = new FixService(List.of(h), support);

        assertThrows(IllegalArgumentException.class,
                () -> service.apply(new FixRequest("LB-404", "EPIC_NO_DUE_DATE", null, Map.of())));
    }
}
