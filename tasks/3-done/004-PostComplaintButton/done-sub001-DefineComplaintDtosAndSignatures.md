Ziel

Die fachlichen Vertraege fuer Beschwerden und Beschwerdeentscheidungen sollen im `shared`-Modul so definiert werden, dass Server und Client dieselben DTOs und dieselbe Signatursemantik verwenden koennen.

Umfang

- Definiere die minimalen shared DTOs fuer:
  - Beschwerdestatus eines einzelnen Beitrags aus Sicht eines normalen Nutzers
  - Request zum Anlegen einer Beschwerde
  - Request zur Admin-Entscheidung
  - Eintrag einer offenen Beschwerde fuer die Admin-Queue
  - Ergebnis fuer offene Beschwerden mit Paging oder Count, sofern dafuer eigene DTOs noetig sind
- Definiere die gemeinsamen Complaint-Zustandswerte passend zur Aufgabenbeschreibung.
- Definiere signierbare Payload-Typen fuer:
  - `POST_COMPLAINT`
  - `POST_COMPLAINT_DECISION`
- Lege die kanonische Serialisierung analog zu den bestehenden Content-Signaturen fest.
- Halte die API bewusst klein und ohne vorgezogene Historien- oder Mehrfachfall-Komplexitaet.

Wichtige Randbedingungen

- Keine deprecated APIs.
- Nur die in der Hauptbeschreibung festgelegten Felder aufnehmen.
- Keine Implementierung der Server- oder Clientlogik in dieser Subtask.
- Testgetrieben arbeiten: zuerst minimale Unit-Tests im `shared`-Modul fuer Serialisierung und Payload-Verhalten.

Erledigt wenn

- Server und Client koennen dieselben Complaint-DTOs referenzieren.
- Die signierten Payloads sind eindeutig und testbar definiert.
- Die Tests belegen die vorgesehene Serialisierung und Feldbelegung.
