Define the executable design for moving snapshot archive keys from the JSON file into SQLite-backed structured state.

Scope of this subtask:

- inspect the current file-backed snapshot archive-key store and its consumers
- choose the concrete SQLite schema shape for active and historical archive keys
- define where the new table and implementation live so responsibility stays with snapshot/privacy while initialization
  still flows through structured state
- make the pre-release no-migration assumption explicit in the implementation plan

Required outcomes:

- a concrete persistence shape is chosen for snapshot archive keys, including how active-key resolution works after restart
- ownership boundaries are explicit for:
  - snapshot domain interfaces
  - snapshot-specific SQLite store implementation
  - structured-state table registration/bootstrap
- the implementation plan states whether bootstrap creates the first key lazily and how that avoids partially initialized state
- any documentation surfaces that must change are listed so they can be closed in the final increment

Constraints:

- do not start the productive store swap in this subtask
- do not widen the shared boundary; snapshot archive keys remain server-only runtime state
- do not introduce a second long-lived persistence source or a legacy dual-read mode

Verification expectations for this subtask:

- leave behind an implementation-ready plan in the task artifacts, not only code-reading notes
- confirm that the current architectural findings in `ArchitectureNotes.md` still hold or update them explicitly
- make the next increment narrow enough that the developer can implement it without re-deciding the storage model
