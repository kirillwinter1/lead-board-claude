package com.leadboard.planning.dto;

import java.time.OffsetDateTime;

public record RecalculateResponse(
        int recalculated,
        OffsetDateTime timestamp
) {}
