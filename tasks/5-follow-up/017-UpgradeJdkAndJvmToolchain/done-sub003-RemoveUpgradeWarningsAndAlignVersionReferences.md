Ziel

Alle durch das Toolchain-Upgrade neu sichtbar gewordenen oder neu eingefuehrten Warnungen sollen mit verhaeltnismaessigem Aufwand beseitigt und alle im Repo relevanten Java-Versionsverweise auf den neuen Stand gebracht werden.

Scope

- Build-Warnungen pruefen, die nach der Toolchain-Umstellung sichtbar werden, insbesondere Deprecations und versionsbezogene Hinweise.
- Warnungen, die durch das Java-Upgrade oder dafuer noetige Folgeanpassungen neu sichtbar werden, mit verhaeltnismaessigem Aufwand beheben.
- Relevante Dokumentation, Setup-Hinweise, Skripte oder sonstige Konfigurationen auf korrekte Java-Versionsangaben anpassen.
- Warnungen, die bewusst nicht behoben werden, kurz und nachvollziehbar dokumentieren.

Nicht Teil dieser Subtask

- Das blinde Beseitigen unabhaengiger Altwarnungen ohne Bezug zur Toolchain-Umstellung.
- Groessere Bibliotheks- oder Kotlin-Upgrades nur zur kosmetischen Warnungsreduktion.
- Der abschliessende Repo-weite Integrationsnachweis.

Akzeptanzkriterien

- Durch die Toolchain-Umstellung neu eingefuehrte oder neu sichtbar gewordene Warnungen sind soweit sinnvoll beseitigt.
- Relevante Java-Versionsverweise ausserhalb der eigentlichen Toolchain-Konfiguration sind aktualisiert oder bewusst als weiterhin korrekt bestaetigt.
- Verbleibende bewusst nicht behobene Warnungen sind mit kurzer Begruendung dokumentiert.
- Fuer die Warnungspruefung ist ein Lauf mit `--warning-mode all` vorgesehen oder bereits gezielt verwendet worden.
