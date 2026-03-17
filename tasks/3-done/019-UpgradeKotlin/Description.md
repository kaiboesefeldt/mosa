Die im Repo verwendete Kotlin-Version soll auf den nach aktuellem Stand neuesten stabilen Release-Stand angehoben werden.

Ziel ist, Compiler, Gradle-Plugin und Kotlin-Multiplatform-Build auf einen aktuellen Kotlin-Stand zu bringen, ohne das beobachtbare Verhalten des Systems, die Teststabilitaet oder die lokale Entwicklererfahrung unbeabsichtigt zu verschlechtern.

Die Aufgabe ist bewusst separat vom JDK-/JVM-Toolchain-Upgrade geschnitten, damit Java- und Kotlin-Risiken getrennt bewertet, umgesetzt und verifiziert werden koennen.

Die Aufgabe setzt voraus, dass der Java-/JVM-Toolchain-Stand des Repos bereits bewusst bewertet wurde. Wenn die aktuelle oder angestrebte Java-LTS-Version zusaetzliche Einschraenkungen fuer ein Kotlin-Upgrade erzeugt, muessen diese explizit dokumentiert werden.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle Stellen identifizieren, an denen die Kotlin-Version oder der Kotlin-Gradle-Plugin-Stand im Repo festgelegt oder implizit vorausgesetzt wird.
- Die konkrete Zielversion als neuesten stabilen Kotlin-Release-Stand bestimmen und kurz begruenden.
- Offizielle Kompatibilitaet zu Gradle `9.4.0`, zur verwendeten Java-LTS-Version und zu den eingesetzten Kotlin-Plugins und Targets pruefen.
- Noetige Anpassungen an Build-Konfiguration, Kotlin-Multiplatform-Setup, Tests und Produktionscode minimal umsetzen.
- Pruefen, ob das Upgrade bestehende Warnungen aendert, insbesondere im Zusammenspiel mit dem Client-Build, den Kotlin-Wrappers und bekannten Gradle-Deprecations.
- Den vollen Qualitaetslauf mit `./gradlew check` verifizieren.
- Den relevanten Verifikationslauf mit `--warning-mode all` ausfuehren.
- Den Abschluss so weit wie sinnvollerweise moeglich frei von Build-Warnungen halten; verbleibende bewusst nicht behobene Warnungen muessen mit kurzer Begruendung dokumentiert werden.

Besonders zu klaeren:

- Ob die neueste stabile Kotlin-Version mit Gradle `9.4.0` offiziell freigegeben oder nur faktisch lauffaehig ist.
- Ob Kotlin-Multiplatform-, JS-, Serialization- oder Wrapper-bezogene APIs angepasst werden muessen.
- Ob das Upgrade die bekannte `archives`-Deprecation im Client veraendert, verschaerft oder beseitigt, ohne diese Aufgabe mit Task `018` unklar zu vermischen.
- Lucene-bezogene Warnungen duerfen fuer diese Aufgabe voruebergehend toleriert werden, wenn sie nicht durch das Kotlin-Upgrade verursacht werden und durch die geplante Lucene-Aktualisierung separat adressiert sind.
- Ob fuer die lokale Entwicklung zusaetzliche Dokumentation oder Setup-Hinweise noetig werden.

Abnahme:

- `./gradlew check` laeuft mit dem neuen Kotlin-Stand erfolgreich durch.
- Der relevante Verifikationslauf wird mit `--warning-mode all` ausgefuehrt.
- Der Abschluss ist so weit wie sinnvollerweise moeglich frei von Build-Warnungen.
- Verbleibende bewusst nicht behobene Warnungen sind mit kurzer Begruendung dokumentiert.
- Alle im Repo relevanten Verweise auf die Kotlin-Version sind entweder weiter korrekt oder wurden im Zuge der Umstellung angepasst.
