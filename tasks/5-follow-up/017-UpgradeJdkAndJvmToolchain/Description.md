Die JDK-/JVM-Toolchain des Repos soll auf die zum jeweiligen Bearbeitungszeitpunkt neueste sinnvolle Java-LTS-Version ausgerichtet werden.

Ziel ist, Build und Laufzeit auf einen moderneren Java-Stand zu bringen, ohne das beobachtbare Verhalten des Systems, die Teststabilitaet oder die lokale Entwicklererfahrung unbeabsichtigt zu verschlechtern.

Die Aufgabe ist bewusst separat von Bibliotheksupdates geschnitten, weil ein Toolchain-Upgrade andere Risiken und andere Rueckkopplungen hat als das Anheben einzelner Abhaengigkeiten. Nach einer erfolgten Umstellung bleibt die Aufgabe als Wiedervorlage geeignet, um spaetere LTS-Wechsel oder Folgearbeiten erneut gesammelt zu betrachten.

Das Upgrade der Kotlin-Version oder des Kotlin-Gradle-Plugins ist nicht Teil dieser Aufgabe und soll bewusst separat betrachtet werden. Wenn sich zeigt, dass die anvisierte Java-LTS-Version mit dem aktuell eingesetzten Kotlin-Stand nicht tragfaehig ist, ist das als Blocker oder Folgeaufgabe zu dokumentieren, statt innerhalb dieser Aufgabe stillschweigend auch Kotlin mit anzuheben.

Die Aufgabe umfasst mindestens diese Punkte:

- Alle Stellen identifizieren, an denen die Java- oder JVM-Version im Repo festgelegt oder implizit vorausgesetzt wird.
- Die konkrete Zielversion als neueste LTS-Version bestimmen und kurz begruenden.
- Primaer die Gradle-Toolchain minimal auf diese Zielversion umstellen.
- Zusaetzlich pruefen, ob die Java-Version ausserhalb der Gradle-Toolchain im Repo noch relevant ist, zum Beispiel in Dokumentation, Setup-Hinweisen, Skripten oder sonstigen Konfigurationen, und noetige Folgeanpassungen minimal mitziehen.
- Noetige Anpassungen an Gradle-Konfiguration, Toolchains, Tests und gegebenenfalls Laufzeitskripten minimal umsetzen.
- Pruefen, ob Abhaengigkeiten oder Plugins an der Zielversion scheitern oder neue Warnungen erzeugen.
- Den vollen Qualitaetslauf mit `./gradlew check` gegen die neue Toolchain verifizieren.
- Den Abschluss so weit wie sinnvollerweise moeglich frei von Build-Warnungen halten, insbesondere frei von neuen Deprecations; Warnungen, die durch die neue Toolchain oder durch dafuer notwendige Anpassungen neu sichtbar werden, muessen im Rahmen der Aufgabe mit behoben werden, soweit das mit verhaeltnismaessigem Aufwand im Repo selbst moeglich ist.
- Alle Warnungen, die am Ende bewusst nicht behoben werden, mit kurzer Begruendung dokumentieren.

Besonders zu klaeren:

- Welche Java-LTS-Version zum Bearbeitungszeitpunkt tatsaechlich die neueste stabile LTS-Version ist.
- Ob Kotlin `2.2.x`, Gradle `9.4.0` und die verwendeten Plugins mit der Zielversion stabil zusammenspielen.
- Ob sich Warnungen oder Verhaltensaenderungen bei Reflection, Security, Native Access oder JS-Testausfuehrung ergeben.
- Ob fuer die lokale Entwicklung zusaetzliche Dokumentation oder Setup-Hinweise noetig werden oder vorhandene Hinweise auf eine alte Java-Version angepasst werden muessen.

Abnahme:

- `./gradlew check` laeuft mit der neuen Toolchain erfolgreich durch.
- Der relevante Verifikationslauf wird mit `--warning-mode all` ausgefuehrt.
- Der Abschluss ist so weit wie sinnvollerweise moeglich frei von Build-Warnungen, insbesondere frei von neuen Deprecation-Warnungen.
- Verbleibende bewusst nicht behobene Warnungen sind mit kurzer Begruendung dokumentiert.
- Alle im Repo relevanten Verweise auf die Java-Version sind entweder weiter korrekt oder wurden im Zuge der Umstellung angepasst.
