Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration für Task 009.

Ziel ist, nach Abschluss aller vorherigen Subtasks den vollständigen Pfad vom Laden der Operator-Konfiguration bis zur sichtbaren Darstellung im Client zusammenhängend zu prüfen.

Art des Abschlusstests:

- Vollautomatisierter Projektcheck über Tests und Build-Validierung

Voraussetzungen:

- `sub001-LoadAndValidateImprintConfiguration.md` ist abgeschlossen.
- `sub002-ExposeImprintToClient.md` ist abgeschlossen.
- `sub003-RenderImprintInClient.md` ist abgeschlossen.

Durchführung:

1. Eine gültige `imprint.yaml` im erwarteten Format bereitstellen.
2. Die relevanten Server- und Client-Tests für Impressumsladen, API-Auslieferung und Rendering ausführen.
3. Den vollständigen Qualitätscheck mit `./gradlew check` ausführen.

Erfolgskriterien:

- Der Server akzeptiert eine gültige `imprint.yaml`.
- Fehlende optionale Angaben führen nicht zu Fehlern.
- Fehlende Pflichtangaben sind durch Tests als Startfehler mit aussagekräftiger Meldung abgesichert.
- Der Client kann das Impressum laden und in der vorgegebenen Blockreihenfolge darstellen.
- Leere optionale Angaben erscheinen nicht in der UI.
- Der Einstieg über `Rechtliches` führt zum gerenderten Impressum.
- `./gradlew check` ist erfolgreich.

Fehlschlag:

- Einer der beteiligten Tests schlägt fehl.
- Der Server verhält sich bei ungültiger Konfiguration nicht wie spezifiziert.
- Die UI zeigt Blöcke oder Felder entgegen der beschriebenen Rendering-Regeln an.
- `./gradlew check` schlägt fehl.
