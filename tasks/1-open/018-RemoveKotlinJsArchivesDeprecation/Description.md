Der Clean-Build erzeugt unter Gradle `9.4.0` weiterhin eine Gradle-Deprecation im Client-Projekt:

- `The archives configuration has been deprecated for artifact declaration. This will fail with an error in Gradle 10.`

Die Warnung wird derzeit beim Konfigurieren des Kotlin-Multiplatform-JS-Builds im Client sichtbar und ist kein einfacher lokaler API-Aufruf, der mit einem kleinen Buildscript-Patch verschwindet.

Ziel ist, die verbleibende Gradle-Deprecation so zu beseitigen, dass der Client-Build auch unter Gradle `10` kompatibel bleibt, ohne das Build-Verhalten unbeabsichtigt zu aendern.

Die Aufgabe umfasst mindestens diese Punkte:

- Die genaue Ursache der `archives`-Nutzung im Zusammenspiel von Kotlin Multiplatform, JS-Target und aktuellen Plugins eingrenzen.
- Pruefen, ob die Warnung durch eine Buildscript-Anpassung, ein geaendertes JS-Target-Setup oder ein Plugin-Upgrade verschwindet.
- Die minimal notwendige Migration im Client-Build umsetzen.
- Mit `./gradlew clean build --warning-mode all` verifizieren, dass diese Deprecation nicht mehr erscheint.

Besonders zu klaeren:

- Ob die Warnung direkt aus dem verwendeten Kotlin-Plugin stammt oder aus einer veralteten JS-/Browser-Konfiguration im Projekt.
- Ob ein Kotlin-Plugin-Upgrade notwendig ist und mit den Repo-Vorgaben zu Kotlin `2.2.x` vereinbar waere.
- Ob fuer die Beseitigung der Warnung Aenderungen an Artefakt-Erzeugung, Distribution oder Packaging im Client noetig sind.
