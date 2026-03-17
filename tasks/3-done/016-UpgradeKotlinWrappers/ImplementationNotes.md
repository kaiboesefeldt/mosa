Task 016 wurde mit einem isolierten Wrapper-Upgrade im bestehenden Kotlin-, React- und Toolchain-Rahmen umgesetzt.

Ermittelte Zielversionen aus den offiziellen Maven-Metadaten:

- `org.jetbrains.kotlin-wrappers:kotlin-react:2026.3.12-19.2.4`
- `org.jetbrains.kotlin-wrappers:kotlin-react-dom:2026.3.12-19.2.4`
- `org.jetbrains.kotlin-wrappers:kotlin-browser:2026.3.12`

Repo-bezogene Kompatibilitaetseinschaetzung:

- Das Upgrade war ohne React-, Kotlin- oder Toolchain-Anhebung moeglich.
- Notwendig waren nur API-Anpassungen an `key`-Typen (`Key?` statt nackter `String`) und an `ChangeEvent`-Zugriffe (`currentTarget` mit zwei Typargumenten).
- Es waren keine funktionalen UI-Aenderungen noetig.

Ergaenzte Regressionsabsicherung:

- Bestehende Client-Regressionstests fuer Composer-Interaktion und Listen-Rendering blieben grün.
- Zusaetzlich wurde `PasswordPromptOverlayTest` ergaenzt, damit der geaenderte Eingabe-Pfad des Passwort-Prompts ueber beobachtbares Verhalten abgesichert ist.

Relevante Verifikation:

- `YARN_CACHE_FOLDER=/tmp/mosa-yarn-cache ./gradlew :client:jsTest`
- `YARN_CACHE_FOLDER=/tmp/mosa-yarn-cache ./gradlew check`
- `YARN_CACHE_FOLDER=/tmp/mosa-yarn-cache ./gradlew :client:jsTest --warning-mode all`

Verbleibende Warnungen im Verifikationslauf:

- Gradle meldet in `client/build.gradle.kts:22` weiterhin die deprecated `archives`-Konfiguration. Das ist bestehender Build-Kontext und nicht Teil dieses Wrapper-Upgrades.
- `./gradlew check` zeigt bestehende JVM-/Tooling-Warnungen zu `sun.misc.Unsafe`.
- Der Produktions-Webpack-Lauf meldet weiterhin Asset-Size-Warnungen fuer `mosa-client.js`.

Diese Warnungen wurden durch das Wrapper-Upgrade nicht neu eingefuehrt.
