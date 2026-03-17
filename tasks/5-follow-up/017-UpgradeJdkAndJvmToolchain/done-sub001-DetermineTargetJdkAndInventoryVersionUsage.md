Ziel

Vor der eigentlichen Umstellung soll die konkrete Java-Zielversion als zum Bearbeitungszeitpunkt neueste stabile LTS-Version verifiziert und alle relevanten Stellen im Repo inventarisiert werden, an denen die Java- oder JVM-Version festgelegt, vorausgesetzt oder erwaehnt wird.

Scope

- Die neueste stabile Java-LTS-Version per Web-Recherche verifizieren und als Zielversion fuer die Aufgabe festhalten.
- Alle Stellen im Repo identifizieren, an denen die Java- oder JVM-Version technisch gesetzt oder implizit vorausgesetzt wird.
- Zusaetzlich alle fuer die lokale Entwicklung relevanten Hinweise, Dokumentationen, Skripte oder Konfigurationen identifizieren, die auf eine bestimmte Java-Version verweisen.
- Pruefen, ob die geplante Java-LTS-Version mit dem aktuell eingesetzten Kotlin-Stand, Gradle `9.4.0` und den verwendeten Plugins grundsaetzlich vereinbar erscheint.

Nicht Teil dieser Subtask

- Die eigentliche Umstellung der Gradle-Toolchain.
- Produktive Code- oder Build-Aenderungen.
- Die finale Warnungsbereinigung oder der abschliessende Repo-weite Vollcheck.
- Ein Upgrade von Kotlin oder des Kotlin-Gradle-Plugins.

Akzeptanzkriterien

- Die Zielversion ist als verifizierte neueste stabile Java-LTS-Version festgelegt.
- Alle technisch relevanten Versionseintraege oder Java-Annahmen im Repo sind inventarisiert.
- Alle sichtbaren Java-Versionsverweise in lokal relevanter Dokumentation oder Konfiguration sind inventarisiert.
- Bekannte oder vermutete Kompatibilitaetsrisiken zur Zielversion sind vor der Umstellung benannt.
