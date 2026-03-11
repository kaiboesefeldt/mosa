# Next Task Subtask 2: Server-Principal und Signaturpruefung beim Empfang

## Ziel

Der Pod soll `POST /v1/publish` und `POST /v1/reply` nur noch akzeptieren, wenn die Inhaltssignatur kryptographisch zum authentifizierten Autor passt.

Diese Unteraufgabe umfasst Auth-Schicht, Controller-/Service-Eingang und Verifikation beim Empfang. Noch keine Response-Erweiterung fuer Lesepfade.

## Voraussetzungen

- Shared-Signaturmodell und DTO-Erweiterungen aus `NextTask.sub1.md` sind umgesetzt.

## Umzusetzen

### 1. Authentifizierten Public Key verfuegbar machen

Erweitere den Auth-Pfad so, dass der aus dem MIS-JWT gelesene Public Key im Principal landet.

Ziel:

- `PodPrincipal` enthaelt mindestens:
  - `certificate`
  - `publicKeyBase64`

Wichtig:

- `publicKeyBase64` kommt aus dem bereits validierten JWT.
- Keine zweite Quelle fuer den Public Key einfuehren.

### 2. Serverseitige GID-Ableitung verwenden

Leite die GID fuer die Inhaltspruefung serverseitig aus `publicKeyBase64` ab.

Anforderungen:

- `derivedGid = SHA-256(publicKeyRaw)` base64url-kodiert
- `derivedGid` muss exakt der GID aus dem Zertifikat entsprechen

Die Request-Bodies liefern keine eigene GID und keinen eigenen Public Key.

### 3. Signaturpruefer fuer Inhalte einfuehren

Fuehre eine Server-Komponente ein, die:

- den kanonischen Payload fuer Post oder Reply erzeugt
- die Signatur base64url-dekodiert
- mit Ed25519 gegen `publicKeyBase64` prueft

Empfehlung:

- Eine eigene kleine Klasse wie `ContentSignatureVerifier`
- Keine Verifikationslogik direkt im Controller verteilen

### 4. Empfangspfad absichern

`/publish` und `/reply` muessen vor dem Speichern pruefen:

1. Signaturversion unterstuetzt?
2. Public Key aus Principal gueltig?
3. Abgeleitete GID passt zum Zertifikat?
4. Signatur passt zum Payload?

Nur dann darf `ContentService.writeContent(...)` laufen.

### 5. Fehlerverhalten festlegen

Vorgabe:

- unbekannte `signatureVersion` => `422`
- Signatur formal unlesbar => `400`
- Signatur kryptographisch ungueltig => `422`

Nutze bestehende Exception-Uebersetzung bzw. ergaenze sie minimal und konsistent.

## Nicht Teil dieser Unteraufgabe

- Noch keine Persistenz der Signaturmetadaten
- Noch keine Erweiterung von `fetch/query`
- Noch keine Client-seitige Verifikation

## Tests

Schreibe Tests ueber oeffentliches Verhalten:

1. `publish` mit gueltiger Signatur liefert `201`.
2. `publish` mit falscher Signatur wird abgewiesen.
3. `reply` mit gueltiger Signatur liefert `201`.
4. `reply` mit Signatur fuer falsche `rootId` wird abgewiesen.
5. Unbekannte `signatureVersion` wird abgewiesen.
6. Public Key und Zertifikat-GID muessen zusammenpassen.

Nutze vorhandene Auth-/HTTP-Testinfrastruktur.

## Abnahmekriterien

- Der Principal liefert den authentifizierten Public Key.
- Der Server akzeptiert neue Posts/Replies nur noch mit gueltiger Inhaltssignatur.
- GID wird serverseitig aus dem Public Key abgeleitet und geprueft.
- Fehlerfaelle sind per HTTP-Test abgedeckt.

