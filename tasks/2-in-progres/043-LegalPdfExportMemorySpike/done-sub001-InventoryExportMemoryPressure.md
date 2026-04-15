# Inventory Export Memory Pressure

## Goal

Locate the exact client-side memory pressure source in the privacy-policy PDF export path and document the observed behavior.

## Scope

- Inspect the legal PDF export flow in `client/src/jsMain/kotlin/straightway/mosa/client/legal/LegalPdfExporter.kt`.
- Check whether the issue is caused by the temporary DOM clone, canvas creation, image loading, or PDF serialization.
- Reproduce or reason about the spike with targeted, observable evidence only.

## Output

- A short inventory of the likely pressure points and what evidence supports each one.
- A concrete stop/go recommendation for the minimal fix surface to implement in `sub002`.
- The observable symptom, measurement, or reproduction detail that justifies that recommendation.
- At least one measurable proxy for the pressure source, such as retained object type, page count, canvas size, or heap delta.

## Exit Criteria

- The pressure source is narrowed enough that `sub002` can focus on one implementation path without guessing.
- The task records which export path behavior was observed and why that evidence is sufficient to proceed.
