# sub001 - Delete all replies for root posts

## Goal

Fix root-post deletion so it removes replies from every page, not just the first page.

## Scope

- Traverse all reply pages when collecting descendants for a root post.
- Keep existing authorization and deletion behavior unchanged.
- Add regression tests that include replies beyond page one.

## Acceptance Criteria

- A root post with replies spanning multiple pages is fully deleted.
- The implementation no longer relies on first-page-only reply collection.
- Tests fail before the fix and pass after the fix.
