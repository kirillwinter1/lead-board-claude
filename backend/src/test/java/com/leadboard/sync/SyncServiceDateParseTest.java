package com.leadboard.sync;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jira returns datetimes with a no-colon offset ({@code +0300}); the parser must
 * accept that (regression: created/updated were all null because ISO_OFFSET_DATE_TIME
 * rejected it).
 */
class SyncServiceDateParseTest {

    @Test
    void parsesJiraOffsetWithoutColon() {
        OffsetDateTime dt = SyncService.parseOffsetDateTime("2026-01-23T16:38:52.663+0300");
        assertThat(dt).isNotNull();
        assertThat(dt.toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-01-23T13:38:52.663Z").toInstant());
    }

    @Test
    void parsesIsoOffsetWithColon() {
        OffsetDateTime dt = SyncService.parseOffsetDateTime("2026-01-23T16:38:52.663+03:00");
        assertThat(dt).isNotNull();
    }

    @Test
    void parsesWithoutFractionalSeconds() {
        OffsetDateTime dt = SyncService.parseOffsetDateTime("2026-01-23T16:38:52+0300");
        assertThat(dt).isNotNull();
    }

    @Test
    void nullAndEmptyAndGarbageReturnNull() {
        assertThat(SyncService.parseOffsetDateTime(null)).isNull();
        assertThat(SyncService.parseOffsetDateTime("")).isNull();
        assertThat(SyncService.parseOffsetDateTime("not-a-date")).isNull();
    }
}
