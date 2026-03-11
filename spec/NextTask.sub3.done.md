# Next Task Subtask 3: Persistenz und Lese-DTOs fuer Signaturdaten

## Ziel

Der Pod soll Signaturdaten zusammen mit dem Content speichern und bei `fetch` und `query` wieder ausliefern, damit Clients spaeter selbst verifizieren koennen.

## Voraussetzungen

- `NextTask.sub1.md`
- `NextTask.sub2.md`

## Umzusetzen

### 1. Content-Metadaten erweitern

Erweitere die Content-Metadaten um:

- `authorPublicKey`
- `authorSignature`
- `signatureVersion`

Pruefe die betroffenen Stellen:

- `ContentMetadataKeys`
- `ContentMetadataField`
- Hilfsfunktionen fuer Pflichtfelder

### 2. Beim Schreiben persistieren

Passe `ContentService.writeContent(...)` so an, dass beim Speichern neuer Inhalte mindestens folgende Metadaten geschrieben werden:

- `authorGID`
- `authorPublicKey`
- `authorSignature`
- `signatureVersion`
- `receivedAt`
- optional `rootId`

Wichtig:

- `authorGID` wird vom Server aus dem authentifizierten Public Key abgeleitet oder aus dem bereits geprueften Principal uebernommen, nicht aus dem Request-Body.

### 3. Lesepfade erweitern

Passe die Rueckgabe von:

- `fetch`
- `query`

an, sodass `FetchResult.Entry` und `QueryResult.Entry` die neuen Signaturfelder enthalten.

### 4. Query-/Fetch-Mapping absichern

Fehlende Signaturmetadaten muessen wie andere Pflichtmetadaten behandelt werden.

Das heisst:

- nicht stillschweigend `null`
- keine stillschweigende Legacy-Behandlung

## Nicht Teil dieser Unteraufgabe

- Keine Client-Verifikation
- Keine Browser-Signaturerzeugung
- RAML nur dann anpassen, wenn es fuer Tests zwingend notwendig ist

## Tests

Schreibe oder erweitere Tests fuer:

1. Nach `publish` werden die Signaturmetadaten gespeichert.
2. `fetch` liefert `authorPublicKey`, `authorSignature`, `signatureVersion`.
3. `query` liefert dieselben Daten.
4. Fehlende Pflichtmetadaten fuehren zu einem Fehler statt zu stiller Akzeptanz.

## Abnahmekriterien

- Signaturdaten sind persistent im Content gespeichert.
- `fetch` und `query` liefern sie zurueck.
- Es gibt keinen unsichtbaren Fallback ohne Signaturmetadaten.

