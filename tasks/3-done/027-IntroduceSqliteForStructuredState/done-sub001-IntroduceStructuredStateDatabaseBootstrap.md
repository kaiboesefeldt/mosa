Die Aufgabe fuehrt die gemeinsame technische Grundlage fuer lokale strukturierte Zustandsdaten auf SQLite-Basis ein.

Ziel:

- Exposed und den benoetigten SQLite-JDBC-Treiber wieder in den Server einbinden.
- Eine kleine, klar abgegrenzte Bootstrap-Struktur fuer die neue lokale Datenbank schaffen.
- Die produktive Datenbankdatei fest auf `mosa_db.sqlite` im aktuellen Working Directory des Servers legen.
- Sicherstellen, dass eine fehlende Datenbankdatei beim Start automatisch angelegt und initialisiert wird.
- Die Loesung so schneiden, dass spaetere strukturierte Datenbereiche wie Beschwerden dieselbe Datenbank mit eigenen Tabellen nutzen koennen.
- Sicherstellen, dass eine alte `mosa_admin_messages.json` fuer die neue Persistenz keinerlei Fallback-Rolle mehr hat.

Randbedingungen:

- Kein Liquibase.
- Exposed wird als schlankes Datenzugriffs- und Schema-Werkzeug genutzt.
- Kein komplextes allgemeines Persistenz-Framework um die eigentliche Aufgabe herum bauen.
- Die Aenderung soll die bestehende Architektur klarer machen, nicht diffuser.

Erwartetes Ergebnis:

- Es gibt eine klar benannte zentrale Stelle fuer den Zugriff auf `mosa_db.sqlite`.
- Die Datenbankinitialisierung laeuft beim Serverstart automatisch an.
- Die Struktur ist nicht mehr auf Admin-Nachrichten als einzigen kuenftigen Datenbereich fest verdrahtet.
- Tests decken den produktiven Pfad zur automatischen Erzeugung und Initialisierung der Datenbank ab.
