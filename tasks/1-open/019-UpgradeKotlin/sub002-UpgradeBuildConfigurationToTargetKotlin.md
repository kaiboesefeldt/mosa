Die im Repo fest verdrahteten Kotlin-Versionen und unmittelbar davon abhaengigen Build-Konfigurationen sollen minimal auf die in Subtask 001 bestimmte Zielversion umgestellt werden.

Zu bearbeiten:

- Alle relevanten Kotlin-Versionsreferenzen im Repo inventarisieren.
- Kotlin Gradle Plugin, Multiplatform Plugin und Serialization Plugin konsistent auf die Zielversion anheben.
- Noetige minimale Buildscript-Anpassungen im Root-Projekt und in betroffenen Modulen umsetzen.
- Falls noetig, zu enge Dokumentations- oder Repo-Regeln auf die neue Kotlin-Linie anpassen.

Ergebnis:

- Das Repo baut mit der neuen Kotlin-Version konfigurationsseitig konsistent.
