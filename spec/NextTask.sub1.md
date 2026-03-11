# Next Task Subtask 1: Shared Signaturmodell und DTO-Erweiterung

## Ziel

Fuehre ein gemeinsames Signaturmodell fuer Posts und Replies ein und erweitere die Shared-DTOs so, dass Requests Signaturen senden und Responses alle fuer die spaetere Verifikation noetigen Daten enthalten.

Diese Unteraufgabe umfasst nur `shared` und reine Modellierung. Keine Server- oder Clientlogik implementieren.

## Kontext

- Posts und Replies sollen als Inhalt signiert werden.
- Die Signatur basiert auf einem kanonischen Payload.
- Die GID ist aus dem Public Key ableitbar und keine eigenstaendige Vertrauensquelle.
- CLI ist nicht im Scope.

## Umzusetzen

### 1. Gemeinsames Payload-Modell einfuehren

Lege in `shared` ein Modell fuer den zu signierenden Payload an, z. B.:

- `ContentSignaturePayload`
- `ContentKind`
- `ContentSignatureVersion`

Der Payload muss diese Felder enthalten:

- `v`
- `kind`
- `text`
- `rootId`

Anforderungen:

- Feste, deterministische Serialisierung.
- Root-Posts verwenden `rootId = ""`.
- Replies verwenden `rootId = <root-id>`.
- Keine Kommentare im Code.

### 2. Gemeinsame Kanonisierung kapseln

Stelle eine kleine Shared-API bereit, die aus Post- oder Reply-Daten genau die Bytefolge erzeugt, die spaeter signiert bzw. verifiziert wird.

Beispielhafte API:

- `ContentSignaturePayload.forPost(text)`
- `ContentSignaturePayload.forReply(text, rootId)`
- `ContentSignaturePayload.toCanonicalBytes()`

Wichtig:

- Keine ad-hoc String-Konkatenation an Call-Sites.
- Die Bytefolge muss auf allen Plattformen identisch sein.

### 3. Schreibende DTOs erweitern

Erweitere:

- `PublishRequest`
- indirekt auch `ReplyRequest`, da dort `PublishRequest` verwendet wird

`PublishRequest` soll enthalten:

- `text`
- `signature`
- `signatureVersion`

### 4. Lesende DTOs erweitern

Erweitere:

- `FetchResult.Entry`
- `QueryResult.Entry`

um:

- `authorPublicKey`
- `authorSignature`
- `signatureVersion`

`authorGID` bleibt vorerst erhalten, weil bestehende UI- und Datenfluesse davon abhaengen.

## Nicht Teil dieser Unteraufgabe

- Keine Server-Verifikation
- Kein Browser-Signing
- Keine Client-Verifikation
- Keine RAML-Aktualisierung

## Tests

Schreibe minimale Shared-Tests fuer:

1. Root-Post-Payload wird deterministisch serialisiert.
2. Reply-Payload enthaelt die `rootId`.
3. Root-Post und Reply erzeugen unterschiedliche kanonische Bytes.
4. `rootId = ""` bei Root-Posts ist stabil.
5. DTO-Serialisierung enthaelt die neuen Felder.

## Abnahmekriterien

- Gemeinsames Signaturmodell liegt in `shared`.
- Requests koennen Signatur und Version transportieren.
- Responses koennen Public Key, Signatur und Version transportieren.
- Die Kanonisierung ist zentral gekapselt und getestet.

