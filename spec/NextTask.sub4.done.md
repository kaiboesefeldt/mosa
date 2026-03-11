# Next Task Subtask 4: Browser-Client signiert Posts und Replies vor dem Senden

## Ziel

Der Browser-Client soll neue Posts und Replies vor dem Senden mit dem privaten Ed25519-Schluessel der aktuellen Identitaet signieren.

## Voraussetzungen

- `NextTask.sub1.md`
- `NextTask.sub2.md`

## Umzusetzen

### 1. Clientseitigen Signierer einfuehren

Fuehre eine kleine Client-Komponente ein, z. B. `ContentSigner`, die:

- aus Text und optional `rootId` den Shared-Payload baut
- die kanonischen Bytes erzeugt
- mit `SubtleCrypto.sign("Ed25519", ...)` signiert
- die Signatur base64url-kodiert

### 2. Publish-Pfad anpassen

Passe `PostPublisher` an:

- Text trimmen wie bisher
- Payload fuer Root-Post bauen
- Signatur erzeugen
- `PublishRequest(text, signature, version)` senden

### 3. Reply-Pfad anpassen

Passe `ReplyPublisher` an:

- Text trimmen wie bisher
- Payload fuer Reply mit `rootId` bauen
- Signatur erzeugen
- `ReplyRequest(PublishRequest(...), rootId)` senden

### 4. Schluesselquelle klar halten

Die Signatur muss mit der aktuell aktiven Identitaet erfolgen, also mit dem Schluesselpaar, das auch fuer das MIS-JWT verwendet wird.

Es darf kein separates Inhalts-Schluesselpaar eingefuehrt werden.

## Nicht Teil dieser Unteraufgabe

- Keine Empfangsverifikation
- Kein Cache-Verhalten
- Keine CLI

## Tests

Schreibe oder erweitere Client-Tests fuer:

1. `PostPublisher` sendet `signature` und `signatureVersion`.
2. `ReplyPublisher` signiert unter Einbezug der `rootId`.
3. Leerer oder nur whitespacehaltiger Text wird weiterhin nicht gesendet.
4. Unterschiedliche `rootId` fuehren zu unterschiedlicher Signatur.

Nutze vorhandene Test-Doubles fuer `SubtleCrypto`, `PodSessionProvider` und `PodAccess`.

## Abnahmekriterien

- Jeder gesendete Post enthaelt eine gueltig erzeugte Signatur.
- Jede gesendete Reply enthaelt eine gueltig erzeugte Signatur fuer genau diese `rootId`.
- Die Signierlogik ist gekapselt und nicht ueber mehrere UI-/Publisher-Klassen verteilt.

