`straightway.mosa.content` currently mixes true content concerns with adjacent responsibilities that belong to other bounded areas. The package has become a catch-all for contracts and server classes that are not about publishing, reading, querying, signing, or deleting content itself.

The goal of this task is to restore clear namespace boundaries across `shared`, `server`, and `client` without changing externally visible behavior. Types and implementations should live in packages that match their actual responsibility instead of their historical landing spot.

This cleanup must be driven by responsibility, not by file-moving for its own sake. Pure content contracts and implementations should remain under `straightway.mosa.content`. Identity-specific, snapshot-specific, and shared-infrastructure concerns should be moved to namespaces that reflect those concerns.

Current candidates that need explicit review include at least:

- identity moderation contracts and services currently under `straightway.mosa.content`
- identity deletion contracts and services currently under `straightway.mosa.content`
- privacy snapshot contracts that currently sit in the root content package although server implementation already has a dedicated snapshot subpackage
- blocked-identity and structured-state classes that may currently be hidden under content for convenience rather than because they are true content logic

The task includes at least these outcomes:

- inventory all classes, DTOs, exceptions, stores, controllers, and client services currently living under `straightway.mosa.content` and classify whether they are truly content-owned
- define the target namespace for each misplaced element before moving code
- move shared contracts, server code, and client code to the appropriate namespaces with minimal behavioral change
- update imports, DI wiring, tests, and package declarations consistently across all modules
- keep serialization names, HTTP routes, payload structure, and persistent data behavior unchanged unless a concrete mismatch is discovered and documented
- update `spec/Architecture.md` so it no longer blesses `straightway.mosa.content` as the home for responsibilities that have been split out

Important constraints:

- do not create a new generic dumping-ground namespace
- do not move code out of `shared` unless it is not a real cross-module contract
- keep complaint handling under its current namespace unless the inventory shows a clear responsibility mismatch
- preserve compatibility of client-server contracts and existing persisted data
- prefer small, coherent package moves over a broad rename that hides unresolved ownership questions

Acceptance:

- the remaining contents of `straightway.mosa.content` are defensibly content-specific
- misplaced classes and contracts are moved to namespaces that match their responsibility
- `shared`, `server`, and `client` use the updated packages consistently
- `spec/Architecture.md` reflects the corrected boundaries
- `./gradlew check` passes after the package cleanup
