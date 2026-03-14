Ziel

Das neue Verhalten fuer fehlende und unvollstaendige Operator-Profile soll ueber automatisierte Tests abgesichert werden.

Scope

- Tests fuer Erzeugung nur fehlender Dateien.
- Tests fuer Nicht-Ueberschreiben bereits vorhandener Dateien.
- Tests fuer `null`-basierte Template-Inhalte.
- Tests fuer ERROR-Logging und Startabbruch.

Nicht Teil dieser Subtask

- Produktive Implementierung ohne unmittelbaren Testbezug.

Akzeptanzkriterien

- Es gibt mindestens einen automatisierten Test fuer den Fall, dass nur `mosa_privacy.yaml` fehlt.
- Es gibt mindestens einen automatisierten Test fuer den Fall, dass nur `mosa_terms.yaml` fehlt.
- Es gibt mindestens einen automatisierten Test fuer den Fall, dass bereits vorhandene Dateien unangetastet bleiben.
- Es gibt mindestens einen automatisierten Test, der den Startabbruch bei erzeugten oder unvollstaendigen Dateien nachweist.
- Die Tests laufen mit den bestehenden Testwerkzeugen des Projekts und pruefen oeffentlich beobachtbares Verhalten.
