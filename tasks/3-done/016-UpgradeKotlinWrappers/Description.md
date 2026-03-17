Die direkt verwendeten Kotlin-Wrappers im Client sollen auf die zum Bearbeitungszeitpunkt neueste stabile Version angehoben werden.

Betroffen sind mindestens `kotlin-react`, `kotlin-react-dom` und `kotlin-browser`.

Ziel ist, den Frontend-Stack auf einen aktuelleren Wrapper-Stand zu bringen, ohne das beobachtbare UI-Verhalten oder die Testbarkeit unbeabsichtigt zu verschlechtern.

Die Aufgabe ist bewusst separat von der allgemeinen Dependency-Aktualisierung geschnitten, weil Wrapper-Upgrades haeufig breite API-Aenderungen im React- und Browser-Interop-Code nach sich ziehen.

React-Upgrades, Kotlin-Upgrades und Toolchain-Aenderungen sind nicht Teil dieser Aufgabe. Wenn sich zeigt, dass ein Wrapper-Upgrade ohne solche zusaetzlichen Aenderungen nicht sinnvoll oder nicht stabil umsetzbar ist, ist das als Blocker oder Folgeaufgabe zu dokumentieren, statt diese Aenderungen stillschweigend mitzuziehen.

Die Aufgabe umfasst mindestens diese Punkte:

- Vor dem eigentlichen Upgrade die bestehende Testabdeckung fuer den betroffenen Client-Code so weit staerken, dass die Wahrscheinlichkeit unerkannter Regressionen nach dem Wrapper-Upgrade sehr gering ist.
- Die aktuell verwendeten Wrapper-APIs im Client inventarisieren.
- Die Zielversion als neueste stabile Version festlegen und relevante Breaking Changes bewerten.
- Noetige Anpassungen an Produktionscode, Test-Harnesses und Build-Konfiguration minimal umsetzen.
- Die bestehenden Client-Tests grün bekommen.
- Den Vollcheck des Repos mit `./gradlew check` ausfuehren.
- Den relevanten Verifikationslauf mit `--warning-mode all` ausfuehren.

Besonders zu klaeren:

- Ob Hook-, Props-, Event- oder Children-APIs angepasst werden muessen.
- Ob Test-Hilfscode oder `@testing-library`-Integration durch Wrapper-Aenderungen betroffen ist.
- Ob sich das Wrapper-Upgrade innerhalb des bestehenden React-, Kotlin- und Toolchain-Rahmens sauber isoliert umsetzen laesst.

Abnahme:

- Die direkt verwendeten Kotlin-Wrappers im Client sind auf die zum Bearbeitungszeitpunkt neueste stabile Version angehoben oder es wurde verifiziert, dass dies bereits vor der Bearbeitung der Fall war.
- Die Testabdeckung fuer den vom Upgrade betroffenen Client-Code ist vor oder im Zuge der Aufgabe so weit verbessert worden, dass die Wahrscheinlichkeit unerkannter Regressionen fuer die geaenderten Pfade sehr gering ist.
- `./gradlew check` laeuft mit dem aktualisierten oder verifizierten Stand erfolgreich durch.
- Der relevante Verifikationslauf wird mit `--warning-mode all` ausgefuehrt.
- Der Abschluss ist so weit wie sinnvollerweise moeglich frei von Build-Warnungen; verbleibende bewusst nicht behobene Warnungen sind mit kurzer Begruendung dokumentiert.
- Falls sich zeigt, dass ein Upgrade nur zusammen mit React-, Kotlin- oder Toolchain-Aenderungen sinnvoll waere, ist dies als Blocker oder Folgeaufgabe dokumentiert und nicht implizit innerhalb dieser Aufgabe miterledigt worden.
