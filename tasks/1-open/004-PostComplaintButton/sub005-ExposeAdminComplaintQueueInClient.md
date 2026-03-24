Ziel

Admins sollen im Client einen getrennten Moderations-Einstieg mit Badge und offener Beschwerde-Queue erhalten, ohne die bestehende Inbox oder userbezogene Admin-Aktionskarte zweckzuentfremden.

Umfang

- Fuehre im Client einen getrennten Moderations-Einstieg fuer offene Beschwerden ein.
- Zeige ein Badge mit der Anzahl offener Beschwerden.
- Implementiere eine kompakte Liste offener Beschwerden mit den vom Server gelieferten Queue-Daten.
- Implementiere fuer jeden offenen Fall die drei Aktionen:
  - Beitrag loeschen
  - Beschwerde bestaetigen
  - Beitrag als in Ordnung approven
- Binde je Aktion das Pflichtfeld fuer die Rueckmeldung an den Beschwerdefuehrer ein.
- Implementiere Paging fuer die offene Queue passend zum Serververtrag.

Wichtige Randbedingungen

- Die Beschwerde-Queue bleibt fachlich und visuell von der bestehenden Admin-Message-Inbox getrennt.
- Fuer die Admin-Entscheidung ist die neue signierte Decision-Payload zu verwenden.
- UI-Code braucht keine gesonderten dedizierten UI-Tests, aber die nichttriviale Zustandslogik im Client soll automatisiert abgesichert werden.

Erledigt wenn

- Ein Admin sieht die Anzahl offener Beschwerden getrennt von der Inbox.
- Ein Admin kann einen offenen Fall in alle drei Zielzustaende ueberfuehren.
- Nach erfolgreicher Entscheidung verschwindet der Fall aus der offenen Queue.
