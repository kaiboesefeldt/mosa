Dies ist die verpflichtende Abschluss-Subtask fuer die Gesamtintegration der Aenderung.

Ziel ist, die geaenderte Gesamtloesung ueber die wichtigsten funktionalen Pfade hinweg zu pruefen.

Betroffene Komponenten:

- Shared-Vertrag und RAML-Spezifikation fuer den Inbox-Read-Pfad
- serverseitiger Inbox-Abruf mit Zeitstempel-basierter Synchronisation
- Browser-Speicher fuer lokal bekannte Admin-Nachrichten
- Client-seitige Merge-, Filter- und Read-Darstellung der Inbox

Vorbedingungen:

- Die Subtasks fuer Vertrag, Server und Client sind umgesetzt.
- Testhilfen koennen Inbox-Daten und Refreshs deterministisch simulieren.

Auszufuehrende Pruefungen:

1. Ein initialer Inbox-Abruf kann die sichtbare serverseitige Inbox vollstaendig laden.
2. Ein inkrementeller Refresh laedt nur Nachrichten aus dem relevanten Zeitfenster nach und bleibt bei Paging konsistent.
3. Nachrichten an der Zeitgrenze duerfen erneut geliefert werden, werden im Client aber nicht doppelt angezeigt.
4. Nach dem Lesen einer Nachricht bleibt sie lokal erhalten und ist ueber den Filter wieder sichtbar.
5. Beim ersten Oeffnen der Inbox sind gelesene Nachrichten standardmaessig ausgeblendet.
6. Eine bereits lokal bekannte Nachricht bleibt auch dann im Client sichtbar, wenn sie spaeter serverseitig nicht mehr ausgeliefert wird.
7. Ungueltige Read-Requests werden serverseitig sauber abgewiesen.
8. Die RAML-Spezifikation beschreibt die umgesetzte Inbox-Semantik konsistent.
9. `./gradlew check` laeuft erfolgreich.

Erfolgskriterium:

- Die wesentlichen Synchronisations-, Retentions-, Filter-, API- und UI-Pfade sind automatisch abgesichert und der komplette Quality Gate ist gruen.
