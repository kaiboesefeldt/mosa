Dies ist die verpflichtende Abschluss-Subtask fuer die Gesamtintegration der Aenderung.

Ziel ist, die geaenderte Gesamtloesung ueber die wichtigsten funktionalen Pfade hinweg zu pruefen.

Betroffene Komponenten:

- Shared DTOs und API-Vertrag
- serverseitige Nachrichtenerzeugung
- serverseitiger Inbox-Abruf
- serverseitiger Background-Cleanup
- Lifecycle mit langer TTL bei Erzeugung und Verkuerzung auf die kurze TTL beim Abruf
- Berechtigungspruefung fuer Admin-Senden und Empfaenger-Abruf
- Client-seitiger API-Zugriff

Vorbedingungen:

- Die Subtasks fuer Shared, Server und Client sind umgesetzt.
- Testhilfen koennen die Uhr kontrollieren oder Ablaufzeitpunkte deterministisch simulieren.

Auszufuehrende Pruefungen:

1. Admin erzeugt eine Nachricht fuer einen Nutzer und der Nutzer kann sie abrufen.
2. Ein anderer Nutzer kann dieselbe Nachricht nicht abrufen.
3. Ein Nicht-Admin kann keine Admin-Nachricht erzeugen.
4. Eine nie abgerufene Nachricht wird nach Ablauf der langen TTL nicht mehr ausgeliefert.
5. Eine beim Abruf sichtbare Nachricht bleibt fuer weitere Abrufe desselben Kontos auf anderen Geraeten bis zum Ende der kurzen TTL verfuegbar.
6. Nach Ablauf der kurzen TTL wird die zuvor abgerufene Nachricht nicht mehr ausgeliefert.
7. `./gradlew check` laeuft erfolgreich.

Erfolgskriterium:

- Die wesentlichen Lifecycle-, Auth- und Integrationspfade sind automatisch abgesichert und der komplette Quality Gate ist gruen.
