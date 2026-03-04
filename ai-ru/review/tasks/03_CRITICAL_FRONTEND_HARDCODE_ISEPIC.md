# TASK: Remove hardcoded isEpic() from helpers.ts

**Priority:** Critical
**Review ID:** C4
**Files:**
- `frontend/src/components/board/helpers.ts:49-51`
- `frontend/src/components/board/BoardRow.tsx`
- `frontend/src/components/board/RoleChips.tsx`

## Проблема

```ts
export function isEpic(issueType: string): boolean {
  return issueType === 'Epic' || issueType === 'Эпик'
}
```

Тенанты с другим названием типа эпика (не "Epic"/"Эпик") будут работать некорректно на доске.

## Рекомендация

- Удалить `isEpic()` из `helpers.ts`
- В `BoardRow` и `RoleChips` использовать `useWorkflowConfig().isEpic(type)` который проверяет по DB category
