Admins sollen bereits gesendete Admin-Nachrichten wieder loeschen koennen.

Aktuell koennen Admins Admin-Nachrichten an Nutzer senden, aber nicht mehr entfernen. Fuer Moderation, Korrekturen und Fehlbedienungen fehlt damit ein einfacher Rueckbaupfad. Wenn eine Nachricht versehentlich an das falsche Profil geschickt wurde oder inhaltlich unpassend ist, bleibt sie bis zum Ablauf der Aufbewahrung sichtbar.

Ziel ist eine kleine, klare Loeschfunktion fuer Admin-Nachrichten. Im ersten Wurf geht es nur um das Loeschen bestehender Nachrichten durch Admin-Konten, nicht um Bearbeiten, Wiederherstellen, Soft-Delete oder komplexe Audit-Workflows.

Die Aufgabe umfasst mindestens diese Punkte:

- Admin-Konten koennen eine bestehende Admin-Nachricht loeschen.
- Geloeschte Admin-Nachrichten werden danach weder in der Admin-Verlaufssicht noch in der Empfaenger-Inbox weiter ausgeliefert.
- Die Loeschfunktion ist nur fuer authentifizierte Admin-Konten verfuegbar.
- Die Loeschung baut auf der bestehenden Admin-Nachrichtenfunktion auf und fuehrt keinen zweiten fachlich abweichenden Nachrichtenkanal ein.

Akzeptanzkriterien:

- Ein Admin kann eine vorhandene Admin-Nachricht gezielt loeschen.
- Nach dem Loeschen ist die Nachricht serverseitig nicht mehr ueber die relevanten Read-Endpoints sichtbar.
- Nicht-Admins koennen Admin-Nachrichten nicht loeschen.
- Das Loeschen einer Nachricht fuehrt nicht dazu, dass andere Nachrichten desselben Profils verschwinden oder veraendert werden.

Bei der Umsetzung ist insbesondere zu klaeren:

- Ob das Loeschen als harte Entfernung aus dem bestehenden Speicher erfolgt oder ob ein minimaler technischer Tombstone noetig ist.
- An welcher Stelle der Admin-UI die Loeschaktion am besten erreichbar ist, ohne versehentliche Loeschungen zu foerdern.
- Ob vor dem Loeschen eine bestaetigende Rueckfrage noetig ist.
