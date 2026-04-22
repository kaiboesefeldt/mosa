# Architecture Notes

## Split Rationale

This task has two distinct failure modes with different code paths:

1. root-post deletion stops after the first page of replies
2. complaint cleanup can partially succeed when complaint deletion fails after vector-store cleanup

Those concerns should be fixed in separate subtasks so each can be implemented and tested without coupling the paging traversal change to the complaint cleanup rollback behavior.

## Boundaries

- Keep the reply traversal fix inside the content deletion path.
- Keep the rollback protection inside the complaint cleanup integration.
- Do not widen the public API unless a test proves it is necessary.
- Preserve authorization and existing deletion semantics outside the identified failure modes.

## Subtask Plan

### sub001 - Delete all replies for root posts

- Inspect and fix the root-post reply collection logic so it drains all pages, not only the first page.
- Add regression coverage for replies beyond the first page.

### sub002 - Make complaint cleanup atomic

- Wrap complaint cleanup so vector-store deletion and complaint-row deletion do not leave the system partially deleted.
- Add regression coverage for failure after vector-store cleanup and verify complaint rows are not left behind.

## Verification Expectations

- Each subtask should include targeted tests for the behavior it changes.
- Run the expensive full verification only once near the end of the task, after both subtasks are complete.

## Current Architectural Guidance

- Keep `sub001` confined to descendant collection in the content deletion path. The structural fix is traversal completeness, not a broader deletion API redesign.
- Keep `sub002` confined to complaint cleanup consistency. If rollback is needed, prefer a local compensating step or a narrow transactional boundary over spreading retry logic into unrelated services.
- Preserve existing server-side authorization and deletion semantics. Any behavior change outside the two identified failure modes should be treated as scope creep.
- No shared-model or client-side changes are expected for this task. If an implementation ends up needing a public contract change, record that explicitly before making it.

## Current Finding

- `sub002` still needs the exception to reach the caller before the HTTP response is finalized. The failed integration run shows the request currently returns `200 OK`, which means the compensating cleanup boundary is not yet coupled tightly enough to request completion.
- Recommended direction: keep the rollback logic local to complaint cleanup, but make sure the failure path is surfaced in the same service/request flow that performs the deletion so the handler does not commit success before cleanup finishes.
