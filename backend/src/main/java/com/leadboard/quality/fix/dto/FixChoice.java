package com.leadboard.quality.fix.dto;

import java.util.List;

/**
 * A mutually-exclusive way to resolve a violation (e.g. ASSIGNEE_NOT_IN_TEAM:
 * "reassign" vs "add to team"). The user picks one choice by {@code id}, which is
 * echoed back in {@link FixRequest#choiceId()}.
 *
 * @param id      stable identifier of the choice
 * @param label   human label
 * @param changes preview of changes this choice would make
 * @param inputs  inputs this choice requires (may be empty)
 */
public record FixChoice(
        String id,
        String label,
        List<FixChange> changes,
        List<FixInput> inputs
) {}
