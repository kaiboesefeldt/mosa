# Legal PDF Export For Additional Documents

## Problem

At the moment, PDF export is only available for the privacy policy. The imprint and terms of service documents still
need the same export capability so users can download those legal texts in the same format.

## Goal

Add PDF export support for the other legal documents, specifically:

- Imprint
- Terms of Service

## Constraints

- Reuse the existing legal PDF export path and shared export logic where practical.
- Keep the exported PDFs consistent with the current privacy policy export in layout and behavior.
- Avoid duplicating rendering code if the same export pipeline can handle all legal documents.
- Update the relevant UI and any user-facing help or documentation surfaces if they describe the available exports.

## Expected Outcome

- Users can trigger PDF export for Impressum and Nutzungsbedingungen.
- The export behavior is coherent with the existing privacy policy export.
- Tests cover the new public behavior where it is practical to do so.
