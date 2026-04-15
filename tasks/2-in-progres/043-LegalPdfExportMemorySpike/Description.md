# Legal PDF Export Memory Spike

## Problem

Exporting the privacy policy to PDF appears to trigger a large memory spike in Firefox. In one observed case,
Firefox was automatically terminated after the export had finished, suggesting retained or peak memory pressure
instead of a simple synchronous error.

## Goal

Identify and fix the client-side PDF export path so exporting the privacy policy no longer causes excessive memory
growth or delayed out-of-memory termination in Firefox.

## Constraints

- Keep the scope focused on the legal PDF export path first.
- Prefer a minimal fix that reduces memory pressure without changing exported content unnecessarily.
- Investigate whether the issue is peak memory, delayed release, or a real leak before making structural changes.
- Preserve existing export behavior unless a change is needed to prevent the memory problem.

## Expected Outcome

- The PDF export should complete without causing Firefox to run out of memory under normal privacy-policy export use.
- Any retained objects, canvases, blobs, object URLs, or offscreen DOM nodes involved in the export should be
  reviewed and released appropriately.
- Add or adjust tests only if they help prove the behavior through public or observable effects.
