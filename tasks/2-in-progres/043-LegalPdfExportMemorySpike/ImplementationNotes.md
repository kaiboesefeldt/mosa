# Implementation Notes

## Current Increment

Reduced peak memory pressure in the legal PDF export path by reusing the temporary slice canvas while iterating over PDF pages and clearing both the reusable slice canvas and the rendered export canvas after export completes. Added failure-path test coverage to prove cleanup still runs if PDF generation throws mid-export.

## Chosen Strategy

- Keep the fix in `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPdfExporter.kt`.
- Reduce peak materialization in the page-assembly loop instead of assuming the issue is only stale retention.
- Preserve the existing export layout and content flow.

## Verification

- Targeted JS test run: `./gradlew :client:jsTest`
- Result: passed
- Repository verification: `./gradlew check`
- Result: passed

## Notes

- Added cleanup coverage in `client/src/jsTest/kotlin/straightway/mosa/client/legal/LegalPdfExporterTest.kt`, including a mid-export failure case.
- No new dependency or bundled asset was introduced, so no license review was needed for this increment.
