# Snapshot Freeze/Restore Minimal Design

## Goal

Implement a simple decentralized processing restriction workflow:

1. A user can freeze processing of their content on a pod.
2. The pod exports the relevant content into an encrypted snapshot file.
3. The user downloads and stores that file.
4. The exported content is deleted from the pod.
5. The user can later upload the snapshot to the same pod to restore the content.

This first version intentionally optimizes for simplicity over maximal recoverability.

## Scope

The snapshot must contain:

- all root posts authored by the user
- all replies authored by the user
- all replies authored by other users on root posts authored by the user

The snapshot must not contain unrelated third-party content.

## Product Rules

- A snapshot is restorable only on the same pod that created it.
- A snapshot is restorable only for a limited time.
- After the snapshot has expired, restore must fail.
- After successful freeze, the exported content is deleted from the pod.
- If the user loses the snapshot file, the data is lost.
- If the pod loses the required archive key, restore is impossible.

## Security Model

Use a dedicated persistent pod archive key for snapshot encryption.

- Do not reuse JWT signing keys or other existing pod keys.
- Use AEAD encryption so encryption and integrity protection are handled together.
- Do not add a separate signature in v1.

Recommended primitive:

- `AES-GCM`

Meaning:

- the user cannot read the snapshot contents
- the user cannot modify the snapshot without restore failing
- if the archive key is compromised, old snapshots encrypted with that key can be read and forged

## Key Management

Keep the key lifecycle intentionally small and explicit.

- At most two archive keys are stored on the pod at the same time.
- Each key has a stable `keyId`, for example `archive-2026`.
- New snapshots are always encrypted with the current active key.
- A snapshot carries its `keyId`.
- A snapshot also carries an explicit `expiresAt`.
- Restore must fail if `now > expiresAt`.
- Old keys may be deleted once no non-expired snapshot should still depend on them.

Practical policy for v1:

- target restore validity: one year after snapshot creation
- keep current and previous archive key
- if a key is compromised, remove it immediately and generate a new active key
- snapshots depending on a removed key become unrestorable

## Snapshot File Format

Use one binary snapshot file, for example:

- `mosa-snapshot-YYYY-MM-DD.bin`

Structure:

1. unencrypted header
2. encrypted payload

### Unencrypted Header

The header should be minimal and machine-readable:

- `formatVersion`
- `podId`
- `keyId`
- `createdAt`
- `expiresAt`
- `nonce`

The header must be included as AEAD associated data so it cannot be modified undetected.

### Encrypted Payload

The payload contains:

- `manifest.json`
- content records

`manifest.json` should contain:

- `ownerGid`
- `recordCount`
- `archivePolicyVersion`

For content records, prefer `NDJSON` over CSV in v1 because parsing and validation are simpler and less fragile.

Each record should contain:

- `id`
- `text`
- `authorGid`
- `storedAt`
- `rootId`

## API Proposal

### `POST /v1/privacy/freeze`

Authenticated endpoint.

Behavior:

1. Resolve export set for authenticated user.
2. Resolve the current active archive key.
3. Generate a fresh random nonce on the server.
4. Derive `createdAt` and `expiresAt` from server-side policy.
5. Build snapshot payload.
6. Encrypt payload with the active archive key using AEAD.
7. Return snapshot bytes as file download.
8. Delete all exported records from the pod only after the snapshot has been successfully created in memory.

Response:

- binary file download

Notes:

- v1 may keep this endpoint synchronous.
- If export set is empty, return an empty but valid snapshot or reject explicitly. This must be decided once and documented.

### `POST /v1/privacy/restore`

Authenticated endpoint.

Behavior:

1. Accept uploaded snapshot file.
2. Parse unencrypted header.
3. Verify `formatVersion` is supported.
4. Verify `podId` matches this pod.
5. Verify `expiresAt` has not passed.
6. Enforce configured snapshot size limits.
7. Resolve key by `keyId`.
8. AEAD-decrypt payload.
9. Validate manifest and all content records.
10. Restore records.

Response:

- summary object with restored ids and counts

## Restore Validation Rules

Restore must reject the snapshot if:

- `formatVersion` is unknown
- `podId` does not match
- `keyId` is unknown
- `expiresAt` is in the past
- snapshot size exceeds configured limits
- AEAD authentication fails
- record count exceeds configured limits
- required fields are missing
- a reply references a missing root
- duplicate ids appear inside the snapshot

Even though the snapshot is encrypted, restore must still enforce normal size and content validation.

## Deletion Semantics

Freeze deletes exactly the records that were exported.

This includes:

- the user's root posts
- the user's replies
- replies by others on the user's root posts

This behavior must be communicated clearly because it removes third-party replies from the pod as part of freezing the user's thread context.

## Failure Semantics

`freeze` must not delete anything if:

- export set resolution fails
- active archive key resolution fails
- nonce generation fails
- snapshot serialization fails
- encryption fails

`restore` must not partially import content unless the implementation can guarantee a clearly defined transactional behavior.

Preferred v1 behavior:

- validate whole snapshot first
- restore only after validation succeeds

## TDD Delivery Plan

### Increment 1

- add domain-level logic for selecting the freeze export set
- cover exact inclusion rules with unit tests

### Increment 2

- add snapshot header and payload model
- add serialization tests

### Increment 3

- add AEAD encryption/decryption service
- test successful decrypt and tamper failure

### Increment 4

- add freeze application service
- add server-side nonce generation and active archive key resolution
- test: snapshot produced and matching records deleted

### Increment 5

- add restore application service
- add header-first validation and size limits
- test: valid snapshot restores records

### Increment 6

- add failure tests
- expired snapshot
- wrong pod id
- unsupported format version
- unknown key id
- oversized snapshot
- modified ciphertext
- invalid reply/root relationships

## Decisions

- freeze with zero matching records should succeed and yield NO_CONTENT.
- maximum allowed snapshot size: constrained by configuration and enforced before decrypt
- whether restore should be blocked if the user already has active content on the pod: No. If content is duplicated by the snapshot (e.g. because the snapshot is restored twice), the content from the snapshot 
- exact binary framing for header plus ciphertext
