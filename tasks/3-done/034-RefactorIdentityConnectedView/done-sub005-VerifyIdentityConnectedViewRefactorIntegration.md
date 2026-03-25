Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration fuer Task 034.

Ziel

Nach Abschluss aller vorherigen Subtasks soll nachgewiesen werden, dass die Aufraeumarbeiten an `IdentityConnectedView` das bestehende Verhalten nicht veraendert haben und die neue Struktur belastbar ist.

Art des Abschlusstests

- Ueberwiegend vollautomatisierter Test ueber die bestehenden Client-Testwerkzeuge und den abschliessenden Projektcheck.
- Ergaenzend ein kurzer manueller Smoke-Test fuer die UI-Verdrahtung.

Voraussetzungen

- `sub001-StabilizeRefactoringSurfaceWithStateSlices.md` ist abgeschlossen.
- `sub002-ExtractModerationAndAdminOverlays.md` ist abgeschlossen.
- `sub003-ExtractContentColumnsAndComposerUi.md` ist abgeschlossen.
- `sub004-ReduceConnectedViewToOrchestrationShell.md` ist abgeschlossen.

Durchfuehrung

1. Die relevanten Client-Tests fuer die vorab abgesicherten Refactoring-Flaechen ausfuehren.
2. Nachweisen, dass die in den vorherigen Subtasks angefassten Bereiche nicht erst nach der Extraktion, sondern bereits vor den groesseren Strukturveraenderungen fachlich abgesichert wurden.
3. Die wichtigsten UI-Pfade der Connected-View pruefen:
   - Topic auswaehlen und zugehoerige Posts laden
   - Post auswaehlen und Replies anzeigen
   - Composer oeffnen, schliessen und einen bestehenden Publish-Pfad pruefen
   - Inbox oeffnen und Filterverhalten pruefen
   - Complaint-Queue oeffnen und Filterverhalten pruefen
   - Admin-User-Action-Card und Admin-Message-Composer oeffnen
4. Einen kurzen manuellen Smoke-Test in der laufenden Anwendung durchfuehren, um die Verdrahtung der extrahierten Teilkomponenten sichtbar zu bestaetigen.
5. Den vollstaendigen Qualitaetscheck mit `./gradlew check` ausfuehren.

Erfolgskriterien

- Die ausgelagerten Teilbereiche verhalten sich aus Sicht der Nutzerinnen und Nutzer unveraendert.
- Die wichtigen Pfade fuer Content, Moderation, Admin-Messaging und Shell-Interaktionen sind automatisiert oder als gezielter Smoke-Test nachgewiesen.
- `IdentityConnectedView.kt` fungiert nur noch als uebersichtliche Verdrahtungsdatei.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Ein zuvor beobachtbares Verhalten der Connected-View aendert sich unbeabsichtigt.
- Die vorherigen Subtasks haben fachlich riskante Flaechen ohne vorab vorhandene Absicherung refactored.
- `./gradlew check` schlaegt fehl.
