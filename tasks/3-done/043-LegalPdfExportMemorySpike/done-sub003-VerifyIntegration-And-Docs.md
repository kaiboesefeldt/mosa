# Verify Integration And Docs

## Goal

Verify the privacy-policy PDF export fix end-to-end and confirm any documentation implications.

## Components To Test

- Client legal PDF export flow
- Privacy-policy dialog action that triggers the download
- Any tests covering observable cleanup or export completion behavior

## Preconditions

- The implementation from `sub002` is complete.
- The export path is available in the local build.

## Steps

1. Run targeted tests for the client legal PDF export path.
2. Run the broader repository verification that is appropriate for the change.
3. Review `spec/Architecture.md` and related docs for any required updates.
4. Check whether any new third-party dependency or asset was introduced; only then perform a license review.
5. Confirm the verification evidence matches the pressure source and fix path recorded in `sub001` and `sub002`.

## Success Criteria

- The export completes without reintroducing the memory spike under normal use.
- The verification evidence is recorded clearly.
- Documentation updates, if needed, are applied in the same change.
- If a new dependency or asset was introduced, its license compatibility is checked and recorded.

## Execution Type

- Fully automated where possible, with any browser reproduction step documented so a human can repeat it if needed.
