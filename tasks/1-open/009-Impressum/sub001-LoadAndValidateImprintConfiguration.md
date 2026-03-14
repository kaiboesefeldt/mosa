Ziel dieser Subtask ist, das neue Impressumsmodell serverseitig einzuführen und beim Start zu validieren.

Umfang:

- Ein Datenmodell für `imprint.yaml` mit den in der Hauptaufgabe definierten festen Blöcken einführen.
- Laden von `imprint.yaml` aus dem CWD des Mosa-Servers implementieren.
- Den Pflichtkern validieren:
  - `provider.name`
  - `address.street`
  - `address.postal_code`
  - `address.city`
  - `address.country`
  - `contact.email`
- `null` und fehlende optionale Angaben zulassen.
- Bei fehlenden Pflichtangaben oder nicht lesbarer/ungültiger Datei den Serverstart mit aussagekräftigem Error-Log fehlschlagen lassen.

Vorgehen:

- Die Validierung testgetrieben über öffentliche Start- oder Ladepfade absichern.
- Mindestens einen Test für gültige Konfiguration und einen Test für fehlende Pflichtangaben anlegen.

Nicht Teil dieser Subtask:

- API-Auslieferung des Impressums
- Client-Rendering
- UI-Integration
