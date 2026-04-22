# Content delete leaves complaint data inconsistent

## Problem

The current content deletion flow is not fully consistent across content and complaint storage.

Two issues were identified in the branch changes:

1. Deleting a root post only collects one page of replies, so replies beyond the first page can remain undeleted.
2. `deleteAllForUser()` removes vector-store content and then deletes complaints without rollback protection, so complaint rows can remain if complaint deletion fails.

## Goal

Make content deletion and complaint cleanup behave consistently:

- deleting a root post must remove all replies, not just the first page
- complaint deletion must not leave the system in a partially deleted state
- the behavior should be covered by tests

## Constraints

- Keep the fix localized to the content deletion path and complaint cleanup integration.
- Do not change the public API unless necessary.
- Preserve existing authorization rules.
- Add regression tests for the paging and rollback behavior.
