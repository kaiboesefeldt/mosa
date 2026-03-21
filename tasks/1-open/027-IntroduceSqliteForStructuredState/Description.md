Mosa soll fuer dynamische strukturierte Zustandsdaten eine einfache lokale SQLite-basierte Persistenz einfuehren.

Ausloeser fuer diese Aufgabe ist, dass die bisherige dateibasierte JSON-Speicherung fuer Admin-Nachrichten zwar fuer den ersten Wurf ausgereicht hat, aber als Grundlage fuer weitere relationale und statusbehaftete Daten zu eng wird. Mit Beschwerden, Moderationsentscheidungen und moeglichen Sperrzustaenden fuer bereits gepruefte Beitraege entstehen zunehmend Daten, die atomare Updates, gezielte Abfragen und belastbare Konsistenzregeln brauchen.

Diese Aufgabe ist deshalb bewusst vor Aufgabe 004 angesiedelt. Die SQLite-Einfuehrung soll zuerst den bestehenden Admin-Message-Store von der JSON-Datei auf SQLite migrieren und dabei gleichzeitig die technische Grundlage fuer spaetere strukturierte Daten wie Beschwerden schaffen.

Die Aufgabe umfasst mindestens diese Punkte:

- Der bisherige JSON-basierte Store fuer Admin-Nachrichten wird durch einen SQLite-basierten Store ersetzt.
- Die bisherige Datei `mosa_admin_messages.json` wird als produktiver Primarspeicher abgeloest.
- Das fachliche Verhalten der Admin-Nachrichten bleibt erhalten, insbesondere Berechtigung, TTL-Verkuerzung beim Abruf, Paging und Loeschen abgelaufener Eintraege.
- Die Persistenz wird so geschnitten, dass spaetere weitere strukturierte Datenbereiche wie Beschwerden nicht wieder einen separaten ad-hoc-Dateispeicher bekommen muessen.
- Die Datenbankanbindung bleibt bewusst einfach, lokal und ohne unnoetige Infrastrukturabhaengigkeiten.
- Die Loesung bleibt fuer einen Einzelprozess-Betrieb pragmatisch und gut wartbar.

Akzeptanzkriterien:

- Admin-Nachrichten werden nach der Umstellung nicht mehr in einer JSON-Liste, sondern in SQLite gespeichert.
- Alle bestehenden fachlichen Admin-Message-Flows funktionieren weiterhin ueber denselben API-Vertrag.
- Die bisherige TTL- und Cleanup-Logik fuer Admin-Nachrichten bleibt fachlich erhalten.
- Die Persistenzschicht besitzt eine klare Struktur fuer kuenftige Tabellen oder Stores weiterer dynamischer strukturierter Daten.
- Die Loesung ist so vorbereitet, dass Aufgabe 004 Beschwerden auf derselben SQLite-Grundlage aufbauen kann.

Bei der Umsetzung ist insbesondere zu klaeren:

- Welche minimale SQLite-Bibliothek oder welcher JDBC-Treiber zur bestehenden Ktor/Kotlin-Serverstruktur am besten passt.
- Wie das Datenbankschema fuer Admin-Nachrichten so definiert wird, dass kuenftige Moderationsdaten ohne Bruch anschliessen koennen.
- Ob und wie eine einmalige Migration aus einer vorhandenen `mosa_admin_messages.json` in die Datenbank erfolgen soll.
- Wie der Datenbankpfad konfiguriert wird und welcher Default-Dateiname sinnvoll ist.
- Wie atomare Lese-, Update- und Cleanup-Schritte fuer TTL-Verkuerzung und Ablauf umgesetzt werden.
- Wie Tests die neue Persistenz robust abdecken, ohne aus ihnen langsame oder fragile Integrationsmonster zu machen.

Nicht Ziel dieser Aufgabe:

- Bereits die Beschwerdefunktion selbst umzusetzen.
- Ein komplexes allgemeines ORM- oder Repository-Framework einzufuehren.
- Konfigurationsdateien wie Allow- oder Blocklisten auf SQLite umzustellen, sofern dafuer noch kein fachlicher Bedarf besteht.
