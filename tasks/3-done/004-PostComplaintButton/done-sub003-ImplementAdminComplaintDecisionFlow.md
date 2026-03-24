Ziel

Admins sollen offene Beschwerden serverseitig sehen und entscheiden koennen, inklusive Signaturpruefung, Zustandswechsel und Rueckmeldung an den Beschwerdefuehrer.

Umfang

- Implementiere die Admin-Endpunkte fuer:
  - Anzahl offener Beschwerden
  - paginierte Liste offener Beschwerden
  - Entscheidung ueber einen offenen Fall
- Die offene Liste soll die fuer die Queue benoetigten Informationen liefern:
  - kompletter Beitragstext
  - Autor und Zeitstempel des Beitrags
  - Beschwerdefuehrer
  - Begruendung der Beschwerde
  - Zeitstempel der Beschwerde
- Implementiere die serverseitige Signaturpruefung fuer Admin-Entscheidungen.
- Implementiere die drei Aktionen:
  - `CONFIRMED`
  - `REJECTED`
  - `DELETED`
- Erzeuge bei jeder erfolgreichen Entscheidung genau eine private Admin-Nachricht an den Beschwerdefuehrer.
- Bei `DELETED` den bestehenden serverseitigen Loeschpfad verwenden und den Fall nur dann abschliessen, wenn die Gesamtaktion erfolgreich ist.

Wichtige Randbedingungen

- Nur Admins duerfen diese Endpunkte verwenden.
- Entscheidungen auf bereits abgeschlossene Faelle muessen als Konflikt scheitern.
- Die Queue zeigt nur `OPEN`-Faelle.
- Die Reihenfolge der offenen Queue folgt der Eingangszeit aufsteigend.
- Testgetrieben ueber oeffentliche Server-Schnittstellen arbeiten.

Erledigt wenn

- Automatisierte Tests decken alle drei Admin-Entscheidungen ab.
- Nach `CONFIRMED` kann spaeter erneut ein neuer offener Fall fuer denselben Beitrag entstehen.
- Nach `REJECTED` oder `DELETED` bleibt eine neue Beschwerde fuer denselben Beitrag serverseitig gesperrt.
- Die private Rueckmeldung wird ueber die bestehende Admin-Message-Infrastruktur zugestellt.
