Ziel

Die zuvor ausgewaehlte Migration soll mit der minimal notwendigen Aenderung im Client-Build umgesetzt werden, so dass die `archives`-Deprecation verschwindet und das bisherige Verhalten des Client-Builds erhalten bleibt.

Scope

- Die noetigen Buildscript- oder Plugin-Anpassungen im kleinsten sinnvollen Umfang umsetzen.
- Sicherstellen, dass Browser-Build, Packaging, Artefakt-Erzeugung und Client-Testausfuehrung durch die Aenderung nicht unbeabsichtigt kippen.
- Falls fuer die Umsetzung eine kleine, direkt zugehoerige Dokumentation im Aufgabenordner noetig ist, diese knapp mitziehen.

Nicht Teil dieser Subtask

- Ein allgemeines Upgrade der Kotlin-Version, sofern es nicht bereits in der vorigen Subtask als kleinste tragfaehige Loesung begruendet wurde.
- Funktionale Client-Aenderungen ohne unmittelbaren Bezug zur Build-Migration.
- Der abschliessende Repo-weite Integrations- und Warnungscheck.

Akzeptanzkriterien

- Die produktive Aenderung ist im Repo umgesetzt.
- Der zuvor definierte minimale Reproduktionslauf zeigt die `archives`-Deprecation nicht mehr.
- Es gibt keine unbeabsichtigte beobachtbare Aenderung am betroffenen Client-Buildpfad.
