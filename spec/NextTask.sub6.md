# Next Task Subtask 6: Nebenpfade und Spezifikation nachziehen

## Ziel

Alle Content-Nebenpfade und die API-Spezifikation sollen nachgezogen werden, damit Signaturdaten nicht auf dem Weg verloren gehen.

## Voraussetzungen

- `NextTask.sub1.md`
- `NextTask.sub3.md`

## Umzusetzen

### 1. Snapshot-/Import-/Export-Pfade pruefen und anpassen

Pruefe alle Pfade, die Content ausserhalb von `publish/query/fetch` serialisieren oder wiederherstellen, insbesondere:

- `PostDumpStreamIO`
- `ImportCommand`
- Privacy-Snapshot-Freeze/Restore

Wenn diese Pfade Content vollstaendig transportieren oder restaurieren, muessen sie auch diese Daten mitnehmen:

- `authorPublicKey`
- `authorSignature`
- `signatureVersion`
- falls erforderlich weiterhin `authorGID`

### 2. Konsistenz fuer Restore/Import sicherstellen

Importierte oder restaurierte Inhalte duerfen nicht in einen Zustand geraten, in dem der Browser-Client sie spaeter wegen fehlender Signaturdaten verwirft.

Falls ein Pfad Inhalte nur teilweise exportiert, muss das explizit im Code oder in der Spezifikation klar sein. Keine stillschweigende Datenverkuerzung.

### 3. RAML aktualisieren

Aktualisiere [mosa-pod-api.raml](/home/kai/Projects/straightway/mosa/spec/mosa-pod-api.raml):

- `PublishRequest`
- `ReplyRequest`
- `FetchResultEntry`
- `QueryResultEntry`
- ggf. Fehlerbeschreibungen fuer Signaturfehler bei `/publish` und `/reply`

## Nicht Teil dieser Unteraufgabe

- Keine neue fachliche Logik fuer Signieren oder Verifizieren
- Keine Client-UI-Aenderungen

## Tests

Schreibe die minimalen Tests dort, wo diese Pfade bereits testgetrieben abgesichert sind:

1. Export/Restore verliert die Signaturmetadaten nicht.
2. Import uebernimmt die neuen Pflichtmetadaten korrekt.
3. RAML spiegelt die implementierten DTOs wider.

## Abnahmekriterien

- Kein relevanter Content-Pfad verliert Signaturdaten.
- Restore/Import erzeugen weiterhin vom Client verifizierbare Inhalte.
- Die API-Spezifikation ist mit der Implementierung konsistent.

