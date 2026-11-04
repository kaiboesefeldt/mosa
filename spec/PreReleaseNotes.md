# Pre-Release Notes

## Temporary Assumptions

### ARCH-TEMP-1: No migration logic before first release

- status: active
- rationale:
  - MOSA currently has no released software in the field.
  - Therefore there is no supported upgrade path for productive persisted state yet.
- current consequence:
  - as long as MOSA remains unreleased, architecture and implementation changes do not need migration logic for legacy persisted state
  - this includes one-time imports, compatibility bootstraps, and similar upgrade-only migration paths
- removal condition:
  - remove this note once MOSA has released software that may carry productive persisted state across upgrades
