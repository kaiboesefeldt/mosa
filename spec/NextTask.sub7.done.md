# Subtask 7: UX, Policy Text, and Final Verification

## Goal

Finish the feature with clear user-facing communication and full project verification.

## Scope

Add or update:

- user-facing wording for freeze and restore
- wording about expiration and irrecoverable data loss
- wording about same-pod-only restore
- any privacy/legal text that must mention the workflow
- final integration verification

## Deliverables

- concise UI copy
- any required legal or policy text updates
- final verification through the project quality gate

## Constraints

- be explicit about destructive consequences
- do not over-design the UI

## Required User Communication

- freezing deletes the exported data from the pod
- the snapshot must be stored by the user
- the snapshot cannot be restored after expiration
- the snapshot can only be restored on the same pod
- if the snapshot is lost, the data is lost

## Done When

- the workflow is understandable to the user
- `./gradlew check` passes
