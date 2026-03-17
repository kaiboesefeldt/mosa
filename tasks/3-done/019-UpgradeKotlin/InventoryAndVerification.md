Stand: 2026-03-17

Zielversion:

- Kotlin `2.3.10` ist laut offizieller Kotlin-Release-Dokumentation der aktuelle stabile Release-Stand.

Repo-Inventar der Kotlin-Versionsreferenzen:

- Root-Plugins in `build.gradle.kts` verwenden `2.3.10` fuer `kotlin("jvm")`, `kotlin("multiplatform")` und `org.jetbrains.kotlin.plugin.serialization`.
- `gradle/libs.versions.toml` fuehrt `kotlin = "2.3.10"` als zentrale Versionsreferenz.
- Weitere harte Kotlin-Plugin-Versionen wurden im aktiven Buildsetup nicht gefunden.

Kompatibilitaetseinschaetzung:

- Laut offizieller Kotlin-Gradle-Dokumentation ist KGP `2.3.10` voll unterstuetzt mit Gradle `7.6.3` bis `9.0.0`.
- Gradle `9.4.0` liegt damit ausserhalb des offiziell voll unterstuetzten Bereichs.
- Im vorliegenden Repo ist diese Kombination jedoch faktisch lauffaehig: `./gradlew check` und `./gradlew check --warning-mode all` liefen erfolgreich durch.
- Die aktuelle Java-Konfiguration des Repos bleibt dabei unveraendert: Toolchain `25`, Bytecode-Ziel `24`.

Tatsaechlich noetige Upgrade-Anpassungen:

- Root-Build und Versionskatalog wurden von Kotlin `2.2.21` auf `2.3.10` angehoben.
- Im Server wurde fuer `InetAddress.maxPrefixLength()` eine explizite Fallback-Branch fuer unbekannte Java-Sealed-Untertypen ergaenzt, weil Kotlin `2.3.x` hier eine neue Vollstaendigkeitswarnung meldet, die in Kotlin `2.4` zum Fehler wird.
- Im Client- und JS-Testcode wurden nur die durch Kotlin `2.3.10` neu gemeldeten Warnungen bereinigt:
  - unnoetige DOM-Casts entfernt
  - redundante JS-Konvertierungen entfernt
  - ein unnoetiger Safe-Call im Test entfernt
- Fuer den JS-Teil war zusaetzlich ein `kotlinUpgradeYarnLock`-Lauf noetig, damit der Kotlin/JS-Lockfile-Stand zum neuen Plugin passt.

Ergebnis der Verifikation:

- `YARN_CACHE_FOLDER=/tmp/yarn-cache ./gradlew check`: erfolgreich.
- `YARN_CACHE_FOLDER=/tmp/yarn-cache ./gradlew check --warning-mode all`: erfolgreich.
- Es bleiben keine unbegruendeten neuen Kotlin-Compilerwarnungen aus dem Upgrade im Repo-Code offen.

Verbleibende Warnungen und Einordnung:

- Die bekannte Gradle-`archives`-Deprecation im Client-Kontext bleibt bestehen. Sie ist bereits separat als Task `018-RemoveKotlinJsArchivesDeprecation` geschnitten und wurde durch dieses Kotlin-Upgrade weder neu eingefuehrt noch stillschweigend mit erledigt.
- Unter JDK `25` erscheinen weiterhin `sun.misc.Unsafe`-Warnungen aus externen Komponenten waehrend Build/Test, unter anderem aus `byte-buddy` und aus einem eingebetteten Kotlin-Compiler-Jar im Gradle-Cache. Das sind Upstream-/Toolingwarnungen und keine direkt vom Repo verursachten Kotlin-Quellwarnungen.
- Webpack meldet weiterhin Bundle-Groessenwarnungen fuer `mosa-client.js`. Diese sind vom Kotlin-Upgrade unabhaengig und kein Kotlin-Kompatibilitaetsfehler.

Fazit:

- Task 019 ist technisch umgesetzt fuer den tatsaechlich aktuellen Kotlin-Stand `2.3.10`.
- Die wesentliche Einschraenkung bleibt die fehlende offizielle Vollfreigabe von Kotlin `2.3.10` fuer Gradle `9.4.0`; im Repo zeigte sich jedoch keine funktionale Regression.
