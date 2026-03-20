Die Gesamtintegration der Umbenennung auf `mosa_imprint.yaml` und der aktualisierten `serve`-Dateihilfe soll abschliessend verifiziert werden.

Diese Subtask ist der verpflichtende Abschlusstest der zerlegten Hauptaufgabe.

Art des Abschlusstests:

- Kombination aus automatisierten Projektchecks fuer die betroffenen Codeaenderungen und gezielter manueller Sichtpruefung der generierten `serve --doc`-Ausgabe.

Vorbedingungen:

- `sub001-RenameImprintConfigToMosaImprint.md` ist fachlich umgesetzt.
- `sub002-UpdateServeFilesOnlineHelp.md` ist fachlich umgesetzt.
- Es gibt keine offenen lokalen Konflikte in den fuer die Aufgabe geaenderten Dateien.

Durchzufuehrende Schritte:

1. Die fuer die Imprint-Umbenennung neu hinzugefuegten oder angepassten automatisierten Tests ausfuehren und gruene Ergebnisse bestaetigen.
2. Den projektweiten Qualitaetscheck `./gradlew check` ausfuehren, da Produktions- und voraussichtlich Testcode betroffen sind.
3. Die Ausgabe von `serve --doc` erzeugen und die Dateiuebersicht inhaltlich pruefen.
4. Verifizieren, dass die Doku `mosa_imprint.yaml` nennt und keinen veralteten operativen Verweis auf `imprint.yaml` mehr enthaelt.
5. Verifizieren, dass die dokumentierten Top-Level-Artefakte mit dem tatsaechlichen implementierten Serververhalten uebereinstimmen.

Erfolgskriterien:

- Die automatisierten Tests fuer die Umbenennung sind gruen.
- `./gradlew check` ist gruen.
- Die `serve --doc`-Ausgabe dokumentiert die relevanten Top-Level-Artefakte konsistent und nennt `mosa_imprint.yaml` als Imprint-Datei.

Fehlschlag wird erkannt an:

- Roten Tests oder fehlschlagendem `./gradlew check`.
- Abweichungen zwischen Dokumentation und implementiertem Dateiverhalten.
- Verbliebenen veralteten Betreiberhinweisen auf `imprint.yaml`.
