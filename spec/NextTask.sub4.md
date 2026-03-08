# Subtask 4: Freeze Application Service

## Goal

Implement the server-side use case that produces a snapshot and deletes the exported records.

## Scope

The service must:

1. resolve the freeze export set
2. build snapshot payload
3. encrypt snapshot
4. return snapshot bytes
5. delete exactly the exported records

## Deliverables

- freeze application service
- unit tests around success and failure semantics

## Constraints

- do not add HTTP route yet
- deletion must happen only after snapshot creation succeeds
- avoid partial failure behavior

## Test Cases

- successful freeze returns snapshot bytes
- successful freeze deletes exactly the exported records
- empty export set follows the chosen policy consistently
- serialization failure prevents deletion
- encryption failure prevents deletion

## Done When

- freeze is testable as one application use case
- failure paths leave pod data untouched
