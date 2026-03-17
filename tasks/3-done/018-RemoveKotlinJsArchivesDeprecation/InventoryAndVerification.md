Stand: 2026-03-17

Befund im aktuellen Repo-Stand:

- Die urspruenglich beschriebene Gradle-Deprecation `The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10.` ist im aktuellen Repo-Stand nicht mehr reproduzierbar.
- Der relevante Client-Build laeuft unter Gradle `9.4.0` und dem aktuell im Repo eingetragenen Kotlin-Gradle-Plugin `2.3.20`, ohne dass die `archives`-Deprecation in den geprueften Client-/JS-Pfaden erscheint.
- Damit ist fuer Task 018 keine weitere produktive Buildscript-Migration mehr noetig. Zusetzliche Aenderungen im Client-Build wuerden aktuell nur spekulativen Umbau bedeuten.

Reproduktion und Eingrenzung:

- `./gradlew :client:tasks --warning-mode all`: erfolgreich, keine `archives`-Deprecation schon bei reiner Projektkonfiguration.
- `./gradlew :client:jsJar --warning-mode all`: erfolgreich, keine `archives`-Deprecation.
- `./gradlew :client:jsBrowserDistribution --warning-mode all`: erfolgreich, keine `archives`-Deprecation.
- `./gradlew :client:build --warning-mode all`: erfolgreich, keine `archives`-Deprecation.
- `./gradlew clean :client:build --warning-mode all`: erfolgreich, keine `archives`-Deprecation auch im sauberen Client-Build.

Bewertung der Ursache:

- Der aeltere Befund aus der Aufgabenbeschreibung und aus frueheren Upgrade-Notizen passt zu einer inzwischen beseitigten Interaktion im Kotlin-Multiplatform-/JS-Buildpfad, nicht zu einer noch aktiven projektspezifischen `archives`-Nutzung im aktuellen `client/build.gradle.kts`.
- Im heutigen Repo-Stand zeigt kein gepruefter Client-Lauf mehr einen Hinweis auf eine noch lokal noetige Buildscript-Anpassung.
- Die kleinste tragfaehige Umsetzung fuer Task 018 ist deshalb, keine weitere Produktionsaenderung vorzunehmen und den aktuellen Stand als wirksame Aufloesung der frueheren Deprecation zu dokumentieren.

Abschliessende Verifikation:

- `./gradlew clean build --warning-mode all`: erfolgreich.
- Die vormals offene `archives`-Deprecation erscheint auch im Repo-weiten Verifikationslauf nicht mehr.

Verbleibende Warnungen ausserhalb des Task-Scopes:

- `sun.misc.Unsafe`-Warnungen aus externer Tooling-Infrastruktur:
  - aus `kotlin-compiler-embeddable 2.2.21`, das ueber `org.jmailen.kotlinter` hereinkommt
  - aus Byte Buddy waehrend Server-Tests
- Node/Yarn-Warnungen waehrend `kotlinNpmInstall`, unter anderem zu `url.parse()` und `punycode`
- Webpack-Bundlegroessenwarnungen fuer `mosa-client.js`
- JVM-/Test-Umfeldwarnungen wie CDS-Hinweis sowie die bekannten Shadow-JAR-Duplikathinweise

Fazit:

- Task 018 ist im aktuellen technischen Zustand des Repos inhaltlich erfuellt.
- Es war keine weitere Aenderung am produktiven Client-Build noetig.
- Fuer den Hauptordner bleibt nur noch der formale Statuswechsel nach menschlicher Freigabe offen.
