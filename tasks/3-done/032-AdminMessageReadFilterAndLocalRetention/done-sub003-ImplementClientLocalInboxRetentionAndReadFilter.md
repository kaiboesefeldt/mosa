Ziel ist, die Inbox im Client so umzubauen, dass gelesene und spaeter serverseitig nicht mehr gelieferte Nachrichten lokal erhalten bleiben und ueber einen Filter sichtbar gemacht werden koennen.

Diese Subtask umfasst mindestens:

- den lokalen Browser-Zustand fuer Admin-Nachrichten so erweitern, dass bereits bekannte Nachrichten nicht beim naechsten Refresh verloren gehen
- die Synchronisation mit dem Server auf den vereinbarten Zeitstempel-basierten Refresh umstellen
- Antworten vom Server anhand der Nachrichten-ID mit dem lokalen Zustand mergen und Duplikate robust vermeiden
- einen Filterknopf fuer gelesene Nachrichten in der Inbox-UI einfuehren
- den Default-Zustand so umsetzen, dass gelesene Nachrichten beim ersten Oeffnen ausgeblendet sind
- die Darstellung so anpassen, dass gelesene Nachrichten in der eingeblendeten Ansicht erkennbar bleiben
- die Client-Tests fuer lokale Retention, Filterung, Refresh und serverseitig verschwundene, aber lokal bekannte Nachrichten erweitern

Fachliche Entscheidung:

- Der Client speichert lokal nicht nur bekannte und gelesene Nachrichten-IDs, sondern den bekannten Inbox-Bestand inklusive Nachrichtendaten.
- Refreshes arbeiten immer inkrementell mit dem lokal spaetesten `createdAt` als `fromTime`; der Erstabruf faellt dadurch automatisch auf `0` zurueck.
- Antworten vom Server werden nach Nachrichten-ID in den lokalen Bestand gemergt; bei Duplikaten gewinnt die frisch gelieferte Serverversion.
- Standardmaessig blendet die Inbox nur Nachrichten aus, die bereits vor dem aktuellen Oeffnen als gelesen bekannt waren.
- Sobald gelesene Nachrichten eingeblendet werden, bleiben sie in der Ansicht klar als gelesen markiert.

Erwartetes Ergebnis:

- Die Inbox zeigt standardmaessig nur ungelesene Nachrichten
- Gelesene Nachrichten und bereits lokal bekannte serverseitig entfernte Nachrichten bleiben im Browser verfuegbar
- Der Client synchronisiert effizient und bleibt gegen doppelt gelieferte Grenzfaelle stabil
