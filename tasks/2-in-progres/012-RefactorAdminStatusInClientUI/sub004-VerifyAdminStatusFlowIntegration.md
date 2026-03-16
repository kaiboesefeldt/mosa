Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration fuer Task 012.

Ziel ist, nach Abschluss aller vorherigen Subtasks die fachlich korrekte Admin-Praesentation ueber State-Machine, Capability-Kapselung und Connected-View im Zusammenhang nachzuweisen.

Art des Abschlusstests

- Vollautomatisierter Test ueber die bestehenden Client-Testwerkzeuge und den abschliessenden Projektcheck.

Voraussetzungen

- `sub001-ModelDisplayedIdentityBinding.md` ist abgeschlossen.
- `sub002-CapsuleAdminCapabilityLookup.md` ist abgeschlossen.
- `sub003-ExtractAdminPresentationFromConnectedView.md` ist abgeschlossen.

Durchfuehrung

1. Die relevanten Client-Tests fuer State-Machine, Admin-Capability-Kapselung und Admin-Praesentationslogik ausfuehren.
2. Nachweisen, dass fuer jeden refactorten Ausschnitt bereits vor der Strukturveraenderung fachliche Tests oder Teilintegrationstests des bestehenden Verhaltens vorhanden waren.
3. Testfaelle fuer mindestens diese Uebergaenge ausfuehren:
   - verbundenes Admin-Profil -> anderes Profil -> zurueck
   - verbundenes Nicht-Admin-Profil -> verbundenes Admin-Profil
   - Nicht-Admin-Profil im Overlay -> Admin-Profil im Overlay
   - Wechsel der verbundenen Identitaet mit unterschiedlichem Admin-Status
4. Nachweisen, dass veraltete Capability-Responses bei diesen Sequenzen keinen sichtbaren falschen Badge-Status hinterlassen.
5. Nachweisen, dass die automatisierten Tests eine explizite Matrix der fachlich riskanten Kombinationen aus verbundener Identitaet, angezeigter Identitaet, Admin-Status und Response-Reihenfolge abdecken.
6. Einen kurzen manuellen Smoke-Test durchfuehren:
   - verbundenes Admin-Profil oeffnen und Header-Badge pruefen
   - in ein Nicht-Admin-Profil navigieren und Profil-Badge pruefen
   - zur verbundenen Identitaet zuruecknavigieren und beide Badges erneut pruefen
   - die verbundene Identitaet auf ein Profil mit anderem Admin-Status wechseln und die Badges erneut pruefen
7. Den vollstaendigen Qualitaetscheck mit `./gradlew check` ausfuehren.

Erfolgskriterien

- Das Header-Badge zeigt in allen geprueften Faellen den Status der verbundenen Identitaet.
- Das Profil-Badge zeigt in allen geprueften Faellen den Status der aktuell angezeigten Identitaet.
- Profilnavigation verbindet oder aktiviert kein anderes Profil unbeabsichtigt.
- Veraltete Capability-Responses schlagen sichtbar nicht mehr durch.
- Die automatisierten Tests decken die vorab definierte Szenario-Matrix vollstaendig ab.
- Fuer jeden refactorten Ausschnitt ist nachweisbar, dass das zuvor relevante fachliche Verhalten schon vor der Umstrukturierung automatisiert abgesichert war.
- Der manuelle Smoke-Test bestaetigt die UI-Verdrahtung, ohne neue fachliche Abweichungen aufzudecken.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Einer der geforderten Uebergaenge ist nicht automatisiert abgesichert.
- Die Badges zeigen nach Navigation oder Identitaetswechsel einen fachlich falschen Status.
- Veraltete Capability-Responses koennen einen neueren Zustand sichtbar ueberschreiben.
- `./gradlew check` schlaegt fehl.
