# Inventar und Verifikation

## Zielversion

- Zielversion ist Java 25.
- Verifiziert ueber OpenJDK: JDK 25 ist seit dem 16. September 2025 allgemein verfuegbar.
- Verifiziert ueber die Oracle Java SE Support Roadmap: Java 25 ist die naechste bzw. aktuelle LTS-Linie nach Java 21.

## Inventarisierte Repo-Stellen

- `gradle.properties`: zentrale Projekt-Properties `mosaJvmToolchainVersion` und `mosaJvmBytecodeTarget`.
- `server/build.gradle.kts`: JVM-Toolchain sowie Java/Kotlin-Bytecode-Kompatibilitaet fuer das Servermodul.
- `shared/build.gradle.kts`: JVM-Toolchain fuer das Shared-JVM-Target.
- `gradle/wrapper/gradle-wrapper.properties`: Gradle 9.4.0.
- `client/build.gradle.kts`: kein JVM-Toolchain-Eintrag, aber relevante Build-Deprecation im Kotlin/JS-Pfad sichtbar.

## Kompatibilitaetsbefund

- Gradle 9.4.0 unterstuetzt Java 25 sowohl fuer Toolchains als auch zum Ausfuehren von Gradle.
- Das Repo verwendet Kotlin Gradle Plugin 2.2.21. Damit ist kein separates Kotlin-Upgrade fuer die Toolchain-Umstellung erzwungen worden.
- Kotlin 2.2.21 unterstuetzt noch kein `JVM_25`-Bytecode-Target. Deshalb wird das Bytecode-Target explizit auf `JVM_24` gesetzt, waehrend die Toolchain selbst auf Java 25 laeuft.
- Im Servermodul wird zusaetzlich die Java-Compile-Kompatibilitaet ebenfalls auf 24 ausgerichtet, damit Kotlin- und Java-Tasks unter Gradles Zielvalidierung konsistent bleiben.
- Unter Java 25 erscheint waehrend Kotlin-Kompilation weiterhin eine `sun.misc.Unsafe`-Warnung aus `kotlin-compiler-embeddable`. Das ist ein Upstream-Thema des eingesetzten Kotlin-Stands, kein Repo-Codepfad.
- Fuer Server-Tests und lokale `JavaExec`-Laufpfade wird `--enable-native-access=ALL-UNNAMED` gesetzt, damit Lucene unter Java 25 keine Restricted-Native-Access-Warnung mehr ausgibt.

## Warnungsstand

- Verbleibend: Gradle-Deprecation zur `archives`-Konfiguration aus dem Kotlin/JS-Plugin beim Client-Projekt.
- Verbleibend: `sun.misc.Unsafe`-Warnung aus dem Kotlin-Compiler unter Java 25.
- Verbleibend: `sun.misc.Unsafe`-Warnung aus Byte Buddy waehrend serverseitiger Tests.
- Verbleibend: CDS-VM-Warnung (`Sharing is only supported for boot loader classes...`) waehrend instrumentierter Server-Tests.
- Diese Warnungen werden nicht durch produktiven Projektcode ausgeloest; eine Behebung wuerde voraussichtlich ein separates Kotlin-/Plugin- oder Testinstrumentierungs-Upgrade erfordern und ist deshalb ausserhalb des zugeschnittenen Scopes dokumentiert statt implizit mitgezogen.
