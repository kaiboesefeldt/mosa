# Subtask 1: Freeze Selection Rules

## Goal

Implement and test the business logic that determines which records belong into a freeze snapshot.

## Scope

The selection must include:

- all root posts authored by the authenticated user
- all replies authored by the authenticated user
- all replies authored by other users on root posts authored by the authenticated user

The selection must exclude:

- root posts authored by other users
- replies by other users on root posts authored by other users
- unrelated content that only happens to mention the user

## Deliverables

- a small business service for resolving the freeze export set
- unit tests that cover all inclusion and exclusion rules

## Constraints

- implement through public behavior
- keep the logic independent from HTTP
- do not introduce crypto yet
- do not introduce file format concerns yet

## Test Cases

- selecting one own root post includes that post
- selecting one own reply includes that reply
- selecting a foreign reply on an own root includes that foreign reply
- selecting a foreign root excludes that root
- selecting a foreign reply on a foreign root excludes that reply
- mixed content returns exactly the expected record ids

## Done When

- the selection logic is fully covered by unit tests
- the selected set is stable and deterministic
