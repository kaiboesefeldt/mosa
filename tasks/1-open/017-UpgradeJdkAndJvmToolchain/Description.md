Die JDK-/JVM-Toolchain des Repos soll von der aktuell konfigurierten Version `19` auf eine aktuelle sinnvolle Zielversion angehoben werden.

Ziel ist, Build und Laufzeit auf einen moderneren Java-Stand zu bringen, ohne das beobachtbare Verhalten des Systems, die Teststabilitaet oder die lokale Entwicklererfahrung unbeabsichtigt zu verschlechtern.

Die Aufgabe ist bewusst separat von Bibliotheksupdates geschnitten, weil ein Toolchain-Upgrade andere Risiken und andere Rueckkopplungen hat als das Anheben einzelner Abhaengigkeiten.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle Stellen identifizieren, an denen die Java- oder JVM-Version im Repo festgelegt oder implizit vorausgesetzt wird.
- Eine konkrete Zielversion festlegen und begruenden.
- Noetige Anpassungen an Gradle-Konfiguration, Toolchains, Tests und gegebenenfalls Laufzeitskripten minimal umsetzen.
- Pruefen, ob Abhaengigkeiten oder Plugins an der Zielversion scheitern oder Warnungen erzeugen.
- Den vollen Qualitaetslauf mit `./gradlew check` gegen die neue Toolchain verifizieren.

Besonders zu klaeren:

- Ob die Zielversion eine LTS-Version sein soll.
- Ob Kotlin `2.2.x`, Gradle `9.4.0` und die verwendeten Plugins mit der Zielversion stabil zusammenspielen.
- Ob sich Warnungen oder Verhaltensaenderungen bei Reflection, Security, Native Access oder JS-Testausfuehrung ergeben.
- Ob fuer lokale Entwicklung und CI zusaetzliche Dokumentation oder Setup-Hinweise noetig werden.
