# Test Findings

## TEST-1
- title: Export cleanup is only covered on the happy path
- status: resolved
- evidence or rationale: `client/src/jsTest/kotlin/straightway/mosa/client/legal/LegalPdfExporterTest.kt` now checks `clearCanvas(canvas)` directly, and `./gradlew :client:jsTest` passes, but there is still no test that exercises `downloadLegalPdf(...)` or `saveCanvasAsPdf(...)` through a failure path. The current coverage does not prove that `exportRoot`, the reusable slice canvas, and the rendered canvas are released if `html2canvas`, `toDataURL`, `pdf.addImage`, or `pdf.save` fails mid-export. That is the path most likely to leave memory retained after a large privacy-policy export.
- recommended action: Add a targeted test around the export flow that forces a mid-export failure and verifies cleanup still happens, or document why that path cannot be tested and what observable browser evidence replaces it.
- Developer Response: Added an internal test hook to `saveCanvasAsPdf(...)` and a regression test that forces `pdf.addImage(...)` to throw. The test verifies both the source canvas and reusable slice canvas are cleared in the `finally` block, covering the failure path that was previously untested.
- verification: `./gradlew :client:jsTest` -> passed

## TEST-2
- title: Current subtask evidence is sufficient for handoff to implementation
- status: resolved
- evidence or rationale: The current `sub001-InventoryExportMemoryPressure` scope is already backed by an observable proxy and a concrete stop/go recommendation in `tasks/2-in-progres/043-LegalPdfExportMemorySpike/ArchitectureNotes.md`, and the export implementation is covered by the failure-path cleanup regression in `client/src/jsTest/kotlin/straightway/mosa/client/legal/LegalPdfExporterTest.kt`. The remaining gap is browser-level memory measurement, which belongs in the later implementation/verification subtasks rather than in this inventory step.
- recommended action: Proceed with `sub002` if the goal is to reduce retained memory or peak pressure further; do not keep `sub001` open unless a new observable pressure source is discovered.
- verification: `./gradlew :client:jsTest` -> passed
