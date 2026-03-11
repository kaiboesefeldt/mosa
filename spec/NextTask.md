# Konzept: Autorensignaturen fuer Posts und Replies

## Ziel

Posts und Replies sollen nicht nur ueber MIS-JWT transport-authentifiziert sein, sondern als Inhalte selbst kryptographisch vom Autor signiert werden.

Das System soll drei Dinge erzwingen:

1. Der Client signiert Post oder Reply vor dem Senden mit dem privaten Ed25519-Schluessel der Identitaet.
2. Der Pod prueft diese Signatur beim Empfang und weist den Request bei Fehlschlag zurueck.
3. Clients pruefen empfangene Posts und Replies erneut und verarbeiten nur gueltige Inhalte weiter.

## Ausgangslage

Derzeit bindet das MIS-JWT den Request an eine Identitaet:

- Das JWT enthaelt `pub` und ein vom Pod signiertes Zertifikat `cert`.
- Der Pod validiert JWT-Signatur, Zertifikat und die Bindung `gid = SHA-256(pub)`.
- Der Content selbst ist aber nicht separat signiert.

Konsequenz:

- Nach dem Speichern ist fuer spaetere Leser kryptographisch nicht mehr nachweisbar, dass genau dieser Text vom Autor stammt.
- `FetchResult.Entry` und `QueryResult.Entry` enthalten heute nicht die Daten, die ein Client fuer eine Inhaltspruefung braucht.

## Leitentscheidung

MIS-JWT bleibt fuer Session- und Transport-Authentifizierung zustaendig.

Zusaetzlich bekommt jeder Post und jede Reply eine eigene, persistierte Inhaltssignatur. Der Pod uebernimmt dabei nicht die Rolle des Inhaltssignierers, sondern nur die Rolle des Verifizierers und Speichers.

## Empfohlenes Signaturmodell

### 1. Signiert wird ein kanonischer Inhalts-Payload

Der Autor signiert nicht den kompletten HTTP-Request und auch nicht serverseitige Felder wie `id` oder `storedAt`, weil diese beim Signieren noch nicht feststehen.

Empfohlen wird ein eigener kanonischer Payload `ContentSignaturePayloadV1`:

```json
{
  "v": 1,
  "kind": "post",
  "text": "Hello",
  "rootId": ""
}
```

Fuer Replies:

```json
{
  "v": 1,
  "kind": "reply",
  "text": "I agree",
  "rootId": "root-123"
}
```

Regeln:

- UTF-8 kodieren.
- Feste Feldreihenfolge verwenden.
- `rootId` fuer Root-Posts immer als leerer String serialisieren, nicht als fehlendes Feld.
- Die Payload-Erzeugung in gemeinsamen Shared-Code kapseln, nicht per ad-hoc String-Konkatenation an mehreren Stellen.

Der Pod und alle Clients muessen exakt dieselbe Bytefolge erzeugen.

### 2. Die Signatur verwendet den gleichen Autorenschluessel wie das MIS-JWT

Der sendende Client signiert mit demselben privaten Ed25519-Schluessel, dessen oeffentlicher Schluessel bereits im MIS-JWT als `pub` transportiert wird.

Vorteile:

- Kein zweites Schluesselpaar.
- Der Pod kann die Inhaltssignatur mit dem bereits authentifizierten `pub` pruefen.
- Die Bindung Autor <-> Schluessel bleibt konsistent.

### 3. Der Pod speichert die fuer spaetere Verifikation noetigen Nachweise

Damit spaetere Leser die Signatur selbst pruefen koennen, muss der Pod bei jedem gespeicherten Inhalt mindestens diese Felder persistieren:

- `authorPublicKey`
- `authorSignature`
- `signatureVersion`
- `rootId` fuer Replies
- `receivedAt`

`authorGID` kann weiterhin als abgeleitete, redundante Information gespeichert oder in Responses mitgegeben werden, sollte aber nicht mehr die primaere Vertrauensbasis sein. Die primaere Vertrauensbasis ist der oeffentliche Schluessel des Autors plus die daraus deterministisch abgeleitete GID.

Die Signatur wird nicht nur transient beim Request geprueft, sondern als Teil der Content-Metadaten gespeichert.

## API-Aenderungen

### Schreibende Requests

`PublishRequest` sollte um eine Inhaltssignatur erweitert werden:

```kotlin
@Serializable
data class PublishRequest(
    val text: String,
    val signature: String,
    val signatureVersion: Int = 1,
)
```

`ReplyRequest` bleibt strukturell gleich, weil es bereits `PublishRequest` verschachtelt:

```kotlin
@Serializable
data class ReplyRequest(
    val content: PublishRequest,
    val rootId: String,
)
```

Empfehlung:

- `authorPublicKey` nicht im Request mitsenden, sondern aus dem validierten MIS-JWT beziehen.
- `authorGID` ebenfalls nicht im Request mitsenden, sondern serverseitig aus `authorPublicKey` ableiten.
- Dadurch gibt es keine Redundanz und keine Inkonsistenz zwischen Body und Authorization-Header.

### Lesende Responses

Alle DTOs, die kompletten Inhalt transportieren, muessen die Verifikationsdaten mitliefern:

- `FetchResult.Entry`
- `QueryResult.Entry`

Empfohlene Felder:

```kotlin
val authorPublicKey: String
val authorSignature: String
val signatureVersion: Int
```

`authorGID` kann in Responses erhalten bleiben, sollte aber vom empfangenden Client immer gegen `SHA-256(authorPublicKeyRaw)` gegengeprueft werden statt isoliert vertraut zu werden.

`SearchResult` braucht keine Erweiterung, weil dort nur IDs und Scores transportiert werden.

## Server-Verhalten beim Empfang

### Auth-Schicht erweitern

Heute liefert `PodPrincipal` nur das Zertifikat bzw. die `gid`.

Empfehlung:

- `PodPrincipal` um den authentifizierten oeffentlichen Schluessel aus dem JWT erweitern.
- Die JWT-Pruefung bleibt in `Auth0TokenUtil`.
- `ContentController` oder `ContentService` bekommt damit direkten Zugriff auf `gid` und `authorPublicKey`.

Beispiel:

```kotlin
data class PodPrincipal(
    val certificate: PodCertificate,
    val publicKeyBase64: String,
)
```

### Verifikation bei `/publish` und `/reply`

Pruefablauf:

1. MIS-JWT wie bisher validieren.
2. Kanonischen Payload fuer Post oder Reply bilden.
3. `signatureVersion` pruefen.
4. `signature` base64url-dekodieren.
5. Mit `publicKeyBase64` aus dem authentifizierten Principal die Ed25519-Signatur pruefen.
6. `gid` serverseitig aus `publicKeyBase64` ableiten.
7. Zusaetzlich pruefen, dass dieses abgeleitete `gid` exakt `certificate.gid` entspricht.
7. Nur bei Erfolg speichern.

Fehlschlaege:

- Ungueltiges JWT: `401 Unauthorized` wie bisher.
- Unbekannte `signatureVersion`: `422 Unprocessable Entity`.
- Ungueltige base64url-Signatur oder ungueltiges Format: `400 Bad Request`.
- Kryptographische Signatur prueft nicht gegen Payload und Author-Key: `422 Unprocessable Entity`.

Wichtig:

- Keine stillschweigende Akzeptanz unsignierter Inhalte.
- Keine serverseitige Ersatzsignatur.

## Persistenz im Pod

`ContentMetadataKeys` sollte erweitert werden um:

- `AUTHOR_PUBLIC_KEY_KEY = "authorPublicKey"`
- `AUTHOR_SIGNATURE_KEY = "authorSignature"`
- `SIGNATURE_VERSION_KEY = "signatureVersion"`

`ContentMetadataField` muss diese Felder ebenfalls kennen, damit fehlende Pflichtmetadaten sauber erkannt werden.

`ContentService.writeContent(...)` speichert dann zusaetzlich:

- `AUTHOR_PUBLIC_KEY_KEY`
- `AUTHOR_SIGNATURE_KEY`
- `SIGNATURE_VERSION_KEY`
- `RECEIVED_AT_KEY`
- optional `ROOT_ID_KEY`

`AUTHOR_GID_KEY` kann entweder:

- weiterhin explizit gespeichert werden, aber nur als vom Server aus `AUTHOR_PUBLIC_KEY_KEY` abgeleitete Redundanz, oder
- perspektivisch ganz aus dem persistierten Content entfallen, falls keine Query- oder Exportpfade davon abhaengen.

Fuer die Einfuehrung ist die pragmatischere Variante, `AUTHOR_GID_KEY` vorerst beizubehalten, aber niemals aus dem Request zu uebernehmen.

## Client-Verhalten beim Senden

### Browser-Client

Beim Publizieren:

1. Text trimmen wie bisher.
2. Signatur-Payload `ContentSignaturePayloadV1(kind = "post", text = text, rootId = "")` bauen.
3. Payload mit `subtleCrypto.sign("Ed25519", identity.keyPair.privateKey, payloadBytes)` signieren.
4. `PublishRequest(text, signature, 1)` senden.

Beim Reply:

1. Payload `ContentSignaturePayloadV1(kind = "reply", text = text, rootId = rootId)` bauen.
2. Signieren.
3. `ReplyRequest(PublishRequest(text, signature, 1), rootId)` senden.

### Client-Scope

Die CLI wird hier ignoriert und gehoert nicht mehr zum Scope dieser Aufgabe.

Die Umsetzung betrifft nur:

- Browser-Client
- Pod
- geteilte DTOs und gemeinsames Signaturmodell in `shared`

## Client-Verhalten beim Empfang

### Was verifiziert werden muss

Jeder Client muss alle empfangenen Posts und Replies verifizieren, bevor sie in Cache, UI oder weitere Verarbeitung gelangen.

Das betrifft mindestens:

- `FetchResult.Entry`
- `QueryResult.Entry`

### Verifikationsregeln

Fuer jeden Eintrag:

1. `authorPublicKey` base64url-dekodieren.
2. `SHA-256(authorPublicKeyRaw)` berechnen.
3. Falls `authorGID` im DTO vorhanden ist, muss das Ergebnis exakt `authorGID` ergeben.
4. Kanonischen Payload aus `content`, `rootId` und `kind` bilden.
5. `authorSignature` per Ed25519 gegen diesen Payload pruefen.

`kind` wird aus `rootId` abgeleitet:

- `rootId == null` oder leer => Post
- sonst => Reply

### Verhalten bei Fehlschlag

Empfehlung fuer normale Clients:

- Ungueltige Eintraege nicht cachen.
- Ungueltige Eintraege nicht rendern.
- Den Vorfall loggen.
- In der UI eine knappe Integritaetswarnung anzeigen, wenn durch Verifikation Eintraege weggefiltert wurden.

Das ist bewusst fail-closed. Wenn der Pod bereits bei der Annahme prueft, deutet ein spaeterer Fehlschlag auf Datenkorruption, fehlerhafte Migration oder kompromittierte Daten hin.

## Auswirkungen auf die bestehende Client-Architektur

### Browser-Cache

`CachedPost` sollte um die Verifikationsdaten erweitert werden:

- `authorPublicKey`
- `authorSignature`
- `signatureVersion`

`authorGID` kann fuer bestehende UI- und Sortierpfade erhalten bleiben, muss aber als aus dem Public Key abgeleitete Information behandelt werden.

Nur erfolgreich verifizierte Eintraege duerfen in `IndexedDbPostCache` und `IndexedDbReplyCache` landen.

### Search- und Fetch-Flows

Aktuell holen `TopicSearchService` und `ReplySearchService` Inhalte ueber `fetch(...)`.

Dort sollte die Verifikation unmittelbar nach dem Mapping von `FetchResult.Entry` stattfinden, bevor:

- ein `CachedPost` erzeugt wird
- in den Cache geschrieben wird
- die UI Daten sieht

Falls spaeter `QueryResult.Entry` direkt fuer UI genutzt wird, gilt dort dieselbe Regel.

## Migration

Migration ist hier nicht relevant, weil noch keine Bestandsdaten im Feld existieren.

Daraus folgt:

- Es gibt keinen Legacy-Modus fuer unsignierte Inhalte.
- Der Pod akzeptiert ab Einfuehrung nur noch signierte Posts und Replies.
- Clients verarbeiten ab Einfuehrung nur noch erfolgreich verifizierte Inhalte.

Das System startet damit direkt fail-closed.

## Nebeneffekte auf Export, Import und Snapshots

Die Signaturmetadaten muessen ueberall mitgenommen werden, wo Content serialisiert oder restauriert wird:

- `PostDumpStreamIO`
- `ImportCommand`
- CSV-Export, falls er als vollstaendige Wiederherstellungsquelle gedacht ist
- Privacy-Snapshot-Freeze/Restore

Wenn diese Pfade die neuen Metadaten nicht transportieren, entstehen nach Restore oder Import Inhalte, die Clients spaeter als ungueltig verwerfen.

## Empfohlene Implementierungsreihenfolge

1. Shared Payload-Codec fuer `ContentSignaturePayloadV1` einfuehren und testen.
2. Browser-Client: Signatur beim Publish/Reply erzeugen.
3. Server: Empfangsverifikation fuer `/publish` und `/reply` erzwingen.
4. Persistenz und Response-DTOs um Public Key, Signatur und Version erweitern.
5. Browser-Client: Empfangsverifikation vor Cache/UI einbauen.
6. Import/Export/Snapshot-Pfade nachziehen.
7. RAML-Spezifikation aktualisieren.

## Teststrategie

### Shared

- deterministische Tests fuer den kanonischen Payload
- Tests fuer `post` vs. `reply`
- Tests fuer `rootId = ""` vs. gesetzte `rootId`

### Client

- `PostPublisherTest`: signiert Payload und sendet Signatur mit
- `ReplyPublisherTest`: signiert mit `rootId`
- `TopicSearchServiceTest` und `ReplySearchServiceTest`: verwerfen ungueltige Eintraege
- Cache-Tests: nur verifizierte Inhalte werden persistiert

### Server

- `ContentControllerTest` oder HTTP-Tests:
  - `publish` mit gueltiger Signatur => `201`
  - `publish` mit falscher Signatur => `422`
  - `reply` mit falscher Signatur => `422`
  - `reply` mit gueltiger Signatur und falschem `rootId` im signierten Payload => `422`
- `ContentServiceTest`:
  - speichert Signaturmetadaten
  - liefert Signaturmetadaten ueber `fetch/query` zurueck

### Integration

- Ende-zu-Ende-Test: Client signiert, Pod verifiziert, anderer Client verifiziert beim Lesen erfolgreich.

## Kurzfazit

Die sauberste Loesung ist:

- MIS-JWT weiter fuer Request-Authentifizierung behalten
- zusaetzlich jeden Post und jede Reply als eigenen Inhalt signieren
- den Author-Public-Key aus dem authentifizierten JWT fuer die Serverpruefung verwenden
- die GID nicht als eigenstaendige Vertrauensquelle behandeln, sondern aus dem Public Key ableiten und nur dagegen querpruefen
- Public Key, Signatur und Signaturversion als Content-Metadaten persistieren und in Responses mitliefern
- Clients fail-closed nur verifizierte Inhalte anzeigen und cachen

Damit ist kryptographisch nachweisbar, dass der konkrete Inhalt vom behaupteten Autor stammt, auch noch lange nach dem urspruenglichen Request.
