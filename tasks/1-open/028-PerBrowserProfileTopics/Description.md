## Problem

Browser-local topic state can leak between local identities in the same browser.
This can cause one identity to see, reuse, or overwrite topics that belong to a
different identity.

## Goal

Ensure that each identity has its own browser state for topics, including its
own topic list, so switching identities does not mix their local topic data.

Also assess whether any other browser-local identity properties should be
scoped per identity for the same reason.

## Constraints

- Topic state must be separated per identity in client state and browser
  persistence.
- Switching identities in the same browser must show only the topic data for
  the active identity.
- Actions on one identity's topics must not affect another identity's topics in
  the same browser.
- The separation must survive browser reload or restart where topic data is
  already persisted locally.
- Existing topic display, selection, and navigation flows must remain coherent.

## Expected Outcome

- Each identity in the same browser has its own topic list.
- A topic created or visible in one identity does not appear in another
  identity just because the browser instance is shared.
- Topic identity separation survives reloads.
- Any additional browser-local identity-specific properties that should be
  separated are identified clearly enough for follow-up work if needed.
