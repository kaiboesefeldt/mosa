Ziel

Der Server soll Beschwerden annehmen, ihren Status pro Beitrag durchsetzen und offene Beschwerden fuer spaetere Bearbeitung in SQLite speichern.

Umfang

- Fuehre die minimalen SQLite/Exposed-Tabellen fuer Beschwerden ein.
- Implementiere den Server-Endpunkt, der beim Oeffnen des Beschwerde-UI den aktuellen Beschwerdestatus fuer genau einen Beitrag liefert.
- Implementiere den Server-Endpunkt zum Anlegen einer signierten Beschwerde.
- Implementiere serverseitige Validierungen:
  - kein eigener Beitrag
  - Beitrag muss existieren
  - Begruendung Pflichtfeld, nicht nur Whitespace, max. 500 Zeichen
  - genau ein offener Fall je Beitrag
  - nach `CONFIRMED` darf ein neuer offener Fall entstehen
  - nach `REJECTED` oder `DELETED` darf kein neuer Fall entstehen
- Implementiere die Signaturpruefung fuer Nutzer-Beschwerden analog zur bestehenden Content-Signaturpruefung.
- Implementiere die noetigen Fehlerabbildungen auf sinnvolle HTTP-Statuscodes.

Wichtige Randbedingungen

- Business-Logik TDD-first entwickeln.
- Die Persistenz soll sich auf offene und abgeschlossene Moderationsfaelle stutzen, ohne bereits eine Historien-UI zu benoetigen.
- Keine kosmetische Client-Loesung ohne serverseitige Durchsetzung.
- Die bestehende Content-Loeschlogik noch nicht in dieser Subtask fuer Admin-Entscheidungen verdrahten; das folgt spaeter.

Erledigt wenn

- Ein automatisierter Test reproduziert und belegt die Konfliktregel fuer parallele oder doppelte offene Beschwerden.
- Der Server kann den Status eines Beitrags fuer den Beschwerdebutton liefern.
- Eine gueltige signierte Beschwerde wird gespeichert, eine ungueltige sauber abgewiesen.
