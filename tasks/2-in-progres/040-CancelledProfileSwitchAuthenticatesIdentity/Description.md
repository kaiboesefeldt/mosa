Beim Erzeugen und anschliessenden Aktivieren eines neuen Profils kann der Client in einen fachlich inkonsistenten Zustand geraten.

Reproduktion:

1. Mit einem bestehenden Profil anmelden.
2. Die Profilseite oeffnen.
3. Ein neues Profil erzeugen.
4. Auf den Avatar des neuen Profils klicken, sodass sich der Passwortdialog oeffnet.
5. Im Passwortdialog `Abbrechen` waehlen.

Aktuelles Fehlverhalten:

- Trotz `Abbrechen` wechselt der Client in die Identitaet, fuer die der Dialog gerade abgebrochen wurde.
- Wenn zuvor eine Admin-Identitaet aktiv war, wird die neue Identitaet im Client zusaetzlich als Admin markiert.
- Die serverseitigen Rechte auf dem Pod werden zwar weiterhin abgewiesen, aber der lokale UI- und Sitzungszustand ist trotzdem falsch und irrefuehrend.

Erwartetes Verhalten:

- `Abbrechen` muss den Identitaetswechsel vollstaendig abbrechen.
- Nach dem Abbrechen darf weder eine neue Identitaet aktiv sein noch ein Admin-Status aus der vorherigen Sitzung uebernommen werden.
- Clientseitiger Authentifizierungs- und Rollenstatus muessen nach dem Abbruch konsistent zur tatsaechlich aktiven Identitaet bleiben.

Die Aufgabe umfasst mindestens diese Punkte:

- Den Fehler mit einem automatisierten Test ueber das oeffentliche Verhalten reproduzierbar machen.
- Die Stelle identifizieren, an der der Identitaetswechsel oder die Rollenuebernahme bereits vor der Passwortbestaetigung lokal committed wird.
- Sicherstellen, dass ein abgebrochener Passwortdialog keinerlei Aktivierung oder Rollenuebernahme ausloest.
- Absichern, dass Admin-Markierungen ausschliesslich aus der tatsaechlich aktiven und erfolgreich entsperrten Identitaet abgeleitet werden.

Akzeptanzkriterien:

- Ein automatisierter Test deckt den Abbruchfall ab und war vor der Korrektur rot.
- Nach `Abbrechen` bleibt die zuvor aktive Identitaet aktiv.
- Nach `Abbrechen` erscheint die neu angeklickte Identitaet nicht als eingeloggt oder aktiv.
- Ein zuvor vorhandener Admin-Status wird nach dem abgebrochenen Wechsel nicht auf die neue Identitaet uebertragen.
