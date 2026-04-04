Die Archive-Keys fuer Privacy-Snapshots sollen nicht mehr in einer separaten JSON-Datei gespeichert werden, sondern im bestehenden SQLite-basierten Structured State.

Aktuell verwendet der Snapshot-Bereich mit `FileBackedFreezeSnapshotArchiveKeyStore` eine eigene dateibasierte Persistenz in `mosa_snapshot_archive_keys.json`. Das fuehrt die zuvor bewusst eingefuehrte Trennung wieder auf: strukturierte laufzeitrelevante Anwendungsdaten liegen bereits in `mosa_db.sqlite`, waehrend ein weiterer strukturierter Datenbereich erneut in einer separaten Datei gehalten wird. Fuer Konsistenz, einfachere Sicherung und klarere Betriebssemantik sollen auch diese Archive-Keys in die SQLite-Datenbank verlagert werden.

Ziel ist, dass Snapshot-Archive-Keys als Teil des strukturierten Zustands behandelt werden und dieselbe technische Persistenzgrundlage wie andere strukturierte serverseitige Laufzeitdaten nutzen.

Die Aufgabe umfasst mindestens diese Punkte:

- Die produktive Persistenz fuer Snapshot-Archive-Keys wird von `mosa_snapshot_archive_keys.json` nach `mosa_db.sqlite` verlagert.
- Der bisherige `FileBackedFreezeSnapshotArchiveKeyStore` wird durch eine SQLite-basierte Implementierung ersetzt oder funktional entsprechend abgeloest.
- Die bestehende fachliche Semantik der Archive-Keys bleibt erhalten, insbesondere aktiver Key, Lookup vorhandener Keys und Hinzufuegen neuer Keys.
- Die neue Persistenz wird in die bestehende Structured-State-Architektur mit `StructuredStateContext` und `StructuredStateDatabase` integriert.
- Die Loesung bleibt fuer den Einzelprozess-Betrieb pragmatisch und vermeidet eine zweite parallele Strukturpersistenz nur fuer Snapshot-Keys.
- Die serverseitige Dokumentation und sonstige Hinweise auf dateibasierte Laufzeit-Artefakte werden angepasst, sodass `mosa_snapshot_archive_keys.json` nicht mehr als aktueller produktiver Speicher erscheint.

Akzeptanzkriterien:

- Snapshot-Archive-Keys werden produktiv in SQLite statt in einer separaten JSON-Datei gespeichert.
- Freeze und Restore funktionieren weiterhin ueber denselben fachlichen API-Vertrag.
- Der aktive Archive-Key kann nach Server-Neustart weiterhin geladen werden.
- Bereits gespeicherte Archive-Keys koennen weiterhin fuer Restore- und Entschluesselungsszenarien gefunden werden.
- Die Koin-Verdrahtung des Snapshot-Bereichs verwendet keinen dateibasierten Archive-Key-Store mehr als produktive Standardimplementierung.
- Die serverseitige Dokumentation nennt keine produktiv genutzte Datei `mosa_snapshot_archive_keys.json` mehr.

Bei der Umsetzung ist insbesondere zu klaeren:

- Wie das SQLite-Schema fuer Snapshot-Archive-Keys geschnitten wird, damit aktive und historische Keys eindeutig und robust verwaltet werden koennen.
- Ob eine bestehende Datei `mosa_snapshot_archive_keys.json` bewusst ignoriert oder einmalig importiert werden soll. Falls ein Import sinnvoll ist, muss er klar testbar und einfach gehalten werden.
- Wie die neue Store-Implementierung in die bestehende Snapshot-Logik in `server/src/main/kotlin/straightway/mosa/content/snapshot` integriert wird, ohne die Kryptologie fachlich zu veraendern.
- Wie Tests die Persistenz und Wiederverwendung mehrerer Archive-Keys ueber Neustarts hinweg belastbar abdecken.

Nicht Ziel dieser Aufgabe:

- Das Snapshot-Format selbst fachlich zu aendern.
- Die kryptographische Semantik der Archive-Keys zu aendern.
- Allgemeine Konfigurationsdateien oder andere bewusst dateibasierte Inhalte pauschal nach SQLite zu verschieben.
