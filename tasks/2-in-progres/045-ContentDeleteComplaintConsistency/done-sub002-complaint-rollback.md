# sub002 - Make complaint cleanup atomic

## Goal

Prevent complaint rows from being left behind if complaint deletion fails after vector-store cleanup.

## Scope

- Update the complaint cleanup path to avoid partially deleted state.
- Prefer transactional or compensating behavior over best-effort deletion.
- Add regression tests for a failure during complaint deletion that proves the system does not end in an inconsistent state.

## Acceptance Criteria

- Failure during complaint deletion does not leave complaint rows behind in a partially deleted state.
- Vector-store cleanup and complaint-row cleanup behave consistently under failure.
- Tests fail before the fix and pass after the fix.
