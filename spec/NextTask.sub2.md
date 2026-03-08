# Subtask 2: Snapshot Payload Format

## Goal

Define and implement the non-cryptographic snapshot structure used by freeze and restore.

## Scope

Create models and serialization for:

- snapshot header
- snapshot manifest
- content records
- binary framing of header and payload

## Deliverables

- snapshot data model
- serializer and parser
- tests for roundtrip stability

## Constraints

- do not encrypt yet
- do not wire into HTTP yet
- prefer `NDJSON` for content records
- keep the framing simple and explicit

## Required Fields

### Header

- `formatVersion`
- `podId`
- `keyId`
- `createdAt`
- `expiresAt`
- `nonce`

### Manifest

- `ownerGid`
- `recordCount`
- `archivePolicyVersion`

### Content Record

- `id`
- `text`
- `authorGid`
- `storedAt`
- `rootId`

## Test Cases

- header serializes and parses losslessly
- manifest serializes and parses losslessly
- content records serialize and parse losslessly
- full payload roundtrips correctly
- unknown format version is rejected
- malformed framing is rejected

## Done When

- snapshot payload can be built and parsed deterministically
- invalid payloads fail clearly
