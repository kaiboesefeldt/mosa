Für den Client soll Multi-Pod-Access implementiert werden.

Ziel ist, dass der Client nicht nur mit genau einem Heimat-Pod arbeiten kann, sondern gezielt auch auf weitere Pods zugreifen kann, wenn das für Suche, Abruf oder andere Anwendungsfälle erforderlich ist.

Die Aufgabe umfasst mindestens diese Punkte:

- Klären, welche fachlichen Anwendungsfälle Multi-Pod-Access im Client zuerst abdecken soll.
- Das bestehende Client-Modell so erweitern, dass mehrere Pods adressierbar sind.
- Festlegen, wie ein Ziel-Pod im UI oder in der Client-Logik ausgewählt wird.
- Prüfen, welche bestehenden Services und Flows aktuell implizit von genau einem Pod ausgehen.
- Festlegen, wie Fehler, Timeouts und Teilergebnisse bei Zugriffen auf mehrere Pods behandelt werden.

Bei der Umsetzung ist insbesondere zu klären:

- Ob ein Pod explizit ausgewählt wird oder ob der Client mehrere Pods automatisch anspricht.
- Welche Funktionen Multi-Pod-fähig werden sollen und welche vorerst bewusst Single-Pod bleiben.
- Wie Pod-Identitäten und Basis-URLs im Client modelliert werden.
- Wie Ergebnisse aus verschiedenen Pods im UI kenntlich gemacht werden.
- Ob für Multi-Pod-Access zusätzliche Sicherheits-, Vertrauens- oder Missbrauchsschutzregeln nötig sind.
