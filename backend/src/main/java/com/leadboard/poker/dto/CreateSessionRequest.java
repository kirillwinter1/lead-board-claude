package com.leadboard.poker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
    @NotNull Long teamId,
    @NotBlank String epicKey
) {}
