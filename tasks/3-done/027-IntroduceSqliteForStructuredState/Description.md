Mosa soll fuer dynamische strukturierte Zustandsdaten eine einfache lokale SQLite-basierte Persistenz einfuehren.

Ausloeser fuer diese Aufgabe ist, dass die bisherige dateibasierte JSON-Speicherung fuer Admin-Nachrichten zwar fuer den ersten Wurf ausgereicht hat, aber als Grundlage fuer weitere relationale und statusbehaftete Daten zu eng wird. Mit Beschwerden, Moderationsentscheidungen und moeglichen Sperrzustaenden fuer bereits gepruefte Beitraege entstehen zunehmend Daten, die atomare Updates, gezielte Abfragen und belastbare Konsistenzregeln brauchen.

Diese Aufgabe ist deshalb bewusst vor Aufgabe 004 angesiedelt. Die SQLite-Einfuehrung soll zuerst den bestehenden Admin-Message-Store von der JSON-Datei auf SQLite migrieren und dabei gleichzeitig die technische Grundlage fuer spaetere strukturierte Daten wie Beschwerden schaffen.

Die Aufgabe umfasst mindestens diese Punkte:

- Der bisherige JSON-basierte Store fuer Admin-Nachrichten wird durch einen SQLite-basierten Store ersetzt.
- Die bisherige Datei `mosa_admin_messages.json` wird als produktiver Primarspeicher abgeloest.
- Das fachliche Verhalten der Admin-Nachrichten bleibt erhalten, insbesondere Berechtigung, TTL-Verkuerzung beim Abruf, Paging und Loeschen abgelaufener Eintraege.
- Die Persistenz wird so geschnitten, dass spaetere weitere strukturierte Datenbereiche wie Beschwerden nicht wieder einen separaten ad-hoc-Dateispeicher bekommen muessen.
- Die Datenbankanbindung bleibt bewusst einfach, lokal und ohne unnoetige Infrastrukturabhaengigkeiten.
- Der Zugriff auf SQLite erfolgt ueber ein passendes Framework statt ueber ad-hoc-SQL-Strings, damit Abfragen und Updates strukturiert und robust formuliert werden koennen.
- Die Loesung bleibt fuer einen Einzelprozess-Betrieb pragmatisch und gut wartbar.
- Die SQLite-Datei liegt fest im aktuellen Working Directory des Servers und heisst `mosa_db.sqlite`.
- Falls `mosa_db.sqlite` noch nicht existiert, wird die Datei beim Start inklusive benoetigter Schema-Initialisierung automatisch angelegt.
- Falls noch eine alte Datei `mosa_admin_messages.json` vorhanden ist, wird sie ignoriert und nicht mehr als Fallback oder aktiver Datenspeicher verwendet.
- Die serverseitige Onlinehilfe fuer dateibasierte Laufzeit-Artefakte wird auf die neue SQLite-Datei aktualisiert und darf die bisherige JSON-Datei nicht mehr als aktuelles Artefakt nennen.

Akzeptanzkriterien:

- Admin-Nachrichten werden nach der Umstellung nicht mehr in einer JSON-Liste, sondern in SQLite gespeichert.
- Alle bestehenden fachlichen Admin-Message-Flows funktionieren weiterhin ueber denselben API-Vertrag.
- Die bisherige TTL- und Cleanup-Logik fuer Admin-Nachrichten bleibt fachlich erhalten.
- Die Persistenzschicht besitzt eine klare Struktur fuer kuenftige Tabellen oder Stores weiterer dynamischer strukturierter Daten.
- Die Loesung ist so vorbereitet, dass Aufgabe 004 Beschwerden auf derselben SQLite-Grundlage aufbauen kann.
- Die produktive SQLite-Datei heisst fest `mosa_db.sqlite` und wird ohne zusaetzliche Pfadkonfiguration im Server-CWD verwendet.
- Eine fehlende `mosa_db.sqlite` wird beim Start automatisch mit dem benoetigten Schema erzeugt.
- Eine vorhandene `mosa_admin_messages.json` wird von der neuen Persistenz nicht mehr verwendet.
- Die Onlinehilfe des Servers dokumentiert `mosa_db.sqlite` statt `mosa_admin_messages.json`.

Bei der Umsetzung ist insbesondere zu klaeren:

- Wie Exposed zusammen mit dem SQLite-JDBC-Treiber minimal und sauber in die bestehende Ktor/Kotlin-Serverstruktur eingebunden wird.
- Wie das Datenbankschema fuer Admin-Nachrichten so definiert wird, dass kuenftige Moderationsdaten ohne Bruch anschliessen koennen.
- Wie atomare Lese-, Update- und Cleanup-Schritte fuer TTL-Verkuerzung und Ablauf umgesetzt werden.
- Wie Tests die neue Persistenz robust abdecken, ohne aus ihnen langsame oder fragile Integrationsmonster zu machen.

Bereits fuer diese Aufgabe festgelegt:

- Der SQLite-Zugriff erfolgt ueber Exposed.
- Die produktive Datenbankdatei heisst fest `mosa_db.sqlite`.
- Die Datei liegt immer im aktuellen Working Directory des Servers.
- Eine fehlende `mosa_db.sqlite` wird automatisch erzeugt und initialisiert.
- Eine vorhandene `mosa_admin_messages.json` wird ignoriert.
- Eine Migration bestehender Daten aus `mosa_admin_messages.json` ist nicht Teil dieser Aufgabe und wird nicht umgesetzt.

Architekturentscheidung:

- Der SQLite-Zugriff und die initiale Schema-Erzeugung werden in dieser Aufgabe ueber Exposed umgesetzt.
- Exposed wird hier als schlankes Datenzugriffs- und Schema-Werkzeug genutzt, nicht als vollstaendiges, versionsgefuehrtes Migrationssystem.
- Eine fehlende `mosa_db.sqlite` wird beim Start automatisch erzeugt und mit dem fuer die aktuelle Anwendung benoetigten Schema initialisiert.
- Spaetere Schemaaenderungen duerfen ebenfalls ueber Exposed umgesetzt werden, solange sie mit vertretbarer Komplexitaet und nachvollziehbaren, testbaren Migrationsschritten auskommen.
- Ein separates Schema-Migrationswerkzeug wie Liquibase ist nicht Teil dieser Aufgabe.
- Falls spaetere Anforderungen ein ausgereiftes, versionsgefuehrtes Datenbank-Migrationssystem erfordern, wird dafuer eine eigene Folgeaufgabe angelegt.

Nicht Ziel dieser Aufgabe:

- Bereits die Beschwerdefunktion selbst umzusetzen.
- Ein komplexes allgemeines ORM- oder Repository-Framework einzufuehren.
- Konfigurationsdateien wie Allow- oder Blocklisten auf SQLite umzustellen, sofern dafuer noch kein fachlicher Bedarf besteht.
