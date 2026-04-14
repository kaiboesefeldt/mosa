Apply the package cleanup for `shared` and `server` based on the approved inventory.

This includes:

- moving misplaced shared DTOs and protocol types to the correct shared namespaces
- moving server controllers, services, stores, exceptions, and infrastructure classes out of `straightway.mosa.content` where content is not the real owner
- updating package declarations, imports, Koin wiring, and tests
- keeping routes, serialization contracts, and persistence behavior stable

If `spec/Architecture.md` changes are already obvious while moving the server/shared slices, prepare them together with the implementation instead of leaving the architecture description stale.
