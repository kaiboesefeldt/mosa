# Subtask 5: Restore Application Service

## Goal

Implement the server-side use case that validates, decrypts, and restores a snapshot.

## Scope

The service must:

1. parse snapshot header
2. reject unknown format versions before decrypt
3. verify pod binding before decrypt
4. verify expiration before decrypt
5. enforce configured snapshot size limits before decrypt
6. resolve archive key
7. decrypt payload
8. validate manifest and records
9. restore records

## Deliverables

- restore application service
- validation rules
- snapshot size policy
- unit tests for accepted and rejected snapshots

## Constraints

- do not add HTTP route yet
- restore must not import invalid partial data
- header validation must happen before decryption where practical
- validation should happen before write operations where practical
- restore must not fully trust encrypted input; normal content and size validation still applies after decrypt

## Test Cases

- valid snapshot restores all records
- expired snapshot is rejected
- snapshot for another pod is rejected
- unknown format version is rejected
- unknown `keyId` is rejected
- modified ciphertext is rejected
- oversized snapshot is rejected
- manifest record count over configured limits is rejected
- duplicate ids are rejected
- reply without existing root is rejected
- any validation failure leaves pod data unchanged

## Done When

- restore behavior is deterministic and well covered
- invalid snapshots leave the pod unchanged
