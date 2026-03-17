Ziel

Eine belastbare Ausgangsliste aller direkt deklarierten 3rd-Party-Abhaengigkeiten und Plugins im Repo erstellen und fuer jede Position eine aktuelle stabile Zielversion festlegen.

Scope

- Root-Build, Version-Catalog und die Gradle-Build-Dateien von `server`, `client` und `shared` sichten.
- Direkt deklarierte Gradle-Plugins, JVM/Kotlin-Bibliotheken und NPM-Pakete erfassen.
- Fuer jede direkt deklarierte Abhaengigkeit pruefen, welche aktuelle stabile Version sinnvoll ist.
- Auffaellige Risiken wie Major-Upgrades, bekannte Breaking-Change-Zonen oder unklare Kompatibilitaet sichtbar markieren.

Nicht Teil dieser Subtask

- Produktive Versionsaenderungen in Build-Dateien.
- Code-Anpassungen zur Behebung von API-Breaks.

Akzeptanzkriterien

- Es gibt eine vollstaendige Arbeitsliste der direkt deklarierten 3rd-Party-Abhaengigkeiten und Plugins im Repo.
- Fuer jede Position ist eine konkrete Zielversion oder eine begruendete Nicht-Aktualisierung festgehalten.
- Kritische Upgrade-Kandidaten sind so markiert, dass die Folge-Subtasks gezielt damit arbeiten koennen.

Ergebnis vom 2026-03-16

- Root-Plugins:
  - Kotlin JVM, Kotlin Multiplatform, Kotlin Serialization Plugin: bleiben auf `2.2.21`, weil das Repo laut Agent-Regeln auf Kotlin `2.2` festgelegt ist.
  - Kotlinter: `5.1.1` -> `5.4.2`
  - Shadow: `9.0.0` -> `9.4.0`
  - Kover Aggregation: `0.9.3` -> `0.9.7`
- Shared:
  - `kotlinx-serialization-json`: bleibt auf `1.9.0`; `1.10.0` fuehrte mit Kotlin `2.2.21` in `shared:compileKotlinJs` zu einem reproduzierbaren Compilerfehler.
  - `kotlinx-coroutines-test`: bleibt auf `1.10.2`
- Client:
  - Koin Core: bleibt auf `4.1.1`; im Registry-Stand war nur `4.2.0-RC2` neuer, also keine neuere stabile Version.
  - `kotlinx-coroutines-core`: bleibt auf `1.10.2`
  - `ktor-client-js`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`, `ktor-client-mock`: bleiben auf `3.3.1`; `3.4.1` verursachte reproduzierbar einen internen Kotlin/JS-Compilerfehler in `:client:compileKotlinJs`.
  - `kotlinx-serialization-json`: bleibt auf `1.9.0`; gleicher Kompatibilitaetsgrund wie in `shared`.
  - Kotlin Wrappers (`kotlin-react`, `kotlin-react-dom`, `kotlin-browser`): bleiben auf `2025.11.1-19.2.0`; keine notwendige Upgrade-Arbeit fuer gruene Checks, und ein Sprung haette Frontend-API-Break-Risiko ohne direkten Mehrwert fuer diese Aufgabe.
  - `@dicebear/core`: `9.2.4` -> `9.4.0`
  - `@dicebear/adventurer-neutral`: `9.2.4` -> `9.4.0`
  - `@fortawesome/fontawesome-free`: `7.1.0` -> `7.2.0`
  - `html2canvas`: bleibt auf `1.4.1`
  - `jspdf`: bleibt auf `4.2.0`
  - `@testing-library/react`: `16.3.0` -> `16.3.2`
  - `@testing-library/user-event`: bleibt auf `14.6.1`
  - `@testing-library/dom`: bleibt auf `10.4.1`
  - `karma-source-map-support`: bleibt auf `1.4.0`
- Server:
  - Ktor Server/Client JVM: `3.3.1` -> `3.4.1`
  - Koin Ktor / Logger: bleibt auf `4.1.1`; keine neuere stabile Version verfuegbar.
  - Exposed: bleibt auf `1.0.0-beta-2`; `1.1.1` waere ein groesserer ORM-Sprung und wurde fuer diese Aufgabe bewusst nicht ohne dedizierte Migrationsarbeit gezogen.
  - SQLite JDBC: `3.50.3.0` -> `3.51.3.0`
  - Lucene: bleibt auf `9.12.3`; `10.x` wurde als separater Major-Kandidat fuer spaetere Migrationspruefung belassen.
  - DJL BOM: `0.35.0` -> `0.36.0`
  - Jackson Kotlin / JSR310 / YAML: `2.21.0` bzw. `2.20.1` -> `2.21.1`
  - Bouncy Castle: `1.82` -> `1.83`
  - Auth0 JWT: `4.5.0` -> `4.5.1`
  - Kotlin Logging: `7.0.13` -> `8.0.01`
  - Logback Classic: `1.5.20` -> `1.5.32`
  - MockK: `1.14.6` -> `1.14.9`
  - Micrometer Core: `1.16.1` -> `1.16.4`
  - JNA / JNA Platform: bleiben auf `5.18.1`
  - Commons Validator: bleibt auf `1.10.1`
  - `kotlinx-cli-jvm`: bleibt auf `0.3.6`
  - Mordant / Mordant Markdown: bleiben auf `3.0.2`
