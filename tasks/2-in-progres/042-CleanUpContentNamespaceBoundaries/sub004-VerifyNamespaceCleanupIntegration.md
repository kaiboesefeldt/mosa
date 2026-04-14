Verify the integrated namespace cleanup and close the remaining documentation gaps.

This last subtask must include:

1. Final integrated test
   Components tested together: `shared` contracts, server package wiring, client pod access, and any updated tests that rely on the moved namespaces.
   Preconditions: all package moves are complete, architecture notes are resolved, and the repo builds locally.
   Steps:
   - run `./gradlew check`
   - inspect the remaining files under `straightway.mosa.content` in `shared`, `server`, and `client`
   - confirm that moved contracts and services are imported from their target namespaces everywhere relevant
   Success is recognized when the full check passes and the remaining `content` package contents are defensibly content-owned.
   Failure is recognized by any failing test, compile error, stale import, or residual misplaced type that still depends on the old catch-all boundary.
   Automation level: fully automated inside the repo, plus a manual inspection of the final package inventory.

2. Documentation review
   Review `spec` and update `spec/Architecture.md` as needed so the documented responsibility boundaries match the implemented package structure.
   Review `server/src/main/resources/clearterms` and `server/src/main/resources/doc/serve` for namespace references or operational guidance that became stale because of the cleanup.

3. License review
   Confirm that the namespace cleanup introduced no new third-party dependencies. If any dependency was added unexpectedly, review its license compatibility with `LICENSE` and document the result.
