Ziel dieser Subtask ist, `client/src/jsMain/kotlin/straightway/mosa/client/Main.kt` so zu konsolidieren, dass der App-Einstieg wieder klar von der Legal-Dialog- und Overlay-Logik getrennt ist.

Ausgangsproblem:

- Die Datei mischt App-Bootstrap, globale Service-Aufloesung, Session-Screen-Umschaltung und die komplette Legal-Dialog-Interaktion.
- Dadurch sind Einstiegspunkt und spezielle Legal-Oberflaechenlogik unnoetig eng gekoppelt.

Erwartetes Ergebnis:

- Der eigentliche Einstieg bleibt klein und auf Bootstrap sowie Root-Rendering fokussiert.
- Die Legal-FAB-, Overlay- und Vergleichslogik liegt in klar benannten eigenen Komponenten oder Hilfstypen.
- Die bestehende sichtbare Funktion bleibt unveraendert.
