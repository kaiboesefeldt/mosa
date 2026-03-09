# Subtask 4: Freeze Application Service

## Goal

Implement the server-side use case that produces a snapshot and deletes the exported records.

## Scope

The service must:

1. resolve the freeze export set
2. build snapshot payload
3. resolve the active archive key
4. generate a fresh random nonce on the server
5. derive `createdAt` and `expiresAt` from server-side policy
6. encrypt snapshot
7. return snapshot bytes
8. delete exactly the exported records

## Deliverables

- freeze application service
- nonce generation abstraction
- active archive key abstraction
- unit tests around success and failure semantics

## Constraints

- do not add HTTP route yet
- the client must not supply `keyId`, `createdAt`, `expiresAt`, or `nonce`
- every snapshot must use a fresh nonce for the selected archive key
- new snapshots must always use the current active archive key
- deletion must happen only after snapshot creation succeeds
- avoid partial failure behavior

## Test Cases

- successful freeze returns snapshot bytes
- successful freeze deletes exactly the exported records
- successful freeze uses the active archive key id in the header
- successful freeze generates a fresh nonce on the server
- successful freeze derives expiration from the configured policy
- empty export set follows the chosen policy consistently
- serialization failure prevents deletion
- encryption failure prevents deletion
- nonce generation failure prevents deletion
- active key resolution failure prevents deletion

## Done When

- freeze is testable as one application use case
- failure paths leave pod data untouched
