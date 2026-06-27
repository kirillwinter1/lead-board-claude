package com.leadboard.matrix;

import java.util.List;

/**
 * The full Eisenhower matrix for a team: orphan tasks grouped by quadrant.
 * {@code unassigned} holds tasks with no quadrant set (eisenhower_quadrant IS NULL).
 */
public record MatrixViewDto(
        List<MatrixCardDto> p1,
        List<MatrixCardDto> p2,
        List<MatrixCardDto> p3,
        List<MatrixCardDto> p4,
        List<MatrixCardDto> unassigned
) {
}
