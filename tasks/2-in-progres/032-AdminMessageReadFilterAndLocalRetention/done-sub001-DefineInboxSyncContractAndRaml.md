Ziel ist, den fachlichen und technischen Vertrag fuer die lokale Inbox-Retention und die inkrementelle Synchronisation festzulegen.

Diese Subtask umfasst mindestens:

- den Shared-Vertrag fuer Inbox-Nachrichten und den dafuer benoetigten lokalen Browser-Zustand pruefen und nur minimal erweitern
- die Read-Semantik von `GET /messages` fuer einen vom Client uebergebenen UTC-Zeitpunkt fachlich festlegen
- die Paging-Semantik fuer initialen Vollabruf und inkrementellen Refresh konsistent beschreiben
- die Deduplizierung fachlich ueber die Nachrichten-ID absichern
- die RAML-Spezifikation fuer den Inbox-Read-Pfad passend zum vereinbarten Vertrag aktualisieren

Erwartetes Ergebnis:

- Ein kleiner, klarer API- und Shared-Vertrag fuer Inbox-Synchronisation und lokale Retention
- Eine zur Implementierung passende RAML-Spezifikation ohne Widerspruch zu Client und Server
