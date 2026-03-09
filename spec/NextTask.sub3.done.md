# Subtask 3: Archive Crypto Layer

## Goal

Add encryption and integrity protection for snapshots using a dedicated pod archive key.

## Scope

Implement:

- archive key lookup by `keyId`
- AEAD encryption of snapshot payload
- AEAD decryption of snapshot payload
- associated data binding for the unencrypted header

## Deliverables

- crypto service for encrypt and decrypt
- archive key abstraction
- tests for successful and failing decrypt operations

## Constraints

- use `AES-GCM`
- do not add a separate signature in v1
- keep key management interface small

## Test Cases

- encrypt then decrypt returns original payload
- modified ciphertext is rejected
- modified header associated data is rejected
- wrong key is rejected
- unknown `keyId` is rejected

## Done When

- crypto behavior is covered by deterministic tests
- tampering causes restore-time failure
