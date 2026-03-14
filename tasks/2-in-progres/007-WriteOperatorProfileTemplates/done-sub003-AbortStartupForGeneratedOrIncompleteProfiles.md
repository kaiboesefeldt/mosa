Ziel

Der Pod soll auch dann nicht starten, wenn gerade fehlende Profil-Dateien erzeugt wurden oder vorhandene Profile noch unvollstaendig sind.

Scope

- Nach dem Erzeugen fehlender Dateien den Start weiterhin abbrechen.
- Vorhandene Profile mit `null`-Werten weiterhin als unvollstaendig behandeln.
- Textuelle Default-Ersetzungen in Textbloecken weiterhin akzeptieren.
- ERROR-Logs mit Verweis auf die betroffenen Dateien ausgeben.

Nicht Teil dieser Subtask

- Erstmalige Dateierzeugung selbst.
- Detailliertes Rendering der YAML-Vorlagen.

Akzeptanzkriterien

- Werden fehlende Dateien erzeugt, endet die Startup-Validierung mit Fehler.
- Enthalten vorhandene Operator-Profile weiterhin `null`-Werte, endet die Startup-Validierung mit Fehler.
- Die Fehlermeldung oder das ERROR-Log verweist auf die betroffenen Dateipfade.
- Zulaessige textuelle Default-Ersetzungen in Textbloecken werden durch die Aenderung nicht unnoetig als Fehler behandelt.

Pragmatisch geklaert

- Die strikte Vollstaendigkeitspruefung wird gezielt im Startup-Pfad verankert. Direkte Dokumentzugriffe koennen ihr bisheriges Fallback-Verhalten behalten, damit bestehende nicht-Startup-Anwendungsfaelle stabil bleiben.
