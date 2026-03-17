Ziel

Die `archives`-Deprecation soll zunaechst mit einem moeglichst kleinen Gradle-Lauf reproduzierbar gemacht und technisch bis zur verantwortlichen Konfiguration, zum verantwortlichen Plugin oder zur verantwortlichen Plugin-Interaktion eingegrenzt werden.

Scope

- Einen minimalen Gradle-Befehl bestimmen, in dem die Deprecation verlaesslich sichtbar wird.
- Pruefen, ob die Warnung bereits bei der Projektkonfiguration oder erst bei konkreten Client-Tasks entsteht.
- Die beteiligten Buildpfade im Client- und Root-Buildscript eingrenzen.
- Festhalten, ob die Ursache eher im Projekt-Buildscript, im Kotlin-Multiplatform-JS-Setup oder in Plugin-Interna liegt.

Nicht Teil dieser Subtask

- Die produktive Behebung der Warnung.
- Ein allgemeines Kotlin- oder Plugin-Upgrade.
- Repo-weite Verifikationslaeufe ueber die reine Reproduktion und Eingrenzung hinaus.

Akzeptanzkriterien

- Ein kleinster reproduzierbarer Gradle-Lauf ist bekannt.
- Es ist nachvollziehbar eingegrenzt, in welchem Buildpfad die `archives`-Deprecation entsteht.
- Der Befund ist im Aufgabenordner so dokumentiert, dass die nachfolgende Umsetzungs-Subtask ohne erneute Grundanalyse ansetzen kann.
