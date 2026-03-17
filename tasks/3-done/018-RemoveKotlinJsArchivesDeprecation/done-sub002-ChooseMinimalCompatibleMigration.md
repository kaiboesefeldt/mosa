Ziel

Auf Basis der Reproduktion und Eingrenzung soll die kleinste tragfaehige Migration bestimmt werden, mit der die `archives`-Deprecation im Client-/JS-Build verschwindet, ohne den Task stillschweigend zu einem groesseren Kotlin-Upgrade auszuweiten.

Scope

- Die moeglichen Loesungswege gegeneinander abgrenzen: Buildscript-Anpassung, geaendertes JS-Target-Setup oder eng begrenztes Plugin-Upgrade.
- Pruefen, ob eine Loesung innerhalb der Repo-Vorgaben zu Kotlin `2.2.x` und Gradle `9.4.0` tragfaehig ist.
- Falls ein Plugin-Upgrade notwendig erscheint, den minimalen Upgrade-Schnitt bestimmen und pruefen, ob dadurch weitere, fachlich getrennte Upgrade-Arbeiten ausgeloest wuerden.
- Die bevorzugte Loesung mit kurzer Begruendung im Aufgabenordner festhalten.

Nicht Teil dieser Subtask

- Die eigentliche produktive Umsetzung.
- Breitere Modernisierungen des Client-Builds ohne direkten Bezug zur Deprecation.
- Das Schliessen unabhaengiger Warnungen ausserhalb des betroffenen Client-/JS-Buildpfads.

Akzeptanzkriterien

- Es gibt eine klar benannte bevorzugte Migrationsstrategie.
- Die Entscheidung ist gegen die naheliegenden Alternativen abgegrenzt.
- Eventuelle Blocker, Risiken oder noetige Folgeaufgaben sind knapp dokumentiert.
