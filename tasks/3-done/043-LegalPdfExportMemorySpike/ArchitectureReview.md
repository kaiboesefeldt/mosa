# Architecture Review

## AR-1

- `status`: open
- `title`: The fix is still framed as cleanup, but the export path is structurally a peak-memory pipeline
- `evidence or rationale`: `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPdfExporter.kt` does not just retain temporary objects; it rasterizes the full export root with `html2canvas`, then builds one full-size slice canvas per PDF page, then converts each slice to a PNG data URL before embedding it in jsPDF. That means the current memory profile is dominated by repeated full-frame image materialization, not by a clearly isolated leak. The current decomposition in `tasks/2-in-progres/043-LegalPdfExportMemorySpike/ArchitectureNotes.md` treats `sub002` as a minimal cleanup pass, but the implementation shape may require an explicit decision about reducing rasterization strategy or chunking work, not just releasing temporary nodes after the fact.
- `recommended action`: Rework the implementation split before coding `sub002`. Make the implementation notes explicitly allow a lower-peak export change if cleanup alone is insufficient, and keep the task focused on the legal export path rather than assuming the problem is a simple retention bug.
- `Developer Response`: `ArchitectureNotes.md` now states that `sub002` must reduce peak materialization if cleanup is not enough, and the implementation now reuses the slice canvas and clears export canvases after use, so the fix matches the peak-memory framing.

## AR-2

- `status`: open
- `title`: Verification scope mixes unrelated concerns into the same closing step
- `evidence or rationale`: `tasks/2-in-progres/043-LegalPdfExportMemorySpike/sub003-VerifyIntegration-And-Docs.md` combines end-to-end verification, architecture doc review, and third-party license impact review. For a client-local cleanup fix, license review is only relevant if a new dependency or asset is introduced, but the stated goal and constraints prefer a minimal local change in the existing legal export path. This makes the last subtask broader than the change itself and blurs ownership between quality verification and dependency/compliance review.
- `recommended action`: Remove the license check from the default path unless the implementation actually adds or changes a dependency or asset. Keep `sub003` focused on observable export behavior and required docs updates.
- `Developer Response`: `ArchitectureNotes.md` now narrows `sub003` so license review is conditional on a new dependency or bundled asset, not part of the default verification path. The current implementation did not add either, so no license review was required.

## AR-3

- `status`: open
- `title`: The task split does not define a measurable handoff between investigation and fix
- `evidence or rationale`: `sub001` asks for a short inventory of likely pressure points, but it does not require any concrete measurement, reproduction artifact, or stop/go criterion that would prove the problem is dominated by one mechanism instead of several. `sub002` then assumes the inventory is sufficient to choose a minimal fix. For a browser memory spike that may come from peak rasterization, object URL lifetimes, or transient DOM retention, that is a brittle handoff: the implementation can start on a guess and still satisfy the written subtasks.
- `recommended action`: Add an explicit decision gate after `sub001` that records the observed pressure source and the metric or observable evidence that justifies the chosen fix path before `sub002` begins. If no such evidence is available, the task should stay in investigation mode rather than advancing to a presumed cleanup patch.
- `Developer Response`: The current task files still do not require a concrete measurement or stop/go artifact from `sub001`, so `sub002` can still begin on a qualitative guess. The split should be tightened before implementation continues.
- `Developer Response`: `sub001` now ends with an explicit stop/go recommendation and the observable evidence that supports it, so `sub002` no longer starts from a purely qualitative guess. The remaining implementation risk is whether the evidence itself is strong enough once collected.
- `Developer Response`: `ArchitectureNotes.md` now requires at least one measurable proxy from `sub001` before `sub002` starts, which turns the handoff into an explicit evidence gate instead of a qualitative guess. The current implementation used the repeated full-frame rasterization and per-page slice materialization as the observable pressure source.

## Residual Risks

- The current task is only as strong as the evidence `sub001` records; if that step stays qualitative, `sub002` can still drift into a guess-driven fix.
- `spec/Architecture.md` currently documents the client `legal` area only as legal document display; if the export pipeline ownership or shape changes, the architecture doc will need a targeted update.

## Current Review

No new structural findings beyond the existing `AR-1` through `AR-3`.

The current split is now implementation-ready on paper because `sub001` is required to record a concrete stop/go recommendation, the pressure source, and at least one measurable proxy before `sub002` starts. The remaining risk is execution discipline, not task shape: if `sub001` does not produce that evidence, `sub002` should not proceed.

## Human Decision

The human project lead has explicitly accepted the architecture and the current subtask split as sufficient to continue.

- `status`: accepted
- `decision`: proceed with the current task structure
- `scope`: no further architectural re-splitting required before implementation
- `note`: any remaining uncertainty is to be handled during execution, not by reopening the task structure
