Ziel

Die Content-bezogenen Renderbloecke der Connected-View sollen in kleinere Dateien zerlegt werden, damit `IdentityConnectedView` nicht mehr zugleich Shell, Feed-Renderer und Composer-Ansicht ist.

Scope

- Die Renderteile fuer Topic-Spalte, Post-Spalte, Reply-Spalte sowie die zugehoerigen Karten und Popover aus `IdentityConnectedView.kt` extrahieren.
- Reply-Composer, Complaint-Composer, Floating-Composer-Button und Composer-Dialog so auslagern, dass Content-Interaktionen strukturell von der Gesamtshell getrennt sind.
- Gemeinsame Rendering-Helfer fuer Zeitstempel, Karten oder Aktionsleisten nur dann zusammenfassen, wenn die Benennung klarer wird und keine uebergreifende Sammeldatei entsteht.
- Die Verdrahtung zu Callbacks und State so belassen oder vereinfachen, dass das sichtbare Verhalten gleich bleibt.

Nicht Teil dieser Subtask

- Admin-Overlays und Inbox-Bereiche extrahieren.
- Fachliche Aenderungen an Publishing-, Reply- oder Complaint-Ablauf.
- Abschlusspruefung des Gesamtsystems.

Akzeptanzkriterien

- Topic-, Post- und Reply-Rendering liegen nicht mehr gesammelt in `IdentityConnectedView.kt`.
- Composer-UI und Content-Karten sind in fachlich passend benannten Dateien organisiert.
- Die Hauptdatei beschreibt fuer den Content-Bereich vor allem noch, welche Daten und Callbacks wohin gehen.
- Das bestehende Verhalten fuer Oeffnen, Schliessen und Absenden der Composer bleibt automatisiert oder ueber bestehende Tests abgesichert.
