Dies ist die verpflichtende Abschluss-Subtask fuer die Gesamtintegration der Aenderung.

Ziel ist, die geaenderte Gesamtloesung ueber die wichtigsten funktionalen Pfade hinweg zu pruefen.

Betroffene Komponenten:

- Shared DTOs und API-Vertrag fuer gepagte Listen
- serverseitiger Nutzer-Inbox-Abruf
- serverseitiger Admin-Verlauf pro Profil
- Paging-Informationen mit Seitennummer und Flag fuer weitere Seiten
- getrennte Read-Semantik fuer Nutzer-Inbox und Admin-Verlauf
- Client-seitiges smartes Nachladen fuer Inbox und Admin-Verlauf
- Admin-Aktionskarte mit Verlaufssicht

Vorbedingungen:

- Die Subtasks fuer Shared, Server und Client sind umgesetzt.
- Testhilfen koennen Ablaufzeitpunkte deterministisch simulieren.

Auszufuehrende Pruefungen:

1. `GET /messages` liefert fuer die Nutzer-Inbox gepagte Daten mit Seitennummer und Flag fuer weitere Seiten.
2. `GET /admin/messages` liefert fuer ein Profil nur dessen Nachrichten, absteigend nach Versandzeitpunkt und gepaged.
3. Der Admin-Verlauf zeigt Nachrichten aller Admin-Konten fuer das betrachtete Profil.
4. Ein Nicht-Admin kann `GET /admin/messages` nicht erfolgreich nutzen.
5. Das Lesen ueber `GET /admin/messages` veraendert die TTL einer Nachricht nicht.
6. Abgelaufene Nachrichten werden beim Abruf nicht mehr ausgeliefert.
7. Inbox und Admin-Verlauf laden im Client bei Bedarf weitere Seiten nach.
8. Ein Profil ohne Admin-Nachrichten zeigt in der Aktionskarte einen klaren Leerzustand.
9. `./gradlew check` laeuft erfolgreich.

Erfolgskriterium:

- Die wesentlichen Paging-, Auth-, TTL- und UI-Integrationspfade sind automatisch abgesichert und der komplette Quality Gate ist gruen.
