// Shared sentinel keys/labels used across planning columns (Backlog, InQuarter).
// Map keys cannot be null for predictable lookup, so we use sentinel strings
// everywhere a non-null `string` is required (Map.get / Set.has / filter state).

export const NO_PROJECT_KEY = '__no_project__'
export const NO_PROJECT_LABEL = 'No project'
export const UNASSIGNED_KEY = '__unassigned__'
export const UNASSIGNED_LABEL = 'Unassigned'
