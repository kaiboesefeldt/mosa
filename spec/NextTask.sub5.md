# Subtask 5: Restore Application Service

## Goal

Implement the server-side use case that validates, decrypts, and restores a snapshot.

## Scope

The service must:

1. parse snapshot header
2. verify pod binding
3. verify expiration
4. resolve archive key
5. decrypt payload
6. validate manifest and records
7. restore records

## Deliverables

- restore application service
- validation rules
- unit tests for accepted and rejected snapshots

## Constraints

- do not add HTTP route yet
- restore must not import invalid partial data
- validation should happen before write operations where practical

## Test Cases

- valid snapshot restores all records
- expired snapshot is rejected
- snapshot for another pod is rejected
- unknown `keyId` is rejected
- modified ciphertext is rejected
- duplicate ids are rejected
- reply without existing root is rejected

## Done When

- restore behavior is deterministic and well covered
- invalid snapshots leave the pod unchanged
