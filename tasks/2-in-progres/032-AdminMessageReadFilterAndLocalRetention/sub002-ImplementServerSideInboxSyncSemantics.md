Ziel ist, den serverseitigen Inbox-Abruf fuer die neue Synchronisationssemantik umzusetzen und automatisiert abzusichern.

Diese Subtask umfasst mindestens:

- den Read-Pfad `GET /messages` fuer den vom Client uebergebenen UTC-Zeitpunkt erweitern
- die serverseitige Filterung und Sortierung so umsetzen, dass Paging und Refresh fachlich konsistent bleiben
- die bestehende Abruf-Semantik fuer Sichtbarkeit und TTL-Verkuerzung beibehalten, soweit sie durch die neue Synchronisation nicht fachlich geaendert wird
- ungueltige Request-Parameter ueber oeffentlich sichtbares Verhalten sauber behandeln
- die Server- und Endpoint-Tests fuer den neuen Read-Pfad minimal und deterministisch erweitern

Erwartetes Ergebnis:

- `GET /messages` unterstuetzt die vereinbarte Synchronisationssemantik serverseitig
- Die wichtigsten Filter-, Paging-, Auth- und TTL-Pfade sind automatisiert abgesichert
