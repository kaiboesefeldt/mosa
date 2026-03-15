Mosa soll einen Admin-Zugriff bekommen, mit dem ausgewählte Konten alle Beiträge löschen können.

Wichtig ist dabei, dass diese Berechtigung nicht allgemein für normale Nutzerkonten gilt, sondern nur für eine klar definierte kleine Menge privilegierter Konten. Die Lösung muss zum bestehenden Authentifizierungs- und Berechtigungsmodell von Mosa passen.

Fachliche Entscheidung:

- Die Admin-Berechtigung ist eine pod-lokale Betreiberentscheidung.
- Die Konfiguration erfolgt serverseitig über eine eigene Allowlist von GIDs.
- Client-Zustand, Zertifikate und JWTs werden dafür nicht um Rollen erweitert.

Konfiguration der Admin-Allowlist:

- Es gibt eine separate Konfigurationsdatei `mosa_admin_gids.yaml`.
- Die Realisierung soll ausdrücklich analog zur bestehenden Identity-Blocklist erfolgen.
- Die Datei wird serverseitig geladen und regelmäßig zur Laufzeit neu eingelesen.
- Änderungen an der Admin-Konfiguration sollen ohne Server-Neustart und ohne Downtime wirksam werden.
- Fehlt die Datei, dann gibt es keine Admin-Konten.
- Hat die Datei ein ungültiges Format, dann bleibt die zuletzt erfolgreich geladene Admin-Liste wirksam.

Akzeptanzkriterien:

- Admin-Konten sollen alle Beiträge löschen können, unabhängig davon, von wem der Beitrag ursprünglich erstellt wurde.
- Nicht-Admin-Konten dürfen weiterhin nur die bisher erlaubten Löschaktionen ausführen.
- Der Server muss die Berechtigung zuverlässig prüfen; eine reine Client-Prüfung reicht nicht aus.

Umsetzungsschwerpunkte:

- Die serverseitige Admin-Allowlist muss geladen, validiert und zur Laufzeit regelmäßig neu eingelesen werden.
- Welche bestehenden Endpunkte, Services und Tests für Löschrechte erweitert werden müssen.
