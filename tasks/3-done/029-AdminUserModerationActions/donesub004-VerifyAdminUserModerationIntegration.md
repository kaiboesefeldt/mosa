Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration fuer Task 029.

Ziel ist, serverseitige Moderationslogik, Client-Anbindung und verbleibende Doku-Folgen gemeinsam zu pruefen.

Art des Abschlusstests

- Vollautomatischer Repo-Test ueber `./gradlew check`
- Ergaenzendes Integrationsreview der geaenderten Doku und Laufzeit-Artefakte

Voraussetzungen

- `sub001-DefineModerationContractAndProtectionRules.md` ist abgeschlossen.
- `sub002-ImplementServerSideIdentityModerationActions.md` ist abgeschlossen.
- `sub003-ExposeModerationActionsInAdminUserCard.md` ist abgeschlossen.

Durchfuehrung

1. `./gradlew check` ausfuehren.
2. Pruefen, dass serverseitige Tests erfolgreiche und abgewiesene Moderationsfaelle abdecken.
3. Pruefen, dass Client-Tests die sichtbaren Zustandswechsel der Admin-Aktionsflaeche abdecken.
4. Falls der Wahrheitsort fuer Identity-Sperrzustaende geaendert wurde, Doku in `spec`, `server/src/main/resources/clearterms` und `server/src/main/resources/doc/serve` pruefen und noetigenfalls anpassen.
5. Pruefen, ob neue 3rd-Party-Lizenzen hinzugekommen sind und ob sie mit `License` kompatibel sind.

Erfolgskriterien

- `./gradlew check` ist erfolgreich.
- Gesamtverhalten fuer Blockieren, Deblockieren und Admin-Loeschen ist integriert verifiziert.
- Doku und Laufzeit-Artefakte passen zum tatsaechlich implementierten Wahrheitsort fuer Sperrzustaende.
- Es wurden keine inkompatiblen neuen 3rd-Party-Lizenzen eingefuehrt.

Fehlschlag

- `./gradlew check` scheitert.
- Server- und Client-Teile pruefen nur isoliert, aber nicht als zusammenhaengenden Moderationsfluss.
- Geaenderte Persistenz oder Laufzeit-Artefakte sind in Doku nicht nachvollziehbar.
