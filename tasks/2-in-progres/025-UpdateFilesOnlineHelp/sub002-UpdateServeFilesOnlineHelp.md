Die Onlinehilfe fuer dateibasierte Serverartefakte soll in `serve --doc` auf den aktuellen Stand gebracht werden.

Ausgangspunkt ist `server/src/main/resources/doc/serve/files.md`. Diese Hilfe soll alle relevanten Top-Level-Dateien und -Verzeichnisse im Arbeitsverzeichnis des MOSA-Servers erfassen, die vom Server erwartet, gelesen, geschrieben oder persistent angelegt werden.

Diese Subtask umfasst mindestens:

- Die aktuell relevanten Top-Level-Artefakte im CWD des Servers vollstaendig inventarisieren.
- Die Dateiuebersicht in `files.md` so aktualisieren, dass die dokumentierten Namen mit dem implementierten Verhalten uebereinstimmen.
- Pro Artefakt Zweck, Rolle im Betrieb und relevantes Laufzeitverhalten knapp und klar beschreiben.
- Manuell gepflegte Konfigurationsdateien, automatisch erzeugte Artefakte und persistent beschriebene Laufzeitdaten erkennbar unterscheiden.
- Sensible oder sicherheitsrelevante Dateien ausdruecklich als solche kenntlich machen und den sorgfaeltigen Betreiberumgang knapp beschreiben.

Akzeptanzkriterien:

- `serve --doc` beschreibt die relevanten dateibasierten Top-Level-Artefakte des Servers vollstaendig und ohne veraltete Dateinamen.
- Der primaere Anpassungsort ist `server/src/main/resources/doc/serve/files.md`.
- Andere `serve`-Dokumentation wird nur angepasst, wenn dies fuer unmittelbare Konsistenz oder Verstaendlichkeit noetig ist.

Hinweise zur Umsetzung:

- Diese Subtask ist primaer Dokumentationsarbeit.
- Die Imprint-Datei ist in dieser Doku bereits als `mosa_imprint.yaml` zu fuehren.
