Mosa soll einen Admin-Zugriff bekommen, mit dem ausgewählte Konten alle Beiträge löschen können.

Wichtig ist dabei, dass diese Berechtigung nicht allgemein für normale Nutzerkonten gilt, sondern nur für eine klar definierte kleine Menge privilegierter Konten. Die Lösung muss zum bestehenden Authentifizierungs- und Berechtigungsmodell von Mosa passen.

Die Aufgabe umfasst mindestens diese Punkte:

- Es braucht eine Möglichkeit, bestimmte Konten als Admin-Konten zu markieren oder zu konfigurieren.
- Admin-Konten sollen alle Beiträge löschen können, unabhängig davon, von wem der Beitrag ursprünglich erstellt wurde.
- Nicht-Admin-Konten dürfen weiterhin nur die bisher erlaubten Löschaktionen ausführen.
- Der Server muss die Berechtigung zuverlässig prüfen; eine reine Client-Prüfung reicht nicht aus.
- Löschaktionen durch Admin-Konten sollen nachvollziehbar sein, damit Missbrauch oder Fehlbedienung später untersucht werden kann.
- Falls es im Client eine Löschfunktion für Beiträge gibt, soll dort für Admin-Konten die passende Bedienmöglichkeit sichtbar sein, ohne das bestehende Verhalten für normale Konten zu verschlechtern.

Bei der Umsetzung ist zu klären:

- Wo die Admin-Berechtigung fachlich am sinnvollsten konfiguriert wird.
- Ob für Admin-Löschungen zusätzliche Schutzmechanismen sinnvoll sind, zum Beispiel eine klare Bestätigung vor dem endgültigen Löschen.
- Welche bestehenden Endpunkte, Services und Tests für Löschrechte erweitert werden müssen.
