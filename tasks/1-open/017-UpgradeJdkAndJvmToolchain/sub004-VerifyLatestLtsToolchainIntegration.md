Ziel

Die Gesamtintegration des Java-LTS-Toolchain-Upgrades soll abschliessend ueber den gemeinsamen Nachweis von Build-Erfolg, Testgruenheit, Warnungsstand und korrekten Repo-Verweisen abgesichert werden.

Scope

- Gemeinsame Betrachtung von Zielversionsfestlegung, Toolchain-Umstellung, Warnungsbereinigung und angepassten Repo-Verweisen.
- Nachweis, dass die wichtigsten Build- und Testpfade mit der neuen Java-LTS-Version zusammen funktionieren.
- Abschliessende Pruefung des Warnungsstands mit voller Gradle-Warnungsausgabe.

Form des Abschlusstests

- Vollautomatisierter Verifikationslauf mit den bestehenden Build- und Testwerkzeugen des Projekts.

Vorbedingungen

- Die Zielversion als neueste stabile Java-LTS-Version ist verifiziert und alle relevanten Versionsverwendungen sind inventarisiert.
- Die Gradle-Toolchain ist auf die Zielversion umgestellt.
- Durch das Upgrade neu eingefuehrte oder neu sichtbar gewordene Warnungen sind soweit sinnvoll beseitigt.
- Relevante Java-Versionsverweise im Repo sind angepasst oder als korrekt bestaetigt.

Durchfuehrung

- `./gradlew check --warning-mode all` ausfuehren.
- Den Lauf darauf pruefen, ob Build und Tests erfolgreich durchlaufen.
- Die Warnungsausgabe darauf pruefen, ob noch Warnungen verbleiben und ob diese bereits dokumentiert und begruendet sind.
- Falls zusaetzliche Verifikationslaeufe fuer die Warnungsanalyse noetig sind, diese gezielt ausfuehren und das Ergebnis in die Abschlussbewertung einbeziehen.

Akzeptanzkriterien

- `./gradlew check --warning-mode all` laeuft erfolgreich durch.
- Der Abschluss ist so weit wie sinnvollerweise moeglich frei von Build-Warnungen.
- Verbleibende bewusst nicht behobene Warnungen sind mit kurzer Begruendung dokumentiert.
- Die neue Java-LTS-Toolchain ist repo-weit konsistent eingefuehrt und relevante Verweise sind korrekt.
