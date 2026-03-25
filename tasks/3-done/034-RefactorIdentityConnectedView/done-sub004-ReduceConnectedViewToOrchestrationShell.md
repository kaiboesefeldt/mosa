Ziel

Nach den vorangehenden Extraktionen soll `IdentityConnectedView` auf eine ueberschaubare Orchestrierungsrolle reduziert werden.

Scope

- Die verbleibende Datei auf globale Verdrahtung, Dependency-Zugriff, uebergeordnete Zustandskoordination und Hook-Aufrufe zurueckschneiden.
- Uebergreifende Shell-Aspekte wie mobile Pane-Umschaltung, Profil-Overlay-Rahmen, Scroll- und IntersectionObserver-Verdrahtung nur dort belassen, wo sie mehrere Teilbereiche koordinieren.
- Wo sinnvoll, kleinere lokale Hilfsfunktionen in passend benannte Dateien verschieben, damit die Hauptdatei ohne lange Spruenge lesbar bleibt.
- Die finale Struktur so ordnen, dass neue Aenderungen an einzelnen Teilbereichen ohne erneutes Anwachsen der Hauptdatei moeglich sind.

Nicht Teil dieser Subtask

- Neue Produktfunktionalitaet.
- Erneute Zerlegung bereits sauber ausgelagerter Unterkomponenten ohne erkennbaren Mehrwert.
- Der verpflichtende Abschlusscheck.

Akzeptanzkriterien

- `IdentityConnectedView.kt` ist deutlich kleiner als vor Beginn der Aufgabe und hat eine klar erkennbare Shell-Rolle.
- Die Datei enthaelt nicht mehr gleichzeitig grosse Mengen an Inline-Rendering, lokale Zustandslogik und fachbereichsspezifische Hilfsfunktionen.
- Ein neuer Bearbeiter kann aus der Hauptdatei schnell erkennen, welche Teilkomponenten fuer Content, Moderation, Admin und Shell zustaendig sind.
- Die sichtbare Funktion bleibt unveraendert.
