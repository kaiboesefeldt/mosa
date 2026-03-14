In ClearTerms sollen Baselines und Profilketten Signaturen erhalten, damit beide unabhängig voneinander überprüft werden können.

Ziel ist, die Integrität und Herkunft dieser Inhalte kryptographisch nachvollziehbar zu machen, ohne dass für die Verifikation jeweils der andere Teil vorausgesetzt werden muss. Baseline und Profilkette sollen deshalb jeweils so signiert und bereitgestellt werden, dass ihre Gültigkeit separat geprüft werden kann.

Die Aufgabe umfasst mindestens diese Punkte:

- Festlegen, welche Daten einer Baseline signiert werden und in welcher Form die Signatur mitgeführt wird.
- Festlegen, welche Daten einer Profilkette signiert werden und in welcher Form die Signatur mitgeführt wird.
- Sicherstellen, dass Baselines unabhängig von Profilketten verifiziert werden können.
- Sicherstellen, dass Profilketten unabhängig von Baselines verifiziert werden können.
- Die Verifikationslogik so gestalten, dass sie mit dem bestehenden ClearTerms-Format und den vorhandenen Lade- und Prüfpfaden zusammenpasst.
- Klären, wie öffentliche Schlüssel oder andere Vertrauensanker bereitgestellt und referenziert werden.

Bei der Umsetzung ist zu klären:

- Ob Baseline und Profilkette jeweils eigene Signaturschemata oder nur getrennte Signaturdaten innerhalb eines gemeinsamen Schemas benötigen.
- Wie kanonisiert wird, damit dieselben Inhalte reproduzierbar signiert und geprüft werden können.
- Wie mit Teilupdates umgegangen wird, wenn sich nur Baseline oder nur Profilkette ändert.
- Wie Verifikationsfehler für Nutzerinnen, Nutzer oder aufrufende Komponenten sichtbar gemacht werden sollen.
- Ob bestehende unsignierte Inhalte weiterhin unterstützt oder in ein Migrationsformat überführt werden müssen.
