Die direkt verwendeten Kotlin-Wrappers im Client sollen auf eine aktuelle stabile Hauptlinie angehoben werden.

Betroffen sind mindestens `kotlin-react`, `kotlin-react-dom` und `kotlin-browser`.

Ziel ist, den Frontend-Stack auf einen aktuelleren Wrapper-Stand zu bringen, ohne das beobachtbare UI-Verhalten oder die Testbarkeit unbeabsichtigt zu verschlechtern.

Die Aufgabe ist bewusst separat von der allgemeinen Dependency-Aktualisierung geschnitten, weil Wrapper-Upgrades haeufig breite API-Aenderungen im React- und Browser-Interop-Code nach sich ziehen.

Die Aufgabe umfasst mindestens diese Punkte:

- Die aktuell verwendeten Wrapper-APIs im Client inventarisieren.
- Die Zielversion festlegen und relevante Breaking Changes bewerten.
- Noetige Anpassungen an Produktionscode, Test-Harnesses und Build-Konfiguration minimal umsetzen.
- Die bestehenden Client-Tests grün bekommen.
- Den Vollcheck des Repos mit `./gradlew check` ausfuehren.

Besonders zu klaeren:

- Ob Hook-, Props-, Event- oder Children-APIs angepasst werden muessen.
- Ob Test-Hilfscode oder `@testing-library`-Integration durch Wrapper-Aenderungen betroffen ist.
- Ob der Upgrade zusammen mit einer groesseren React- oder Kotlin/JS-Toolchain-Aenderung erfolgen sollte oder bewusst isoliert bleiben muss.
