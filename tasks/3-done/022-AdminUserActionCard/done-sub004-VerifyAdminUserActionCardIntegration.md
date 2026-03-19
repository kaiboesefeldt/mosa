Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration fuer Task 022.

Ziel

- Sicherstellen, dass der neue userbezogene Admin-Einstieg in den betroffenen Content-Ansichten konsistent funktioniert und die bisherige inhaltsbezogene Semantik wirklich verschwindet.

Art des Abschlusstests

- Kombination aus automatisierten Tests fuer das relevante Verhalten und manuellem UI-Check der Darstellungsgroesse und Bedienbarkeit.

Voraussetzungen

- `sub001-ReplaceContentBoundEntryWithUserEntry.md` ist abgeschlossen.
- `sub002-BuildComfortableAdminUserActionCard.md` ist abgeschlossen.
- `sub003-WireAdminMessageFlowFromActionCard.md` ist abgeschlossen.

Durchfuehrung

1. Die betroffenen Client-Ansichten mit Posts und Replies als Admin rendern oder im laufenden Client aufrufen.
2. Pruefen, dass am User-Block eines fremden Users ein Admin-Einstieg vorhanden ist.
3. Pruefen, dass fuer Nicht-Admins und fuer eigene Inhalte keine zugaengliche Admin-Aktionskarte angeboten wird.
4. Die Aktionskarte oeffnen und verifizieren, dass Avatar und Fictional Name sichtbar sind.
5. Verifizieren, dass die Karte sichtbar komfortabel gross ist und auf Mobilbreite oder engem Viewport nicht zu einem kleinen Tooltip zusammenschrumpft.
6. Die Aktion `Admin-Nachricht senden` ausloesen und pruefen, dass der bestehende Admin-Nachrichtenfluss mit korrekt vorbelegtem Zieluser startet.
7. Pruefen, dass die UI an keiner Stelle nahelegt, die Nachricht sei eine Antwort auf genau den gerade sichtbaren Post oder Reply.
8. Den projektueblichen Qualitaetslauf fuer Codeaenderungen ausfuehren.

Erfolgskriterien

- Der userbezogene Einstieg funktioniert in den betroffenen Content-Ansichten konsistent.
- Die Aktionskarte ist ausreichend gross und gut bedienbar.
- Der Admin-Nachrichtenfluss startet mit dem richtigen Zieluser.
- Die bisherige contentbezogene Semantik ist im Ergebnis nicht mehr dominant.
- `./gradlew check` ist erfolgreich.

Fehlschlag

- Der Einstieg bleibt faktisch an einen Post oder Reply gebunden.
- Die Aktionskarte wirkt wie ein kleiner Tooltip oder ist auf Mobilgeraeten schlecht bedienbar.
- Der Zieluser wird nicht korrekt in den Admin-Nachrichtenfluss uebernommen.
- Die UI laesst weiter offen, ob es sich um Admin-Kommunikation oder um normale Direktnachrichten handelt.
