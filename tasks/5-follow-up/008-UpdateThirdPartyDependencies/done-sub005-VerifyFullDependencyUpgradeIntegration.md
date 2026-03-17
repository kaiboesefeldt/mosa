Ziel

Die Gesamtintegration aller aktualisierten 3rd-Party-Abhaengigkeiten pruefen und offen gebliebene Upgrade-Kandidaten nachvollziehbar dokumentieren.

Scope

- Den vollen Qualitaetslauf des Repos mit `./gradlew check` ausfuehren.
- Sicherstellen, dass Root-Build, `shared`, `client` und `server` zusammen mit den aktualisierten Versionen konsistent funktionieren.
- Fuer nicht umgesetzte oder bewusst verschobene Updates die technischen Gruende und den empfohlenen naechsten Schritt festhalten.

Nicht Teil dieser Subtask

- Weitere neue Abhaengigkeits-Upgrades ausserhalb der in den vorherigen Subtasks festgelegten Arbeitsliste.

Akzeptanzkriterien

- `./gradlew check` laeuft erfolgreich mit den aktualisierten Abhaengigkeiten.
- Sichtbare Integrationsprobleme zwischen Build, Shared-Code, Client und Server sind behoben oder klar dokumentiert.
- Fuer jede bewusst nicht angehobene Abhaengigkeit gibt es eine kurze, nachvollziehbare Begruendung.
- Falls Restarbeiten bleiben, sind sie so beschrieben, dass daraus direkt eine neue Aufgabe oder eine Wiedervorlage entstehen kann.

Pragmatisch geklaert

- Diese Subtask ist der verpflichtende Abschlusstest der Gesamtintegration. Sie darf nicht auf Teilmodul-Checks reduziert werden, weil gerade Versionsupdates haeufig erst im Zusammenspiel der Module, Toolchains und Testumgebungen scheitern.

Ergebnis vom 2026-03-16

- Voller Qualitaetslauf erfolgreich: `./gradlew check`
- Gradle-Wrapper aktualisiert: `9.1.0` -> `9.4.0`
- Waehren der Umsetzung aufgetretene Upgrade-Folgen:
  - Das JS-Lockfile musste nach den NPM-Updates mit `./gradlew kotlinUpgradeYarnLock` aktualisiert werden.
  - `kotlinx-serialization-json` `1.10.0` war mit der fest vorgegebenen Kotlin-`2.2.21`-Toolchain in `shared` nicht stabil lauffaehig und wurde deshalb auf `1.9.0` belassen.
  - `ktor-client-js` `3.4.1` fuehrte im Client zu einem reproduzierbaren internen Kotlin/JS-Compilerfehler und wurde deshalb auf `3.3.1` belassen.
  - Mit der aktualisierten Server-Testkombination mussten vereinzelte Assertions auf `kotlin.test.assertNull` bzw. `kotlin.test.assertNotNull` umgestellt werden.
  - Das Kotlinter-Upgrade auf `5.4.2` hat automatisch formatbedingte Anpassungen in bestehenden Kotlin-Dateien erzeugt; der anschliessende Vollcheck lief grün.
- Bewusst nicht angehobene Kandidaten:
  - Kotlin `2.2.21` bleibt wegen Repo-Vorgabe auf der `2.2`-Linie.
  - Koin bleibt auf `4.1.1`, weil nur ein neuerer Release Candidate verfuegbar war.
  - Exposed `1.1.1`, Lucene `10.x` und neuere Kotlin-Wrappers wurden nicht in diese Aufgabe gezogen, weil dafuer zusaetzliche dedizierte Migrationsarbeit mit hoeherem API- und Verhaltensrisiko noetig waere.
- Wrapper-Verifikation:
  - `./gradlew wrapper --gradle-version 9.4.0`
  - anschliessend `./gradlew check` erfolgreich mit Gradle `9.4.0`
