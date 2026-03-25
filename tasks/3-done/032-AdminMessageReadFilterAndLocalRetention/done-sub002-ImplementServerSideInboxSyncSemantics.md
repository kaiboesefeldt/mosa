Ziel ist, den serverseitigen Inbox-Abruf fuer die neue Synchronisationssemantik umzusetzen und automatisiert abzusichern.

Diese Subtask umfasst mindestens:

- den Read-Pfad `GET /messages` fuer den vom Client uebergebenen UTC-Zeitpunkt erweitern
- die serverseitige Filterung und Sortierung so umsetzen, dass Paging und Refresh fachlich konsistent bleiben
- die bestehende Abruf-Semantik fuer Sichtbarkeit und TTL-Verkuerzung beibehalten, soweit sie durch die neue Synchronisation nicht fachlich geaendert wird
- ungueltige Request-Parameter ueber oeffentlich sichtbares Verhalten sauber behandeln
- die Server- und Endpoint-Tests fuer den neuen Read-Pfad minimal und deterministisch erweitern

Fachliche Entscheidung:

- Jeder Inbox-Abruf arbeitet mit einem expliziten `fromTime`; fuer den Vollabruf wird `0` verwendet.
- Eine Nachricht gilt nur dann als `retrieved`, wenn sie im durch `fromTime` gefilterten Response-Fenster tatsaechlich ausgeliefert wird.
- Aeltere weiterhin sichtbare Nachrichten ausserhalb dieses Fensters behalten daher ihre bisherige TTL, bis sie in einem passenden Abruf wirklich zurueckgegeben werden.
- Dadurch darf die `fromTime`-Filterung serverseitig in die Datenbankabfrage verschoben werden, ohne `null`-Sonderfaelle zu benoetigen.

Erwartetes Ergebnis:

- `GET /messages` unterstuetzt die vereinbarte Synchronisationssemantik serverseitig
- Die wichtigsten Filter-, Paging-, Auth- und TTL-Pfade sind automatisiert abgesichert
