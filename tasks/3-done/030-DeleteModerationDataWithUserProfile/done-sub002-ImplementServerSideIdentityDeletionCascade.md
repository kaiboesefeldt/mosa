Ziel ist, die serverseitige Loeschkaskade fuer eine Identitaet testgetrieben umzusetzen.

Diese Subtask umfasst mindestens:

- einen minimalen failing Test fuer den oeffentlichen Loeschpfad der Identitaet schreiben
- die serverseitige Loeschung aller Beitraege und Antworten der Identitaet umsetzen oder an die bestehende Logik anbinden
- die serverseitige Loeschung aller Beschwerden der Identitaet umsetzen, sowohl selbst eingereichte Beschwerden als auch Beschwerden ueber Inhalte der Identitaet
- die serverseitige Loeschung aller Admin-Nachrichten der Identitaet umsetzen
- sicherstellen, dass unbeteiligte Daten anderer Identitaeten unveraendert bleiben
- die Loeschung ueber oeffentliches Verhalten testbar halten und keine untestbaren Sonderpfade einfuehren

Wichtig:

- Die Reihenfolge ueber die betroffenen Speicher muss fachlich konsistent und testbar sein.
- Die Umsetzung soll auf minimalen Schnitten aufbauen und keine neue Moderationsarchitektur vorwegnehmen.
