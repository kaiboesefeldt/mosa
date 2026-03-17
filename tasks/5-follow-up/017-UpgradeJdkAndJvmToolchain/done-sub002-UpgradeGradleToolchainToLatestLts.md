Ziel

Die Gradle-JDK-/JVM-Toolchain des Repos soll minimal auf die in der Vorarbeit festgelegte neueste stabile Java-LTS-Version umgestellt werden.

Scope

- Alle technisch relevanten Toolchain-Definitionen in den Build-Dateien auf die Zielversion anheben.
- Noetige minimale Anpassungen an Build-Logik, Tests oder Laufzeitskripten umsetzen, damit das Repo mit der neuen Toolchain wieder kompiliert und lauffaehig ist.
- Das beobachtbare Verhalten des Systems und die lokale Entwicklererfahrung soweit moeglich unveraendert erhalten.
- Falls sich waehrend der Umstellung zeigt, dass ein separates Kotlin-Upgrade erforderlich waere, dies als Blocker oder Folgeproblem explizit dokumentieren statt stillschweigend mitzuziehen.

Nicht Teil dieser Subtask

- Repo-weite Bereinigung aller durch den Upgrade sichtbaren Warnungen.
- Dokumentationsanpassungen ohne direkten Bedarf fuer die eigentliche Toolchain-Migration.
- Der abschliessende Repo-weite Vollcheck.
- Ein Upgrade von Kotlin oder des Kotlin-Gradle-Plugins.

Akzeptanzkriterien

- Die Gradle-Toolchain ist auf die festgelegte Java-LTS-Version umgestellt.
- Das Repo kompiliert wieder gegen die neue Toolchain.
- Nur die fuer die Toolchain-Migration noetigen Build-, Test- oder Skriptanpassungen wurden vorgenommen.
- Ein eventueller Kotlin-bezogener Blocker ist klar dokumentiert statt implizit innerhalb der Aufgabe geloest.
