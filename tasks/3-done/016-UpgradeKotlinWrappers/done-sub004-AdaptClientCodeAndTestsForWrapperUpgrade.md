Produktionscode, Testcode und Test-Harnesses sollen nur dort angepasst werden, wo das Wrapper-Upgrade tatsaechlich Inkompatibilitaeten oder neue Warnungen erzeugt.

Zu bearbeiten:

- Den ersten durch das Upgrade verursachten Fehler oder Regressionseffekt ueber beobachtbares Verhalten reproduzieren, soweit Business-Logik betroffen ist.
- Notwendige Anpassungen an Hook-, Props-, Event-, Children- und Browser-Interop-Code minimal umsetzen.
- Betroffenen Test-Hilfscode und die `@testing-library`-Integration nur im erforderlichen Umfang anpassen.
- Vorhandene und neu ergaenzte Client-Tests wieder grün bekommen, ohne das beobachtbare UI-Verhalten unbegruendet zu veraendern.
- Wenn sich zeigt, dass fuer eine saubere Umstellung React-, Kotlin- oder Toolchain-Aenderungen noetig waeren, diese nicht mitziehen, sondern als Blocker oder Folgeaufgabe dokumentieren.

Ergebnis:

- Der Client-Code laeuft mit dem aktualisierten Wrapper-Stand ohne unbegruendete funktionale Aenderungen.
- Notwendige Folgearbeiten ausserhalb des Wrapper-Scope sind klar dokumentiert.
