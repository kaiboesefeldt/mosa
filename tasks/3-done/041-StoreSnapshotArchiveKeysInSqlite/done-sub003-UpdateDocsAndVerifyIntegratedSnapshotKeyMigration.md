Perform the final integrated verification pass for the snapshot archive-key migration.

Scope of this subtask:

- update architecture and operational documentation to match the new productive persistence model
- remove or rewrite runtime/help references that still present `mosa_snapshot_archive_keys.json` as a productive artifact
- run the final integrated verification and prepare the task for quality closure

Required outcomes:

- `spec/Architecture.md` describes snapshot archive keys as SQLite-backed structured state rather than filesystem state
- runtime/help/documentation surfaces no longer present `mosa_snapshot_archive_keys.json` as a productive storage file
- task coordination artifacts reflect the final implemented structure and any residual assumptions clearly
- the task is left ready for whole-task quality review and closure

Constraints:

- do not hide unfinished implementation work in this verification increment; if a real code gap appears, it must become an
  explicit follow-up increment instead of being silently accepted
- prefer narrow documentation and verification changes tied directly to the implemented persistence migration

Verification expectations for this subtask:

- run `./gradlew check` for the final integrated state because production and test code changed
- perform a targeted scan for residual references to `mosa_snapshot_archive_keys.json`
- confirm that no new third-party dependency was introduced as part of the migration
