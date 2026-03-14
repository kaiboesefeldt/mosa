Ziel

Der Pod soll beim Start fuer jede betroffene Dokumentart sicherstellen, dass sich aus dem Operator-Profil eine vollstaendige und konsistente Dokumentkette bis zur Baseline aufbauen laesst.

Scope

- Die Kette jeweils vom Operator-Profil aus rueckwaerts ueber Parent-Referenzen bis zur Baseline aufbauen.
- Die geordnete Profilkette fuer den neuen API-Response bereitstellen.
- Pruefen, dass jedes Profil in der Kette unmittelbar das vorhergehende Dokument referenziert.
- Fehlende oder inkompatible Zwischenprofile erkennen.
- Fehlende oder inkompatible Baselines erkennen.
- Den Pod-Start bei unvollstaendigen oder inkompatiblen Ketten verhindern.
- Den Startfehler mit klaren Details loggen:
- betroffene Dokumentart
- betroffenes Ziel-Operator-Profil
- fehlende oder inkompatible referenzierte Versionsnummer
- konkreter Fehlergrund
- im Pod verfuegbare Versionsnummern der passenden Dokumentart

Nicht Teil dieser Subtask

- Client-Umstellung.
- Oeffentliche HTTP-Endpoints.

Akzeptanzkriterien

- Fuer jede aktuell betroffene Dokumentart wird die Kette deterministisch aus Parent-Referenzen aufgebaut.
- Bei erfolgreichem Pod-Start ist garantiert, dass die Dokumentkette vollstaendig und konsistent ist.
- Fehlt ein benoetigtes Dokument oder ist es mit dem Softwarestand inkompatibel, startet der Pod nicht.
- Der Fehler ist ueber oeffentlich beobachtbares Verhalten automatisiert testbar.
- Die Error-Logs enthalten die in der Hauptaufgabe geforderten Diagnoseinformationen.
