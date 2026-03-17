Ziel ist, den ersten oeffentlichen API-Zuschnitt fuer das Admin-zu-User-Nachrichtensystem festzulegen und in `shared` zu modellieren.

Diese Subtask umfasst mindestens:

- DTOs fuer das Erzeugen einer Admin-Nachricht definieren
- DTOs fuer den Inbox-Abruf definieren
- Benennung und Minimalfelder mit dem Design der Hauptaufgabe abgleichen
- Festlegen, welche Endpunkte fuer v1 existieren und welche bewusst noch nicht

Erwartetes Ergebnis:

- Ein kleiner, stabiler Shared-Vertrag fuer `POST /v1/admin/messages` und `GET /v1/messages`
- Keine vorgezogenen Felder fuer Antworten, Threads, Read-Receipts oder Moderationskopplung
