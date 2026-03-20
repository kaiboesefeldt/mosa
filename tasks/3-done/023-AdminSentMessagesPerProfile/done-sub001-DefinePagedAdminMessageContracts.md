Ziel ist, den Shared-Vertrag fuer gepagte Admin-Nachrichtenlisten festzulegen.

Diese Subtask umfasst mindestens:

- DTOs fuer gepagte Listenresponses der Admin-Nachrichten definieren
- Rueckgabe von Eintraegen, ausgelieferter Seitennummer und Flag fuer weitere Seiten modellieren
- benoetigte Request-Parameter fuer `GET /messages` und `GET /admin/messages` im Shared-Vertrag festlegen
- den neuen Admin-Read-Pfad `GET /admin/messages` fachlich vom bestehenden `POST /admin/messages` und von `GET /messages` abgrenzen
- die fuer den Admin-Verlauf noetigen Felder fuer Absender und Restlaufzeit in Tagen im Vertrag beruecksichtigen

Erwartetes Ergebnis:

- Ein kleiner, stabiler Shared-Vertrag fuer gepagte Inbox- und Admin-Verlaufslisten
- Keine vorgezogenen Felder fuer Editieren, Loeschen, Antworten oder Threads
