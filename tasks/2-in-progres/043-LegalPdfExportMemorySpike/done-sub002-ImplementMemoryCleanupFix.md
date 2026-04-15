# Implement Memory Cleanup Fix

## Goal

Implement the minimal client-side change that reduces the memory pressure in the privacy-policy PDF export path.

## Scope

- Adjust the legal PDF export path only as far as needed to reduce retained memory or peak pressure.
- Explicitly release temporary export artifacts when they are no longer needed.
- Keep exported content and layout stable unless a change is required for memory safety.
- Use the evidence recorded in `sub001` to choose between cleanup, lifecycle tightening, or a lower-peak export strategy.

## Constraints

- Prefer the smallest fix that addresses the documented pressure source from `sub001`.
- Do not expand the fix into unrelated legal rendering cleanup.
- Preserve the observable PDF export behavior.

## Output

- A focused code change in the client legal export path.
- Updated implementation notes describing the chosen cleanup strategy and any tradeoffs.

## Exit Criteria

- The export path no longer leaves obvious large transient objects retained after completion.
- The fix is ready for integrated verification in `sub003`.
