Ziel

Fehlende Operator-Profile sollen beim Start automatisch als Dateien im aktuellen Arbeitsverzeichnis des Mosa-Server-Prozesses angelegt werden.

Scope

- Prüfen, ob `mosa_privacy.yaml` fehlt.
- Prüfen, ob `mosa_terms.yaml` fehlt.
- Nur fehlende Dateien erzeugen.
- Bereits vorhandene Dateien vollständig unangetastet lassen.

Nicht Teil dieser Subtask

- Inhaltliche Ausgestaltung der Template-Werte.
- YAML-Kommentare fuer `expectedContent`.
- Logging-Details und Abbruchverhalten.

Akzeptanzkriterien

- Fehlt `mosa_privacy.yaml`, wird genau diese Datei erzeugt.
- Fehlt `mosa_terms.yaml`, wird genau diese Datei erzeugt.
- Fehlen beide Dateien, werden beide erzeugt.
- Existiert eine der Dateien bereits, wird sie weder ueberschrieben noch ergaenzt.
- Die Dateierzeugung ist ueber oeffentlich beobachtbares Verhalten automatisiert testbar.
