Ziel

Die Gesamtintegration der Beschwerdefunktion soll ueber alle betroffenen Komponenten abgesichert werden.

Art des Abschlusstests

- Primaer vollautomatisiert ueber bestehende Gradle-Tests.
- Ergaenzend ist ein kurzer manueller Repo- und Laufzeit-Check erlaubt, falls ein zentraler UI-Pfad nicht sinnvoll automatisiert abgedeckt ist.

Betroffene Komponenten

- `shared` Complaint-DTOs und Signatur-Payloads
- Server-Endpunkte, Validierung, SQLite-Persistenz und Admin-Entscheidungslogik
- bestehende Admin-Message-Infrastruktur fuer Rueckmeldungen
- Client-Beschwerdeaktion fuer normale Nutzer
- Client-Moderations-Queue fuer Admins

Vorbedingungen

- Alle vorherigen Subtasks sind umgesetzt.
- Die Anwendung baut lokal.
- Testdaten oder Testdoubles decken mindestens einen fremden Beitrag, einen eigenen Beitrag und einen Admin-Zugang ab.

Durchfuehrung

1. Fuehre gezielte automatisierte Tests fuer die neu eingefuehrte Complaint-Logik in `shared`, `server` und `client` aus.
2. Fuehre anschliessend den verpflichtenden Gesamtcheck `./gradlew check` aus.
3. Verifiziere in den Testergebnissen mindestens diese Pfade:
   - Nutzer kann eine signierte Beschwerde fuer fremden Beitrag absenden.
   - Zweite offene Beschwerde fuer denselben Beitrag scheitert.
   - Nach `CONFIRMED` ist spaeter wieder eine neue Beschwerde fuer denselben Beitrag moeglich.
   - Nach `REJECTED` ist eine weitere Beschwerde gesperrt.
   - Nach `DELETED` ist eine weitere Beschwerde gesperrt.
   - Admin-Entscheidung erzeugt eine private Rueckmeldung.
   - Offene Queue und Badge aktualisieren sich konsistent.
4. Wenn fuer einen zentralen UI-Pfad keine ausreichende Automatisierung vorhanden ist, dokumentiere einen kurzen manuellen Check mit klaren Schritten und beobachtetem Ergebnis.

Erfolgskriterien

- `./gradlew check` ist erfolgreich.
- Die wichtigsten funktionalen Pfade der Gesamtloesung sind nachweislich abgedeckt.
- Etwaige verbleibende manuelle Restpruefungen sind explizit benannt und knapp dokumentiert.
