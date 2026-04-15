# Architecture Notes

## Structural Read

The memory spike belongs to the client-side legal PDF export path in `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPdfExporter.kt`.
The likely pressure points are the transient export DOM, rendered canvas data, and any retained image or PDF objects created during export.

## Required Boundaries

- Keep the fix in the client legal export path unless evidence shows a server-side or shared concern.
- Prefer local cleanup and lifecycle tightening over moving legal rendering logic across modules.
- Avoid broad refactors of the legal document UI while investigating the memory issue.
- Treat the export path as a peak-memory pipeline first: if the issue is dominated by rasterization or page assembly, the implementation must reduce peak materialization rather than only releasing final references.

## Subtask Decomposition

The task is split into three subtasks:

1. `sub001` localizes the memory pressure, records the observed export behavior, and ends with a concrete stop/go decision for the implementation path.
2. `sub002` implements the minimal client-side fix that reduces retained memory or peak pressure, including any necessary rasterization or page-assembly adjustment if cleanup alone is insufficient.
3. `sub003` verifies the integrated export behavior and checks documentation impact; license review is only required if the implementation introduces a new dependency or bundled asset.

## Evidence Gate

`sub001` must record at least one concrete observable proxy for the pressure source before `sub002` starts.
Acceptable evidence includes:

- a browser reproduction with the export step and the observed failure mode
- a measured heap delta, page-count correlation, or canvas/image size proxy
- a retained object class or lifecycle observation tied to the export flow

The stop/go recommendation for `sub002` must name the observed pressure source and the proxy that justified it. If the evidence is ambiguous, `sub001` should keep the task in investigation mode instead of guessing at a cleanup-only fix.

## Documentation Impact

If the export flow or ownership boundaries change, update `spec/Architecture.md` in the same change.
Record any such requirement here before implementation starts.

## Open Questions

- Whether the pressure comes primarily from the cloned export DOM, the canvas rasterization step, or jsPDF output handling.
- Whether a small cleanup step after PDF generation is sufficient or whether export rendering needs to be chunked or simplified.
- Which observable measure from `sub001` will justify the chosen fix path before `sub002` starts.
