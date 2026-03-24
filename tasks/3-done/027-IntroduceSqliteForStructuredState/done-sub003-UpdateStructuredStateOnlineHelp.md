Die Onlinehilfe des Servers fuer dateibasierte Laufzeit-Artefakte wird auf die SQLite-Umstellung angepasst.

Ziel:

- `serve --doc` beschreibt `mosa_db.sqlite` als neues produktives Artefakt.
- Die alte JSON-Datei `mosa_admin_messages.json` wird dort nicht mehr als aktuelles Laufzeit-Artefakt genannt.
- Die Hilfe bleibt inhaltlich konsistent mit dem realen Verhalten des Servers.

Mindestens abzudecken:

- Anpassung der betroffenen Hilfetexte.
- Anpassung der automatisierten Tests zur `serve --doc`-Ausgabe.
- Pruefung, dass kein veralteter Dateiname in der Hilfe stehen bleibt.

Erwartetes Ergebnis:

- Betreiberinnen und Betreiber sehen in der Onlinehilfe die aktuelle Datenbankdatei `mosa_db.sqlite`.
- Die Hilfe beschreibt keine veraltete JSON-Primärpersistenz mehr.
