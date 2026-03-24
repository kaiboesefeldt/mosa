Diese Abschluss-Subtask prueft die Gesamtintegration der SQLite-Umstellung fuer strukturierte Zustandsdaten.

Art des Abschlusstests:

- Vollautomatisierter Repo-Testlauf fuer die betroffenen Komponenten plus gezielte manuelle Sichtpruefung der `serve --doc`-Ausgabe anhand der Testabdeckung und der geaenderten Quellen.

Vorbedingungen:

- `sub001-IntroduceStructuredStateDatabaseBootstrap.md` ist abgeschlossen.
- `sub002-ImplementSqliteAdminMessageStore.md` ist abgeschlossen.
- `sub003-UpdateStructuredStateOnlineHelp.md` ist abgeschlossen.
- Die produktive Datenbankdatei heisst `mosa_db.sqlite`.

Durchzufuehrende Schritte:

1. Die gezielten Servertests fuer Admin-Messages und die betroffene Onlinehilfe grün ausfuehren.
2. Den Repo-weiten Qualitaetscheck mit `./gradlew check` ausfuehren.
3. Die geaenderten Tests und Quellen darauf pruefen, dass `mosa_admin_messages.json` nicht mehr als produktiver Speicher oder als aktuelles Hilfe-Artefakt verwendet wird.
4. Gegen die Implementierung gegenpruefen, dass eine fehlende `mosa_db.sqlite` automatisch angelegt und initialisiert wird.
5. Gegen die Implementierung gegenpruefen, dass die Persistenzstruktur fuer kuenftige weitere Tabellen in derselben Datenbank geeignet bleibt.

Erfolgskriterien:

- Die betroffenen Tests sind grün.
- `./gradlew check` ist grün.
- Admin-Nachrichten laufen produktiv ueber SQLite statt ueber die alte JSON-Liste.
- Die Onlinehilfe nennt `mosa_db.sqlite` und nicht `mosa_admin_messages.json`.
- Die neue Datenbankgrundlage ist so geschnitten, dass spaetere strukturierte Zustandsdaten dort anschliessen koennen.
