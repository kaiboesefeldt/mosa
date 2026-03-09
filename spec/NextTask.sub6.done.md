# Subtask 6: HTTP API and Client Integration

## Goal

Expose freeze and restore through the pod API and integrate the workflow into the client.

## Scope

Add:

- server endpoint for freeze download
- server endpoint for restore upload
- shared request and response DTOs if needed
- client pod access methods
- client workflow for download and upload

## Deliverables

- API routes
- client integration
- endpoint tests and targeted client tests where useful

## Constraints

- preserve existing auth model
- keep UI-specific logic small
- business logic must stay in previously introduced services

## API Shape

### Freeze

- `POST /v1/privacy/freeze`
- authenticated
- returns snapshot file bytes

### Restore

- `POST /v1/privacy/restore`
- authenticated
- accepts uploaded snapshot file
- returns restore summary

## Test Cases

- authenticated freeze returns file download
- authenticated restore returns summary
- unauthorized access is rejected
- endpoint surfaces validation failures correctly
- client can download freeze snapshot
- client can upload restore snapshot

## Done When

- freeze and restore are usable end to end through the existing pod/client integration
